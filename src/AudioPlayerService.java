import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import java.util.function.LongSupplier;

/**
 * Controla a reprodução de uma faixa no terminal.
 *
 * <p>Quando o arquivo existe e o mixer do Java consegue abri-lo, a posição vem do
 * {@link Clip}. O catálogo de demonstração aponta para MP3 que não estão no
 * repositório e o mixer padrão não lê MP3; nesse caso o player gera um tom PCM com
 * a duração da faixa, para tocar, pausar e parar continuarem funcionando. Se não
 * houver dispositivo de áudio, o avanço da posição usa o relógio injetado.</p>
 *
 * <p>O clip permanece aberto durante a pausa. Ele não é fechado dentro do
 * {@link LineListener}: o aviso de parada chega na thread do mixer, e fechar a linha
 * nesse callback trava o player com o estado preso em {@link Estado#TOCANDO}.</p>
 */
public class AudioPlayerService implements AutoCloseable {
    public enum Estado { PARADO, TOCANDO, PAUSADO }

    private static final long NANOS_POR_SEGUNDO = 1_000_000_000L;
    private static final int LARGURA_PADRAO_DA_BARRA = 10;
    /** Evita alocar dezenas de megabytes se a duração cadastrada for absurda. */
    private static final int DURACAO_MAXIMA_DO_TOM_SEGUNDOS = 600;
    private static final float TAXA_DO_TOM = 22_050f;

    private final LongSupplier relogioNanos;
    private Track faixaAtual;
    private Estado estado = Estado.PARADO;
    private Clip clip;
    private AudioInputStream fluxoAudio;
    private long posicaoPausadaMicros;
    private boolean paradaManual;
    private boolean fimPendente;
    private boolean usandoRelogio;
    private boolean avisouSubstituicao;
    private String motivoDaSubstituicao;
    private long posicaoSalvaNanos;
    private long inicioDoTrechoNanos;

    public AudioPlayerService() {
        this(System::nanoTime);
    }

    /**
     * @param relogioNanos relógio usado somente quando não há dispositivo de áudio;
     *                     com um {@link Clip} aberto, a posição vem do mixer
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
        posicaoSalvaNanos = 0;
        usandoRelogio = false;
        avisouSubstituicao = false;
        motivoDaSubstituicao = null;
    }

    public synchronized void limparSelecao() {
        fecharRecursos();
        faixaAtual = null;
        estado = Estado.PARADO;
        posicaoPausadaMicros = 0;
        posicaoSalvaNanos = 0;
        usandoRelogio = false;
        avisouSubstituicao = false;
        motivoDaSubstituicao = null;
    }

    public synchronized Optional<Track> getFaixaAtual() {
        return Optional.ofNullable(faixaAtual);
    }

    /** Inicia a faixa ou retoma uma faixa pausada. */
    public synchronized boolean tocar() {
        aplicarFimPendente();
        exigirFaixa();
        if (estado == Estado.TOCANDO) {
            return false;
        }
        try {
            if (clip == null && !usandoRelogio) {
                if (!abrirAudio()) {
                    usandoRelogio = true;
                    if (!avisouSubstituicao) {
                        avisouSubstituicao = true;
                        System.out.println("Dispositivo de áudio indisponível. "
                                + "O progresso segue a duração da faixa, sem som.");
                    }
                }
            }
            if (usandoRelogio) {
                inicioDoTrechoNanos = relogioNanos.getAsLong();
                estado = Estado.TOCANDO;
                sincronizarRelogio();
                return true;
            }
            clip.setMicrosecondPosition(Math.min(posicaoPausadaMicros, clip.getMicrosecondLength()));
            paradaManual = false;
            clip.start();
            estado = Estado.TOCANDO;
            return true;
        } catch (RuntimeException e) {
            fecharRecursos();
            usandoRelogio = true;
            inicioDoTrechoNanos = relogioNanos.getAsLong();
            estado = Estado.TOCANDO;
            System.out.println("Não foi possível usar o mixer (" + e.getMessage()
                    + "). O progresso segue a duração da faixa.");
            return true;
        }
    }

