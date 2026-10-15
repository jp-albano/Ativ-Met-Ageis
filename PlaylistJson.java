package playlist;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Conversão entre {@link Playlist} e a estrutura JSON (usada na API e no arquivo de dados). */
final class PlaylistJson {

    private PlaylistJson() {
    }

    static Map<String, Object> paraMapa(Playlist p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.id());
        m.put("nome", p.nome());
        m.put("musicas", new ArrayList<Object>(p.musicas()));
        m.put("criadaEm", p.criadaEm().toString());
        return m;
    }

    /**
     * @throws IllegalArgumentException (ou subclasse, como DateTimeParseException)
     *                                  se a estrutura não for uma playlist válida
     */
    static Playlist dePlaylist(Object objeto) {
        if (!(objeto instanceof Map<?, ?> m)) {
            throw new IllegalArgumentException("playlist deve ser um objeto");
        }
        String id = texto(m, "id");
        String nome = texto(m, "nome");
        Instant criadaEm = Instant.parse(texto(m, "criadaEm"));
        List<String> musicas = new ArrayList<>();
        Object bruto = m.get("musicas");
        if (!(bruto instanceof List<?> itens)) {
            throw new IllegalArgumentException("campo musicas deve ser uma lista");
        }
        for (Object item : itens) {
            if (!(item instanceof String s)) {
                throw new IllegalArgumentException("id de música deve ser texto");
            }
            musicas.add(s);
        }
        return new Playlist(id, nome, musicas, criadaEm);
    }

    private static String texto(Map<?, ?> m, String campo) {
        Object v = m.get(campo);
        if (!(v instanceof String s) || s.isEmpty()) {
            throw new IllegalArgumentException("campo " + campo + " ausente ou inválido");
        }
        return s;
    }
}
