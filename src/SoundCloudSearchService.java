import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
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
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Integração oficial com a API do SoundCloud (v2 / REST API).
 *
 * <p>Recursos suportados:</p>
 * <ul>
 *   <li>Autenticação de aplicação via OAuth 2 Client Credentials (recomendada) com renovação
 *       automática antes do vencimento e tolerância a revogação (HTTP 401);</li>
 *   <li>Token estático opcional para testes locais rápidos ({@code SOUNDCLOUD_ACCESS_TOKEN});</li>
 *   <li>Pesquisa de faixas com suporte oficial a paginação ({@code linked_partitioning} e {@code next_href});</li>
 *   <li>Resolução de links canônicos do SoundCloud (endpoint {@code /resolve});</li>
 *   <li>Inspeção de permissões de reprodução (reproduzível, prévia ou bloqueada) sem contornar restrições;</li>
 *   <li>Atribuição obrigatória e utilitários para integração com o SoundCloud Web Widget e oEmbed.</li>
 * </ul>
 *
 * <p>Segurança: credenciais e tokens são mantidos exclusivamente na memória do servidor e nunca
 * registrados em log ou expostos ao frontend.</p>
 */
public final class SoundCloudSearchService {
    public static final String API_BASE = "https://api.soundcloud.com";
    public static final String TOKEN_URL = "https://secure.soundcloud.com/oauth/token";
    public static final String WIDGET_BASE = "https://w.soundcloud.com/player/";
    public static final String OEMBED_URL = "https://soundcloud.com/oembed";

    /** Renova o token 60s antes do vencimento informado pelo provedor para evitar chamadas com token expirado. */
    private static final long TOKEN_REFRESH_MARGIN_SECONDS = 60;
    private static final long DEFAULT_TOKEN_LIFETIME_SECONDS = 3600;
    private static final int MAX_QUERY_LENGTH = 120;
    private static final int MAX_PAGE_LIMIT = 50;

    /** Domínios autorizados para resolução e paginação, prevenindo ataques de SSRF. */
    private static final Set<String> ALLOWED_HOSTS = Set.of(
            "soundcloud.com",
            "m.soundcloud.com",
            "on.soundcloud.com",
            "api.soundcloud.com",
            "api-v2.soundcloud.com"
    );

    private final String staticToken;
    private final String clientId;
    private final String clientSecret;
    private final String apiBase;
    private final String tokenUrl;
    private final HttpClient httpClient;
    private final ObjectMapper json;
    private final Clock clock;

    // Cache thread-safe do token de aplicação.
    private String cachedToken;
    private Instant cachedTokenRefreshAt = Instant.EPOCH;

    /**
     * Estado de disponibilidade da faixa na plataforma do SoundCloud.
     */
    public enum PlaybackState {
        /** Faixa completa liberada para reprodução pública. */
        PLAYABLE,
        /** Apenas trecho demonstrativo autorizado (preview / snippet). */
        PREVIEW,
        /** Reprodução restrita por direitos autorais, geobloqueio ou paywall (SoundCloud Go+). */
        BLOCKED
    }

    /**
     * Resultado paginado de consulta à coleção do SoundCloud.
     */
    public record PagedResult<T>(List<T> collection, String nextHref) {
        public boolean hasNext() {
            return nextHref != null && !nextHref.isBlank();
        }
    }

    /**
     * Metadados públicos de uma faixa do SoundCloud.
     */
    public record SoundCloudTrack(
            String id,
            String title,
            String artist,
            String artworkUrl,
            String permalinkUrl,
            long durationMillis,
            PlaybackState playbackState,
            boolean streamable,
            String waveformUrl
    ) {
        /** Construtor de compatibilidade para código que espera o formato simplificado de 6 campos. */
        public SoundCloudTrack(String id, String title, String artist, String artworkUrl,
                               String permalinkUrl, long durationMillis) {
            this(id, title, artist, artworkUrl, permalinkUrl, durationMillis, PlaybackState.PLAYABLE, true, null);
        }
    }

    /** Exceção com o status HTTP original retornado pela API do SoundCloud. */
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

    /** Usa as variáveis de ambiente e o cliente HTTP padrão. */
    public SoundCloudSearchService() {
        this(System.getenv("SOUNDCLOUD_ACCESS_TOKEN"),
                System.getenv("SOUNDCLOUD_CLIENT_ID"),
                System.getenv("SOUNDCLOUD_CLIENT_SECRET"),
                API_BASE, TOKEN_URL,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                new ObjectMapper(), Clock.systemUTC());
    }