    /** Pausa sem descartar o clip, permitindo retomar da mesma posição. */
    public synchronized boolean pausar() {
        aplicarFimPendente();
        if (estado != Estado.TOCANDO) {
            return false;
        }
        if (usandoRelogio) {
            posicaoSalvaNanos = posicaoAtualNanos();
            estado = Estado.PAUSADO;
            return true;
        }
        if (clip == null) {
            return false;
        }
        // Marcado antes do stop: o mixer avisa STOP também na pausa, e isso não é fim de faixa.
        paradaManual = true;
        posicaoPausadaMicros = clip.getMicrosecondPosition();
        clip.stop();
        estado = Estado.PAUSADO;
        return true;
    }

    /** Para e libera imediatamente o clip e o fluxo de áudio. */
    public synchronized boolean parar() {
        aplicarFimPendente();
        boolean mudou = estado != Estado.PARADO || clip != null || posicaoPausadaMicros > 0 || posicaoSalvaNanos > 0;
        fecharRecursos();
        estado = Estado.PARADO;
        posicaoPausadaMicros = 0;
        posicaoSalvaNanos = 0;
        usandoRelogio = false;
        return mudou;
    }

    public synchronized Estado getEstado() {
        aplicarFimPendente();
        sincronizarRelogio();
        return estado;
    }

    public synchronized int getPosicaoSegundos() {
        aplicarFimPendente();
        sincronizarRelogio();
        long micros;
        if (usandoRelogio) {
            micros = posicaoAtualNanos() / 1_000L;
        } else if (estado == Estado.PAUSADO) {
            micros = posicaoPausadaMicros;
        } else if (clip != null) {
            micros = clip.getMicrosecondPosition();
        } else {
            micros = posicaoPausadaMicros;
        }
        int segundos = (int) (micros / 1_000_000L);
        int duracao = duracaoDaFaixa();
        if (duracao > 0) {
            return Math.min(segundos, duracao);
        }
        return segundos;
    }

    public synchronized int getDuracaoSegundos() {
        aplicarFimPendente();
        return duracaoDaFaixa();
    }

    public synchronized int getPercentual() {
        int duracao = getDuracaoSegundos();
        if (duracao == 0) {
            return 0;
        }
        return (int) Math.min(100, getPosicaoSegundos() * 100L / duracao);
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
        System.out.println(faixaAtual.title() + " - " + faixaAtual.artist() + " (" + getEstado() + ")");
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

    /**
     * @return {@code true} quando um clip ficou aberto
     */
    private boolean abrirAudio() {
        AudioInputStream aberto = null;
        try {
            aberto = abrirFluxo();
            AudioFormat formato = formatoCompativel(aberto);
            if (!formato.matches(aberto.getFormat())) {
                AudioInputStream convertido = AudioSystem.getAudioInputStream(formato, aberto);
                aberto.close();
                aberto = convertido;
            }
            DataLine.Info info = new DataLine.Info(Clip.class, aberto.getFormat());
            if (!AudioSystem.isLineSupported(info)) {
                fecharFluxo(aberto);
                return false;
            }
            Clip novo = (Clip) AudioSystem.getLine(info);
            novo.addLineListener(ouvinteDeFimDeFaixa());
            novo.open(aberto);
            fluxoAudio = aberto;
            clip = novo;
            if (motivoDaSubstituicao != null) {
                avisarSubstituicao(motivoDaSubstituicao);
                motivoDaSubstituicao = null;
            }
            return true;
        } catch (UnsupportedAudioFileException | LineUnavailableException | IOException | IllegalArgumentException e) {
            fecharFluxo(aberto);
            fecharRecursos();
            return false;
        }
    }

    private AudioInputStream abrirFluxo() throws UnsupportedAudioFileException, IOException {
        File arquivo = faixaAtual.audioFile();
        if (arquivo.exists() && arquivo.isFile()) {
            try {
                motivoDaSubstituicao = null;
                return AudioSystem.getAudioInputStream(arquivo);
            } catch (UnsupportedAudioFileException e) {
                motivoDaSubstituicao = "O formato de " + arquivo.getName()
                        + " não é lido pelo mixer do Java (use WAV, AIFF ou AU).";
            }
        } else {
            motivoDaSubstituicao = "Arquivo de áudio não encontrado: " + arquivo.getPath() + ".";
        }
        return tomDeDemonstracao();
    }

    private void avisarSubstituicao(String motivo) {
        if (avisouSubstituicao) {
            return;
        }
        avisouSubstituicao = true;
        System.out.println(motivo + " Tocando um tom com a duração da faixa.");
    }

    private AudioInputStream tomDeDemonstracao() {
        int segundos = faixaAtual.durationSeconds();
        if (segundos < 1) {
            segundos = 1;
        }
        segundos = Math.min(segundos, DURACAO_MAXIMA_DO_TOM_SEGUNDOS);
        AudioFormat formato = new AudioFormat(TAXA_DO_TOM, 16, 1, true, false);
        int quadros = (int) (segundos * TAXA_DO_TOM);
        byte[] pcm = new byte[quadros * 2];
        double passo = 2 * Math.PI * 440d / TAXA_DO_TOM;
        for (int i = 0; i < quadros; i++) {
            short amostra = (short) (Math.sin(i * passo) * 2800);
            pcm[i * 2] = (byte) (amostra & 0xff);
            pcm[i * 2 + 1] = (byte) ((amostra >> 8) & 0xff);
        }
        return new AudioInputStream(new ByteArrayInputStream(pcm), formato, quadros);
    }

    private static AudioFormat formatoCompativel(AudioInputStream fluxo) {
        AudioFormat origem = fluxo.getFormat();
        if (origem.getEncoding().equals(AudioFormat.Encoding.PCM_SIGNED) && origem.getSampleSizeInBits() == 16) {
            return origem;
        }
        int canais = origem.getChannels() > 0 ? origem.getChannels() : 1;
        float taxa = origem.getSampleRate() > 0 ? origem.getSampleRate() : TAXA_DO_TOM;
        return new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, taxa, 16, canais, canais * 2, taxa, false);
    }

