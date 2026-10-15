package playlist;

import static playlist.Verifica.caso;
import static playlist.Verifica.igual;
import static playlist.Verifica.lanca;
import static playlist.Verifica.verdade;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

final class FilePlaylistRepositoryTest {

    private FilePlaylistRepositoryTest() {
    }

    static Path pastaTemporaria() throws IOException {
        return Files.createTempDirectory("playlists-teste");
    }

    private static Playlist nova(String id, String nome) {
        return new Playlist(id, nome, List.of("m1", "m2"), Instant.parse("2026-10-15T12:00:00.123Z"));
    }

    static void rodar() {
        caso("Arquivo: sem arquivo a lista é vazia", () -> {
            Path pasta = pastaTemporaria();
            igual(List.of(), new FilePlaylistRepository(pasta.resolve("p.json")).listar());
        });

        caso("Arquivo: salva, cria pastas e outra instância lê os mesmos dados", () -> {
            Path arquivo = pastaTemporaria().resolve("sub/dir/p.json");
            Playlist p = nova("pl_1", "Música 😀 \"aspas\"");
            new FilePlaylistRepository(arquivo).salvar(p);

            FilePlaylistRepository outra = new FilePlaylistRepository(arquivo);
            igual(List.of(p), outra.listar());
            igual(Optional.of(p), outra.buscarPorId("pl_1"));
            igual(Optional.empty(), outra.buscarPorId("x"));
        });

        caso("Arquivo: mantém a ordem de criação e não deixa .tmp para trás", () -> {
            Path pasta = pastaTemporaria();
            FilePlaylistRepository repo = new FilePlaylistRepository(pasta.resolve("p.json"));
            repo.salvar(nova("pl_1", "A"));
            repo.salvar(nova("pl_2", "B"));
            igual(List.of("A", "B"), repo.listar().stream().map(Playlist::nome).toList());
            try (Stream<Path> arquivos = Files.list(pasta)) {
                igual(List.of("p.json"), arquivos.map(a -> a.getFileName().toString()).toList());
            }
        });

        caso("Arquivo: id repetido é rejeitado", () -> {
            FilePlaylistRepository repo = new FilePlaylistRepository(pastaTemporaria().resolve("p.json"));
            repo.salvar(nova("pl_1", "A"));
            lanca(StorageException.class, () -> repo.salvar(nova("pl_1", "B")));
            igual(1, repo.listar().size());
        });

        caso("Arquivo: conteúdo corrompido gera erro e não é sobrescrito", () -> {
            Path arquivo = pastaTemporaria().resolve("p.json");
            String ruim = "{quebrado";
            Files.writeString(arquivo, ruim, StandardCharsets.UTF_8);
            FilePlaylistRepository repo = new FilePlaylistRepository(arquivo);

            StorageException e = lanca(StorageException.class, repo::listar);
            igual("Os dados salvos estão corrompidos.", e.getMessage());
            lanca(StorageException.class, () -> repo.salvar(nova("pl_1", "A")));
            igual(ruim, Files.readString(arquivo, StandardCharsets.UTF_8));
        });

        caso("Arquivo: arquivo vazio, raiz que não é lista e campo faltando contam como corrompidos", () -> {
            String[] ruins = {
                "", "{}", "[1]", "[{\"id\":\"a\"}]",
                "[{\"id\":\"a\",\"nome\":\"n\",\"musicas\":[1],\"criadaEm\":\"2026-01-01T00:00:00Z\"}]",
                "[{\"id\":\"a\",\"nome\":\"n\",\"musicas\":[],\"criadaEm\":\"ontem\"}]"
            };
            for (String ruim : ruins) {
                Path arquivo = pastaTemporaria().resolve("p.json");
                Files.writeString(arquivo, ruim, StandardCharsets.UTF_8);
                lanca(StorageException.class, () -> new FilePlaylistRepository(arquivo).listar());
            }
        });

        caso("Arquivo: UTF-8 inválido gera erro de armazenamento", () -> {
            Path arquivo = pastaTemporaria().resolve("p.json");
            Files.write(arquivo, new byte[] {'[', (byte) 0xC3, (byte) 0x28, ']'});
            lanca(StorageException.class, () -> new FilePlaylistRepository(arquivo).listar());
        });

        caso("Arquivo: falha ao gravar vira StorageException", () -> {
            Path pasta = pastaTemporaria();
            Path bloqueio = pasta.resolve("arquivo-comum");
            Files.writeString(bloqueio, "x");
            // o "diretório" de destino é um arquivo comum: não dá para criar a pasta
            FilePlaylistRepository repo = new FilePlaylistRepository(bloqueio.resolve("p.json"));
            StorageException e = lanca(StorageException.class, () -> repo.salvar(nova("pl_1", "A")));
            verdade(e.getCause() != null, "a causa técnica deve ficar na exceção");
        });
    }
}