    /** Construtor para testes com token fixo injetado. */
    SoundCloudSearchService(String accessToken, HttpClient httpClient, ObjectMapper json) {
        this(accessToken, null, null, API_BASE, TOKEN_URL, httpClient, json, Clock.systemUTC());
    }

    /** Construtor completo injetável para testes com servidores simulados. */
    SoundCloudSearchService(String accessToken, String clientId, String clientSecret,
                            String apiBase, String tokenUrl, HttpClient httpClient,
                            ObjectMapper json, Clock clock) {
        this.staticToken = accessToken;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.apiBase = Objects.requireNonNull(apiBase, "apiBase");
        this.tokenUrl = Objects.requireNonNull(tokenUrl, "tokenUrl");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.json = Objects.requireNonNull(json, "json");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Informa se há credenciais de autenticação configuradas no ambiente. */
    public boolean isConfigured() {
        return usesClientCredentials() || !isBlank(staticToken);
    }

    /**
     * Pesquisa faixas públicas por termo de busca com limite de resultados (compatibilidade retroativa).
     */
    public List<SoundCloudTrack> search(String query, int limit) throws IOException, InterruptedException {
        return searchPaged(query, limit, null).collection();
    }

    /**
     * Pesquisa faixas públicas com suporte oficial à paginação (linked_partitioning).
     *
     * @param query termo de busca (obrigatório se nextHref for nulo)
     * @param limit quantidade de itens desejada (1 a 50)
     * @param nextHref link da próxima página fornecido pela API em requisição anterior, se houver
     * @return página contendo a lista de faixas e o link opcional para a próxima página
     */
    public PagedResult<SoundCloudTrack> searchPaged(String query, int limit, String nextHref)
            throws IOException, InterruptedException {
        URI uri;
        if (nextHref != null && !nextHref.isBlank()) {
            uri = validateExternalUri(nextHref.strip());
        } else {
            if (query == null || query.isBlank()) {
                throw new IllegalArgumentException("Digite o nome da música ou do artista.");
            }
            if (query.strip().length() > MAX_QUERY_LENGTH) {
                throw new IllegalArgumentException("A pesquisa deve ter no máximo " + MAX_QUERY_LENGTH + " caracteres.");
            }
            if (limit < 1 || limit > MAX_PAGE_LIMIT) {
                throw new IllegalArgumentException("O limite deve ficar entre 1 e " + MAX_PAGE_LIMIT + ".");
            }
            String encodedQuery = URLEncoder.encode(query.strip(), StandardCharsets.UTF_8);
            uri = URI.create(apiBase + "/tracks?q=" + encodedQuery
                    + "&limit=" + limit + "&linked_partitioning=true");
        }

        if (!isConfigured()) {
            throw new IllegalStateException("SoundCloud não configurado: defina SOUNDCLOUD_CLIENT_ID e "
                    + "SOUNDCLOUD_CLIENT_SECRET (ou SOUNDCLOUD_ACCESS_TOKEN) no ambiente do servidor.");
        }

        HttpResponse<String> response = sendWithAuthAndRetry(uri);
        JsonNode root = json.readTree(response.body());

        JsonNode collection = root.isArray() ? root : root.path("collection");
        String resolvedNextHref = textOrNull(root, "next_href");

        List<SoundCloudTrack> tracks = new ArrayList<>();
        if (collection.isArray()) {
            for (JsonNode item : collection) {
                SoundCloudTrack track = parseTrack(item);
                if (track != null) {
                    tracks.add(track);
                }
            }
        }
        return new PagedResult<>(List.copyOf(tracks), resolvedNextHref);
    }

    /**
     * Resolve um link permanente do SoundCloud (ex: {@code https://soundcloud.com/artista/musica})
     * para a faixa correspondente na API oficial usando o endpoint {@code /resolve}.
     *
     * @param permalinkUrl URL canônica no SoundCloud
     * @return metadados da faixa ou vazio se o recurso não for uma faixa válida ou não existir
     */
    public Optional<SoundCloudTrack> resolveTrack(String permalinkUrl) throws IOException, InterruptedException {
        if (permalinkUrl == null || permalinkUrl.isBlank()) {
            throw new IllegalArgumentException("A URL permanente do SoundCloud é obrigatória.");
        }
        if (!isConfigured()) {
            throw new IllegalStateException("SoundCloud não configurado: defina credenciais no servidor.");
        }

        URI inputUri = validateExternalUri(permalinkUrl.strip());
        String encoded = URLEncoder.encode(inputUri.toString(), StandardCharsets.UTF_8);
        URI requestUri = URI.create(apiBase + "/resolve?url=" + encoded);

        HttpResponse<String> response = sendWithAuthAndRetry(requestUri);

        // O endpoint /resolve frequentemente responde com redirect 302/301 para a URL da API
        if (response.statusCode() == 301 || response.statusCode() == 302) {
            String location = response.headers().firstValue("Location").orElse(null);
            if (location == null) {
                return Optional.empty();
            }
            URI targetUri = validateExternalUri(location);
            response = sendWithAuthAndRetry(targetUri);
        }

        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new SoundCloudException(response.statusCode(),
                    "Falha ao resolver link do SoundCloud (HTTP " + response.statusCode() + ").");
        }

        JsonNode node = json.readTree(response.body());
        SoundCloudTrack track = parseTrack(node);
        return Optional.ofNullable(track);
    }