    private LineListener ouvinteDeFimDeFaixa() {
        return evento -> {
            if (evento.getType() != LineEvent.Type.STOP) {
                return;
            }
            synchronized (AudioPlayerService.this) {
                if (paradaManual || estado != Estado.TOCANDO || clip == null) {
                    return;
                }
                // Só registra o fim. Fechar a linha aqui disputa o cadeado do mixer.
                fimPendente = true;
            }
        };
    }

    private void aplicarFimPendente() {
        if (!fimPendente) {
            return;
        }
        fimPendente = false;
        fecharRecursos();
        estado = Estado.PARADO;
        posicaoPausadaMicros = 0;
        posicaoSalvaNanos = 0;
        usandoRelogio = false;
    }

    private void exigirFaixa() {
        if (faixaAtual == null) {
            throw new IllegalStateException("Nenhuma faixa selecionada. Selecione uma música primeiro.");
        }
    }

    private int duracaoDaFaixa() {
        return faixaAtual == null ? 0 : faixaAtual.durationSeconds();
    }

    private long posicaoAtualNanos() {
        if (faixaAtual == null) {
            return 0;
        }
        long posicao = posicaoSalvaNanos;
        if (estado == Estado.TOCANDO) {
            posicao += Math.max(0, relogioNanos.getAsLong() - inicioDoTrechoNanos);
        }
        return Math.min(posicao, duracaoDaFaixa() * NANOS_POR_SEGUNDO);
    }

    private void sincronizarRelogio() {
        if (!usandoRelogio || estado != Estado.TOCANDO) {
            return;
        }
        if (posicaoAtualNanos() >= duracaoDaFaixa() * NANOS_POR_SEGUNDO) {
            estado = Estado.PARADO;
            posicaoSalvaNanos = 0;
            usandoRelogio = false;
        }
    }

    private void fecharRecursos() {
        paradaManual = true;
        fimPendente = false;
        Clip clipParaFechar = clip;
        clip = null;
        if (clipParaFechar != null) {
            clipParaFechar.stop();
            clipParaFechar.close();
        }
        fecharFluxo(fluxoAudio);
        fluxoAudio = null;
    }

    private static void fecharFluxo(AudioInputStream fluxo) {
        if (fluxo == null) {
            return;
        }
        try {
            fluxo.close();
        } catch (IOException ignored) {
            // A linha já foi liberada; não há o que recuperar no encerramento.
        }
    }
}
