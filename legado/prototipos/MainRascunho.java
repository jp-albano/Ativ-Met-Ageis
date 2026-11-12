import java.util.Scanner;

public class Main {

    public static void main(String[] args) {

        Scanner sc = new Scanner(System.in);
        FavoriteSongsKeeper favoritas = new FavoriteSongsKeeper(new SampleCatalog());

        String opcao = "";

        while (!opcao.equals("0")) {
            System.out.println("===== MENU =====");
            System.out.println("1 - Favoritas");
            System.out.println("0 - Sair");
            System.out.print("Escolha uma opção: ");
            opcao = sc.nextLine().trim();

            if (opcao.equals("1")) {
                menuFavoritas(sc, favoritas);
            } else if (!opcao.equals("0")) {
                System.out.println("Opção inválida!");
            }
            System.out.println();
        }

        System.out.println("Até logo!");
        sc.close();
    }

    private static void menuFavoritas(Scanner sc, FavoriteSongsKeeper favoritas) {

        String opcao = "";

        while (!opcao.equals("0")) {
            System.out.println("--- FAVORITAS ---");
            System.out.println("1 - Favoritar faixa");
            System.out.println("2 - Desfavoritar faixa");
            System.out.println("0 - Voltar");
            System.out.print("Escolha uma opção: ");
            opcao = sc.nextLine().trim();

            if (opcao.equals("1")) {
                favoritar(sc, favoritas);
            } else if (opcao.equals("2")) {
                desfavoritar(sc, favoritas);
            } else if (!opcao.equals("0")) {
                System.out.println("Opção inválida!");
            }
            System.out.println();
        }
    }

    private static void favoritar(Scanner sc, FavoriteSongsKeeper favoritas) {

        System.out.print("Digite o ID da faixa: ");
        String id = sc.nextLine();

        try {
            if (favoritas.addToFavorites(id)) {
                System.out.println("Faixa favoritada!");
            } else {
                System.out.println("Essa faixa já está nas favoritas.");
            }
        } catch (UnknownTrackException e) {
            System.out.println(e.getMessage());
        }
    }

    private static void desfavoritar(Scanner sc, FavoriteSongsKeeper favoritas) {

        System.out.print("Digite o ID da faixa: ");
        String id = sc.nextLine();

        if (favoritas.removeFromFavorites(id)) {
            System.out.println("Faixa removida das favoritas!");
        } else {
            System.out.println("Essa faixa não estava nas favoritas.");
        }
    }
}
