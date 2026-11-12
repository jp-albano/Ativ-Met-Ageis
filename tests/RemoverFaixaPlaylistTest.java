import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Remove uma faixa da playlist pelo ID, sem exigir que ela continue no catálogo. */
class RemoverFaixaPlaylistTest {

    @Test
    void removeFaixaExistenteEIgnoraEspacos() {
        PlaylistService service = new PlaylistService();
        CatalogService catalogo = new CatalogService();
        Playlist playlist = service.criar("Favoritas");
        service.adicionarFaixa(playlist.getId(), "t-001", catalogo);

        Playlist atualizada = service.removerFaixa(playlist.getId(), "  t-001  ");

        assertEquals(0, atualizada.getQuantidadeFaixas());
        assertTrue(atualizada.getFaixaIds().isEmpty());
    }

    @Test
    void rejeitaPlaylistOuFaixaInexistente() {
        PlaylistService service = new PlaylistService();
        Playlist playlist = service.criar("Favoritas");

        assertThrows(IllegalArgumentException.class, () -> service.removerFaixa(99, "t-001"));
        assertThrows(IllegalArgumentException.class, () -> service.removerFaixa(playlist.getId(), "t-001"));
        assertThrows(IllegalArgumentException.class, () -> service.removerFaixa(playlist.getId(), "  "));
    }
}
