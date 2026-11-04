import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Pesquisa faixas na API oficial do SoundCloud.
 *
 * Duas formas de autenticação, lidas do ambiente (nunca do código ou do Git):
 * <ul>
 *   <li>{@code SOUNDCLOUD_CLIENT_ID} + {@code SOUNDCLOUD_CLIENT_SECRET} (recomendado): o serviço
 *       obtém um token pelo fluxo OAuth client credentials e o renova sozinho antes de expirar;</li>
 *   <li>{@code SOUNDCLOUD_ACCESS_TOKEN}: token fixo, útil para testes rápidos, mas o SoundCloud
 *       o expira em cerca de uma hora e ele precisa ser trocado manualmente.</li>
 * </ul>
 * Se as duas formas estiverem configuradas, client credentials tem prioridade.
 * A classe devolve metadados e URLs; a reprodução continua sujeita às regras do SoundCloud.
 */
public final class SoundCloudSearchService {
    private static final String API_BASE = "https://api.soundcloud.com";
    private static final String TOKEN_URL = "https://secure.soundcloud.com/oauth/token";
    /** Renova o token um pouco antes do vencimento para não usar um token expirando. */
    private static final long TOKEN_REFRESH_MARGIN_SECONDS = 60;
    private static final long DEFAULT_TOKEN_LIFETIME_SECONDS = 3600;

    private final String staticToken;
    private final String clientId;
    private final String clientSecret;
    private final String apiBase;
    private final String tokenUrl;
    private final HttpClient httpClient;
    private final ObjectMapper json;
    private final Clock clock;

    // Token obtido por client credentials; protegido por "this" porque várias threads pesquisam.
    private String cachedToken;
    private Instant cachedTokenRefreshAt = Instant.EPOCH;

    /** Usa as credenciais configuradas no ambiente e um cliente HTTP com timeout. */
    public SoundCloudSearchService() {
        this(System.getenv("SOUNDCLOUD_ACCESS_TOKEN"),
                System.getenv("SOUNDCLOUD_CLIENT_ID"),
                System.getenv("SOUNDCLOUD_CLIENT_SECRET"),
                API_BASE, TOKEN_URL,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                new ObjectMapper(), Clock.systemUTC());
    }

    /** Construtor injetável com token fixo, mantido para testes e integrações simples. */
    SoundCloudSearchService(String accessToken, HttpClient httpClient, ObjectMapper json) {
        this(accessToken, null, null, API_BASE, TOKEN_URL, httpClient, json, Clock.systemUTC());
    }

    /** Construtor completo: permite apontar para servidores falsos nos testes. */
    SoundCloudSearchService(String accessToken, String clientId, String clientSecret,
                            String apiBase, String tokenUrl, HttpClient httpClient,
                            ObjectMapper json, Clock clock) {
        this.staticToken = accessToken;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.apiBase = apiBase;
        this.tokenUrl = tokenUrl;
        this.httpClient = httpClient;
        this.json = json;
        this.clock = clock;
    }

    /** Informa se há alguma credencial configurada (não garante que ela seja válida). */
    public boolean isConfigured() {
        return usesClientCredentials() || !isBlank(staticToken);
    }

    /**
     * Busca faixas públicas por título, artista ou palavras-chave.
     *
     * @param query termo de busca
     * @param limit quantidade desejada entre 1 e 50
     * @return resultados com artista, capa e URL original quando disponíveis
     * @throws IllegalArgumentException se a consulta ou o limite forem inválidos
     * @throws IllegalStateException se nenhuma credencial estiver configurada
     * @throws SoundCloudException se o SoundCloud recusar ou falhar a requisição
     */
    public List<SoundCloudTrack> search(String query, int limit) throws IOException, InterruptedException {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Digite o nome da música ou do artista.");
        }
        if (limit < 1 || limit > 50) {
            throw new IllegalArgumentException("O limite deve ficar entre 1 e 50.");
        }
        if (!isConfigured()) {
            throw new IllegalStateException("SoundCloud não configurado: defina SOUNDCLOUD_CLIENT_ID e "
                    + "SOUNDCLOUD_CLIENT_SECRET (ou SOUNDCLOUD_ACCESS_TOKEN) no ambiente do servidor.");
        }

        String encodedQuery = URLEncoder.encode(query.strip(), StandardCharsets.UTF_8);
        URI uri = URI.create(apiBase + "/tracks?q=" + encodedQuery
                + "&limit=" + limit + "&linked_partitioning=true");

