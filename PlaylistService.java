package playlist;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Regras de negócio das playlists: validação do nome, criação e consulta.
 *
 * <p>Regras do nome: obrigatório, sem espaços nas pontas, no máximo {@value #NOME_MAX}
 * caracteres, sem caracteres de controle e único sem diferenciar maiúsculas/minúsculas
 * (acentos compostos e decompostos são tratados como iguais).
 *
 * <p>{@link #criar} é {@code synchronized} para que "verificar duplicado" e "salvar"
 * não se misturem entre requisições simultâneas (válido para um único processo).
 */
public final class PlaylistService {

    /** Tamanho máximo do nome, em caracteres Unicode (code points). */
    public static final int NOME_MAX = 50;

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final PlaylistRepository repositorio;
    private final Supplier<String> geradorId;
    private final Clock relogio;

    public PlaylistService(PlaylistRepository repositorio) {
        this(repositorio, () -> "pl_" + UUID.randomUUID(), Clock.systemUTC());
    }

    /** Construtor para testes: permite fixar ids e relógio. */
    public PlaylistService(PlaylistRepository repositorio, Supplier<String> geradorId, Clock relogio) {
        this.repositorio = repositorio;
        this.geradorId = geradorId;
        this.relogio = relogio;
    }

    /**
     * Valida e normaliza o nome (remove espaços das pontas e normaliza para NFC).
     * Não verifica duplicidade.
     *
     * @return o nome normalizado
     * @throws ValidationException se o nome for inválido
     */
    public static String validarNome(String nome) {
        if (nome == null || nome.strip().isEmpty()) {
            throw new ValidationException("Informe o nome da playlist.");
        }
        String limpo = Normalizer.normalize(nome.strip(), Normalizer.Form.NFC);
        if (limpo.codePointCount(0, limpo.length()) > NOME_MAX) {
            throw new ValidationException("O nome deve ter no máximo " + NOME_MAX + " caracteres.");
        }
        if (limpo.codePoints().anyMatch(Character::isISOControl)) {
            throw new ValidationException("O nome não pode conter caracteres de controle.");
        }
        return limpo;
    }

    /**
     * Cria e salva uma playlist vazia.
     *
     * @throws DuplicateNameException se já existir playlist com esse nome
     * @throws ValidationException    se o nome for inválido
     * @throws StorageException       se não for possível ler/gravar
     */
    public synchronized Playlist criar(String nome) {
        String limpo = validarNome(nome);
        String chave = chave(limpo);
        for (Playlist existente : repositorio.listar()) {
            if (chave(existente.nome()).equals(chave)) {
                throw new DuplicateNameException();
            }
        }
        Instant agora = Instant.now(relogio).truncatedTo(ChronoUnit.MILLIS);
        return repositorio.salvar(new Playlist(geradorId.get(), limpo, List.of(), agora));
    }

    /** @return todas as playlists, na ordem de criação */
    public List<Playlist> listar() {
        return repositorio.listar();
    }

    /** @return a playlist com o id, se existir */
    public Optional<Playlist> buscarPorId(String id) {
        return repositorio.buscarPorId(id);
    }

    private static String chave(String nome) {
        return Normalizer.normalize(nome, Normalizer.Form.NFC).toLowerCase(PT_BR);
    }
}
