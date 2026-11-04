import java.io.File;
import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;

public class Main {

    private static final CatalogService catalogo = new CatalogService();
    private static final PlaylistService playlists = new PlaylistService();
    private static final Scanner entrada = new Scanner(System.in);

    public static void main(String[] args) {
        System.out.println("=== Spotify Clone (terminal) ===");
        try {
            boolean executando = true;
            while (executando) {
                exibirMenu();
                executando = tratarOpcao(lerLinha("Escolha uma opção: "));
            }
        } catch (NoSuchElementException e) {
            // Scanner.nextLine() lança esta exceção quando não há mais linhas para ler.
            System.out.println("\nEntrada encerrada.");
        }
        System.out.println("Até logo!");
    }

    // ------------------------------------------------------------------ menu

    private static void exibirMenu() {
        System.out.println("""

                ----- Músicas -----
                1. Listar catálogo
                2. Cadastrar nova música
                3. Buscar música (título ou artista)
                4. Remover música do catálogo
                ----- Playlists -----
                5. Criar playlist
                6. Listar playlists
                7. Ver faixas de uma playlist
                8. Adicionar música a uma playlist
                9. Remover música de uma playlist
                0. Sair
                """);
    }

    private static boolean tratarOpcao(String opcao) {
        switch (opcao.strip()) {
            case "1" -> listarCatalogo();
            case "2" -> cadastrarMusica();
            case "3" -> buscarMusica();
            case "4" -> removerMusicaDoCatalogo();
            case "5" -> criarPlaylist();
            case "6" -> listarPlaylists();
            case "7" -> verFaixasDaPlaylist();
            case "8" -> adicionarMusicaNaPlaylist();
            case "9" -> removerMusicaDaPlaylist();
            case "0" -> {
                return false;
            }
            default -> System.out.println("Opção inválida. Digite um número de 0 a 9.");
        }
        return true;
    }

    // --------------------------------------------------------------- músicas

    private static void listarCatalogo() {
        List<Track> todas = catalogo.getAllTracks();
        if (todas.isEmpty()) {
            System.out.println("O catálogo está vazio.");
            return;
        }
        System.out.println("\nCatálogo (" + todas.size() + " músicas):");
        todas.forEach(Main::imprimirMusica);
    }

    private static void cadastrarMusica() {
        String titulo = lerLinha("Título: ").strip();
        String artista = lerLinha("Artista: ").strip();
        if (titulo.isEmpty() || artista.isEmpty()) {
            System.out.println("Título e artista são obrigatórios. Música não cadastrada.");
            return;
        }

        try {
            int duracao = lerDuracaoEmSegundos(lerLinha("Duração (mm:ss ou segundos): "));

            String caminhoAudio = lerLinha("Arquivo de áudio (Enter para o padrão): ").strip();
            if (caminhoAudio.isEmpty()) {
                caminhoAudio = "audio/" + gerarSlug(titulo) + ".mp3";
            }
            String caminhoCapa = lerLinha("Arquivo de capa (Enter para nenhuma): ").strip();
            File capa = caminhoCapa.isEmpty() ? null : new File(caminhoCapa);

            Track nova = new Track(proximoIdDeMusica(), titulo, artista, duracao,
                    capa, new File(caminhoAudio));
            catalogo.addTrack(nova);
            System.out.println("Música cadastrada: " + nova);
        } catch (IllegalArgumentException e) {
            System.out.println("Não foi possível cadastrar: " + e.getMessage());
        }
    }

    private static void buscarMusica() {
        String termo = lerLinha("Termo de busca (título ou artista): ").strip();
        if (termo.isEmpty()) {
            System.out.println("Digite um termo de busca.");
            return;
        }

        Set<Track> resultado = new LinkedHashSet<>(catalogo.findByTitle(termo));
        resultado.addAll(catalogo.findByArtist(termo));

        if (resultado.isEmpty()) {
            System.out.println("Nenhuma música encontrada para \"" + termo + "\".");
            return;
        }
        System.out.println("\n" + resultado.size() + " resultado(s):");
        resultado.forEach(Main::imprimirMusica);
    }

    private static void removerMusicaDoCatalogo() {
        listarCatalogo();
        String id = lerLinha("Id da música a remover: ").strip();
        if (!catalogo.removeById(id)) {
            System.out.println("Música não encontrada: " + id);
            return;
        }
        playlists.listar().forEach(p -> p.removerFaixa(id));
        System.out.println("Música " + id + " removida do catálogo e das playlists.");
    }

    // ------------------------------------------------------------- playlists

    private static void criarPlaylist() {
        try {
            Playlist nova = playlists.criar(lerLinha("Nome da playlist: "));
            System.out.println("Playlist criada: [" + nova.getId() + "] " + nova.getNome());
        } catch (IllegalArgumentException e) {
            System.out.println("Não foi possível criar: " + e.getMessage());
        }
    }

    private static void listarPlaylists() {
        List<Playlist> todas = playlists.listar();
        if (todas.isEmpty()) {
            System.out.println("Nenhuma playlist criada ainda.");
            return;
        }
        System.out.println("\nPlaylists:");
        todas.forEach(p -> System.out.printf("  [%s] %s (%d faixa(s))%n",
                p.getId(), p.getNome(), p.getQuantidadeFaixas()));
    }

