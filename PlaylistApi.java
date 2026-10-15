package playlist;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;

/**
 * API HTTP/JSON das playlists, usando apenas o servidor embutido do JDK.
 *
 * <pre>
 * POST /api/playlists        {"nome": "Treino"}  -> 201 + playlist + cabeçalho Location
 * GET  /api/playlists                            -> 200 + lista
 * GET  /api/playlists/{id}                       -> 200 + playlist | 404
 * </pre>
 *
 * Erros: 400 (JSON ou nome inválido), 404, 405, 409 (nome duplicado), 413 (corpo acima de
 * {@value #CORPO_MAXIMO} bytes) e 500 (falha de armazenamento; o detalhe vai só para o
 * log). Todo erro tem a forma {@code {"erro": "mensagem"}}.
 */
public final class PlaylistApi {

    private static final String BASE = "/api/playlists";
    private static final int CORPO_MAXIMO = 10_000;

    private final PlaylistService servico;
    private final String origemCors;

    private PlaylistApi(PlaylistService servico, String origemCors) {
        this.servico = servico;
        this.origemCors = origemCors;
    }

    /**
     * Cria e inicia o servidor.
     *
     * @param endereco   host e porta (porta 0 escolhe uma livre)
     * @param origemCors valor de Access-Control-Allow-Origin, ou {@code null} para não enviar CORS
     */
    public static HttpServer iniciar(InetSocketAddress endereco, PlaylistService servico, String origemCors)
            throws IOException {
        HttpServer servidor = HttpServer.create(endereco, 0);
        PlaylistApi api = new PlaylistApi(servico, origemCors);
        servidor.createContext(BASE, api::tratar);
        servidor.setExecutor(Executors.newFixedThreadPool(4, tarefa -> {
            Thread t = new Thread(tarefa, "playlist-api");
            t.setDaemon(true);
            return t;
        }));
        servidor.start();
        return servidor;
    }

    // ---------------------------------------------------------------- roteamento

    private void tratar(HttpExchange ex) throws IOException {
        try {
            rotear(ex);
        } catch (StorageException e) {
            registrar(e);
            enviarErro(ex, 500, "Erro interno ao acessar os dados.");
        } catch (RuntimeException e) {
            registrar(e);
            enviarErro(ex, 500, "Erro interno.");
        } finally {
            ex.close();
        }
    }

    private void rotear(HttpExchange ex) throws IOException {
        if (origemCors != null) {
            ex.getResponseHeaders().set("Access-Control-Allow-Origin", origemCors);
        }
        String metodo = ex.getRequestMethod();
        String caminho = ex.getRequestURI().getPath();
        String resto = caminho.substring(BASE.length());

        if (!resto.isEmpty() && !resto.startsWith("/")) {
            enviarErro(ex, 404, "Recurso não encontrado.");
            return;
        }

        if ("OPTIONS".equals(metodo)) {
            ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            ex.sendResponseHeaders(204, -1);
            return;
        }

        if (resto.isEmpty() || resto.equals("/")) {
            switch (metodo) {
                case "GET" -> listar(ex);
                case "POST" -> criar(ex);
                default -> metodoNaoPermitido(ex, "GET, POST, OPTIONS");
            }
        } else if (resto.indexOf('/', 1) < 0) {
            if ("GET".equals(metodo)) {
                buscar(ex, resto.substring(1));
            } else {
                metodoNaoPermitido(ex, "GET, OPTIONS");
            }
        } else {
            enviarErro(ex, 404, "Recurso não encontrado.");
        }
    }

    // ------------------------------------------------------------------ rotas

    private void listar(HttpExchange ex) throws IOException {
        List<Object> itens = servico.listar().stream()
                .<Object>map(PlaylistJson::paraMapa)
                .toList();
        enviar(ex, 200, Json.write(itens));
    }

    private void buscar(HttpExchange ex, String id) throws IOException {
        Optional<Playlist> achada = servico.buscarPorId(id);
        if (achada.isPresent()) {
            enviar(ex, 200, Json.write(PlaylistJson.paraMapa(achada.get())));
        } else {
            enviarErro(ex, 404, "Playlist não encontrada.");
        }
    }

    private void criar(HttpExchange ex) throws IOException {
        String corpo;
        try {
            corpo = lerCorpo(ex);
        } catch (CorpoGrandeException e) {
            enviarErro(ex, 413, "Corpo da requisição grande demais.");
            return;
        }

        Object json;
        try {
            json = Json.parse(corpo);
        } catch (JsonException e) {
            enviarErro(ex, 400, "JSON inválido.");
            return;
        }
        if (!(json instanceof Map<?, ?> campos)) {
            enviarErro(ex, 400, "Envie um objeto JSON com o campo \"nome\".");
            return;
        }
        Object nome = campos.get("nome");
        if (nome != null && !(nome instanceof String)) {
            enviarErro(ex, 400, "O campo nome deve ser texto.");
            return;
        }

        try {
            Playlist criada = servico.criar((String) nome);
            ex.getResponseHeaders().set("Location", BASE + "/" + criada.id());
            enviar(ex, 201, Json.write(PlaylistJson.paraMapa(criada)));
        } catch (DuplicateNameException e) {
            enviarErro(ex, 409, e.getMessage());
        } catch (ValidationException e) {
            enviarErro(ex, 400, e.getMessage());
        }
    }

    // ---------------------------------------------------------------- utilitários

    private static String lerCorpo(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            byte[] dados = in.readNBytes(CORPO_MAXIMO + 1);
            if (dados.length > CORPO_MAXIMO) {
                throw new CorpoGrandeException();
            }
            return new String(dados, StandardCharsets.UTF_8);
        }
    }

    private static void metodoNaoPermitido(HttpExchange ex, String permitidos) throws IOException {
        ex.getResponseHeaders().set("Allow", permitidos);
        enviarErro(ex, 405, "Método não permitido.");
    }

    private static void enviarErro(HttpExchange ex, int status, String mensagem) throws IOException {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("erro", mensagem);
        enviar(ex, status, Json.write(corpo));
    }

    private static void enviar(HttpExchange ex, int status, String json) throws IOException {
        byte[] corpo = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.sendResponseHeaders(status, corpo.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(corpo);
        }
    }

    private static void registrar(Exception e) {
        // Só tipo e mensagem: nunca devolvemos detalhes internos ao cliente.
        System.err.println("[erro] " + e.getClass().getSimpleName() + ": " + e.getMessage()
                + (e.getCause() != null ? " (causa: " + e.getCause() + ")" : ""));
    }

    private static final class CorpoGrandeException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
