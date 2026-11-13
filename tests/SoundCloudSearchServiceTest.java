import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes unitários com mocks HTTP completos para a integração SoundCloud.
 * Valida o ciclo OAuth client credentials, paginação, resolução de permalinks,
 * permissões de reprodução (reproduzível, prévia e bloqueada), erros HTTP e segurança.
 */
class SoundCloudSearchServiceTest {
    private HttpServer stub;
    private final AtomicInteger tokenCalls = new AtomicInteger();
    private volatile int searchStatus = 200;
    private volatile String lastAuthorization;
    private final AtomicInteger tracksRequests = new AtomicInteger();

    @BeforeEach
    void startStub() throws IOException {
        stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stub.createContext("/oauth/token", exchange -> {
            int n = tokenCalls.incrementAndGet();
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            boolean basic = auth != null && auth.startsWith("Basic ");
            byte[] body = ("{\"access_token\":\"token-" + n + "\",\"expires_in\":3600}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(basic ? 200 : 401, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        stub.createContext("/tracks", exchange -> {
            tracksRequests.incrementAndGet();
            lastAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            // Simula token revogado: o primeiro token é recusado quando searchStatus = 401.
            int status = searchStatus != 401 ? searchStatus
                    : "OAuth token-1".equals(lastAuthorization) ? 401 : 200;

            String query = exchange.getRequestURI().getQuery();
            String nextHref = (query != null && query.contains("offset=10"))
                    ? ""
                    : "http://127.0.0.1:" + stub.getAddress().getPort() + "/tracks?q=song&offset=10";

            byte[] body = ("{\"collection\":[{\"urn\":\"soundcloud:tracks:7\",\"title\":\"Song\","
                    + "\"user\":{\"username\":\"Artist\"},\"permalink_url\":\"https://soundcloud.com/a/s\","
                    + "\"duration\":1000,\"streamable\":true,\"access\":\"playable\"},{\"title\":\"Sem artista\"}],"
                    + "\"next_href\":\"" + nextHref + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        stub.start();
    }

    @AfterEach
    void stopStub() {
        stub.stop(0);
    }

    private SoundCloudSearchService service(String token, String id, String secret) {
        String base = "http://127.0.0.1:" + stub.getAddress().getPort();
        return new SoundCloudSearchService(token, id, secret, base, base + "/oauth/token",
                HttpClient.newHttpClient(), new ObjectMapper(), Clock.systemUTC());
    }

    @Test
    void obtemTokenUmaVezEReaproveita() throws Exception {
        SoundCloudSearchService service = service(null, "id", "secret");
        List<SoundCloudSearchService.SoundCloudTrack> tracks = service.search("song", 5);
        service.search("song", 5);

        assertEquals(1, tracks.size());
        assertEquals("soundcloud:tracks:7", tracks.get(0).id());
        assertEquals(SoundCloudSearchService.PlaybackState.PLAYABLE, tracks.get(0).playbackState());
        assertEquals(1, tokenCalls.get());
        assertEquals("OAuth token-1", lastAuthorization);
    }

    @Test
    void renovaTokenUmaVezApos401() throws Exception {
        searchStatus = 401;
        SoundCloudSearchService service = service(null, "id", "secret");
        assertEquals(1, service.search("song", 5).size());
        assertEquals(2, tokenCalls.get());
        assertEquals("OAuth token-2", lastAuthorization);
    }

    @Test
    void tokenFixoNaoChamaEndpointDeToken() throws Exception {
        SoundCloudSearchService service = service("fixo", null, null);
        service.search("song", 5);
        assertEquals(0, tokenCalls.get());
        assertEquals("OAuth fixo", lastAuthorization);
    }

    @Test
    void buscaPaginadaRetornaColecaoENextHref() throws Exception {
        SoundCloudSearchService service = service("fixo", null, null);
        var page1 = service.searchPaged("song", 5, null);
        assertEquals(1, page1.collection().size());
        assertTrue(page1.hasNext());
        assertNotNull(page1.nextHref());

        var page2 = service.searchPaged(null, 0, page1.nextHref());
        assertEquals(1, page2.collection().size());
        assertFalse(page2.hasNext());
    }

    @Test
    void resolveTrackComRedirect302() throws Exception {
        stub.createContext("/resolve", exchange -> {
            exchange.getResponseHeaders().set("Location",
                    "http://127.0.0.1:" + stub.getAddress().getPort() + "/resolved-track");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        stub.createContext("/resolved-track", exchange -> {
            byte[] body = ("{\"id\":\"888\",\"title\":\"Resolved Title\",\"user\":{\"username\":\"Resolved Artist\"},"
                    + "\"permalink_url\":\"https://soundcloud.com/artist/song\",\"duration\":150000,"
                    + "\"streamable\":true,\"access\":\"playable\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        SoundCloudSearchService service = service("fixo", null, null);
        Optional<SoundCloudSearchService.SoundCloudTrack> resolved =
                service.resolveTrack("https://soundcloud.com/artist/song");

        assertTrue(resolved.isPresent());
        assertEquals("Resolved Title", resolved.get().title());
        assertEquals("Resolved Artist", resolved.get().artist());
        assertEquals(SoundCloudSearchService.PlaybackState.PLAYABLE, resolved.get().playbackState());
    }

    @Test
    void resolveTrackRetornaVazioPara404() throws Exception {
        stub.createContext("/resolve", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        SoundCloudSearchService service = service("fixo", null, null);
        Optional<SoundCloudSearchService.SoundCloudTrack> resolved =
                service.resolveTrack("https://soundcloud.com/artist/inexistente");
        assertTrue(resolved.isEmpty());
    }

    @Test
    void rejeitaUrlsSuspeitasAntiSSRF() {
        SoundCloudSearchService service = service("fixo", null, null);
        assertThrows(IllegalArgumentException.class,
                () -> service.resolveTrack("https://google.com/hacker"));
        assertThrows(IllegalArgumentException.class,
                () -> service.searchPaged(null, 10, "http://169.254.169.254/latest/meta-data"));
    }

    @Test
    void classificaFaixasReproduzivelPreviaEBloqueada() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode itemPlayable = mapper.createObjectNode()
                .put("title", "Musica 1")
                .put("streamable", true)
                .put("access", "playable");
        itemPlayable.putObject("user").put("username", "Autor 1");

        ObjectNode itemPreview = mapper.createObjectNode()
                .put("title", "Musica 2")
                .put("streamable", true)
                .put("access", "preview");
        itemPreview.putObject("user").put("username", "Autor 2");

        ObjectNode itemBlocked = mapper.createObjectNode()
                .put("title", "Musica 3")
                .put("streamable", false)
                .put("access", "blocked");
        itemBlocked.putObject("user").put("username", "Autor 3");

        assertEquals(SoundCloudSearchService.PlaybackState.PLAYABLE,
                SoundCloudSearchService.parseTrack(itemPlayable).playbackState());
        assertEquals(SoundCloudSearchService.PlaybackState.PREVIEW,
                SoundCloudSearchService.parseTrack(itemPreview).playbackState());
        assertEquals(SoundCloudSearchService.PlaybackState.BLOCKED,
                SoundCloudSearchService.parseTrack(itemBlocked).playbackState());
    }

    @Test
    void retryEmErroTransitorio5xx() throws Exception {
        final AtomicInteger attempt = new AtomicInteger(0);
        stub.createContext("/tracks/temp-fail", exchange -> {
            int n = attempt.incrementAndGet();
            if (n == 1) {
                exchange.sendResponseHeaders(503, -1);
            } else {
                byte[] body = "{\"collection\":[]}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });

        SoundCloudSearchService service = service("fixo", null, null);
        String nextUrl = "http://127.0.0.1:" + stub.getAddress().getPort() + "/tracks/temp-fail";
        var res = service.searchPaged(null, 0, nextUrl);
        assertEquals(0, res.collection().size());
        assertEquals(2, attempt.get());
    }

    @Test
    void erroDoProvedorPreservaStatus() {
        searchStatus = 429;
        SoundCloudSearchService.SoundCloudException e = assertThrows(
                SoundCloudSearchService.SoundCloudException.class,
                () -> service("fixo", null, null).search("song", 5));
        assertEquals(429, e.statusCode());
    }

    @Test
    void pedeStreamHttpsAutorizadoPelaApi() throws Exception {
        stub.createContext("/tracks/123/streams", exchange -> {
            lastAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            byte[] body = "{\"preview_mp3_128_url\":\"http://inseguro.example/a.mp3\",\"http_mp3_128_url\":\"https://cdn.example/a.mp3\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        SoundCloudSearchService service = service("fixo", null, null);

        assertEquals("https://cdn.example/a.mp3", service.urlDeStream("123").orElseThrow().toString());
        assertEquals("OAuth fixo", lastAuthorization);
    }

    @Test
    void ignoraStreamQueNaoSejaHttps() {
        var json = JsonNodeFactory.instance.objectNode().put("http_mp3_128_url", "http://cdn.example/a.mp3");
        assertTrue(SoundCloudSearchService.escolherUrlDeStream(json).isEmpty());
    }

    @Test
    void semCredencialNaoEstaConfigurado() {
        SoundCloudSearchService service = service(null, null, " ");
        assertFalse(service.isConfigured());
        assertThrows(IllegalStateException.class, () -> service.search("song", 5));
        assertTrue(service("t", null, null).isConfigured());
    }

    @Test
    void geraUrisDeWidgetEOEmbed() {
        URI widgetUri = SoundCloudSearchService.widgetEmbedUri("https://soundcloud.com/artist/track");
        assertTrue(widgetUri.toString().contains("w.soundcloud.com/player"));
        assertTrue(widgetUri.toString().contains("soundcloud.com%2Fartist%2Ftrack"));

        URI oembedUri = SoundCloudSearchService.oembedUri("https://soundcloud.com/artist/track");
        assertTrue(oembedUri.toString().contains("soundcloud.com/oembed"));
        assertTrue(oembedUri.toString().contains("format=json"));
    }
}
