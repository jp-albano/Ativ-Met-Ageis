package playlist;

/**
 * Falha ao ler ou gravar os dados. A mensagem é segura para exibir; os detalhes
 * técnicos ficam na causa e não devem ser enviados ao usuário.
 */
public class StorageException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public StorageException(String mensagem) {
        super(mensagem);
    }

    public StorageException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
