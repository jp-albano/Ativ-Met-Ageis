package playlist;

import static playlist.Verifica.caso;
import static playlist.Verifica.igual;
import static playlist.Verifica.lanca;
import static playlist.Verifica.verdade;

import java.util.List;
import java.util.Map;

final class JsonTest {

    private JsonTest() {
    }

    static void rodar() {
        caso("Json: lê objeto, lista, números, booleanos e null", () -> {
            Object v = Json.parse(" {\"a\": [1, -2.5, true, false, null, \"x\"], \"b\": {}} ");
            Map<?, ?> m = (Map<?, ?>) v;
            List<?> a = (List<?>) m.get("a");
            igual(1L, a.get(0));
            igual(-2.5, a.get(1));
            igual(true, a.get(2));
            igual(false, a.get(3));
            igual(null, a.get(4));
            igual("x", a.get(5));
            verdade(((Map<?, ?>) m.get("b")).isEmpty(), "b deveria ser objeto vazio");
        });

        caso("Json: escapes e unicode", () -> {
            igual("a\"b\\c/d\n\t", Json.parse("\"a\\\"b\\\\c\\/d\\n\\t\""));
            igual("é", Json.parse("\"\\u00e9\""));
            igual("😀", Json.parse("\"\\ud83d\\ude00\""));
        });

        caso("Json: escreve e relê com escapes e controles", () -> {
            String original = "linha1\nlinha2\t\"aspas\" \\ \u0001 é 😀";
            String json = Json.write(Map.of("t", original));
            verdade(!json.contains("\n") && !json.contains("\u0001"), "controles devem sair escapados");
            igual(original, ((Map<?, ?>) Json.parse(json)).get("t"));
        });

        caso("Json: writePretty gera JSON legível que pode ser relido", () -> {
            String pretty = Json.writePretty(List.of(Map.of("a", 1), List.of()));
            verdade(pretty.contains("\n  "), "deveria ter indentação");
            igual(2, ((List<?>) Json.parse(pretty)).size());
        });

        caso("Json: rejeita entradas inválidas", () -> {
            String[] ruins = {
                "", "   ", "{", "[1,]", "{\"a\":1,}", "{'a':1}", "{\"a\" 1}", "01", "1.", "-",
                "\"abc", "\"a\nb\"", "\"\\x\"", "\"\\u12\"", "\"\\uZZZZ\"", "tru", "nulo",
                "{} x", "[1] [2]", "{\"a\":}"
            };
            for (String ruim : ruins) {
                try {
                    Json.parse(ruim);
                    throw new AssertionError("deveria rejeitar: <" + ruim + ">");
                } catch (JsonException esperado) {
                    // ok
                }
            }
        });

        caso("Json: rejeita aninhamento excessivo sem estourar a pilha", () ->
                lanca(JsonException.class, () -> Json.parse("[".repeat(10_000) + "]".repeat(10_000))));

        caso("Json: não aceita tipo desconhecido na escrita", () ->
                lanca(IllegalArgumentException.class, () -> Json.write(new Object())));
    }
}
