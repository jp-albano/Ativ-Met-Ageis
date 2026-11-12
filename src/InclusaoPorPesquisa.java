import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Transforma um resultado de pesquisa online em faixa do catálogo local.
 * Guarda título, artista, duração e um caminho local simbólico. Não baixa o áudio.
 */
public final class InclusaoPorPesquisa {

    private InclusaoPorPesquisa() {
    }

    /**
     * Procura no catálogo uma faixa com o mesmo título e artista, sem diferenciar maiúsculas.
     */
    public static Optional<Track> duplicata(List<Track> catalogo, String titulo, String artista) {
        String tituloNormalizado = normalizar(titulo);
        String artistaNormalizado = normalizar(artista);
        return catalogo.stream()
                .filter(faixa -> normalizar(faixa.title()).equals(tituloNormalizado)
                        && normalizar(faixa.artist()).equals(artistaNormalizado))
                .findFirst();
    }

    /**
     * Monta a faixa local a partir do resultado. O identificador do SoundCloud fica em
     * {@link Track#origemId()} para o player pedir o stream autorizado na hora de tocar.
     * O arquivo local é só um caminho reserva e não recebe uma cópia da música.
     *
     * @param id identificador novo do catálogo, por exemplo {@code t-009}
     */
    public static Track criar(String id, SoundCloudSearchService.SoundCloudTrack resultado) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("O id da faixa é obrigatório.");
        }
        int segundos = duracaoEmSegundos(resultado.durationMillis());
        return new Track(id.strip(), resultado.title().strip(), resultado.artist().strip(), segundos,
                null, new File("audio", id.strip() + ".mp3"), resultado.id());
    }

    static int duracaoEmSegundos(long durationMillis) {
        if (durationMillis <= 0) {
            return 0;
        }
        long segundos = durationMillis / 1000L;
        return (int) Math.min(Integer.MAX_VALUE, segundos);
    }

    private static String normalizar(String texto) {
        return texto == null ? "" : texto.strip().toLowerCase(Locale.ROOT);
    }
}
