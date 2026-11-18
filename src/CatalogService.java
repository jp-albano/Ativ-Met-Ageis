import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Mantém o catálogo de faixas disponível para a aplicação.
 * O catálogo inicial é de demonstração. Os caminhos são referências locais: devem ser
 * não vazios, mas a existência dos arquivos não é exigida para cadastrar uma faixa.
 */
public class CatalogService {

    private final List<Track> tracks;

    /** Inicializa o catálogo em memória com algumas faixas de exemplo. */
    public CatalogService() {
        this(initialCatalog());
    }

    /**
     * Inicializa um catálogo com a coleção informada, preservando a ordem recebida.
     * A coleção é copiada e validada para impedir nulos ou IDs repetidos.
     */
    public CatalogService(Collection<Track> initialTracks) {
        Objects.requireNonNull(initialTracks, "initialTracks");
        this.tracks = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Track track : initialTracks) {
            Objects.requireNonNull(track, "initialTracks must not contain null");
            if (!ids.add(track.id())) {
                throw new IllegalArgumentException("Duplicate track id: " + track.id());
            }
            tracks.add(track);
        }
    }

    /** Cria a lista inicial imutável de exemplos que será copiada para a lista interna. */
    private static List<Track> initialCatalog() {
        return List.of(
                new Track("t-001", "Bohemian Rhapsody", "Queen", 354,
                        new File("covers/bohemian_rhapsody.jpg"),
                        new File("audio/bohemian_rhapsody.mp3")),
                new Track("t-002", "Imagine", "John Lennon", 183,
                        new File("covers/imagine.jpg"),
                        new File("audio/imagine.mp3")),
                new Track("t-003", "Billie Jean", "Michael Jackson", 294,
                        new File("covers/billie_jean.jpg"),
                        new File("audio/billie_jean.mp3")),
                new Track("t-004", "Stairway to Heaven", "Led Zeppelin", 482,
                        new File("covers/stairway_to_heaven.jpg"),
                        new File("audio/stairway_to_heaven.mp3")),
                new Track("t-005", "Hotel California", "Eagles", 391,
                        new File("covers/hotel_california.jpg"),
                        new File("audio/hotel_california.mp3")),
                new Track("t-006", "Smells Like Teen Spirit", "Nirvana", 301,
                        new File("covers/smells_like_teen_spirit.jpg"),
                        new File("audio/smells_like_teen_spirit.mp3")),
                new Track("t-007", "Hey Jude", "The Beatles", 431,
                        new File("covers/hey_jude.jpg"),
                        new File("audio/hey_jude.mp3")),
                new Track("t-008", "Like a Rolling Stone", "Bob Dylan", 369,
                        new File("covers/like_a_rolling_stone.jpg"),
                        new File("audio/like_a_rolling_stone.mp3"))
        );
    }

    /** Retorna uma cópia imutável para que consumidores não modifiquem o catálogo por fora. */
    public List<Track> getAllTracks() {
        return List.copyOf(tracks);
    }

    /** Busca a primeira faixa com o ID exato informado. */
    public Optional<Track> findById(String id) {
        return tracks.stream()
                .filter(t -> t.id().equals(id))
                .findFirst();
    }

    /** Pesquisa títulos por trecho, sem diferenciar maiúsculas/minúsculas. */
    public List<Track> findByTitle(String term) {
        String needle = term.toLowerCase(java.util.Locale.ROOT);
        return filterBy(t -> t.title().toLowerCase(java.util.Locale.ROOT).contains(needle));
    }

    /** Pesquisa artistas por trecho, sem diferenciar maiúsculas/minúsculas. */
    public List<Track> findByArtist(String artist) {
        String needle = artist.toLowerCase(java.util.Locale.ROOT);
        return filterBy(t -> t.artist().toLowerCase(java.util.Locale.ROOT).contains(needle));
    }

    /** Retorna a quantidade atual de faixas cadastradas. */
    public int getTrackCount() {
        return tracks.size();
    }

    /**
     * Adiciona uma faixa se o ID ainda não estiver cadastrado.
     * @throws NullPointerException se a faixa for null
     * @throws IllegalArgumentException se já existir uma faixa com o mesmo ID
     */
    public void addTrack(Track track) {
        Objects.requireNonNull(track, "track");
        if (findById(track.id()).isPresent()) {
            throw new IllegalArgumentException("Duplicate track id: " + track.id());
        }
        tracks.add(track);
    }

    /** Remove todas as faixas com o ID informado; retorna true quando houve remoção. */
    public boolean removeById(String id) {
        return tracks.removeIf(t -> t.id().equals(id));
    }

    /** Aplica um filtro reutilizável e devolve somente os resultados encontrados. */
    private List<Track> filterBy(Predicate<Track> predicate) {
        return tracks.stream().filter(predicate).toList();
    }
}
