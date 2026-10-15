package playlist;

/** Já existe uma playlist com o mesmo nome (comparação sem diferenciar maiúsculas). */
public class DuplicateNameException extends ValidationException {
    private static final long serialVersionUID = 1L;

    public DuplicateNameException() {
        super("Já existe uma playlist com esse nome.");
    }
}
