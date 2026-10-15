package playlist;

import static playlist.Verifica.caso;
import static playlist.Verifica.igual;
import static playlist.Verifica.verdade;

import com.sun.net.httpserver.HttpServer;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

final class PlaylistApiTest {

    private PlaylistApiTest() {
    }

    /** Servidor real em porta livre, com arquivo temporário próprio. */
    static final class Ambiente implements AutoCloseable {
        final Path arquivo;
        final HttpServer servidor;
        final String base;
        final HttpClient cliente = HttpClient.newHttpClient();

        Ambiente(Path arquivo, String cors) throws Exception {
            this.arquivo = arquivo;
            PlaylistService servico = new PlaylistService(new FilePlaylistRepository(arquivo));
            this.servidor = PlaylistApi.iniciar(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), servico, cors);
            this.base = "http://127.0.0.1:" + servidor.getAddress().getPort();
        }

        Ambiente() throws Exception {
            this(FilePlaylistRepositoryTest.pastaTemporaria().resolve("p.json"), null);
        }

        HttpResponse<String> req(String metodo, String caminho, String corpo) throws Exception {
            HttpRequest.BodyPublisher b = corpo == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(corpo);
            HttpRequest r = HttpRequest.newBuilder(URI.create(base + caminho))
                    .header("Content-Type", "application/json")
                    .method(metodo, b)
                    .build();
            return cliente.send(r, HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> criar(String nomeJson) throws Exception {
            return req("POST", "/api/playlists", "{\"nome\":" + nomeJson + "}");
        }

        @Override
        public void close() {
            servidor.stop(0);
        }
    }

    private static Map<?, ?> objeto(HttpResponse<String> r) {
        return (Map<?, ?>) Json.parse(r.body());
    }

    private static void erro(HttpResponse<String> r, int status) {
        igual(status, r.statusCode());
        verdade(objeto(r).get("erro") instanceof String, "resposta de erro deve ter o campo erro");
    }

    static void rodar() {
        caso("API: POST válido devolve 201, Location e a playlist vazia", () -> {
            try (Ambiente a = new Ambiente()) {
                HttpResponse<String> r = a.criar("\"Treino\"");
                igual(201, r.statusCode());
                Map<?, ?> p = objeto(r);
                igual("Treino", p.get("nome"));
                igual(List.of(), p.get("musicas"));
                verdade(((String) p.get("id")).startsWith("pl_"), "id gerado");
                verdade(p.get("criadaEm") instanceof String, "criadaEm");
                igual("/api/playlists/" + p.get("id"), r.headers().firstValue("Location").orElse(null));
                verdade(r.headers().firstValue("Content-Type").orElse("").startsWith("application/json"), "content-type");
            }
        });

        caso("API: GET lista e GET por id", () -> {
            try (Ambiente a = new Ambiente()) {
                Map<?, ?> criada = objeto(a.criar("\"Treino\""));
                a.criar("\"Estudo\"");

                List<?> lista = (List<?>) Json.parse(a.req("GET", "/api/playlists", null).body());
                igual(2, lista.size());
                igual("Treino", ((Map<?, ?>) lista.get(0)).get("nome"));

                HttpResponse<String> um = a.req("GET", "/api/playlists/" + criada.get("id"), null);
                igual(200, um.statusCode());
                igual(criada, objeto(um));

                erro(a.req("GET", "/api/playlists/nao-existe", null), 404);
            }
        });

        caso("API: nome duplicado dá 409 e não cria outra", () -> {
            try (Ambiente a = new Ambiente()) {
                igual(201, a.criar("\"Treino\"").statusCode());
                erro(a.criar("\"  treino \""), 409);
                igual(1, ((List<?>) Json.parse(a.req("GET", "/api/playlists", null).body())).size());
            }
        });

        caso("API: entradas inválidas dão 400", () -> {
            try (Ambiente a = new Ambiente()) {
                erro(a.criar("\"\""), 400);
                erro(a.criar("\"   \""), 400);
                erro(a.criar("null"), 400);
                erro(a.criar("123"), 400);
                erro(a.criar("\"" + "a".repeat(51) + "\""), 400);
                erro(a.req("POST", "/api/playlists", "{}"), 400);
                erro(a.req("POST", "/api/playlists", "[]"), 400);
                erro(a.req("POST", "/api/playlists", "{quebrado"), 400);
                erro(a.req("POST", "/api/playlists", ""), 400);
                igual(0, ((List<?>) Json.parse(a.req("GET", "/api/playlists", null).body())).size());
            }
        });

        caso("API: corpo acima do limite dá 413", () -> {
            try (Ambiente a = new Ambiente()) {
                erro(a.criar("\"" + "a".repeat(20_000) + "\""), 413);
            }
        });

        caso("API: método não permitido dá 405 com Allow; caminhos desconhecidos dão 404", () -> {
            try (Ambiente a = new Ambiente()) {
                HttpResponse<String> r = a.req("DELETE", "/api/playlists", null);
                erro(r, 405);
                igual("GET, POST, OPTIONS", r.headers().firstValue("Allow").orElse(null));
                erro(a.req("POST", "/api/playlists/abc", "{}"), 405);
                erro(a.req("GET", "/api/playlists/a/b", null), 404);
                erro(a.req("GET", "/api/playlistsx", null), 404);
            }
        });

        caso("API: acentos e emoji sobrevivem ida e volta em UTF-8", () -> {
            try (Ambiente a = new Ambiente()) {
                HttpResponse<String> r = a.criar("\"Música ação 😀\"");
                igual(201, r.statusCode());
                igual("Música ação 😀", objeto(r).get("nome"));
                List<?> lista = (List<?>) Json.parse(a.req("GET", "/api/playlists", null).body());
                igual("Música ação 😀", ((Map<?, ?>) lista.get(0)).get("nome"));
            }
        });

        caso("API: dados persistem depois de reiniciar o servidor", () -> {
            Path arquivo = FilePlaylistRepositoryTest.pastaTemporaria().resolve("p.json");
            String id;
            try (Ambiente a = new Ambiente(arquivo, null)) {
                id = (String) objeto(a.criar("\"Treino\"")).get("id");
            }
            try (Ambiente b = new Ambiente(arquivo, null)) {
                HttpResponse<String> r = b.req("GET", "/api/playlists/" + id, null);
                igual(200, r.statusCode());
                igual("Treino", objeto(r).get("nome"));
            }
        });

        caso("API: dados corrompidos dão 500 genérico, sem vazar detalhes, e o arquivo é preservado", () -> {
            Path arquivo = FilePlaylistRepositoryTest.pastaTemporaria().resolve("p.json");
            java.nio.file.Files.writeString(arquivo, "{quebrado");
            try (Ambiente a = new Ambiente(arquivo, null)) {
                HttpResponse<String> r = a.criar("\"Treino\"");
                erro(r, 500);
                verdade(!r.body().contains(arquivo.toString()) && !r.body().contains("Json"), "não pode vazar detalhes");
                igual("{quebrado", java.nio.file.Files.readString(arquivo));
            }
        });

        caso("API: CORS só é enviado quando configurado; OPTIONS responde 204", () -> {
            try (Ambiente sem = new Ambiente()) {
                verdade(sem.req("GET", "/api/playlists", null).headers()
                        .firstValue("Access-Control-Allow-Origin").isEmpty(), "sem CORS por padrão");
            }
            Path arquivo = FilePlaylistRepositoryTest.pastaTemporaria().resolve("p.json");
            try (Ambiente com = new Ambiente(arquivo, "http://localhost:5500")) {
                HttpResponse<String> r = com.req("OPTIONS", "/api/playlists", null);
                igual(204, r.statusCode());
                igual("http://localhost:5500", r.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
                igual("Content-Type", r.headers().firstValue("Access-Control-Allow-Headers").orElse(null));
            }
        });
    }
}
