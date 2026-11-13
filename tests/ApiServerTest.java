import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes de ponta a ponta da API HTTP: sobe o servidor numa porta livre e faz requisições reais.
 * Os provedores externos são falsos, então nenhum teste depende de rede ou credenciais.
 */
class ApiServerTest {
    private static final String ORIGIN = "http://localhost:3000";

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private final FakeProviders providers = new FakeProviders();
    private ApiServer server;
    private String base;

    @BeforeEach
    void start() throws IOException {
        server = new ApiServer(new MusicLibrary(), new DiscoveryApi(providers), Set.of(ORIGIN));
        server.start("127.0.0.1", 0);
        base = "http://127.0.0.1:" + server.port();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @Test
    void healthInformaSeSoundCloudEstaConfigurado() throws Exception {
        HttpResponse<String> response = send("GET", "/api/health", null);
        assertEquals(200, response.statusCode());
        assertEquals("ok", body(response).get("status").asText());
        assertFalse(body(response).get("soundCloudConfigured").asBoolean());
        assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElse(""));
    }

    @Test
    void listaEFiltraOCatalogoInicial() throws Exception {
        JsonNode all = body(send("GET", "/api/tracks", null)).get("tracks");
        assertTrue(all.size() > 0);

        JsonNode filtered = body(send("GET", "/api/tracks?q=bob%20dylan", null)).get("tracks");
        assertEquals(1, filtered.size());
        assertEquals("t-008", filtered.get(0).get("id").asText());
    }

    @Test
    void cadastraFaixaEValidaCorpo() throws Exception {
        HttpResponse<String> created = send("POST", "/api/tracks",
                "{\"title\":\" Nova Faixa \",\"artist\":\"Banda\",\"durationSeconds\":185}");
        assertEquals(201, created.statusCode());
        JsonNode track = body(created);
        assertEquals("Nova Faixa", track.get("title").asText());
        assertEquals("03:05", track.get("duration").asText());

        assertEquals(400, send("POST", "/api/tracks", "{nao e json").statusCode());
        assertEquals(400, send("POST", "/api/tracks", "{\"title\":\"\",\"artist\":\"x\",\"durationSeconds\":1}")
                .statusCode());
        assertEquals(400, send("POST", "/api/tracks", "{\"title\":\"a\",\"artist\":\"x\",\"durationSeconds\":\"1\"}")
                .statusCode());
        assertEquals(413, send("POST", "/api/tracks", "x".repeat(ApiServer.MAX_BODY_BYTES + 1)).statusCode());
    }

    @Test
    void buscaERemoveFaixaPorId() throws Exception {
        HttpResponse<String> ok = send("GET", "/api/tracks/t-001", null);
        assertEquals(200, ok.statusCode());
        assertEquals("Bohemian Rhapsody", body(ok).get("title").asText());

        assertEquals(404, send("GET", "/api/tracks/inexistente", null).statusCode());
        assertEquals(204, send("DELETE", "/api/tracks/t-001", null).statusCode());
        assertEquals(404, send("GET", "/api/tracks/t-001", null).statusCode());
    }

    @Test
    void favoritosAlternamComPutEDelete() throws Exception {
        assertEquals(0, body(send("GET", "/api/favorites", null)).get("tracks").size());

        HttpResponse<String> added = send("PUT", "/api/favorites/t-002", null);
        assertEquals(200, added.statusCode());
        assertTrue(body(added).get("favorite").asBoolean());
        assertTrue(body(added).get("changed").asBoolean());

        HttpResponse<String> repeated = send("PUT", "/api/favorites/t-002", null);
        assertFalse(body(repeated).get("changed").asBoolean());
        assertEquals(1, body(send("GET", "/api/favorites", null)).get("tracks").size());

        HttpResponse<String> removed = send("DELETE", "/api/favorites/t-002", null);
        assertFalse(body(removed).get("favorite").asBoolean());
        assertTrue(body(removed).get("changed").asBoolean());
        assertEquals(404, send("PUT", "/api/favorites/inexistente", null).statusCode());
    }

