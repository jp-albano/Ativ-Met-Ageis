public class ConfereIdRecusado {

    public static void main(String[] args) {

        LikedSongsShelf favoritas = new LikedSongsShelf(new DemoSongCatalog());

        try {
            favoritas.addToFavorites("999");
            System.out.println("FALHOU: o ID 999 não existe e deveria ter sido recusado.");
        } catch (SongNotInCatalogException e) {
            String mensagem = e.getMessage();
            boolean mensagemClara = mensagem.contains("999") && mensagem.contains("catálogo");
            boolean listaVazia = favoritas.listFavorites().isEmpty();

            if (mensagemClara && listaVazia) {
                System.out.println("PASSOU: o ID 999 foi recusado com a mensagem: " + mensagem);
            } else {
                System.out.println("FALHOU: o ID 999 foi recusado, mas a mensagem não está clara ou a lista foi alterada.");
            }
        }
    }
}
