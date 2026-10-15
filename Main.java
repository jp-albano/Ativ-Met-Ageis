package playlist;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;

/**
 * Ponto de entrada do backend.
 *
 * <p>Configuração por variáveis de ambiente (todas opcionais):
 * <ul>
 *   <li>{@code HOST} — endereço de escuta (padrão {@code 127.0.0.1}, só esta máquina)</li>
 *   <li>{@code PORT} — porta (padrão {@code 8080})</li>
 *   <li>{@code PLAYLIST_DATA} — arquivo de dados (padrão {@code data/playlists.json})</li>
 *   <li>{@code CORS_ORIGIN} — origem permitida para um frontend em outro endereço
 *       (ex.: {@code http://localhost:5500}); sem ela, nenhum cabeçalho CORS é enviado</li>
 * </ul>
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        String host = ambiente("HOST", "127.0.0.1");
        int porta = lerPorta(ambiente("PORT", "8080"));
        Path dados = Path.of(ambiente("PLAYLIST_DATA", "data/playlists.json"));
        String cors = System.getenv("CORS_ORIGIN");
        if (cors != null && cors.isBlank()) {
            cors = null;
        }

        PlaylistService servico = new PlaylistService(new FilePlaylistRepository(dados));
        HttpServer servidor = PlaylistApi.iniciar(new InetSocketAddress(host, porta), servico, cors);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> servidor.stop(1)));

        System.out.println("API de playlists em http://" + host + ":" + servidor.getAddress().getPort() + "/api/playlists");
        System.out.println("Dados em: " + dados.toAbsolutePath());
    }

    private static String ambiente(String nome, String padrao) {
        String valor = System.getenv(nome);
        return (valor == null || valor.isBlank()) ? padrao : valor.trim();
    }

    private static int lerPorta(String texto) {
        try {
            int porta = Integer.parseInt(texto);
            if (porta >= 0 && porta <= 65535) {
                return porta;
            }
        } catch (NumberFormatException e) {
            // cai na mensagem abaixo
        }
        System.err.println("PORT inválida: \"" + texto + "\" (use um número de 0 a 65535).");
        System.exit(1);
        return 0;
    }
}
