import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * API HTTP/JSON do catálogo musical, consumida pela interface web em {@code frontend/}.
 *
 * Usa o servidor HTTP embutido no JDK ({@code com.sun.net.httpserver}) para não adicionar
 * framework web ao projeto. Todas as rotas ficam sob {@code /api}; o contrato completo está
 * em {@code docs/API.md}. Configuração por variáveis de ambiente:
 * <ul>
 *   <li>{@code API_HOST} (padrão 127.0.0.1) e {@code API_PORT} (padrão 8080);</li>
 *   <li>{@code API_ALLOWED_ORIGINS}: origens liberadas no CORS, separadas por vírgula
 *       (padrão http://localhost:3000 e http://127.0.0.1:3000);</li>
 *   <li>credenciais do SoundCloud, descritas em {@link SoundCloudSearchService}.</li>
 * </ul>
 * O estado (catálogo, favoritos, playlists) fica em memória e é perdido ao reiniciar.
 * Não há autenticação: o servidor escuta só em 127.0.0.1 por padrão e não deve ser
 * exposto publicamente sem antes adicionar autenticação.
 */
public final class ApiServer {
    static final int MAX_BODY_BYTES = 16 * 1024;
    private static final String DEFAULT_ORIGINS = "http://localhost:3000,http://127.0.0.1:3000";

    private final MusicLibrary library;
    private final DiscoveryApi discovery;
    private final Set<String> allowedOrigins;
    private final ObjectMapper json = new ObjectMapper();
    private HttpServer server;
    private ExecutorService executor;

    ApiServer(MusicLibrary library, DiscoveryApi discovery, Set<String> allowedOrigins) {
        this.library = library;
        this.discovery = discovery;
        this.allowedOrigins = Set.copyOf(allowedOrigins);
    }

    /** Inicia a API com a configuração do ambiente e encerra de forma limpa no Ctrl+C. */
    public static void main(String[] args) throws IOException {
        String host = envOrDefault("API_HOST", "127.0.0.1");
        int port = Integer.parseInt(envOrDefault("API_PORT", "8080"));
        ApiServer api = new ApiServer(new MusicLibrary(), new DiscoveryApi(),
                parseOrigins(envOrDefault("API_ALLOWED_ORIGINS", DEFAULT_ORIGINS)));
        api.start(host, port);
        Runtime.getRuntime().addShutdownHook(new Thread(api::stop));
        System.out.println("API do catálogo musical em http://" + host + ":" + api.port() + "/api");
        System.out.println("SoundCloud configurado: " + (api.discovery.soundCloudConfigured() ? "sim" : "não"));
    }

    /** Abre o socket e começa a atender; porta 0 escolhe uma porta livre (útil em testes). */
    public void start(String host, int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(host, port), 0);
        executor = Executors.newFixedThreadPool(16);
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();
    }

    /** Porta efetivamente usada pelo servidor. */
    public int port() {
        return server.getAddress().getPort();
    }

    /** Para de aceitar conexões e aguarda brevemente as requisições em andamento. */
    public void stop() {
        if (server != null) server.stop(1);
        if (executor != null) {
            executor.shutdown();
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // --------------------------------------------------------------- roteamento

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            addCommonHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                // Preflight do CORS: os cabeçalhos já foram adicionados acima.
                send(exchange, 204, null);
                return;
            }
            Response response;
            try {
                response = route(exchange);
            } catch (DiscoveryApi.ApiError e) {
                response = error(e.status(), e.getMessage());
            } catch (IllegalArgumentException e) {
                response = error(400, e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                response = error(503, "O servidor está encerrando. Tente novamente.");
            } catch (RuntimeException e) {
                // Não expõe detalhes internos; o stack trace fica apenas no log do servidor.
                e.printStackTrace();
                response = error(500, "Erro interno no servidor.");
            }
            if (response.allow() != null) {
                exchange.getResponseHeaders().set("Allow", response.allow());
            }
            send(exchange, response.status(), response.body());
        }
    }

    private Response route(HttpExchange exchange) throws IOException, InterruptedException {
        String method = exchange.getRequestMethod();
        URI uri = exchange.getRequestURI();
        List<String> path = segments(uri.getRawPath());
        Map<String, String> query = queryParams(uri.getRawQuery());

        if (path.isEmpty() || !path.get(0).equals("api")) {
            return error(404, "Rota não encontrada.");
        }
        List<String> rest = path.subList(1, path.size());
        String root = rest.isEmpty() ? "" : rest.get(0);
        return switch (root) {
            case "health" -> rest.size() == 1 ? health(method) : notFound();
            case "tracks" -> tracks(method, rest, query, exchange);
            case "favorites" -> favorites(method, rest);
            case "playlists" -> playlists(method, rest, exchange);
            case "discovery" -> discovery(method, rest, query);
            default -> notFound();
        };
    }

    private Response health(String method) {
        if (!method.equals("GET")) return methodNotAllowed("GET");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("soundCloudConfigured", discovery.soundCloudConfigured());
        return ok(body);
    }

    /** /api/tracks e /api/tracks/{id}. */
    private Response tracks(String method, List<String> rest, Map<String, String> query, HttpExchange exchange) {
        // O catálogo de demonstração é somente leitura pela API. A descoberta externa
        // possui rotas próprias e nunca cadastra/remove faixas deste catálogo.
        if (rest.size() == 1) {
            if (method.equals("GET")) return ok(Map.of("tracks", library.listTracks(query.get("q"))));
            return methodNotAllowed("GET");
        }
        if (rest.size() == 2) {
            if (!method.equals("GET")) return methodNotAllowed("GET");
            String id = rest.get(1);
            return library.findTrack(id).map(ApiServer::ok)
                    .orElseGet(() -> error(404, "Faixa não encontrada."));
        }
        return notFound();
    }

    /** /api/favorites e /api/favorites/{trackId}. */
    private Response favorites(String method, List<String> rest) {
        if (rest.size() == 1) {
            return method.equals("GET") ? ok(Map.of("tracks", library.listFavorites())) : methodNotAllowed("GET");
        }
        if (rest.size() == 2) {
            String trackId = rest.get(1);
            Optional<Boolean> changed;
            if (method.equals("PUT")) changed = library.addFavorite(trackId);
            else if (method.equals("DELETE")) changed = library.removeFavorite(trackId);
            else return methodNotAllowed("PUT, DELETE");
            if (changed.isEmpty()) return error(404, "Faixa não encontrada.");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("trackId", trackId);
            body.put("favorite", method.equals("PUT"));
            body.put("changed", changed.get());
            return ok(body);
        }
        return notFound();
    }

    /** /api/playlists, /api/playlists/{id} e /api/playlists/{id}/tracks/{trackId}. */
    private Response playlists(String method, List<String> rest, HttpExchange exchange) throws IOException {
        if (rest.size() == 1) {
            if (method.equals("GET")) return ok(Map.of("playlists", library.listPlaylists()));
            if (method.equals("POST")) {
                return new Response(201, library.createPlaylist(text(readJson(exchange), "name")), null);
            }
            return methodNotAllowed("GET, POST");
        }
        int id = parsePlaylistId(rest.get(1));
        if (rest.size() == 2) {
            if (method.equals("GET")) {
                return library.findPlaylist(id).map(ApiServer::ok)
                        .orElseGet(() -> error(404, "Playlist não encontrada."));
            }
            if (method.equals("DELETE")) {
                return library.deletePlaylist(id) ? noContent() : error(404, "Playlist não encontrada.");
            }
            return methodNotAllowed("GET, DELETE");
        }
        if (rest.size() == 4 && rest.get(2).equals("tracks")) {
            String trackId = rest.get(3);
            MusicLibrary.PlaylistChange change;
            if (method.equals("PUT")) change = library.addToPlaylist(id, trackId);
            else if (method.equals("DELETE")) change = library.removeFromPlaylist(id, trackId);
            else return methodNotAllowed("PUT, DELETE");
            return switch (change.status()) {
                case OK -> ok(Map.of("changed", change.changed(), "playlist", change.playlist()));
                case PLAYLIST_NOT_FOUND -> error(404, "Playlist não encontrada.");
                case TRACK_NOT_FOUND -> error(404, method.equals("PUT")
                        ? "Faixa não encontrada no catálogo." : "Essa faixa não está na playlist.");
            };
        }
        return notFound();
    }

    /** /api/discovery/search e /api/discovery/verify (provedores externos). */
    private Response discovery(String method, List<String> rest, Map<String, String> query)
            throws InterruptedException {
        if (rest.size() != 2) return notFound();
        if (!method.equals("GET")) return methodNotAllowed("GET");
        return switch (rest.get(1)) {
            case "search" -> ok(discovery.search(query.get("q"),
                    parseIntParam(query.get("limit"), DiscoveryApi.DEFAULT_SEARCH_LIMIT, "limit")));
            case "verify" -> ok(discovery.verify(query.get("title"), query.get("artist"),
                    parseDuration(query.get("duration"))));
            default -> notFound();
        };
    }

    // ------------------------------------------------------------------- apoio

    private void addCommonHeaders(HttpExchange exchange) {
        var headers = exchange.getResponseHeaders();
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Cache-Control", "no-store");
        headers.add("Vary", "Origin");
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin != null && allowedOrigins.contains(origin)) {
            headers.set("Access-Control-Allow-Origin", origin);
            headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            headers.set("Access-Control-Allow-Headers", "Content-Type");
            headers.set("Access-Control-Max-Age", "600");
        }
    }

    private void send(HttpExchange exchange, int status, Object body) throws IOException {
        if (body == null) {
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        byte[] bytes = json.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** Lê o corpo JSON com limite de tamanho; corpo ausente, grande ou inválido gera 400/413. */
    private JsonNode readJson(HttpExchange exchange) throws IOException {
        byte[] bytes;
        try (InputStream in = exchange.getRequestBody()) {
            bytes = in.readNBytes(MAX_BODY_BYTES + 1);
        }
        if (bytes.length > MAX_BODY_BYTES) {
            throw new DiscoveryApi.ApiError(413, "O corpo da requisição é grande demais.");
        }
        try {
            JsonNode node = json.readTree(bytes);
            if (node == null || !node.isObject()) {
                throw new IllegalArgumentException("Envie um objeto JSON no corpo da requisição.");
            }
            return node;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("O corpo da requisição não é um JSON válido.");
        }
    }

    private static String text(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) throw new IllegalArgumentException("O campo " + field + " deve ser texto.");
        return value.asText();
    }

    private static int requireInt(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || !value.canConvertToInt() || !value.isIntegralNumber()) {
            throw new IllegalArgumentException("O campo " + field + " deve ser um número inteiro.");
        }
        return value.asInt();
    }

    private static int parsePlaylistId(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new DiscoveryApi.ApiError(404, "Playlist não encontrada.");
        }
    }

    private static int parseIntParam(String raw, int defaultValue, String name) {
        if (raw == null || raw.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(raw.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("O parâmetro " + name + " deve ser um número inteiro.");
        }
    }

    /** Aceita segundos inteiros ou decimais (arredonda); ausente significa duração desconhecida. */
    private static int parseDuration(String raw) {
        if (raw == null || raw.isBlank()) return 0;
        try {
            double value = Double.parseDouble(raw.strip());
            if (!Double.isFinite(value) || value < 0 || value > MusicLibrary.MAX_DURATION_SECONDS) {
                throw new IllegalArgumentException("Duração inválida para verificação.");
            }
            return (int) Math.round(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Duração inválida para verificação.");
        }
    }

    /** Divide o caminho em segmentos decodificados, ignorando barras extras. */
    static List<String> segments(String rawPath) {
        if (rawPath == null) return List.of();
        return Arrays.stream(rawPath.split("/"))
                .filter(s -> !s.isEmpty())
                .map(s -> URLDecoder.decode(s.replace("+", "%2B"), StandardCharsets.UTF_8))
                .toList();
    }

    /** Converte a query string em mapa; parâmetros repetidos mantêm o primeiro valor. */
    static Map<String, String> queryParams(String rawQuery) {
        Map<String, String> params = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) return params;
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            String key = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            params.putIfAbsent(key, value);
        }
        return params;
    }

    static Set<String> parseOrigins(String raw) {
        return Arrays.stream(raw.split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value.strip();
    }

    private static Response ok(Object body) {
        return new Response(200, body, null);
    }

    private static Response noContent() {
        return new Response(204, null, null);
    }

    private static Response notFound() {
        return error(404, "Rota não encontrada.");
    }

    private static Response methodNotAllowed(String allow) {
        return new Response(405, Map.of("error", "Método não permitido nesta rota."), allow);
    }

    private static Response error(int status, String message) {
        return new Response(status, Map.of("error", message), null);
    }

    /** Resposta montada pelas rotas antes da serialização. */
    private record Response(int status, Object body, String allow) { }
}
