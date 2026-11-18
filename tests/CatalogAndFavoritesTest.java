import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Testes para catálogo, duração e favoritos. */
class CatalogAndFavoritesTest {

    @Test
    void catalogoInicialPossuiFaixasEPermiteBusca() {
        CatalogService catalog = new CatalogService();

        assertTrue(catalog.getTrackCount() > 0);
        assertTrue(catalog.findById("t-001").isPresent());
        assertFalse(catalog.findByTitle("BOHEMIAN").isEmpty());
        assertFalse(catalog.findByArtist("queen").isEmpty());
    }

    @Test
    void naoPermiteIdDuplicadoNoCatalogo() {
        CatalogService catalog = new CatalogService();
        Track original = catalog.getAllTracks().get(0);
        Track duplicada = new Track(original.id(), "Outro título", "Outro artista",
                10, null, new File("outro.mp3"));

        assertThrows(IllegalArgumentException.class, () -> catalog.addTrack(duplicada));
    }

    @Test
    void favoritosNaoDuplicamEAlternamEstado() {
        Track track = new Track("fav-1", "Música", "Artista", 125, null, new File("audio.mp3"));
        FavoriteService favorites = new FavoriteService();

        assertTrue(favorites.add(track));
        assertFalse(favorites.add(track));
        assertTrue(favorites.isFavorite(track));
        assertFalse(favorites.toggle(track));
        assertFalse(favorites.isFavorite(track));
        assertEquals(0, favorites.count());
    }

    @Test
    void formataDuracaoEComparaFaixasPorId() {
        Track primeira = new Track("same", "Uma", "A", 194, null, new File("a.mp3"));
        Track segunda = new Track("same", "Outra", "B", 12, null, new File("b.mp3"));

        assertEquals("03:14", primeira.formattedDuration());
        assertEquals(primeira, segunda);
        assertEquals(primeira.hashCode(), segunda.hashCode());
    }

    @Test
    void rejeitaCamposObrigatoriosInvalidosEDuracaoNaoPositiva() {
        assertThrows(NullPointerException.class,
                () -> new Track(null, "Título", "Artista", 1, null, new File("a.mp3")));
        assertThrows(IllegalArgumentException.class,
                () -> new Track("id", "  ", "Artista", 1, null, new File("a.mp3")));
        assertThrows(IllegalArgumentException.class,
                () -> new Track("id", "Título", "\t", 1, null, new File("a.mp3")));
        assertThrows(IllegalArgumentException.class,
                () -> new Track("id", "Título", "Artista", 0, null, new File("a.mp3")));
        assertThrows(IllegalArgumentException.class,
                () -> new Track("id", "Título", "Artista", -1, null, new File("a.mp3")));
    }

    @Test
    void normalizaTextosEValidaReferenciasSemExigirArquivosExistentes() {
        Track faixa = new Track(" id ", " Título ", " Artista ", 1,
                new File("covers/inexistente.jpg"), new File("audio/inexistente.mp3"));

        assertEquals("id", faixa.id());
        assertEquals("Título", faixa.title());
        assertEquals("Artista", faixa.artist());
        assertFalse(faixa.audioFile().exists());
        assertFalse(faixa.coverFile().exists());
        assertThrows(IllegalArgumentException.class,
                () -> new Track("id", "Título", "Artista", 1, null, new File("  ")));
    }

    @Test
    void catalogoVazioPreservaOrdemEIsolaColecaoExposta() {
        CatalogService catalog = new CatalogService(List.of());

        assertTrue(catalog.getAllTracks().isEmpty());
        assertEquals(0, catalog.getTrackCount());
        assertTrue(catalog.findById("ausente").isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> catalog.getAllTracks().add(new Track("id", "Título", "Artista", 1,
                        null, new File("a.mp3"))));
    }

    @Test
    void catalogoInicialListaTodasNaOrdemEIdAusenteNaoAlteraEstado() {
        CatalogService catalog = new CatalogService();
        List<Track> antes = catalog.getAllTracks();

        assertTrue(antes.stream().allMatch(track -> track.audioFile().isFile()),
                "o catálogo inicial deve apontar para arquivos de áudio existentes");
        assertEquals(List.of("t-001", "t-002", "t-003", "t-004", "t-005", "t-006",
                "t-007", "t-008"), antes.stream().map(Track::id).toList());
        assertTrue(catalog.findById("nao-existe").isEmpty());
        assertEquals(antes, catalog.getAllTracks());
        assertThrows(IllegalArgumentException.class,
                () -> new CatalogService(List.of(antes.get(0), antes.get(0))));
    }
}
