import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import java.util.function.LongSupplier;

/**
 * Controla a reprodução de uma faixa local no terminal usando o mixer de áudio do Java.
 *
 * <p>O player mantém o {@link Clip} aberto enquanto a faixa estiver pausada, para que
 * a retomada preserve a posição. Ao parar, trocar de faixa, terminar a reprodução ou
 * encerrar o player, o clip e o {@link AudioInputStream} são fechados.</p>
 */
public class AudioPlayerService implements AutoCloseable {
    public enum Estado { PARADO, TOCANDO, PAUSADO }

    private static final int LARGURA_PADRAO_DA_BARRA = 10;

    private final LongSupplier relogioNanos;
    private Track faixaAtual;
    private Estado estado = Estado.PARADO;
    private Clip clip;
    private AudioInputStream fluxoAudio;
    private long posicaoPausadaMicros;

    public AudioPlayerService() {
        this(System::nanoTime);
    }

    /**
     * O relógio é mantido como dependência para compatibilidade com clientes e testes
     * antigos; a posição real da reprodução vem do Clip quando há áudio aberto.
     */
    public AudioPlayerService(LongSupplier relogioNanos) {
        this.relogioNanos = Objects.requireNonNull(relogioNanos, "relogioNanos");
    }

    public synchronized void selecionar(Track faixa) {
        Objects.requireNonNull(faixa, "faixa");
        fecharRecursos();
        faixaAtual = faixa;
        estado = Estado.PARADO;
        posicaoPausadaMicros = 0;
    }

    public synchronized void limparSelecao() {
        fecharRecursos();
        faixaAtual = null;
        estado = Estado.PARADO;
        posicaoPausadaMicros = 0;
    }

    public synchronized Optional<Track> getFaixaAtual() {
        return Optional.ofNullable(faixaAtual);
    }

    /** Inicia a faixa ou retoma uma faixa pausada. */
    public synchronized boolean tocar() {
        exigirFaixa();
        if (estado == Estado.TOCANDO) {
            return false;
        }
        try {
            if (clip == null) {
                abrirClip();
                clip.setMicrosecondPosition(posicaoPausadaMicros);
            }
            clip.start();
            estado = Estado.TOCANDO;
            return true;
        } catch (IllegalArgumentException e) {
            fecharRecursos();
            throw new IllegalStateException("Não foi possível iniciar o áudio: formato ou posição inválida.", e);
        }
    }

    /** Pausa sem descartar o clip, permitindo retomar da mesma posição. */
    public synchronized boolean pausar() {
        if (estado != Estado.TOCANDO || clip == null) {
            return false;
        }
        posicaoPausadaMicros = clip.getMicrosecondPosition();
        clip.stop();
        estado = Estado.PAUSADO;
        return true;
    }

    /** Para e libera imediatamente o clip e o fluxo de áudio. */
    public synchronized boolean parar() {
        boolean mudou = estado != Estado.PARADO || clip != null || posicaoPausadaMicros > 0;
        fecharRecursos();
        estado = Estado.PARADO;
        posicaoPausadaMicros = 0;
        return mudou;
    }

    public synchronized Estado getEstado() {
        return estado;
    }

    public synchronized int getPosicaoSegundos() {
        if (clip != null) {
            return (int) (clip.getMicrosecondPosition() / 1_000_000L);
        }
        return (int) (posicaoPausadaMicros / 1_000_000L);
    }

    public synchronized int getDuracaoSegundos() {
        return faixaAtual == null ? 0 : faixaAtual.durationSeconds();
    }

    public synchronized int getPercentual() {
        int duracao = getDuracaoSegundos();
        if (duracao == 0) {
            return 0;
        }
        return Math.min(100, getPosicaoSegundos() * 100 / duracao);
    }

    public String barraDeProgresso() {
        return barraDeProgresso(LARGURA_PADRAO_DA_BARRA);
    }

    public synchronized String barraDeProgresso(int largura) {
        if (largura < 1) {
            throw new IllegalArgumentException("A largura da barra deve ser >= 1.");
        }
        int preenchidos = getPercentual() * largura / 100;
        return "[" + "#".repeat(preenchidos) + "-".repeat(largura - preenchidos) + "] "
                + getPercentual() + "%";
    }

