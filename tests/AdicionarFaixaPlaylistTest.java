import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Regras de incluir faixa na playlist pelos IDs informados. */
class AdicionarFaixaPlaylistTest {

    @Test
    void adicionaFaixaDoCatalogoPelosIds() {
        CatalogService catalogo = new CatalogService();
        PlaylistService service = new PlaylistService();
        Playlist playlist = service.criar("Favoritas");

        PlaylistService.ResultadoAdicaoFaixa resultado =
                service.adicionarFaixa(playlist.getId(), "t-001", catalogo);

        assertEquals(PlaylistService.ResultadoAdicaoFaixa.ADICIONADA, resultado);
        assertEquals(List.of("t-001"), playlist.getFaixaIds());
    }

    @Test
    void naoAlteraAListaQuandoPlaylistNaoExiste() {
        CatalogService catalogo = new CatalogService();
        PlaylistService service = new PlaylistService();
        Playlist playlist = service.criar("Favoritas");

        PlaylistService.ResultadoAdicaoFaixa resultado = service.adicionarFaixa(99, "t-001", catalogo);

        assertEquals(PlaylistService.ResultadoAdicaoFaixa.PLAYLIST_INEXISTENTE, resultado);
        assertEquals(0, playlist.getQuantidadeFaixas());
    }

    @Test
    void naoAlteraAListaQuandoFaixaNaoEstaNoCatalogo() {
        CatalogService catalogo = new CatalogService();
        PlaylistService service = new PlaylistService();
        Playlist playlist = service.criar("Favoritas");

        PlaylistService.ResultadoAdicaoFaixa resultado =
                service.adicionarFaixa(playlist.getId(), "t-999", catalogo);

        assertEquals(PlaylistService.ResultadoAdicaoFaixa.FAIXA_INEXISTENTE, resultado);
        assertEquals(0, playlist.getQuantidadeFaixas());
    }

    @Test
    void naoInsereAMesmaFaixaDuasVezes() {
        CatalogService catalogo = new CatalogService();
        PlaylistService service = new PlaylistService();
        Playlist playlist = service.criar("Favoritas");

        assertEquals(PlaylistService.ResultadoAdicaoFaixa.ADICIONADA,
                service.adicionarFaixa(playlist.getId(), "t-002", catalogo));
        assertEquals(PlaylistService.ResultadoAdicaoFaixa.JA_PRESENTE,
                service.adicionarFaixa(playlist.getId(), "t-002", catalogo));
        assertEquals(List.of("t-002"), playlist.getFaixaIds());
    }

    @Test
    void gravaOIdCanonicoDaFaixa() {
        CatalogService catalogo = new CatalogService();
        PlaylistService service = new PlaylistService();
        Playlist playlist = service.criar("Favoritas");

        service.adicionarFaixa(playlist.getId(), "  t-003  ", catalogo);

        assertEquals(List.of("t-003"), playlist.getFaixaIds());
        assertEquals(PlaylistService.ResultadoAdicaoFaixa.JA_PRESENTE,
                service.adicionarFaixa(playlist.getId(), "t-003", catalogo));
    }
}
