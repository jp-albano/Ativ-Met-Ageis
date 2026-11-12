import java.io.File;
import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Scanner;

public class Main {

    private static final int RESULTADOS_ONLINE = 5;

    private static final CatalogService catalogo = new CatalogService();
    private static final PlaylistService playlists = new PlaylistService();
    private static final FavoriteService favoritos = new FavoriteService();
    private static final SearchService busca = new SearchService();
    private static final AudioPlayerService player = new AudioPlayerService();
    private static final MusicDiscoveryService descoberta = new MusicDiscoveryService();
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
        } finally {
            player.close();
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
                10. Abrir área de playlists
                ----- Favoritas -----
                11. Favoritar / desfavoritar música
                12. Listar favoritas
                ----- Player -----
                13. Selecionar música para tocar
                14. Tocar / retomar
                15. Pausar
                16. Parar
                17. Ver progresso
                18. Abrir submenu do player
                ----- Descoberta -----
                19. Pesquisar música online e adicionar ao catálogo
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
            case "10" -> new AreaDePlaylists(playlists, catalogo, entrada).abrir();
            case "11" -> alternarFavorita();
            case "12" -> listarFavoritas();
            case "13" -> selecionarMusicaParaTocar();
            case "14" -> tocarMusica();
            case "15" -> pausarMusica();
            case "16" -> pararMusica();
            case "17" -> player.exibirProgresso();
            case "18" -> player.abrirMenu(entrada);
            case "19" -> pesquisarOnlineEAdicionar();
            case "0" -> {
                return false;
            }
            default -> System.out.println("Opção inválida. Digite um número de 0 a 19.");
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
        String termo = lerLinha("Termo de busca (título ou artista): ");
        try {
            List<Track> resultado = busca.search(termo, catalogo.getAllTracks());
            if (resultado.isEmpty()) {
                System.out.println("Nenhuma música encontrada para \"" + termo.strip() + "\".");
                return;
            }
            System.out.println("\n" + resultado.size() + " resultado(s):");
            resultado.forEach(Main::imprimirMusica);
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
        }
    }

    private static void removerMusicaDoCatalogo() {
        listarCatalogo();
        String id = lerLinha("Id da música a remover: ").strip();
        Optional<Track> musica = catalogo.findById(id);
        if (musica.isEmpty()) {
            System.out.println("Música não encontrada: " + id);
            return;
        }
        catalogo.removeById(id);
        // Playlists, favoritas e player guardam referências à música; limpar todos evita dados órfãos.
        playlists.removerFaixaDeTodas(id);
        favoritos.remove(musica.get());
        if (player.getFaixaAtual().filter(t -> t.id().equals(id)).isPresent()) {
            player.limparSelecao();
        }
        System.out.println("Música " + id + " removida do catálogo, das playlists e das favoritas.");
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
        Optional<Playlist> escolhida = escolherPlaylist();
        if (escolhida.isEmpty()) {
            return;
        }
        listarCatalogo();
        String id = lerLinha("Id da música a adicionar: ").strip();
        Optional<Track> musica = catalogo.findById(id);
        if (musica.isEmpty()) {
            System.out.println("Música não encontrada: " + id);
            return;
        }

        Playlist playlist = escolhida.get();
        if (playlist.adicionarFaixa(id)) {
            System.out.println("\"" + musica.get().title() + "\" adicionada a \"" + playlist.getNome() + "\".");
        } else {
            System.out.println("Essa música já está na playlist.");
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

    // ------------------------------------------------------------- favoritas

    private static void alternarFavorita() {
        listarCatalogo();
        String id = lerLinha("Id da música: ").strip();
        catalogo.findById(id).ifPresentOrElse(
                t -> System.out.println(favoritos.toggle(t)
                        ? "\"" + t.title() + "\" adicionada às favoritas."
                        : "\"" + t.title() + "\" removida das favoritas."),
                () -> System.out.println("Música não encontrada: " + id));
    }

    private static void listarFavoritas() {
        List<Track> todas = favoritos.getFavorites();
        if (todas.isEmpty()) {
            System.out.println("Nenhuma música favorita ainda.");
            return;
        }
        System.out.println("\nFavoritas (" + todas.size() + "):");
        todas.forEach(Main::imprimirMusica);
    }

    // ---------------------------------------------------------------- player

    private static void selecionarMusicaParaTocar() {
        listarCatalogo();
        String id = lerLinha("Id da música a tocar: ").strip();
        catalogo.findById(id).ifPresentOrElse(
                t -> {
                    player.selecionar(t);
                    System.out.println("Música selecionada: " + t.title() + " - " + t.artist()
                            + " (" + t.formattedDuration() + "). Use a opção 14 para tocar.");
                },
                () -> System.out.println("Música não encontrada: " + id));
    }

    private static void tocarMusica() {
        try {
            int posicao = player.getPosicaoSegundos();
            if (!player.tocar()) {
                System.out.println("A música já está tocando.");
            } else if (posicao > 0) {
                System.out.println("Retomando de " + AudioPlayerService.formatarTempo(posicao) + ".");
            } else {
                System.out.println("Tocando do início.");
            }
        } catch (IllegalStateException e) {
            System.out.println("Não foi possível tocar a faixa: " + e.getMessage());
        }
    }

    private static void pausarMusica() {
        if (player.pausar()) {
            System.out.println("Pausado em " + AudioPlayerService.formatarTempo(player.getPosicaoSegundos())
                    + ". Use a opção 14 para retomar daqui.");
        } else {
            System.out.println("Nenhuma música está tocando.");
        }
    }

    private static void pararMusica() {
        if (player.parar()) {
            System.out.println("Reprodução parada e posição reiniciada.");
        } else {
            System.out.println("Não há nada para parar.");
        }
    }

    // ------------------------------------------------------ descoberta online

    private static void pesquisarOnlineEAdicionar() {
        String termo = lerLinha("Nome da música ou do artista: ").strip();
        if (termo.isEmpty()) {
            System.out.println("Digite um nome para pesquisar.");
            return;
        }
        try {
            List<SoundCloudSearchService.SoundCloudTrack> resultados = descoberta.search(termo, RESULTADOS_ONLINE);
            if (resultados.isEmpty()) {
                System.out.println("Nenhuma música encontrada para \"" + termo + "\".");
                return;
            }
            System.out.println("\n" + resultados.size() + " resultado(s):");
            for (int i = 0; i < resultados.size(); i++) {
                SoundCloudSearchService.SoundCloudTrack resultado = resultados.get(i);
                System.out.println("  " + (i + 1) + ". " + resultado.title() + " - " + resultado.artist()
                        + " (" + AudioPlayerService.formatarTempo((int) (resultado.durationMillis() / 1000)) + ")");
                if (resultado.permalinkUrl() != null) {
                    System.out.println("     " + resultado.permalinkUrl());
                }
            }
            String texto = lerLinha("Número para adicionar ao catálogo (0 cancela): ").strip();
            int indice;
            try {
                indice = Integer.parseInt(texto);
            } catch (NumberFormatException e) {
                System.out.println("Escolha inválida. Use o número mostrado na lista.");
                return;
            }
            if (indice == 0) {
                System.out.println("Inclusão cancelada.");
                return;
            }
            if (indice < 1 || indice > resultados.size()) {
                System.out.println("Número fora da lista.");
                return;
            }
            SoundCloudSearchService.SoundCloudTrack escolhida = resultados.get(indice - 1);
            InclusaoPorPesquisa.duplicata(catalogo.getAllTracks(), escolhida.title(), escolhida.artist())
                    .ifPresentOrElse(
                            existente -> System.out.println("Essa música já está no catálogo: " + existente.id()
                                    + " — " + existente.title() + " - " + existente.artist()),
                            () -> {
                                Track nova = InclusaoPorPesquisa.criar(proximoIdDeMusica(), escolhida);
                                catalogo.addTrack(nova);
                                System.out.println("\"" + nova.title() + "\" adicionada ao catálogo (" + nova.id() + ").");
                                if (escolhida.permalinkUrl() != null) {
                                    System.out.println("Faixa original: " + escolhida.permalinkUrl());
                                }
                                System.out.println("Ao tocar, o player pede o áudio autorizado pelo SoundCloud. "
                                        + "A música não é salva no projeto.");
                            });
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.out.println(e.getMessage());
        } catch (IOException e) {
            System.out.println("Não foi possível pesquisar agora: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Pesquisa interrompida.");
        }
    }


    static String descreverResultadosSoundCloud(List<SoundCloudSearchService.SoundCloudTrack> resultados) {
        if (resultados.isEmpty()) {
            return "Nenhum resultado no SoundCloud.";
        }
        List<String> linhas = new ArrayList<>();
        linhas.add("\n" + resultados.size() + " resultado(s) no SoundCloud:");
        for (SoundCloudSearchService.SoundCloudTrack r : resultados) {
            linhas.add("  %s - %s (%s)".formatted(r.title(), r.artist(),
                    AudioPlayerService.formatarTempo((int) (r.durationMillis() / 1000))));
            if (r.permalinkUrl() != null) {
                linhas.add("    " + r.permalinkUrl());
            }
        }
        return String.join(System.lineSeparator(), linhas);
    }

    static String descreverVerificacao(MusicDiscoveryService.TrackVerification v) {
        List<String> linhas = new ArrayList<>();
        linhas.add("");
        linhas.add("Letra (LRCLIB):");
        LyricsVerificationService.VerificationResult letra = v.lyrics();
        if (letra == null) {
            linhas.add("  Sem resposta.");
        } else {
            linhas.add("  " + letra.message());
            if (letra.found()) {
                linhas.add("  Encontrada: %s - %s (%s)".formatted(letra.title(), letra.artist(),
                        AudioPlayerService.formatarTempo(letra.durationSeconds())));
                linhas.add("  Verificada: " + (letra.verified() ? "sim" : "não, correspondência aproximada"));
                linhas.add("  Letra disponível: "
                        + (letra.instrumental() ? "instrumental" : letra.plainLyrics() != null ? "sim" : "não"));
            }
        }
        linhas.add("Metadados (MusicBrainz):");
        if (!v.hasMetadataCandidates()) {
            linhas.add("  Nenhum registro encontrado.");
        }
        for (MusicMetadataVerificationService.RecordingMetadata m : v.metadataMatches()) {
            String album = m.album() == null ? "" : " [" + m.album() + "]";
            String duracao = m.durationMillis() > 0
                    ? " (" + AudioPlayerService.formatarTempo((int) (m.durationMillis() / 1000)) + ")" : "";
            linhas.add("  " + m.title() + " - " + m.artist() + album + duracao);
        }
        return String.join(System.lineSeparator(), linhas);
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
