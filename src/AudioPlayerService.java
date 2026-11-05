import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

public class AudioPlayerService {
    public enum Estado { PARADO, TOCANDO, PAUSADO }

    private static final long NANOS_POR_SEGUNDO = 1_000_000_000L;
    private static final int LARGURA_PADRAO_DA_BARRA = 10;

    private final LongSupplier relogioNanos;

    private Track faixaAtual;
    private Estado estado = Estado.PARADO;

    private long posicaoSalvaNanos;
    private long inicioDoTrechoNanos;

    public AudioPlayerService() {
        this(System::nanoTime);
    }

    public AudioPlayerService(LongSupplier relogioNanos) {
        this.relogioNanos = Objects.requireNonNull(relogioNanos, "relogioNanos");
    }

    public void selecionar(Track faixa) {
        Objects.requireNonNull(faixa, "faixa");
        parar();
        faixaAtual = faixa;
    }

    public void limparSelecao() {
        parar();
        faixaAtual = null;
    }

    public Optional<Track> getFaixaAtual() {
        return Optional.ofNullable(faixaAtual);
    }

    public boolean tocar() {
        sincronizar();
        exigirFaixa();
        if (estado == Estado.TOCANDO) {
            return false;
        }
        inicioDoTrechoNanos = relogioNanos.getAsLong();
        estado = Estado.TOCANDO;
        return true;
    }

    public boolean pausar() {
        sincronizar();
        if (estado != Estado.TOCANDO) {
            return false;
        }
        posicaoSalvaNanos = posicaoAtualNanos();
        estado = Estado.PAUSADO;
        return true;
    }

    public boolean parar() {
        sincronizar();
        boolean mudou = estado != Estado.PARADO || posicaoSalvaNanos > 0;
        estado = Estado.PARADO;
        posicaoSalvaNanos = 0;
        return mudou;
    }

    public Estado getEstado() {
        sincronizar();
        return estado;
    }

    public int getPosicaoSegundos() {
        sincronizar();
        return (int) (posicaoAtualNanos() / NANOS_POR_SEGUNDO);
    }

    public int getDuracaoSegundos() {
        return faixaAtual == null ? 0 : faixaAtual.durationSeconds();
    }

    public int getPercentual() {
        int duracao = getDuracaoSegundos();
        if (duracao == 0) {
            return 0;
        }
        return (int) Math.min(100, getPosicaoSegundos() * 100L / duracao);
    }

    public String barraDeProgresso() {
        return barraDeProgresso(LARGURA_PADRAO_DA_BARRA);
    }

    public String barraDeProgresso(int largura) {
        if (largura < 1) {
            throw new IllegalArgumentException("A largura da barra deve ser >= 1.");
        }
        int percentual = getPercentual();
        int preenchidos = percentual * largura / 100;
        return "[" + "#".repeat(preenchidos) + "-".repeat(largura - preenchidos) + "] " + percentual + "%";
    }

    public void exibirProgresso() {
        if (faixaAtual == null) {
            System.out.println("Nenhuma faixa selecionada.");
            return;
        }
        System.out.println(faixaAtual.title() + " - " + faixaAtual.artist() + " (" + getEstado() + ")");
        System.out.println(barraDeProgresso() + "  "
                + formatarTempo(getPosicaoSegundos()) + " / " + formatarTempo(getDuracaoSegundos()));
    }

    public static String formatarTempo(int segundos) {
        return "%02d:%02d".formatted(segundos / 60, segundos % 60);
    }

    private void exigirFaixa() {
        if (faixaAtual == null) {
            throw new IllegalStateException("Nenhuma faixa selecionada. Selecione uma música primeiro.");
        }
    }

    private long posicaoAtualNanos() {
        if (faixaAtual == null) {
            return 0;
        }
        long posicao = posicaoSalvaNanos;
        if (estado == Estado.TOCANDO) {
            posicao += relogioNanos.getAsLong() - inicioDoTrechoNanos;
        }
        return Math.min(posicao, getDuracaoSegundos() * NANOS_POR_SEGUNDO);
    }

    private void sincronizar() {
        if (estado == Estado.TOCANDO
                && posicaoAtualNanos() >= getDuracaoSegundos() * NANOS_POR_SEGUNDO) {
            estado = Estado.PARADO;
            posicaoSalvaNanos = 0;
        }
    }
}