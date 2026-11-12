import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Converte um resultado online em faixa do catálogo, sem baixar áudio. */
class InclusaoPorPesquisaTest {

    @Test
    void criaFaixaComDuracaoEArquivoSimbolico() {
        SoundCloudSearchService.SoundCloudTrack resultado = new SoundCloudSearchService.SoundCloudTrack(
                "urn:soundcloud:tracks:1", "Imagine", "John Lennon", null,
                "https://soundcloud.example/imagine", 183_000);

        Track faixa = InclusaoPorPesquisa.criar("t-009", resultado);

        assertEquals("t-009", faixa.id());
        assertEquals("Imagine", faixa.title());
        assertEquals("John Lennon", faixa.artist());
        assertEquals(183, faixa.durationSeconds());
        assertEquals(new File("audio", "t-009.mp3"), faixa.audioFile());
        assertEquals("urn:soundcloud:tracks:1", faixa.origemId());
    }

    @Test
    void reconheceDuplicataSemDiferenciarMaiusculas() {
        Track existente = new Track("t-001", "Imagine", "John Lennon", 183, null, new File("a.mp3"));

        assertTrue(InclusaoPorPesquisa.duplicata(List.of(existente), " imagine ", "JOHN LENNON").isPresent());
        assertTrue(InclusaoPorPesquisa.duplicata(List.of(existente), "Outra", "John Lennon").isEmpty());
    }

    @Test
    void rejeitaIdVazio() {
        SoundCloudSearchService.SoundCloudTrack resultado = new SoundCloudSearchService.SoundCloudTrack(
                "1", "Titulo", "Artista", null, null, 0);

        assertThrows(IllegalArgumentException.class, () -> InclusaoPorPesquisa.criar("  ", resultado));
        assertEquals(0, InclusaoPorPesquisa.duracaoEmSegundos(-1));
    }
}
