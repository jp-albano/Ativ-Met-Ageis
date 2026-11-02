import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Camada de serviço responsável por criar, listar e localizar playlists.
 * O LinkedHashMap mantém as playlists na ordem em que foram criadas.
 */
public class PlaylistService {

    // A chave e o ID da Playlist usam int, evitando incompatibilidade de tipos.
    private final Map<Integer, Playlist> playlists = new LinkedHashMap<>();
    private int proximoId = 1;

    /**
     * Cria e registra uma playlist com nome válido.
     *
     * @param nome nome informado pelo usuário
     * @return playlist criada e já armazenada no serviço
     * @throws IllegalArgumentException se o nome for nulo ou vazio
     */
    public Playlist criar(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("O nome da playlist não pode ser vazio.");
        }

        // O ID é incrementado somente durante a criação de uma nova playlist.
        Playlist nova = new Playlist(proximoId++, nome.strip());
        playlists.put(nova.getId(), nova);
        return nova;
    }

    /** Devolve uma cópia da coleção para impedir alterações externas no mapa interno. */
    public List<Playlist> listar() {
        return new ArrayList<>(playlists.values());
    }

    /**
     * Procura uma playlist pelo ID.
     *
     * @return Optional preenchido quando existe; vazio caso contrário
     */
    public Optional<Playlist> buscarPorId(int id) {
        return Optional.ofNullable(playlists.get(id));
    }

    /**
     * Remove a playlist com o ID informado.
     *
     * @return true quando uma playlist foi removida
     */
    public boolean remover(int id) {
        return playlists.remove(id) != null;
    }

    /** Remove a faixa de todas as playlists; usado quando ela sai do catálogo. */
    public void removerFaixaDeTodas(String faixaId) {
        playlists.values().forEach(p -> p.removerFaixa(faixaId));
    }

    /** Monta a tabela de playlists exibida pela área de playlists do terminal. */
    public String formatarListagem() {
        List<Playlist> todas = listar();
        if (todas.isEmpty()) {
            return "Nenhuma playlist cadastrada." + System.lineSeparator();
        }

        StringBuilder saida = new StringBuilder();
        saida.append("ID | Nome | Quantidade de faixas").append(System.lineSeparator());
        for (Playlist playlist : todas) {
            saida.append(playlist.getId())
                    .append(" | ")
                    .append(playlist.getNome())
                    .append(" | ")
                    .append(playlist.getQuantidadeFaixas())
                    .append(System.lineSeparator());
        }
        return saida.toString();
    }

    /**
     * Monta a descrição de uma playlist com os dados das faixas encontradas no catálogo.
     * IDs que não existem mais no catálogo são exibidos como não encontrados.
     */
    public String formatarConsulta(int id, CatalogService catalogo) {
        Objects.requireNonNull(catalogo, "catalogo");

        Optional<Playlist> encontrada = buscarPorId(id);
        if (encontrada.isEmpty()) {
            return "Playlist não encontrada: " + id + System.lineSeparator();
        }

        Playlist playlist = encontrada.get();
        StringBuilder saida = new StringBuilder();
        saida.append("Playlist [").append(playlist.getId()).append("] ")
                .append(playlist.getNome()).append(System.lineSeparator());
        if (playlist.getQuantidadeFaixas() == 0) {
            saida.append("Nenhuma faixa associada.").append(System.lineSeparator());
            return saida.toString();
        }

        saida.append("Faixas associadas:").append(System.lineSeparator());
        for (String faixaId : playlist.getFaixaIds()) {
            catalogo.findById(faixaId).ifPresentOrElse(
                    faixa -> saida.append("  Título: ").append(faixa.title())
                            .append(" | Artista: ").append(faixa.artist())
                            .append(" | Duração: ").append(faixa.formattedDuration())
                            .append(System.lineSeparator()),
                    () -> saida.append("  ").append(faixaId)
                            .append(" (não encontrada no catálogo)")
                            .append(System.lineSeparator()));
        }
        return saida.toString();
    }
}
