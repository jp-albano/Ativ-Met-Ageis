/** Testes da inclusão de faixa: existência, alinhamento de ID e duplicata. */
public class PlaylistServiceAddTest {

    public static void main(String[] args) {
        CatalogService catalogo = new CatalogService();
        PlaylistService servico = new PlaylistService();
        Playlist rock = servico.criar("Rock");

        servico.adicionarFaixa(rock.getId(), "  T-001  ", catalogo);
        check(rock.getFaixaIds().contains("t-001"), "grava o ID canônico do catálogo (t-001)");
        check(rock.getQuantidadeFaixas() == 1, "primeira inclusão aumenta a quantidade");

        boolean duplicataRecusada = false;
        try {
            servico.adicionarFaixa(rock.getId(), "t-001", catalogo);
        } catch (IllegalArgumentException e) {
            duplicataRecusada = e.getMessage().contains("já está");
        }
        check(duplicataRecusada, "mesma faixa não entra duas vezes");
        check(rock.getQuantidadeFaixas() == 1, "duplicata não altera a lista");

        boolean playlistAusente = false;
        try {
            servico.adicionarFaixa(99, "t-002", catalogo);
        } catch (IllegalArgumentException e) {
            playlistAusente = e.getMessage().contains("Playlist não encontrada");
        }
        check(playlistAusente, "playlist inexistente é recusada antes de alterar");

        boolean faixaAusente = false;
        try {
            servico.adicionarFaixa(rock.getId(), "t-999", catalogo);
        } catch (IllegalArgumentException e) {
            faixaAusente = e.getMessage().contains("catálogo");
        }
        check(faixaAusente, "faixa inexistente no catálogo é recusada");
        check(rock.getQuantidadeFaixas() == 1, "falha de validação não inclui faixa");

        boolean idVazio = false;
        try {
            servico.adicionarFaixa(rock.getId(), "   ", catalogo);
        } catch (IllegalArgumentException e) {
            idVazio = true;
        }
        check(idVazio, "ID de faixa em branco é recusado");

        System.out.println("Resultado: " + passed + "/" + total + " testes passaram.");
        if (passed != total) {
            throw new AssertionError("Existem testes com falha.");
        }
    }

    private static int total;
    private static int passed;

    private static void check(boolean condition, String description) {
        total++;
        if (!condition) {
            System.err.println("FALHOU: " + description);
            return;
        }
        passed++;
        System.out.println("PASSOU: " + description);
    }
}
