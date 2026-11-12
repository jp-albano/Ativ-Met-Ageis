import java.util.List;

public class FavoriteSongsKeeperTest {

    public static void main(String[] args) {

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
}
