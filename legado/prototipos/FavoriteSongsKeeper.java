import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class FavoriteSongsKeeper {

    private final Set<Long> favoriteIds = new LinkedHashSet<>();
    private final TrackSource catalog;

    public FavoriteSongsKeeper(TrackSource catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException("O catálogo não pode ser nulo.");
        }
        this.catalog = catalog;
    }

    public boolean addToFavorites(long trackId) throws UnknownTrackException {
        if (catalog.findById(trackId) == null) {
            throw new UnknownTrackException("Não foi possível favoritar: não existe nenhuma faixa com ID " + trackId + " no catálogo.");
        }
        return favoriteIds.add(trackId);
    }

    public boolean removeFromFavorites(long trackId) {
        return favoriteIds.remove(trackId);
    }

    public boolean isFavorite(long trackId) {
        return favoriteIds.contains(trackId);
    }

    public List<Track> listFavorites() {
        List<Track> tracks = new ArrayList<>();
        for (Long id : favoriteIds) {
            Track track = catalog.findById(id);
            if (track != null) {
                tracks.add(track);
            }
        }
        return tracks;
    }

    public void showFavorites() {
        List<Track> tracks = listFavorites();
        if (tracks.isEmpty()) {
            System.out.println("Você ainda não tem músicas curtidas.");
            return;
        }
        for (int i = 0; i < tracks.size(); i++) {
            Track track = tracks.get(i);
            System.out.println((i + 1) + ". " + track.getTitle() + " - " + track.getArtist() + " (" + formatDuration(track.getDurationInSeconds()) + ")");
        }
    }

    private String formatDuration(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }
}
