package playlist;

/** JSON malformado. */
public class JsonException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public JsonException(String mensagem, int posicao) {
        super(mensagem + " (posição " + posicao + ")");
    }
}
