import java.io.File;
import java.util.Objects;

/**
 * Modelo imutável de uma faixa musical.
 * Record gera automaticamente acessores, construtor, equals/hashCode e outros métodos;
 * equals/hashCode são redefinidos abaixo para considerar a identidade pelo ID.
 *
 * @param id identificador único da faixa
 * @param title título da música
 * @param artist artista ou intérprete
 * @param durationSeconds duração total em segundos
 * @param coverFile caminho do arquivo de capa (pode ser null quando não houver capa)
 * @param audioFile caminho do arquivo de áudio local
 * @param origemId identificador da faixa na plataforma de origem, quando veio de uma pesquisa online
 */
public record Track(
        String id,
        String title,
        String artist,
        int durationSeconds,
        File coverFile,
        File audioFile,
        String origemId
) {

    /** Faixa só local, sem identificador em uma plataforma externa. */
    public Track(String id, String title, String artist, int durationSeconds, File coverFile, File audioFile) {
        this(id, title, artist, durationSeconds, coverFile, audioFile, null);
    }

    /** Valida os campos obrigatórios no momento da criação do objeto. */
    public Track {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(artist, "artist");
        Objects.requireNonNull(audioFile, "audioFile");
        if (durationSeconds < 0) {
            throw new IllegalArgumentException("durationSeconds must be >= 0");
        }
        if (origemId != null && origemId.isBlank()) {
            origemId = null;
        }
    }

    /** Converte segundos para o formato de exibição minutos:segundos (por exemplo, 03:14). */
    public String formattedDuration() {
        return "%02d:%02d".formatted(durationSeconds / 60, durationSeconds % 60);
    }

    /** Duas faixas são consideradas iguais quando têm o mesmo ID. */
    @Override
    public boolean equals(Object o) {
        return o instanceof Track t && id.equals(t.id);
    }

    /** Mantém hashCode coerente com equals, usando o ID como identidade. */
    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /** Texto compacto para logs e depuração, sem imprimir caminhos completos de arquivos. */
    @Override
    public String toString() {
        return "Track{id=%s, title=%s, artist=%s, duration=%s}"
                .formatted(id, title, artist, formattedDuration());
    }
}
