import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Executa pesquisas no catálogo sem depender da interface de terminal.
 * O arquivo fica no mesmo pacote padrão de Track para que a estrutura atual do
 * projeto compile de forma consistente; a migração para pacotes nomeados pode ser
 * feita depois como uma alteração arquitetural separada.
 */
public final class SearchService {

    /**
     * Encontra faixas cujo título ou artista contém o termo pesquisado.
     * A busca ignora diferenças entre maiúsculas e minúsculas e espaços externos.
     *
     * @param term texto que será procurado
     * @param catalogue coleção de faixas disponíveis
     * @return lista imutável de resultados na mesma ordem do catálogo
     * @throws IllegalArgumentException se o termo for nulo ou vazio
     * @throws NullPointerException se o catálogo for nulo
     */
    public List<Track> search(String term, Collection<Track> catalogue) {
        if (term == null) {
            throw new IllegalArgumentException("O termo de pesquisa não pode ser nulo.");
        }

        String cleanedTerm = term.strip();
        if (cleanedTerm.isEmpty()) {
            throw new IllegalArgumentException("Digite um termo de pesquisa antes de continuar.");
        }

        Objects.requireNonNull(catalogue, "O catálogo de músicas é obrigatório.");
        String normalizedTerm = cleanedTerm.toLowerCase(Locale.ROOT);

        return catalogue.stream()
                .filter(Objects::nonNull)
                .filter(track -> containsIgnoreCase(track.title(), normalizedTerm)
                        || containsIgnoreCase(track.artist(), normalizedTerm))
                .toList();
    }

    /** Verifica se um campo contém o termo normalizado, sem distinção de caixa. */
    private static boolean containsIgnoreCase(String value, String normalizedTerm) {
        return value.toLowerCase(Locale.ROOT).contains(normalizedTerm);
    }
}
