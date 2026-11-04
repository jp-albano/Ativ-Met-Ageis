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
     * Resultado de associar uma faixa a uma playlist.
     * A lista só muda em {@link #ADICIONADA}.
     */
    public enum ResultadoAdicaoFaixa {
        /** A faixa passou nas validações e entrou na playlist. */
        ADICIONADA,
        /** Playlist e faixa existem, mas a faixa já estava na lista. */
        JA_PRESENTE,
        /** Não há playlist com o ID informado; nada foi alterado. */
        PLAYLIST_INEXISTENTE,
        /** A faixa não está no catálogo; nada foi alterado. */
        FAIXA_INEXISTENTE
    }

    /**
     * Adiciona uma faixa a uma playlist a partir dos IDs informados no terminal.
     * Confere a playlist e a faixa no catálogo antes de alterar a lista.
     * Grava o ID canônico da faixa ({@code Track.id()}), no mesmo formato do catálogo ({@code t-001}).
     * O grupo não documentou permissão para repetir a mesma faixa na mesma playlist,
     * então uma segunda inclusão é recusada e a ordem atual é mantida.
     *
     * @param playlistId ID numérico da playlist
     * @param faixaId ID da faixa digitado pelo usuário
     * @param catalogo catálogo usado para confirmar que a faixa existe
     * @return o que aconteceu, sem lançar quando playlist ou faixa não existem
     */
    public ResultadoAdicaoFaixa adicionarFaixa(int playlistId, String faixaId, CatalogService catalogo) {
        Objects.requireNonNull(catalogo, "catalogo");

        Playlist playlist = playlists.get(playlistId);
        if (playlist == null) {
            return ResultadoAdicaoFaixa.PLAYLIST_INEXISTENTE;
        }

        String informado = faixaId == null ? "" : faixaId.strip();
        Optional<Track> faixa = informado.isEmpty() ? Optional.empty() : catalogo.findById(informado);
        if (faixa.isEmpty()) {
            return ResultadoAdicaoFaixa.FAIXA_INEXISTENTE;
        }

        // ID do catálogo, não o texto cru, para a playlist e a consulta usarem a mesma chave.
        String idAlinhado = faixa.get().id();
        if (!playlist.adicionarFaixa(idAlinhado)) {
            return ResultadoAdicaoFaixa.JA_PRESENTE;
        }
        return ResultadoAdicaoFaixa.ADICIONADA;
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
