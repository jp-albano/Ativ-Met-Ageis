import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Camada de serviço responsável por criar, listar, localizar e incluir faixas em playlists.
 * IDs de playlist são numéricos; IDs de faixa seguem o catálogo ({@code t-001}, {@code t-002}, ...).
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

    /**
     * Inclui uma faixa a partir dos IDs digitados no terminal.
     * Valida playlist e catálogo antes de alterar a lista.
     * Persiste o ID oficial da faixa ({@link Track#id()}), não o texto cru.
     * O grupo não permite a mesma faixa duas vezes na mesma playlist.
     *
     * @throws IllegalArgumentException se a playlist não existir, a faixa não estiver
     *                                 no catálogo, o ID estiver em branco ou a faixa já estiver na lista
     */
    public Playlist adicionarFaixa(int playlistId, String faixaId, CatalogService catalogo) {
        Objects.requireNonNull(catalogo, "catalogo");

        if (faixaId == null || faixaId.isBlank()) {
            throw new IllegalArgumentException("Informe o ID da faixa.");
        }

        Playlist playlist = buscarPorId(playlistId).orElseThrow(
                () -> new IllegalArgumentException("Playlist não encontrada: " + playlistId));

        Track faixa = alinharFaixaDoCatalogo(faixaId.strip(), catalogo).orElseThrow(
                () -> new IllegalArgumentException("Faixa não encontrada no catálogo: " + faixaId.strip()));

        if (!playlist.adicionarFaixa(faixa.id())) {
            throw new IllegalArgumentException("Essa faixa já está na playlist.");
        }
        return playlist;
    }

    /**
     * Tira uma faixa da playlist pelo ID informado no terminal.
     * Aceita espaços nas pontas e diferença de maiúsculas. A faixa não precisa
     * continuar no catálogo para sair da lista.
     *
     * @throws IllegalArgumentException se a playlist não existir, o ID estiver em branco
     *                                 ou a faixa não estiver na playlist
     */
    public Playlist removerFaixa(int playlistId, String faixaId) {
        if (faixaId == null || faixaId.isBlank()) {
            throw new IllegalArgumentException("Informe o ID da faixa.");
        }

        Playlist playlist = buscarPorId(playlistId).orElseThrow(
                () -> new IllegalArgumentException("Playlist não encontrada: " + playlistId));

        String informado = faixaId.strip();
        String idNaLista = playlist.getFaixaIds().stream()
                .filter(id -> id.equalsIgnoreCase(informado))
                .findFirst()
                .orElse(informado);
        if (!playlist.removerFaixa(idNaLista)) {
            throw new IllegalArgumentException("Essa faixa não está na playlist.");
        }
        return playlist;
    }

    /**
     * Resolve o ID digitado para a faixa real do catálogo.
     * Aceita espaços nas pontas e diferença de maiúsculas, mas grava o ID original (t-001).
     */
    private Optional<Track> alinharFaixaDoCatalogo(String faixaId, CatalogService catalogo) {
        Optional<Track> exata = catalogo.findById(faixaId);
        if (exata.isPresent()) {
            return exata;
        }
        return catalogo.getAllTracks().stream()
                .filter(track -> track.id().equalsIgnoreCase(faixaId))
                .findFirst();
    }
}
