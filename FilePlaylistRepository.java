package playlist;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Armazena as playlists em um arquivo JSON no servidor (gravação durável).
 *
 * <p>Garantias:
 * <ul>
 *   <li>A gravação é atômica: o conteúdo novo vai para um arquivo temporário na mesma
 *       pasta e só então substitui o arquivo de dados. Uma queda no meio da gravação
 *       não deixa o arquivo pela metade.</li>
 *   <li>Arquivo ilegível ou com estrutura inválida gera {@link StorageException} e
 *       <b>nunca é sobrescrito</b> em silêncio.</li>
 *   <li>Os métodos são {@code synchronized}: seguro para várias threads de <b>um</b>
 *       processo. Não use o mesmo arquivo em dois processos ao mesmo tempo.</li>
 * </ul>
 */
public final class FilePlaylistRepository implements PlaylistRepository {

    private final Path arquivo;

    /** @param arquivo caminho do arquivo JSON; criado (com as pastas) na primeira gravação */
    public FilePlaylistRepository(Path arquivo) {
        this.arquivo = arquivo.toAbsolutePath();
    }

    @Override
    public synchronized List<Playlist> listar() {
        return ler();
    }

    @Override
    public synchronized Optional<Playlist> buscarPorId(String id) {
        return ler().stream().filter(p -> p.id().equals(id)).findFirst();
    }

    @Override
    public synchronized Playlist salvar(Playlist playlist) {
        List<Playlist> todas = ler();
        if (todas.stream().anyMatch(p -> p.id().equals(playlist.id()))) {
            throw new StorageException("Já existe uma playlist com esse id.");
        }
        todas.add(playlist);
        gravar(todas);
        return playlist;
    }

    private List<Playlist> ler() {
        if (!Files.exists(arquivo)) {
            return new ArrayList<>();
        }
        String texto;
        try {
            texto = Files.readString(arquivo, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new StorageException("Não foi possível ler o arquivo de dados.", e);
        }
        try {
            if (!(Json.parse(texto) instanceof List<?> itens)) {
                throw new IllegalArgumentException("a raiz deve ser uma lista");
            }
            List<Playlist> playlists = new ArrayList<>();
            for (Object item : itens) {
                playlists.add(PlaylistJson.dePlaylist(item));
            }
            return playlists;
        } catch (RuntimeException e) {
            throw new StorageException("Os dados salvos estão corrompidos.", e);
        }
    }

    private void gravar(List<Playlist> playlists) {
        List<Object> estrutura = new ArrayList<>();
        for (Playlist p : playlists) {
            estrutura.add(PlaylistJson.paraMapa(p));
        }
        String json = Json.writePretty(estrutura);

        Path temporario = null;
        try {
            Path pasta = arquivo.getParent();
            Files.createDirectories(pasta);
            temporario = Files.createTempFile(pasta, "playlists-", ".tmp");
            Files.writeString(temporario, json, StandardCharsets.UTF_8);
            try {
                Files.move(temporario, arquivo,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporario, arquivo, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            if (temporario != null) {
                try {
                    Files.deleteIfExists(temporario);
                } catch (IOException ignorado) {
                    // melhor esforço: o erro principal é o da gravação
                }
            }
            throw new StorageException("Não foi possível salvar a playlist.", e);
        }
    }
}