    @Test
    void cicloDeVidaDePlaylist() throws Exception {
        HttpResponse<String> created = send("POST", "/api/playlists", "{\"name\":\"Minha Lista\"}");
        assertEquals(201, created.statusCode());
        int id = body(created).get("id").asInt();

        HttpResponse<String> added = send("PUT", "/api/playlists/" + id + "/tracks/t-002", null);
        assertEquals(200, added.statusCode());
        assertTrue(body(added).get("changed").asBoolean());
        assertEquals(1, body(added).get("playlist").get("tracks").size());

        HttpResponse<String> fetched = send("GET", "/api/playlists/" + id, null);
        assertEquals(1, body(fetched).get("trackCount").asInt());

        HttpResponse<String> removedTrack = send("DELETE", "/api/playlists/" + id + "/tracks/t-002", null);
        assertTrue(body(removedTrack).get("changed").asBoolean());

        assertEquals(204, send("DELETE", "/api/playlists/" + id, null).statusCode());
        assertEquals(404, send("GET", "/api/playlists/" + id, null).statusCode());
    }

    @Test
    void metodoIncorretoResponde405ComCabecalhoAllow() throws Exception {
        HttpResponse<String> response = send("DELETE", "/api/health", null);
        assertEquals(405, response.statusCode());
        assertEquals("GET", response.headers().firstValue("Allow").orElse(""));
    }

    @Test
    void corsSoLiberaOrigensConfiguradas() throws Exception {
        HttpResponse<String> allowed = send("OPTIONS", "/api/tracks", null, ORIGIN);
        assertEquals(204, allowed.statusCode());
        assertEquals(ORIGIN, allowed.headers().firstValue("Access-Control-Allow-Origin").orElse(""));

        HttpResponse<String> denied = send("GET", "/api/tracks", null, "http://evil.example");
        assertTrue(denied.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
    }

    @Test
    void buscaSemCredencialResponde503() throws Exception {
        HttpResponse<String> response = send("GET", "/api/discovery/search?q=queen", null);
        assertEquals(503, response.statusCode());
        assertTrue(body(response).get("error").asText().contains("SoundCloud"));
    }

    @Test
    void buscaConfiguradaDevolveFaixasValidasEFalhasViramStatus() throws Exception {
        providers.configured = true;
        providers.tracks = List.of(
                new SoundCloudSearchService.SoundCloudTrack("soundcloud:tracks:1", "Song", "Artist",
                        null, "https://soundcloud.com/a/song", 200_000,
                        SoundCloudSearchService.PlaybackState.PLAYABLE, true, null),
                new SoundCloudSearchService.SoundCloudTrack("soundcloud:tracks:2", "Sem link", "Artist",
                        null, null, 1,
                        SoundCloudSearchService.PlaybackState.BLOCKED, false, null));
        providers.nextHref = "https://api.soundcloud.com/tracks?q=song&offset=10";

        JsonNode res = body(send("GET", "/api/discovery/search?q=song&limit=5", null));
        JsonNode tracks = res.get("tracks");
        assertEquals(1, tracks.size());
        assertEquals("soundcloud:tracks:1", tracks.get(0).get("id").asText());
        assertEquals("PLAYABLE", tracks.get(0).get("playbackState").asText());
        assertTrue(tracks.get(0).get("streamable").asBoolean());
        assertEquals("https://api.soundcloud.com/tracks?q=song&offset=10", res.get("nextHref").asText());

        assertEquals(400, send("GET", "/api/discovery/search?q=", null).statusCode());
        assertEquals(400, send("GET", "/api/discovery/search?q=x&limit=abc", null).statusCode());

        providers.searchError = new SoundCloudSearchService.SoundCloudException(429, "limite");
        assertEquals(429, send("GET", "/api/discovery/search?q=outra", null).statusCode());
    }

    @Test
    void resolveTrackViaEndpointDiscovery() throws Exception {
        providers.configured = true;
        providers.resolvedTrack = new SoundCloudSearchService.SoundCloudTrack("soundcloud:tracks:99",
                "Resolved Track", "Resolved Artist", "https://artwork.example/art.jpg",
                "https://soundcloud.com/artist/track", 180_000,
                SoundCloudSearchService.PlaybackState.PLAYABLE, true, null);

        HttpResponse<String> response = send("GET",
                "/api/discovery/resolve?url=https%3A%2F%2Fsoundcloud.com%2Fartist%2Ftrack", null);
        assertEquals(200, response.statusCode());
        JsonNode track = body(response);
        assertEquals("Resolved Track", track.get("title").asText());
        assertEquals("Resolved Artist", track.get("artist").asText());
        assertEquals("PLAYABLE", track.get("playbackState").asText());

        assertEquals(400, send("GET", "/api/discovery/resolve?url=", null).statusCode());
        assertEquals(404, send("GET", "/api/discovery/resolve?url=https%3A%2F%2Fsoundcloud.com%2Fnaoexiste", null).statusCode());
    }

    @Test
    void verificacaoToleraFalhaParcial() throws Exception {
        providers.lyricsError = new IOException("LRCLIB fora do ar");
        JsonNode result = body(send("GET",
                "/api/discovery/verify?title=Song&artist=Artist&duration=200.4", null));
        assertTrue(result.get("lyrics").isNull());
        assertEquals(1, result.get("recordings").size());
        assertEquals(1, result.get("warnings").size());
        assertEquals(200, providers.lastDuration);

        assertEquals(400, send("GET", "/api/discovery/verify?title=Song", null).statusCode());
        assertEquals(400, send("GET", "/api/discovery/verify?title=a&artist=b&duration=-1", null).statusCode());
    }

    @Test
    void segmentosEQueryStringSaoDecodificados() {
        assertEquals(List.of("api", "tracks", "a b+c"), ApiServer.segments("/api//tracks/a%20b+c/"));
        assertEquals("rock & roll", ApiServer.queryParams("q=rock+%26+roll&q=ignorado").get("q"));
        assertNull(ApiServer.queryParams("").get("q"));
    }

    // ------------------------------------------------------------------ apoio

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        return send(method, path, body, null);
    }

