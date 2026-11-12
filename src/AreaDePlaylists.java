import java.util.NoSuchElementException;
import java.util.Scanner;

/**
 * Área de playlists no terminal, sem alterar a Main do grupo.
 * Opções: Criar, Listar, Consultar, Adicionar faixa e Remover faixa.
 */
public class AreaDePlaylists {

    private final PlaylistService playlists;
    private final CatalogService catalogo;
    private final Scanner entrada;

    public AreaDePlaylists(PlaylistService playlists, CatalogService catalogo, Scanner entrada) {
        this.playlists = playlists;
        this.catalogo = catalogo;
        this.entrada = entrada;
    }

    public static void main(String[] args) {
        System.out.println("=== Área de playlists ===");
        CatalogService catalogo = new CatalogService();
        try {
            new AreaDePlaylists(new PlaylistService(), catalogo, new Scanner(System.in)).abrir();
        } catch (NoSuchElementException e) {
            System.out.println("\nEntrada encerrada.");
        }
        System.out.println("Até logo!");
    }

    public void abrir() {
        while (true) {
            System.out.println("""

                    === Área de playlists ===
                    1. Criar
                    2. Listar
                    3. Consultar
                    4. Adicionar faixa
                    5. Remover faixa
                    0. Voltar
                    """);
            switch (lerLinha("Escolha uma opção: ").strip()) {
                case "1" -> criar();
                case "2" -> listar();
                case "3" -> consultar();
                case "4" -> adicionarFaixa();
                case "5" -> removerFaixa();
                case "0" -> {
                    return;
                }
                default -> System.out.println("Opção inválida. Digite um número de 0 a 5.");
            }
        }
    }


    private void criar() {
        try {
            Playlist nova = playlists.criar(lerLinha("Nome da playlist: "));
            System.out.println("Playlist criada: [" + nova.getId() + "] " + nova.getNome());
        } catch (IllegalArgumentException e) {
            System.out.println("Não foi possível criar: " + e.getMessage());
        }
    }

 
    private void listar() {
        System.out.print(playlists.formatarListagem());
    }


    private void consultar() {
        String texto = lerLinha("ID da playlist: ").strip();
        if (texto.isEmpty()) {
            System.out.println("Informe o ID da playlist.");
            return;
        }

        final int id;
        try {
            id = Integer.parseInt(texto);
        } catch (NumberFormatException e) {
            System.out.println("Id de playlist inválido: \"" + texto + "\" (use apenas números).");
            return;
        }

        System.out.print(playlists.formatarConsulta(id, catalogo));
    }

    /**
     * Lê o ID da playlist e o ID da faixa no terminal e pede a inclusão ao serviço.
     */
    private void adicionarFaixa() {
        String texto = lerLinha("ID da playlist: ").strip();
        if (texto.isEmpty()) {
            System.out.println("Informe o ID da playlist.");
            return;
        }
        final int playlistId;
        try {
            playlistId = Integer.parseInt(texto);
        } catch (NumberFormatException e) {
            System.out.println("Id de playlist inválido: \"" + texto + "\" (use apenas números).");
            return;
        }

        String faixaId = lerLinha("ID da faixa (ex.: t-001): ").strip();
        try {
            Playlist atualizada = playlists.adicionarFaixa(playlistId, faixaId, catalogo);
            System.out.println("Faixa adicionada à playlist [" + atualizada.getId() + "] "
                    + atualizada.getNome() + " (" + atualizada.getQuantidadeFaixas() + " faixa(s)).");
        } catch (IllegalArgumentException e) {
            System.out.println("Não foi possível adicionar: " + e.getMessage());
        }
    }

    /** Lê o ID da playlist e o ID da faixa e pede a remoção ao serviço. */
    private void removerFaixa() {
        String texto = lerLinha("ID da playlist: ").strip();
        if (texto.isEmpty()) {
            System.out.println("Informe o ID da playlist.");
            return;
        }
        final int playlistId;
        try {
            playlistId = Integer.parseInt(texto);
        } catch (NumberFormatException e) {
            System.out.println("Id de playlist inválido: \"" + texto + "\" (use apenas números).");
            return;
        }

        String faixaId = lerLinha("ID da faixa (ex.: t-001): ").strip();
        try {
            Playlist atualizada = playlists.removerFaixa(playlistId, faixaId);
            System.out.println("Faixa removida da playlist [" + atualizada.getId() + "] "
                    + atualizada.getNome() + " (" + atualizada.getQuantidadeFaixas() + " faixa(s)).");
        } catch (IllegalArgumentException e) {
            System.out.println("Não foi possível remover: " + e.getMessage());
        }
    }

    private String lerLinha(String prompt) {
        System.out.print(prompt);
        return entrada.nextLine();
    }
}
