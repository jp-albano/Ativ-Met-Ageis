import java.io.File;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Fachada do domínio usada pela API HTTP: reúne catálogo, favoritos e playlists.
 *
 * Os serviços de domínio guardam estado em memória e não são thread-safe; o servidor
 * HTTP atende requisições em várias threads. Por isso todos os métodos públicos são
 * {@code synchronized} e devolvem cópias imutáveis (records), nunca objetos internos.
 * Os dados são perdidos ao reiniciar o processo: não há persistência durável.
 */
public final class MusicLibrary {

    /** Limites de entrada aceitos pela API, para evitar textos arbitrariamente grandes. */
    static final int MAX_TITLE_LENGTH = 200;
    static final int MAX_ARTIST_LENGTH = 160;
    static final int MAX_PLAYLIST_NAME_LENGTH = 120;
    static final int MAX_DURATION_SECONDS = 86_400;

    private final CatalogService catalog;
    private final FavoriteService favorites;
    private final PlaylistService playlists;
    private final SearchService search = new SearchService();

    /** Cria a biblioteca com o catálogo inicial de demonstração. */
    public MusicLibrary() {
        this(new CatalogService(), new FavoriteService(), new PlaylistService());
    }

    /** Construtor injetável para testes. */
    MusicLibrary(CatalogService catalog, FavoriteService favorites, PlaylistService playlists) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.favorites = Objects.requireNonNull(favorites, "favorites");
        this.playlists = Objects.requireNonNull(playlists, "playlists");
    }

    // ---------------------------------------------------------------- faixas

    /** Lista o catálogo; com termo preenchido, filtra por título ou artista. */
    public synchronized List<TrackView> listTracks(String term) {
        List<Track> tracks = term == null || term.isBlank()
                ? catalog.getAllTracks()
                : search.search(term, catalog.getAllTracks());
        return tracks.stream().map(this::view).toList();
    }

    /** Procura uma faixa pelo ID exato. */
    public synchronized Optional<TrackView> findTrack(String id) {
        return catalog.findById(id).map(this::view);
    }

    /**
     * Cadastra uma faixa com ID gerado no formato t-NNN.
     * O caminho de áudio é apenas um marcador; a API não recebe nem grava arquivos.
     *
     * @throws IllegalArgumentException quando título, artista ou duração forem inválidos
     */
    public synchronized TrackView addTrack(String title, String artist, int durationSeconds) {
        String cleanTitle = requireText(title, "título", MAX_TITLE_LENGTH);
        String cleanArtist = requireText(artist, "artista", MAX_ARTIST_LENGTH);
        if (durationSeconds < 0 || durationSeconds > MAX_DURATION_SECONDS) {
            throw new IllegalArgumentException("A duração deve ficar entre 0 e "
                    + MAX_DURATION_SECONDS + " segundos.");
        }
        Track track = new Track(nextTrackId(), cleanTitle, cleanArtist, durationSeconds,
                null, new File("audio/" + slug(cleanTitle) + ".mp3"));
        catalog.addTrack(track);
        return view(track);
    }

    /** Remove a faixa do catálogo e também dos favoritos e de todas as playlists. */
    public synchronized boolean removeTrack(String id) {
        Optional<Track> track = catalog.findById(id);
        if (track.isEmpty()) {
            return false;
        }
        favorites.remove(track.get());
        playlists.removerFaixaDeTodas(id);
        return catalog.removeById(id);
    }

    // ------------------------------------------------------------- favoritos

    /** Lista as faixas favoritas na ordem em que foram marcadas. */
    public synchronized List<TrackView> listFavorites() {
        return favorites.getFavorites().stream().map(this::view).toList();
    }

    /**
     * Marca uma faixa do catálogo como favorita.
     *
     * @return vazio quando a faixa não existe; caso contrário, se ela foi adicionada agora
     */
    public synchronized Optional<Boolean> addFavorite(String trackId) {
        return catalog.findById(trackId).map(favorites::add);
    }

    /**
     * Desmarca uma faixa favorita.
     *
     * @return vazio quando a faixa não existe; caso contrário, se ela foi removida agora
     */
    public synchronized Optional<Boolean> removeFavorite(String trackId) {
        return catalog.findById(trackId).map(favorites::remove);
    }

    // ------------------------------------------------------------- playlists

    /** Lista resumos das playlists na ordem de criação. */
    public synchronized List<PlaylistSummary> listPlaylists() {
        return playlists.listar().stream()
                .map(p -> new PlaylistSummary(p.getId(), p.getNome(), p.getQuantidadeFaixas()))
                .toList();
    }

    /** Cria uma playlist vazia. */
    public synchronized PlaylistView createPlaylist(String name) {
        String cleanName = requireText(name, "nome da playlist", MAX_PLAYLIST_NAME_LENGTH);
        return playlistView(playlists.criar(cleanName));
    }

    /** Detalha uma playlist com os dados das faixas que ainda existem no catálogo. */
    public synchronized Optional<PlaylistView> findPlaylist(int id) {
        return playlists.buscarPorId(id).map(this::playlistView);
    }

    /** Apaga uma playlist; as faixas continuam no catálogo. */
    public synchronized boolean deletePlaylist(int id) {
        return playlists.remover(id);
    }

    /** Adiciona uma faixa existente à playlist, sem duplicar. */
    public synchronized PlaylistChange addToPlaylist(int playlistId, String trackId) {
        Optional<Playlist> playlist = playlists.buscarPorId(playlistId);
        if (playlist.isEmpty()) {
            return PlaylistChange.playlistNotFound();
        }
        if (catalog.findById(trackId).isEmpty()) {
            return PlaylistChange.trackNotFound();
        }
        boolean changed = playlist.get().adicionarFaixa(trackId);
        return new PlaylistChange(PlaylistChange.Status.OK, changed, playlistView(playlist.get()));
    }

    /** Retira uma faixa da playlist. */
    public synchronized PlaylistChange removeFromPlaylist(int playlistId, String trackId) {
        Optional<Playlist> playlist = playlists.buscarPorId(playlistId);
        if (playlist.isEmpty()) {
            return PlaylistChange.playlistNotFound();
        }
        if (!playlist.get().removerFaixa(trackId)) {
            return PlaylistChange.trackNotFound();
        }
        return new PlaylistChange(PlaylistChange.Status.OK, true, playlistView(playlist.get()));
    }

    // ----------------------------------------------------------------- apoio

    private TrackView view(Track track) {
        return new TrackView(track.id(), track.title(), track.artist(),
                track.durationSeconds(), track.formattedDuration(), favorites.isFavorite(track));
    }

    private PlaylistView playlistView(Playlist playlist) {
        List<TrackView> tracks = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String trackId : playlist.getFaixaIds()) {
            catalog.findById(trackId).ifPresentOrElse(t -> tracks.add(view(t)), () -> missing.add(trackId));
        }
        return new PlaylistView(playlist.getId(), playlist.getNome(), playlist.getQuantidadeFaixas(),
                List.copyOf(tracks), List.copyOf(missing));
    }

    /** Mesmo formato de ID usado pelo menu de terminal: t-NNN, pulando IDs já ocupados. */
    private String nextTrackId() {
        int n = catalog.getTrackCount() + 1;
        String id;
        do {
            id = "t-%03d".formatted(n++);
        } while (catalog.findById(id).isPresent());
        return id;
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("O campo " + field + " é obrigatório.");
        }
        String clean = value.strip();
        if (clean.length() > maxLength) {
            throw new IllegalArgumentException("O campo " + field + " deve ter no máximo "
                    + maxLength + " caracteres.");
        }
        return clean;
    }

    private static String slug(String title) {
        String withoutAccents = Normalizer.normalize(title, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = withoutAccents.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return slug.isEmpty() ? "faixa" : slug;
    }

    // ------------------------------------------------------- modelos da API

    /** Faixa como a API expõe: sem caminhos de arquivos locais. */
    public record TrackView(String id, String title, String artist, int durationSeconds,
                            String duration, boolean favorite) { }

    /** Resumo usado na listagem de playlists. */
    public record PlaylistSummary(int id, String name, int trackCount) { }

    /** Playlist detalhada; missingTrackIds lista IDs que não estão mais no catálogo. */
    public record PlaylistView(int id, String name, int trackCount, List<TrackView> tracks,
                               List<String> missingTrackIds) { }

    /** Resultado de uma alteração de playlist, distinguindo os dois tipos de "não encontrado". */
    public record PlaylistChange(Status status, boolean changed, PlaylistView playlist) {
        public enum Status { OK, PLAYLIST_NOT_FOUND, TRACK_NOT_FOUND }

        static PlaylistChange playlistNotFound() {
            return new PlaylistChange(Status.PLAYLIST_NOT_FOUND, false, null);
        }

        static PlaylistChange trackNotFound() {
            return new PlaylistChange(Status.TRACK_NOT_FOUND, false, null);
        }
    }
}
