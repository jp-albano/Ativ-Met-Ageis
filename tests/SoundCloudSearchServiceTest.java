import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testa o fluxo OAuth client credentials contra um servidor local que imita o SoundCloud.
 * Nenhuma credencial real é usada.
 */
class SoundCloudSearchServiceTest {
    private HttpServer stub;
    private final AtomicInteger tokenCalls = new AtomicInteger();
    private volatile int searchStatus = 200;
    private volatile String lastAuthorization;

    @BeforeEach
    void startStub() throws IOException {
        stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stub.createContext("/oauth/token", exchange -> {
            int n = tokenCalls.incrementAndGet();
            boolean basic = exchange.getRequestHeaders().getFirst("Authorization").startsWith("Basic ");
            byte[] body = ("{\"access_token\":\"token-" + n + "\",\"expires_in\":3600}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(basic ? 200 : 401, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        stub.createContext("/tracks", exchange -> {
            lastAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            // Simula token revogado: o primeiro token é recusado quando searchStatus = 401.
            int status = searchStatus != 401 ? searchStatus
                    : "OAuth token-1".equals(lastAuthorization) ? 401 : 200;
            byte[] body = ("{\"collection\":[{\"urn\":\"soundcloud:tracks:7\",\"title\":\"Song\","
                    + "\"user\":{\"username\":\"Artist\"},\"permalink_url\":\"https://soundcloud.com/a/s\","
                    + "\"duration\":1000},{\"title\":\"Sem artista\"}]}").getBytes(StandardCharsets.UTF_8);
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
}
