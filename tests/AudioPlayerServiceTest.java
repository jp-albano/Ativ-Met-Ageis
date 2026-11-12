import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reprodução, pausa e fim de faixa do player de terminal. */
class AudioPlayerServiceTest {

    @TempDir
    Path pasta;

    @Test
    void tocaFaixaDoCatalogoMesmoSemArquivoDeAudio() {
        AudioPlayerService player = new AudioPlayerService();
        Track faixa = new Track("t-001", "Bohemian Rhapsody", "Queen", 2, null,
                new File("audio/bohemian_rhapsody.mp3"));
        player.selecionar(faixa);

        assertTrue(player.tocar());
        assertEquals(AudioPlayerService.Estado.TOCANDO, player.getEstado());

        player.close();
    }

    @Test
    void pausaNaoEncerraAFaixaERetomarContinua() throws Exception {
        AudioPlayerService player = new AudioPlayerService();
        player.selecionar(faixaCurta("pausa.wav"));

        assertTrue(player.tocar());
        Thread.sleep(100);
        assertTrue(player.pausar());
        assertEquals(AudioPlayerService.Estado.PAUSADO, player.getEstado());
        Thread.sleep(300);
        assertEquals(AudioPlayerService.Estado.PAUSADO, player.getEstado());
        assertFalse(player.pausar());

        assertTrue(player.tocar());
        assertEquals(AudioPlayerService.Estado.TOCANDO, player.getEstado());
        assertTrue(player.parar());
        assertEquals(AudioPlayerService.Estado.PARADO, player.getEstado());
        assertEquals(0, player.getPosicaoSegundos());

        player.close();
    }

    @Test
    void aoTerminarVoltaParaParadoEPodeTocarDeNovo() throws Exception {
        AudioPlayerService player = new AudioPlayerService();
        player.selecionar(faixaCurta("fim.wav"));
        assertTrue(player.tocar());

        long prazo = System.nanoTime() + 3_000_000_000L;
        while (player.getEstado() != AudioPlayerService.Estado.PARADO && System.nanoTime() < prazo) {
            Thread.sleep(40);
        }

        assertEquals(AudioPlayerService.Estado.PARADO, player.getEstado());
        assertTrue(player.tocar());
        assertEquals(AudioPlayerService.Estado.TOCANDO, player.getEstado());
        player.close();
    }

    @Test
    void rejeitaTocarSemFaixaSelecionada() {
        AudioPlayerService player = new AudioPlayerService();
        assertThrows(IllegalStateException.class, player::tocar);
    }

    private Track faixaCurta(String nome) throws IOException {
        File wav = pasta.resolve(nome).toFile();
        AudioFormat formato = new AudioFormat(8_000f, 16, 1, true, false);
        int quadros = 4_000;
        byte[] pcm = new byte[quadros * 2];
        try (AudioInputStream fluxo = new AudioInputStream(new ByteArrayInputStream(pcm), formato, quadros)) {
            AudioSystem.write(fluxo, AudioFileFormat.Type.WAVE, wav);
        }
        return new Track("wav", "Tom", "Teste", 1, null, wav);
    }
}
