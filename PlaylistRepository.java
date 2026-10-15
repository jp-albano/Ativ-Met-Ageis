package playlist;

import java.util.List;
import java.util.Optional;

/**
 * Contrato de armazenamento das playlists, independente da tecnologia.
 * Todas as operações podem lançar {@link StorageException}.
 */
public interface PlaylistRepository {

    /** @return todas as playlists, na ordem de criação (lista nova, modificável) */
    List<Playlist> listar();

    /** @return a playlist com o id informado, se existir */
    Optional<Playlist> buscarPorId(String id);

    /**
     * Grava uma playlist nova.
     *
     * @return a própria playlist gravada
     * @throws StorageException se já existir uma playlist com o mesmo id ou se a gravação falhar
     */
    Playlist salvar(Playlist playlist);
}
