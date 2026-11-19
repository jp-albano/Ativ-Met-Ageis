import java.util.List;

public class LikedSongsShelfTest {

    public static void main(String[] args) {
        testarFavoritarFaixaExistente();
        testarSemDuplicacao();
        testarDesfavoritarFaixaExistente();
        testarRemoverFaixaForaDaLista();
    }

    private static void testarFavoritarFaixaExistente() {

        LikedSongsShelf favoritas = new LikedSongsShelf(new DemoSongCatalog());

        try {
            favoritas.addToFavorites("001");

            List<Track> lista = favoritas.listFavorites();

            boolean encontrou = false;
            for (int i = 0; i < lista.size(); i++) {
                if (lista.get(i).getId().equals("001")) {
                    encontrou = true;
                }
            }

            if (encontrou) {
                System.out.println("PASSOU: a faixa 001 aparece na listagem de favoritas.");
            } else {
                System.out.println("FALHOU: a faixa 001 não apareceu na listagem de favoritas.");
            }
        } catch (SongNotInCatalogException e) {
            System.out.println("FALHOU: a faixa 001 deveria existir no catálogo. " + e.getMessage());
        }
    }

    private static void testarSemDuplicacao() {

        LikedSongsShelf favoritas = new LikedSongsShelf(new DemoSongCatalog());

        try {
            favoritas.addToFavorites("002");
            favoritas.addToFavorites("002");

            List<Track> lista = favoritas.listFavorites();

            int quantidade = 0;
            for (int i = 0; i < lista.size(); i++) {
                if (lista.get(i).getId().equals("002")) {
                    quantidade++;
                }
            }

            if (quantidade == 1 && lista.size() == 1) {
                System.out.println("PASSOU: a faixa 002 aparece apenas uma vez na listagem.");
            } else {
                System.out.println("FALHOU: a faixa 002 apareceu " + quantidade + " vezes na listagem.");
            }
        } catch (SongNotInCatalogException e) {
            System.out.println("FALHOU: a faixa 002 deveria existir no catálogo. " + e.getMessage());
        }
    }

    private static void testarDesfavoritarFaixaExistente() {

        LikedSongsShelf favoritas = new LikedSongsShelf(new DemoSongCatalog());

        try {
            favoritas.addToFavorites("003");
            favoritas.removeFromFavorites("003");

            List<Track> lista = favoritas.listFavorites();

            boolean encontrou = false;
            for (int i = 0; i < lista.size(); i++) {
                if (lista.get(i).getId().equals("003")) {
                    encontrou = true;
                }
            }

            if (!encontrou) {
                System.out.println("PASSOU: a faixa 003 deixou de aparecer na listagem de favoritas.");
            } else {
                System.out.println("FALHOU: a faixa 003 continuou aparecendo na listagem de favoritas.");
            }
        } catch (SongNotInCatalogException e) {
            System.out.println("FALHOU: a faixa 003 deveria existir no catálogo. " + e.getMessage());
        }
    }

    private static void testarRemoverFaixaForaDaLista() {

        LikedSongsShelf favoritas = new LikedSongsShelf(new DemoSongCatalog());

        try {
            favoritas.addToFavorites("001");

            boolean removeuNaoFavorita = favoritas.removeFromFavorites("002");
            boolean removeuInexistente = favoritas.removeFromFavorites("999");
            int tamanho = favoritas.listFavorites().size();

            if (!removeuNaoFavorita && !removeuInexistente && tamanho == 1) {
                System.out.println("PASSOU: remover faixa fora da lista devolve false e não altera a listagem.");
            } else {
                System.out.println("FALHOU: remover faixa fora da lista alterou o resultado ou a listagem.");
            }
        } catch (SongNotInCatalogException e) {
            System.out.println("FALHOU: a faixa 001 deveria existir no catálogo. " + e.getMessage());
        }
    }
}