        HttpResponse<String> response = sendSearch(uri, accessToken(false));
        // Um token renovável pode ter sido revogado antes do prazo: renova e tenta uma única vez.
        if (response.statusCode() == 401 && usesClientCredentials()) {
            response = sendSearch(uri, accessToken(true));
        }
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new SoundCloudException(response.statusCode(),
                    "SoundCloud recusou o acesso. Verifique as credenciais e permissões.");
        }
        if (response.statusCode() == 429) {
            throw new SoundCloudException(429, "Limite de requisições do SoundCloud atingido. Tente mais tarde.");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new SoundCloudException(response.statusCode(),
                    "A busca no SoundCloud falhou (HTTP " + response.statusCode() + ").");
        }

        JsonNode root = json.readTree(response.body());
        // A API pode retornar uma coleção paginada ou uma lista JSON direta.
        JsonNode collection = root.isArray() ? root : root.path("collection");
        List<SoundCloudTrack> tracks = new ArrayList<>();
        if (!collection.isArray()) {
            return List.of();
        }

        for (JsonNode item : collection) {
            JsonNode user = item.path("user");
            String title = textOrNull(item, "title");
            String artist = textOrNull(user, "username");
            if (title == null || artist == null) {
                continue; // Ignora resultados incompletos em vez de inventar metadados.
            }
            // A API nova identifica faixas por "urn"; respostas antigas trazem apenas "id".
            String id = textOrNull(item, "urn");
            tracks.add(new SoundCloudTrack(
                    id != null ? id : textOrNull(item, "id"),
                    title,
                    artist,
                    textOrNull(item, "artwork_url"),
                    textOrNull(item, "permalink_url"),
                    item.path("duration").asLong(0)
            ));
        }
        return List.copyOf(tracks);
    }

    private HttpResponse<String> sendSearch(URI uri, String token) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("Authorization", "OAuth " + token)
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /**
     * Devolve o token a usar. Com client credentials, reaproveita o token em cache até
     * perto do vencimento; {@code forceRefresh} descarta o cache após um 401.
     */
    private synchronized String accessToken(boolean forceRefresh) throws IOException, InterruptedException {
        if (!usesClientCredentials()) {
            return staticToken;
        }
        Instant now = clock.instant();
        if (!forceRefresh && cachedToken != null && now.isBefore(cachedTokenRefreshAt)) {
            return cachedToken;
        }

        String basic = Base64.getEncoder().encodeToString(
                (clientId.strip() + ":" + clientSecret.strip()).getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(tokenUrl))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json; charset=utf-8")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", "Basic " + basic)
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
                .build();
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() == 429) {
            throw new SoundCloudException(429, "O SoundCloud limitou a emissão de tokens. Tente mais tarde.");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            // 400/401 aqui significam client_id/client_secret inválidos; não ecoa o corpo (pode conter dados da conta).
            throw new SoundCloudException(401,
                    "O SoundCloud recusou as credenciais do aplicativo (HTTP " + response.statusCode() + ").");
        }

        JsonNode body = json.readTree(response.body());
        String token = textOrNull(body, "access_token");
        if (token == null) {
            throw new SoundCloudException(502, "O SoundCloud não devolveu um token de acesso.");
        }
        long lifetime = body.path("expires_in").asLong(DEFAULT_TOKEN_LIFETIME_SECONDS);
        cachedToken = token;
        cachedTokenRefreshAt = now.plusSeconds(Math.max(0, lifetime - TOKEN_REFRESH_MARGIN_SECONDS));
        return token;
    }

    private boolean usesClientCredentials() {
        return !isBlank(clientId) && !isBlank(clientSecret);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Lê um campo textual opcional do JSON, devolvendo null quando ausente. */
    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank()
                ? null : value.asText();
    }

    /** Modelo enxuto de resultado, sem guardar ou redistribuir o áudio da plataforma. */
    public record SoundCloudTrack(
            String id,
            String title,
            String artist,
            String artworkUrl,
            String permalinkUrl,
            long durationMillis
    ) { }

    /** Falha devolvida pelo SoundCloud; {@code statusCode} é o status HTTP de origem. */
    public static final class SoundCloudException extends IOException {
        private final int statusCode;

        public SoundCloudException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        public int statusCode() {
            return statusCode;
        }
    }
}
