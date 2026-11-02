import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Camada de descoberta usada pela API HTTP: pesquisa no SoundCloud e confere letras
 * (LRCLIB) e créditos (MusicBrainz), devolvendo os mesmos formatos JSON que a interface web usa.
 *
 * Protege os provedores externos com:
 * <ul>
 *   <li>limite de chamadas simultâneas por processo ({@link #MAX_CONCURRENT_UPSTREAM});</li>
 *   <li>cache curto de respostas bem-sucedidas, para repetir a mesma busca sem nova chamada;</li>
 *   <li>tolerância a falhas parciais na verificação: uma fonte pode falhar sem apagar a outra.</li>
 * </ul>
 * O limite e o cache valem só para esta instância; vários processos não compartilham o estado.
 */
public final class DiscoveryApi {
    static final int MAX_CONCURRENT_UPSTREAM = 8;
    static final int MAX_QUERY_LENGTH = 120;
    static final int DEFAULT_SEARCH_LIMIT = 20;
    private static final long SEARCH_CACHE_TTL_MS = 15_000;
    private static final long VERIFY_CACHE_TTL_MS = 30_000;
    private static final long SLOT_WAIT_SECONDS = 10;
    private static final int MAX_CACHE_ENTRIES = 300;

    private final Providers providers;
    private final Semaphore upstreamSlots = new Semaphore(MAX_CONCURRENT_UPSTREAM, true);
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /** Usa os provedores reais configurados pelo ambiente. */
    public DiscoveryApi() {
        this(Providers.real());
    }

    /** Construtor injetável: os testes trocam os provedores por implementações locais. */
    DiscoveryApi(Providers providers) {
        this.providers = Objects.requireNonNull(providers, "providers");
    }

    /** Indica se há credencial do SoundCloud configurada no servidor. */
    public boolean soundCloudConfigured() {
        return providers.soundCloudConfigured();
    }

    /**
     * Pesquisa faixas e devolve {@code {"tracks": [...]}} no formato da interface web.
     *
     * @throws ApiError com status HTTP adequado para entrada inválida ou falha do provedor
     */
    public Map<String, Object> search(String query, int limit) throws InterruptedException {
        String clean = query == null ? "" : query.strip();
        if (clean.isEmpty()) {
            throw new ApiError(400, "Digite o nome de uma música ou artista.");
        }
        if (clean.length() > MAX_QUERY_LENGTH) {
            throw new ApiError(400, "A pesquisa deve ter no máximo " + MAX_QUERY_LENGTH + " caracteres.");
        }
        if (limit < 1 || limit > 50) {
            throw new ApiError(400, "O limite deve ficar entre 1 e 50.");
        }
        if (!providers.soundCloudConfigured()) {
            throw new ApiError(503, "A busca está pronta, mas falta configurar as credenciais do SoundCloud "
                    + "no ambiente do servidor (veja o README).");
        }

        String key = "search|" + limit + "|" + clean.toLowerCase(java.util.Locale.ROOT);
        Map<String, Object> cached = fromCache(key);
        if (cached != null) {
            return cached;
        }

        List<SoundCloudSearchService.SoundCloudTrack> found;
        acquireSlot();
        try {
            found = providers.search(clean, limit);
        } catch (SoundCloudSearchService.SoundCloudException e) {
            int status = e.statusCode() == 429 ? 429
                    : e.statusCode() == 401 || e.statusCode() == 403 ? 503 : 502;
            throw new ApiError(status, status == 429 ? "Limite de buscas atingido. Aguarde um pouco."
                    : status == 503 ? "O SoundCloud recusou as credenciais configuradas no servidor."
                    : "O SoundCloud não conseguiu atender à busca. Tente novamente.");
        } catch (IllegalStateException e) {
            throw new ApiError(503, e.getMessage());
        } catch (IOException e) {
            throw new ApiError(502, "Não foi possível conectar ao SoundCloud agora.");
        } finally {
            upstreamSlots.release();
        }

        List<Map<String, Object>> tracks = new ArrayList<>();
        for (var track : found) {
            // Sem ID ou link original a interface não consegue identificar nem abrir a faixa.
            if (track.id() == null || track.permalinkUrl() == null) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", track.id());
            item.put("title", track.title());
            item.put("artist", track.artist());
            item.put("artworkUrl", track.artworkUrl());
            item.put("permalinkUrl", track.permalinkUrl());
            item.put("durationMillis", track.durationMillis());
            tracks.add(item);
        }
        Map<String, Object> result = Map.of("tracks", List.copyOf(tracks));
        remember(key, result, SEARCH_CACHE_TTL_MS);
        return result;
    }

    /**
     * Confere título/artista em LRCLIB e MusicBrainz e devolve
     * {@code {"lyrics": {...}|null, "recordings": [...], "warnings": [...]}}.
     * Cada fonte é consultada separadamente; a falha de uma vira aviso.
     */
    public Map<String, Object> verify(String title, String artist, int durationSeconds)
            throws InterruptedException {
        String cleanTitle = title == null ? "" : title.strip();
        String cleanArtist = artist == null ? "" : artist.strip();
        if (cleanTitle.isEmpty() || cleanArtist.isEmpty()) {
            throw new ApiError(400, "Título e artista são necessários para verificar a faixa.");
        }
        if (cleanTitle.length() > MusicLibrary.MAX_TITLE_LENGTH
                || cleanArtist.length() > MusicLibrary.MAX_ARTIST_LENGTH) {
            throw new ApiError(400, "Título ou artista excede o limite permitido.");
        }
        if (durationSeconds < 0 || durationSeconds > MusicLibrary.MAX_DURATION_SECONDS) {
            throw new ApiError(400, "Duração inválida para verificação.");
        }

        String key = "verify|" + durationSeconds + "|" + cleanTitle.toLowerCase(java.util.Locale.ROOT)
                + "|" + cleanArtist.toLowerCase(java.util.Locale.ROOT);
        Map<String, Object> cached = fromCache(key);
        if (cached != null) {
            return cached;
        }

        List<String> warnings = new ArrayList<>();
        Map<String, Object> lyrics = null;
        boolean lyricsOk = true;
        acquireSlot();
        try {
            lyrics = lyricsJson(providers.lyrics(cleanTitle, cleanArtist, durationSeconds));
        } catch (IOException | RuntimeException e) {
            lyricsOk = false;
            warnings.add("A consulta de letras não respondeu; os metadados podem continuar disponíveis.");
        } finally {
            upstreamSlots.release();
        }

        List<Map<String, Object>> recordings = new ArrayList<>();
        boolean recordingsOk = true;
        acquireSlot();
        try {
            for (var recording : providers.recordings(cleanTitle, cleanArtist)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", recording.id());
                item.put("title", recording.title());
                item.put("artist", recording.artist());
                item.put("album", recording.album());
                item.put("length", recording.durationMillis() > 0 ? recording.durationMillis() : null);
                recordings.add(item);
            }
        } catch (IOException | RuntimeException e) {
            recordingsOk = false;
            warnings.add("A consulta MusicBrainz não respondeu; tente novamente mais tarde.");
        } finally {
            upstreamSlots.release();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("lyrics", lyrics);
        result.put("recordings", List.copyOf(recordings));
        result.put("warnings", List.copyOf(warnings));
        // Só guarda em cache quando as duas fontes responderam, para não fixar um aviso temporário.
        if (lyricsOk && recordingsOk) {
            remember(key, result, VERIFY_CACHE_TTL_MS);
        }
        return result;
    }

    private static Map<String, Object> lyricsJson(LyricsVerificationService.VerificationResult result) {
        if (result == null || !result.found()) {
            return null;
        }
        Map<String, Object> lyrics = new LinkedHashMap<>();
        lyrics.put("title", result.title());
        lyrics.put("artist", result.artist());
        lyrics.put("album", result.album());
        lyrics.put("duration", result.durationSeconds());
        lyrics.put("verified", result.verified());
        lyrics.put("matchType", result.verified() ? "strong" : "approximate");
        lyrics.put("message", result.message());
        lyrics.put("plainLyrics", result.plainLyrics());
        lyrics.put("syncedLyrics", result.syncedLyrics());
        lyrics.put("instrumental", result.instrumental());
        lyrics.put("source", result.source());
        return lyrics;
    }

    /** Espera por uma vaga de chamada externa; se demorar demais, responde 503 em vez de acumular threads. */
    private void acquireSlot() throws InterruptedException {
        if (!upstreamSlots.tryAcquire(SLOT_WAIT_SECONDS, TimeUnit.SECONDS)) {
            throw new ApiError(503, "Muitas consultas externas ao mesmo tempo. Tente novamente em instantes.");
        }
    }

    private Map<String, Object> fromCache(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null) return null;
        if (entry.expiresAt() > System.currentTimeMillis()) return entry.value();
        cache.remove(key, entry);
        return null;
    }

    private void remember(String key, Map<String, Object> value, long ttlMs) {
        long now = System.currentTimeMillis();
        if (cache.size() >= MAX_CACHE_ENTRIES) {
            // Primeiro descarta expirados; se ainda estiver cheio, remove uma entrada qualquer.
            cache.values().removeIf(e -> e.expiresAt() <= now);
            Iterator<String> keys = cache.keySet().iterator();
            if (cache.size() >= MAX_CACHE_ENTRIES && keys.hasNext()) {
                cache.remove(keys.next());
            }
        }
        cache.put(key, new CacheEntry(now + ttlMs, value));
    }

    private record CacheEntry(long expiresAt, Map<String, Object> value) { }

    /** Erro já traduzido para a API: status HTTP e mensagem segura para exibir ao usuário. */
    public static final class ApiError extends RuntimeException {
        private final int status;

        public ApiError(int status, String message) {
            super(message);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }

    /** Fronteira com os provedores externos; existe para permitir testes sem rede. */
    interface Providers {
        boolean soundCloudConfigured();

        List<SoundCloudSearchService.SoundCloudTrack> search(String query, int limit)
                throws IOException, InterruptedException;

        LyricsVerificationService.VerificationResult lyrics(String title, String artist, int durationSeconds)
                throws IOException, InterruptedException;

        List<MusicMetadataVerificationService.RecordingMetadata> recordings(String title, String artist)
                throws IOException, InterruptedException;

        /** Provedores reais. Os clientes HTTP são criados uma vez e reutilizados entre requisições. */
        static Providers real() {
            SoundCloudSearchService soundCloud = new SoundCloudSearchService();
            LyricsVerificationService lyrics = new LyricsVerificationService();
            MusicMetadataVerificationService metadata = new MusicMetadataVerificationService();
            return new Providers() {
                @Override
                public boolean soundCloudConfigured() {
                    return soundCloud.isConfigured();
                }

                @Override
                public List<SoundCloudSearchService.SoundCloudTrack> search(String query, int limit)
                        throws IOException, InterruptedException {
                    return soundCloud.search(query, limit);
                }

                @Override
                public LyricsVerificationService.VerificationResult lyrics(String title, String artist,
                                                                           int durationSeconds)
                        throws IOException, InterruptedException {
                    return lyrics.findLyrics(title, artist, durationSeconds);
                }

                @Override
                public List<MusicMetadataVerificationService.RecordingMetadata> recordings(String title,
                                                                                           String artist)
                        throws IOException, InterruptedException {
                    return metadata.search(title, artist);
                }
            };
        }
    }
}
