import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.DecoderException;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Converte MP3 em PCM 16 bits para o mixer do Java tocar.
 * O resultado fica só em memória, pelo tempo da reprodução.
 */
public final class DecodificadorMp3 {

    /** Cerca de oito minutos de MP3 a 128 kbps. Acima disso a leitura para. */
    static final int LIMITE_DE_BYTES = 8 * 1024 * 1024;

    private DecodificadorMp3() {
    }

    /**
     * @param mp3 fluxo MP3; este método não o fecha
     * @return fluxo PCM little-endian, com a taxa e os canais do próprio MP3
     * @throws IOException quando o fluxo não contém um quadro MP3 utilizável
     */
    public static AudioInputStream paraPcm(InputStream mp3) throws IOException {
        Bitstream bitstream = new Bitstream(new LeituraLimitada(mp3, LIMITE_DE_BYTES));
        Decoder decoder = new Decoder();
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        int taxa = 44_100;
        int canais = 2;
        boolean primeiro = true;
        try {
            Header quadro;
            while ((quadro = bitstream.readFrame()) != null) {
                if (!(decoder.decodeFrame(quadro, bitstream) instanceof SampleBuffer amostras)) {
                    bitstream.closeFrame();
                    continue;
                }
                if (primeiro) {
                    taxa = amostras.getSampleFrequency();
                    canais = Math.max(1, amostras.getChannelCount());
                    primeiro = false;
                }
                short[] buffer = amostras.getBuffer();
                int quantidade = amostras.getBufferLength();
                for (int i = 0; i < quantidade; i++) {
                    short valor = buffer[i];
                    pcm.write(valor & 0xff);
                    pcm.write((valor >> 8) & 0xff);
                }
                bitstream.closeFrame();
            }
        } catch (BitstreamException | DecoderException e) {
            if (pcm.size() == 0) {
                throw new IOException("Não foi possível decodificar o MP3.", e);
            }
        } finally {
            try {
                bitstream.close();
            } catch (BitstreamException ignored) {
                // O fluxo de entrada continua sob o controle de quem chamou.
            }
        }
        if (pcm.size() < canais * 2) {
            throw new IOException("O MP3 não trouxe amostras de áudio.");
        }
        byte[] dados = pcm.toByteArray();
        AudioFormat formato = new AudioFormat(taxa, 16, canais, true, false);
        return new AudioInputStream(new ByteArrayInputStream(dados), formato, dados.length / (canais * 2L));
    }

    /** Interrompe a leitura ao atingir o limite, sem lançar, para o decodificador encerrar o quadro. */
    private static final class LeituraLimitada extends InputStream {
        private final InputStream origem;
        private int restante;

        private LeituraLimitada(InputStream origem, int limite) {
            this.origem = origem;
            this.restante = limite;
        }

        @Override
        public int read() throws IOException {
            if (restante <= 0) {
                return -1;
            }
            int valor = origem.read();
            if (valor >= 0) {
                restante--;
            }
            return valor;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (restante <= 0) {
                return -1;
            }
            int lido = origem.read(buffer, offset, Math.min(length, restante));
            if (lido > 0) {
                restante -= lido;
            }
            return lido;
        }
    }
}