    /**
     * Solicita à API a URL temporária de stream de áudio autorizada para uma faixa.
     * Respeita restrições e faixas bloqueadas retornando {@code Optional.empty()}.
     *
     * @param trackId URN ou identificador numérico da faixa
     * @return URL HTTPS de reprodução autorizada, ou vazio caso a faixa não seja reproduzível
     */
    public Optional<URI> urlDeStream(String trackId) throws IOException, InterruptedException {
        if (trackId == null || trackId.isBlank()) {
            return Optional.empty();
        }
        if (!isConfigured()) {
            throw new IllegalStateException("SoundCloud não configurado: defina credenciais no servidor.");
        }

        String encoded = URLEncoder.encode(trackId.strip(), StandardCharsets.UTF_8);
        URI uri = URI.create(apiBase + "/tracks/" + encoded + "/streams");
        HttpResponse<String> response;
        try {
            response = sendWithAuthAndRetry(uri);
        } catch (SoundCloudException e) {
            if (e.statusCode() == 401 || e.statusCode() == 403 || e.statusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }

        if (response.statusCode() == 401 || response.statusCode() == 403 || response.statusCode() == 404) {
            return Optional.empty();
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new SoundCloudException(response.statusCode(),
                    "O SoundCloud não informou o áudio da faixa (HTTP " + response.statusCode() + ").");
        }
        return escolherUrlDeStream(json.readTree(response.body()));
    }

    /**
     * Abre a stream autorizada via HTTPS sem gravar o arquivo em disco.
     */
    public InputStream baixarAudio(URI url) throws IOException, InterruptedException {
        if (url == null || !"https".equalsIgnoreCase(url.getScheme())) {
            throw new IOException("A URL de áudio autorizada precisa ser HTTPS.");
        }
        HttpRequest request = HttpRequest.newBuilder(url)
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "audio/mpeg, */*")
                .GET()
                .build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            response.body().close();
            throw new IOException("O áudio autorizado não pôde ser lido (HTTP " + response.statusCode() + ").");
        }
        return response.body();
    }

    /**
     * Gera a URL para incorporar o SoundCloud Web Player Widget oficial (iframe).
     * Esta é a alternativa recomendada pelo SoundCloud para exibir faixas com fidelidade
     * aos direitos de autor, estatísticas de reprodução e atribuição visual completa.
     *
     * @param permalinkUrl link canônico da faixa no SoundCloud
     * @return URI formatada pronta para o parâmetro {@code src} do iframe
     */
    public static URI widgetEmbedUri(String permalinkUrl) {
        if (permalinkUrl == null || permalinkUrl.isBlank()) {
            throw new IllegalArgumentException("A URL permanente é obrigatória para o Widget.");
        }
        String encoded = URLEncoder.encode(permalinkUrl.strip(), StandardCharsets.UTF_8);
        return URI.create(WIDGET_BASE + "?url=" + encoded
                + "&color=%23ff5500&auto_play=false&hide_related=false&show_comments=true&show_user=true&show_reposts=false&show_teaser=true");
    }

    /**
     * Gera a URL do endpoint oficial oEmbed para metadados e código embed oficial.
     */
    public static URI oembedUri(String permalinkUrl) {
        if (permalinkUrl == null || permalinkUrl.isBlank()) {
            throw new IllegalArgumentException("A URL permanente é obrigatória para o oEmbed.");
        }
        String encoded = URLEncoder.encode(permalinkUrl.strip(), StandardCharsets.UTF_8);
        return URI.create(OEMBED_URL + "?format=json&url=" + encoded);
    }

    /**
     * Mapeia um nó JSON de faixa do SoundCloud para {@link SoundCloudTrack},
     * inspecionando título, autor, status de reprodução e permissões da plataforma.
     */
    static SoundCloudTrack parseTrack(JsonNode item) {
        if (item == null || !item.isObject()) {
            return null;
        }
        JsonNode user = item.path("user");
        String title = textOrNull(item, "title");
        String artist = textOrNull(user, "username");
        if (title == null || artist == null) {
            return null; // Ignora faixas sem título ou sem identificação do criador
        }

        String id = textOrNull(item, "urn");
        if (id == null) {
            id = textOrNull(item, "id");
        }

        // Avaliação de permissão de reprodução
        boolean streamable = item.path("streamable").asBoolean(true);
        String access = textOrNull(item, "access");
        String policy = textOrNull(item, "policy");

        PlaybackState playbackState;
        if ("blocked".equalsIgnoreCase(access) || "BLOCK".equalsIgnoreCase(policy) || !streamable) {
            playbackState = PlaybackState.BLOCKED;
        } else if ("preview".equalsIgnoreCase(access) || "SNIP".equalsIgnoreCase(policy)) {
            playbackState = PlaybackState.PREVIEW;
        } else {
            playbackState = PlaybackState.PLAYABLE;
        }

        return new SoundCloudTrack(
                id,
                title,
                artist,
                textOrNull(item, "artwork_url"),
                textOrNull(item, "permalink_url"),
                item.path("duration").asLong(0),
                playbackState,
                streamable,
                textOrNull(item, "waveform_url")
        );
    }

    /** Escolhe a URL de stream dentre os formatos retornados pelo endpoint {@code /streams}. */
    static Optional<URI> escolherUrlDeStream(JsonNode root) {
        if (root == null || !root.isObject()) {
            return Optional.empty();
        }
        String completa = textOrNull(root, "http_mp3_128_url");
        String previa = textOrNull(root, "preview_mp3_128_url");
        String escolhida = completa != null ? completa : previa;
        if (escolhida == null) {
            return Optional.empty();
        }
        try {
            URI url = URI.create(escolhida);
            if (!"https".equalsIgnoreCase(url.getScheme())) {
                return Optional.empty();
            }
            return Optional.of(url);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Envia requisição HTTP com token de autenticação, renovação automática em 401
     * e retry para erros transitórios (5xx).
     */
    private HttpResponse<String> sendWithAuthAndRetry(URI uri) throws IOException, InterruptedException {
        String token = accessToken(false);
        HttpResponse<String> response = sendGet(uri, token);

        // Se o token foi revogado antes da hora pelo SoundCloud, força renovação e repete uma vez
        if (response.statusCode() == 401 && usesClientCredentials()) {
            token = accessToken(true);
            response = sendGet(uri, token);
        }

        // Falhas transitórias do provedor (502, 503, 504): tenta mais uma vez com backoff curto
        if (response.statusCode() == 502 || response.statusCode() == 503 || response.statusCode() == 504) {
            Thread.sleep(150);
            response = sendGet(uri, token);
        }

        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new SoundCloudException(response.statusCode(),
                    "SoundCloud recusou o acesso. Verifique as credenciais e permissões do aplicativo.");
        }
        if (response.statusCode() == 429) {
            throw new SoundCloudException(429,
                    "Limite de requisições do SoundCloud atingido. Aguarde antes de tentar novamente.");
        }
        if (response.statusCode() >= 400 && response.statusCode() != 404 && (response.statusCode() < 300 || response.statusCode() > 302)) {
            throw new SoundCloudException(response.statusCode(),
                    "A requisição ao SoundCloud falhou com código HTTP " + response.statusCode() + ".");
        }

        return response;
    }

    private HttpResponse<String> sendGet(URI uri, String token) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json");
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "OAuth " + token);
        }
        return httpClient.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /**
     * Obtém o token de acesso. Com client credentials, reaproveita o token em cache até
     * perto do vencimento. Nunca expõe credenciais em logs ou exceções.
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

    /** Valida URLs externas para prevenir SSRF antes de repassar à API interna. */
    private URI validateExternalUri(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException("Apenas URLs HTTP(S) são aceitas.");
            }
            if (host == null) {
                throw new IllegalArgumentException("URL inválida: host ausente.");
            }
            String lowerHost = host.toLowerCase(Locale.ROOT);
            String baseHost = URI.create(apiBase).getHost();
            boolean isConfiguredBase = baseHost != null && baseHost.equalsIgnoreCase(lowerHost);

            boolean allowed = isConfiguredBase
                    || ALLOWED_HOSTS.contains(lowerHost)
                    || ALLOWED_HOSTS.stream().anyMatch(h -> lowerHost.endsWith("." + h));
            if (!allowed) {
                throw new IllegalArgumentException("Apenas links do domínio soundcloud.com são permitidos.");
            }
            return uri;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("URL fornecida é inválida ou não pertence ao SoundCloud: " + e.getMessage());
        }
    }

    private boolean usesClientCredentials() {
        return !isBlank(clientId) && !isBlank(clientSecret);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank()
                ? null : value.asText();
    }
}
