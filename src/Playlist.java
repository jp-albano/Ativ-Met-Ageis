import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Representa uma playlist e mantém os IDs das faixas na ordem em que foram adicionadas.
 * A playlist guarda IDs, não objetos Track, para manter a relação simples e independente
 * do catálogo de músicas.
 */
public class Playlist {

    // O ID numérico é gerado pelo PlaylistService e identifica a playlist no serviço.
    private final int id;
    private final String nome;

    // ArrayList preserva a ordem de inclusão das faixas.
    private final List<String> faixaIds = new ArrayList<>();

    /**
     * Cria uma playlist.
     *
     * @param id identificador único atribuído pelo serviço
     * @param nome nome já validado pelo serviço
     */
    public Playlist(int id, String nome) {
        this.id = id;
        this.nome = nome;
    }

    /** Retorna o identificador numérico da playlist. */
    public int getId() {
        return id;
    }

    /** Retorna o nome da playlist. */
    public String getNome() {
        return nome;
    }

    /**
     * Retorna uma visão somente para leitura dos IDs das faixas.
     * A lista interna não pode ser alterada diretamente por quem recebe o resultado.
     */
    public List<String> getFaixaIds() {
        return Collections.unmodifiableList(faixaIds);
    }

    /** Informa quantas faixas estão cadastradas nesta playlist. */
    public int getQuantidadeFaixas() {
        return faixaIds.size();
    }

    /**
     * Adiciona uma faixa sem permitir IDs duplicados.
     *
     * @return true se a faixa foi adicionada; false se já estava presente
     */
    public boolean adicionarFaixa(String faixaId) {
        if (faixaId == null || faixaId.isBlank()) {
            throw new IllegalArgumentException("O ID da faixa não pode ser vazio.");
        }
        if (faixaIds.stream().anyMatch(id -> id.equalsIgnoreCase(faixaId))) {
            return false; // Evita que a mesma faixa apareça duas vezes.
        }
        return faixaIds.add(faixaId);
    }

    /**
     * Remove a faixa informada.
     *
     * @return true se uma faixa foi removida; false se o ID não estava na playlist
     */
    public boolean removerFaixa(String faixaId) {
        return faixaIds.remove(faixaId);
    }
}