    private HttpResponse<String> send(String method, String path, String body, String origin) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) request.header("Content-Type", "application/json");
        if (origin != null) request.header("Origin", origin);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode body(HttpResponse<String> response) throws IOException {
        return json.readTree(response.body());
    }

    /** Provedores controlados pelo teste. */
    private static final class FakeProviders implements DiscoveryApi.Providers {
        volatile boolean configured;
        volatile List<SoundCloudSearchService.SoundCloudTrack> tracks = List.of();
        volatile String nextHref;
        volatile SoundCloudSearchService.SoundCloudTrack resolvedTrack;
        volatile IOException searchError;
        volatile IOException lyricsError;
        volatile int lastDuration = -1;

        @Override
        public boolean soundCloudConfigured() {
            return configured;
        }

        @Override
        public List<SoundCloudSearchService.SoundCloudTrack> search(String query, int limit) throws IOException {
            if (searchError != null) throw searchError;
            return tracks;
        }

        @Override
        public SoundCloudSearchService.PagedResult<SoundCloudSearchService.SoundCloudTrack> searchPaged(
                String query, int limit, String nextHref) throws IOException {
            if (searchError != null) throw searchError;
            return new SoundCloudSearchService.PagedResult<>(tracks, this.nextHref);
        }

        @Override
        public Optional<SoundCloudSearchService.SoundCloudTrack> resolve(String permalinkUrl) {
            if (resolvedTrack != null && permalinkUrl != null && permalinkUrl.contains("naoexiste")) {
                return Optional.empty();
            }
            return Optional.ofNullable(resolvedTrack);
        }

        @Override
        public LyricsVerificationService.VerificationResult lyrics(String title, String artist, int durationSeconds)
                throws IOException {
            lastDuration = durationSeconds;
            if (lyricsError != null) throw lyricsError;
            return new LyricsVerificationService.VerificationResult(true, true, "ok", title, artist,
                    "Album", durationSeconds, "letra", null, false, "LRCLIB");
        }

        @Override
        public List<MusicMetadataVerificationService.RecordingMetadata> recordings(String title, String artist) {
            return List.of(new MusicMetadataVerificationService.RecordingMetadata(
                    "mbid-1", title, artist, "Album", 200_000));
        }
    }
}
