package playlist;

/** Dado informado pelo usuário é inválido. A mensagem pode ser exibida ao usuário. */
public class ValidationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ValidationException(String mensagem) {
        super(mensagem);
    }
}
