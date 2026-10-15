package playlist;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Playlist do sistema (imutável).
 *
 * <p>A playlist guarda apenas os <b>ids</b> das músicas, nunca cópias, para que
 * mudar uma música não deixe playlists desatualizadas. Ela nasce vazia.
 *
 * @param id       identificador único, gerado pelo sistema e imutável
 * @param nome     nome já validado e normalizado (ver {@link PlaylistService#validarNome})
 * @param musicas  ids das músicas (lista imutável; nunca nula)
 * @param criadaEm instante de criação, em UTC
 */
public record Playlist(String id, String nome, List<String> musicas, Instant criadaEm) {

    public Playlist {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nome, "nome");
        Objects.requireNonNull(criadaEm, "criadaEm");
        musicas = List.copyOf(musicas == null ? List.of() : musicas);
    }
}
