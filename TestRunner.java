package playlist;

/** Executa todas as suítes. Código de saída 1 se algum teste falhar. */
public final class TestRunner {

    private TestRunner() {
    }

    public static void main(String[] args) {
        JsonTest.rodar();
        PlaylistServiceTest.rodar();
        FilePlaylistRepositoryTest.rodar();
        PlaylistApiTest.rodar();

        System.out.println();
        if (Verifica.falhas == 0) {
            System.out.println(Verifica.total + " testes passaram");
        } else {
            System.out.println(Verifica.falhas + " de " + Verifica.total + " testes falharam");
            System.exit(1);
        }
    }
}