    private static void verFaixasDaPlaylist() {
        escolherPlaylist().ifPresent(p -> {
            if (p.getQuantidadeFaixas() == 0) {
                System.out.println("A playlist \"" + p.getNome() + "\" está vazia.");
                return;
            }
            System.out.println("\nPlaylist \"" + p.getNome() + "\":");
            for (String faixaId : p.getFaixaIds()) {
                catalogo.findById(faixaId).ifPresentOrElse(
                        Main::imprimirMusica,
                        () -> System.out.println("  " + faixaId + " (música não está mais no catálogo)"));
            }
        });
    }

    private static void adicionarMusicaNaPlaylist() {
        listarPlaylists();
        listarCatalogo();

        String textoPlaylist = lerLinha("Id da playlist: ").strip();
        final int playlistId;
        try {
            playlistId = Integer.parseInt(textoPlaylist);
        } catch (NumberFormatException e) {
            System.out.println("Id de playlist inválido: \"" + textoPlaylist + "\" (use apenas números).");
            return;
        }

        String faixaId = lerLinha("Id da faixa: ").strip();
        switch (playlists.adicionarFaixa(playlistId, faixaId, catalogo)) {
            case ADICIONADA -> {
                String titulo = catalogo.findById(faixaId).map(Track::title).orElse(faixaId);
                String nome = playlists.buscarPorId(playlistId).map(Playlist::getNome).orElse(String.valueOf(playlistId));
                System.out.println("\"" + titulo + "\" adicionada a \"" + nome + "\".");
            }
            case JA_PRESENTE ->
                    System.out.println("Essa faixa já está na playlist. A mesma faixa não é inserida de novo.");
            case PLAYLIST_INEXISTENTE ->
                    System.out.println("Playlist não encontrada: " + textoPlaylist);
            case FAIXA_INEXISTENTE ->
                    System.out.println("Faixa não encontrada no catálogo: " + faixaId);
        }
    }

    private static void removerMusicaDaPlaylist() {
        Optional<Playlist> escolhida = escolherPlaylist();
        if (escolhida.isEmpty()) {
            return;
        }
        Playlist playlist = escolhida.get();
        if (playlist.getQuantidadeFaixas() == 0) {
            System.out.println("A playlist \"" + playlist.getNome() + "\" está vazia.");
            return;
        }
        for (String faixaId : playlist.getFaixaIds()) {
            catalogo.findById(faixaId).ifPresent(Main::imprimirMusica);
        }
        String id = lerLinha("Id da música a remover da playlist: ").strip();
        if (playlist.removerFaixa(id)) {
            System.out.println("Música " + id + " removida de \"" + playlist.getNome() + "\".");
        } else {
            System.out.println("Essa música não está na playlist.");
        }
    }

    private static Optional<Playlist> escolherPlaylist() {
        if (playlists.listar().isEmpty()) {
            System.out.println("Nenhuma playlist criada ainda. Use a opção 5 primeiro.");
            return Optional.empty();
        }
        listarPlaylists();
        String texto = lerLinha("Id da playlist: ").strip();
        try {
            Optional<Playlist> playlist = playlists.buscarPorId(Integer.parseInt(texto));
            if (playlist.isEmpty()) {
                System.out.println("Playlist não encontrada: " + texto);
            }
            return playlist;
        } catch (NumberFormatException e) {
            System.out.println("Id de playlist inválido: \"" + texto + "\" (use apenas números).");
            return Optional.empty();
        }
    }

    // --------------------------------------------------------------- apoio

    private static String lerLinha(String prompt) {
        System.out.print(prompt);
        return entrada.nextLine();
    }

    private static void imprimirMusica(Track t) {
        System.out.printf("  %-6s %-28s %-20s %s%n",
                t.id(), t.title(), t.artist(), t.formattedDuration());
    }

    private static String proximoIdDeMusica() {
        int n = catalogo.getTrackCount() + 1;
        String id;
        do {
            id = "t-%03d".formatted(n++);
        } while (catalogo.findById(id).isPresent());
        return id;
    }

    private static int lerDuracaoEmSegundos(String texto) {
        String t = texto.strip();
        try {
            if (t.contains(":")) {
                String[] partes = t.split(":");
                if (partes.length != 2) {
                    throw new IllegalArgumentException("Use o formato mm:ss ou apenas segundos.");
                }
                int minutos = Integer.parseInt(partes[0].strip());
                int segundos = Integer.parseInt(partes[1].strip());
                if (minutos < 0 || segundos < 0 || segundos > 59) {
                    throw new IllegalArgumentException("Duração inválida: minutos >= 0 e segundos entre 0 e 59.");
                }
                return minutos * 60 + segundos;
            }
            int total = Integer.parseInt(t);
            if (total < 0) {
                throw new IllegalArgumentException("A duração não pode ser negativa.");
            }
            return total;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Duração inválida. Use mm:ss ou apenas segundos.");
        }
    }

    private static String gerarSlug(String titulo) {
        String semAcento = Normalizer.normalize(titulo, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = semAcento.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return slug.isEmpty() ? "faixa" : slug;
    }
}
