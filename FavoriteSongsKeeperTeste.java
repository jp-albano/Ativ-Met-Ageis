import java.util.List;

public class FavoriteSongsKeeperTest {

    public static void main(String[] args) {
        testarFavoritarFaixaExistente();
        testarSemDuplicacao();
    }

    private static void testarFavoritarFaixaExistente() {

        FavoriteSongsKeeper favoritas = new FavoriteSongsKeeper(new SampleCatalog());

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
        } catch (UnknownTrackException e) {
            System.out.println("FALHOU: a faixa 001 deveria existir no catálogo. " + e.getMessage());
        }
    }

    private static void testarSemDuplicacao() {

        FavoriteSongsKeeper favoritas = new FavoriteSongsKeeper(new SampleCatalog());

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
        } catch (UnknownTrackException e) {
            System.out.println("FALHOU: a faixa 002 deveria existir no catálogo. " + e.getMessage());
        }
    }
}