    public synchronized void exibirProgresso() {
        if (faixaAtual == null) {
            System.out.println("Nenhuma faixa selecionada.");
            return;
        }
        System.out.println(faixaAtual.title() + " - " + faixaAtual.artist() + " (" + estado + ")");
        System.out.println(barraDeProgresso() + "  " + formatarTempo(getPosicaoSegundos())
                + " / " + formatarTempo(getDuracaoSegundos()));
    }

    /** Abre o submenu do player e retorna ao menu principal quando a opção 0 é escolhida. */
    public void abrirMenu(Scanner entrada) {
        Objects.requireNonNull(entrada, "entrada");
        boolean aberto = true;
        while (aberto && entrada.hasNextLine()) {
            System.out.println("""

                    ----- Player -----
                    1. Tocar
                    2. Pausar
                    3. Retomar
                    4. Parar
                    5. Exibir progresso
                    0. Voltar ao menu principal
                    """);
            System.out.print("Escolha uma opção do player: ");
            String opcao = entrada.nextLine().strip();
            try {
                switch (opcao) {
                    case "1" -> informarResultado("Tocar", tocar());
                    case "2" -> informarResultado("Pausar", pausar());
                    case "3" -> informarResultado("Retomar", tocar());
                    case "4" -> informarResultado("Parar", parar());
                    case "5" -> exibirProgresso();
                    case "0" -> aberto = false;
                    default -> System.out.println("Opção inválida. Escolha de 0 a 5.");
                }
            } catch (IllegalStateException e) {
                System.out.println("Não foi possível executar o comando do player: " + e.getMessage());
            }
        }
    }

    private void informarResultado(String operacao, boolean alterouEstado) {
        if (!alterouEstado) {
            System.out.println("Não foi possível executar \"" + operacao
                    + "\" no estado atual do player.");
        } else {
            System.out.println(operacao + " executado com sucesso.");
        }
    }

    /** Libera recursos de áudio; pode ser chamado ao sair do programa. */
    @Override
    public synchronized void close() {
        parar();
    }

    public static String formatarTempo(int segundos) {
        return "%02d:%02d".formatted(segundos / 60, segundos % 60);
    }

    private void abrirClip() {
        File arquivo = faixaAtual.audioFile();
        if (!arquivo.exists() || !arquivo.isFile()) {
            throw new IllegalStateException("Arquivo de áudio não encontrado: " + arquivo.getPath());
        }
        try {
            fluxoAudio = AudioSystem.getAudioInputStream(arquivo);
            AudioFormat formato = fluxoAudio.getFormat();
            DataLine.Info info = new DataLine.Info(Clip.class, formato);
            if (!AudioSystem.isLineSupported(info)) {
                throw new IllegalStateException("O dispositivo de áudio não suporta o formato da faixa: "
                        + arquivo.getName());
            }
            clip = (Clip) AudioSystem.getLine(info);
            LineListener listener = evento -> {
                if (evento.getType() == LineEvent.Type.STOP && clip != null
                        && clip.getMicrosecondPosition() >= clip.getMicrosecondLength()) {
                    synchronized (AudioPlayerService.this) {
                        fecharRecursos();
                        estado = Estado.PARADO;
                        posicaoPausadaMicros = 0;
                    }
                }
            };
            clip.addLineListener(listener);
            clip.open(fluxoAudio);
        } catch (UnsupportedAudioFileException e) {
            fecharRecursos();
            throw new IllegalStateException("Formato de áudio incompatível: " + arquivo.getName()
                    + ". Use WAV, AIFF ou AU.", e);
        } catch (LineUnavailableException e) {
            fecharRecursos();
            throw new IllegalStateException("Dispositivo de áudio indisponível ou ocupado.", e);
        } catch (IOException e) {
            fecharRecursos();
            throw new IllegalStateException("Falha ao ler o arquivo de áudio: " + arquivo.getName() + ".", e);
        }
    }

    private void exigirFaixa() {
        if (faixaAtual == null) {
            throw new IllegalStateException("Nenhuma faixa selecionada. Selecione uma música primeiro.");
        }
    }

    private void fecharRecursos() {
        Clip clipParaFechar = clip;
        clip = null;
        if (clipParaFechar != null) {
            clipParaFechar.stop();
            clipParaFechar.close();
        }
        AudioInputStream fluxoParaFechar = fluxoAudio;
        fluxoAudio = null;
        if (fluxoParaFechar != null) {
            try {
                fluxoParaFechar.close();
            } catch (IOException ignored) {
                // A liberação do clip já ocorreu; não há ação útil para o encerramento.
            }
        }
    }
}
