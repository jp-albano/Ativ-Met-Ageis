import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica o ciclo de vida solicitado para playlists e protege os estados após
 * tentativas inválidas de inclusão ou remoção.
 */
class PlaylistWorkflowTest {

    @Test
    void cobreCriacaoAdicaoRemocaoEDuplicata() {
        PlaylistService service = new PlaylistService();
        CatalogService catalogo = new CatalogService();

        Playlist playlist = service.criar("  Rock  ");
        assertEquals(1, playlist.getId());
        assertEquals("Rock", playlist.getNome());

        service.adicionarFaixa(playlist.getId(), " T-001 ", catalogo);
        assertEquals(java.util.List.of("t-001"), playlist.getFaixaIds());

        assertThrows(IllegalArgumentException.class,
                () -> service.adicionarFaixa(playlist.getId(), "T-001", catalogo));
        assertEquals(1, playlist.getQuantidadeFaixas(), "duplicata não pode alterar a playlist");

        service.removerFaixa(playlist.getId(), " T-001 ");
        assertTrue(playlist.getFaixaIds().isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> service.removerFaixa(playlist.getId(), "t-001"));

        assertTrue(service.remover(playlist.getId()));
        assertTrue(service.listar().isEmpty());
        assertFalse(service.remover(playlist.getId()), "remover novamente deve indicar que não existia");
    }

    @Test
    void rejeitaIdsDePlaylistEDeFaixaInexistentesSemAlterarEstado() {
        PlaylistService service = new PlaylistService();
        CatalogService catalogo = new CatalogService();
        Playlist playlist = service.criar("Favoritas");

        assertThrows(IllegalArgumentException.class,
                () -> service.adicionarFaixa(999, "t-001", catalogo));
        assertThrows(IllegalArgumentException.class,
                () -> service.adicionarFaixa(playlist.getId(), "t-999", catalogo));
        assertEquals(0, playlist.getQuantidadeFaixas());

        assertThrows(IllegalArgumentException.class,
                () -> service.removerFaixa(999, "t-001"));
        assertThrows(IllegalArgumentException.class,
                () -> service.removerFaixa(playlist.getId(), "t-999"));
        assertEquals(0, playlist.getQuantidadeFaixas());
    }

    @Test
    void modeloTambemNaoAceitaDuplicataComCaixaDiferente() {
        Playlist playlist = new Playlist(1, "Teste");

        assertTrue(playlist.adicionarFaixa("t-001"));
        assertFalse(playlist.adicionarFaixa("T-001"));
        assertEquals(1, playlist.getQuantidadeFaixas());
    }
}
