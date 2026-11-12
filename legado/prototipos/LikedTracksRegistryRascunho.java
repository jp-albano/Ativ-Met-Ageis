import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class LikedTracksRegistry {

    private final Set<Long> favoriteIds = new LinkedHashSet<>();
    private final TrackCatalog catalog;

    public LikedTracksRegistry(TrackCatalog catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException("O catálogo não pode ser nulo.");
        }
        this.catalog = catalog;
    }

    public boolean addToFavorites(long trackId) throws TrackNotFoundException {
        if (!catalog.contains(trackId)) {
            throw new TrackNotFoundException("Não foi possível favoritar: não existe nenhuma faixa com ID " + trackId + " no catálogo.");
        }
        return favoriteIds.add(trackId);
    }

    public boolean removeFromFavorites(long trackId) {
        return favoriteIds.remove(trackId);
    }

    public boolean isFavorite(long trackId) {
        return favoriteIds.contains(trackId);
    }

    public List<Long> listFavorites() {
        return List.copyOf(favoriteIds);
    }
}
