package playlist;

import static playlist.Verifica.caso;
import static playlist.Verifica.igual;
import static playlist.Verifica.lanca;
import static playlist.Verifica.verdade;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

final class PlaylistServiceTest {

    private PlaylistServiceTest() {
    }

    /** Repositório em memória, só para testar o serviço isoladamente. */
    static final class Memoria implements PlaylistRepository {
        final List<Playlist> dados = new ArrayList<>();

        @Override
        public List<Playlist> listar() {
            return new ArrayList<>(dados);
        }

        @Override
        public Optional<Playlist> buscarPorId(String id) {
            return dados.stream().filter(p -> p.id().equals(id)).findFirst();
        }

        @Override
        public Playlist salvar(Playlist p) {
            dados.add(p);
            return p;
        }
    }

    private static PlaylistService servico(Memoria repo) {
        AtomicInteger n = new AtomicInteger();
        Clock fixo = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC);
        return new PlaylistService(repo, () -> "pl_" + n.incrementAndGet(), fixo);
    }

    static void rodar() {
        caso("Serviço: nome válido cria playlist vazia com id e data", () -> {
            Playlist p = servico(new Memoria()).criar("Treino");
            igual("Treino", p.nome());
            igual("pl_1", p.id());
            igual(List.of(), p.musicas());
            igual(Instant.parse("2026-10-15T12:00:00Z"), p.criadaEm());
        });

        caso("Serviço: remove espaços nas pontas", () ->
                igual("Treino", servico(new Memoria()).criar("  Treino \t").nome()));

        caso("Serviço: vazio, só espaços e null são rejeitados", () -> {
            for (String ruim : new String[] {"", "   ", "\t\n", null}) {
                ValidationException e = lanca(ValidationException.class, () -> servico(new Memoria()).criar(ruim));
                igual("Informe o nome da playlist.", e.getMessage());
            }
        });

        caso("Serviço: 50 caracteres passa e 51 é rejeitado", () -> {
            servico(new Memoria()).criar("a".repeat(50));
            ValidationException e = lanca(ValidationException.class, () -> servico(new Memoria()).criar("a".repeat(51)));
            verdade(e.getMessage().contains("50"), "mensagem deveria citar o limite");
        });

        caso("Serviço: tamanho conta caracteres Unicode, não unidades UTF-16", () -> {
            servico(new Memoria()).criar("😀".repeat(50));
            lanca(ValidationException.class, () -> servico(new Memoria()).criar("😀".repeat(51)));
        });

        caso("Serviço: caracteres de controle no meio do nome são rejeitados", () -> {
            lanca(ValidationException.class, () -> servico(new Memoria()).criar("Tre\nino"));
            lanca(ValidationException.class, () -> servico(new Memoria()).criar("Tre\u0000ino"));
        });

        caso("Serviço: duplicado ignora maiúsculas, espaços e forma Unicode do acento", () -> {
            Memoria repo = new Memoria();
            PlaylistService s = servico(repo);
            s.criar("Música Boa");
            lanca(DuplicateNameException.class, () -> s.criar("música boa"));
            lanca(DuplicateNameException.class, () -> s.criar("  MÚSICA BOA  "));
            lanca(DuplicateNameException.class, () -> s.criar("Mu\u0301sica Boa")); // u + acento combinante
            igual(1, repo.dados.size());
        });

        caso("Serviço: duas playlists com nomes diferentes mantêm as duas", () -> {
            PlaylistService s = servico(new Memoria());
            Playlist a = s.criar("Treino");
            Playlist b = s.criar("Estudo");
            verdade(!a.id().equals(b.id()), "ids devem ser distintos");
            igual(List.of("Treino", "Estudo"), s.listar().stream().map(Playlist::nome).toList());
        });

        caso("Serviço: buscarPorId encontra ou devolve vazio", () -> {
            PlaylistService s = servico(new Memoria());
            Playlist a = s.criar("Treino");
            igual(Optional.of(a), s.buscarPorId(a.id()));
            igual(Optional.empty(), s.buscarPorId("nao-existe"));
        });

        caso("Playlist: lista de músicas é imutável e null vira vazia", () -> {
            Playlist p = new Playlist("x", "n", null, Instant.EPOCH);
            igual(List.of(), p.musicas());
            lanca(UnsupportedOperationException.class, () -> p.musicas().add("m1"));
        });

        caso("Serviço: criações simultâneas do mesmo nome geram uma só playlist", () -> {
            Memoria repo = new Memoria();
            PlaylistService s = new PlaylistService(repo);
            AtomicInteger criadas = new AtomicInteger();
            List<Thread> threads = new ArrayList<>();
            for (int k = 0; k < 20; k++) {
                Thread t = new Thread(() -> {
                    try {
                        s.criar("Corrida");
                        criadas.incrementAndGet();
                    } catch (DuplicateNameException esperado) {
                        // ok
                    }
                });
                threads.add(t);
                t.start();
            }
            for (Thread t : threads) {
                t.join();
            }
            igual(1, criadas.get());
            igual(1, repo.dados.size());
        });
    }
}
