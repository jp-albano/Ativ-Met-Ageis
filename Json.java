package playlist;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Leitor e escritor de JSON mínimo, sem dependências externas.
 *
 * <p>Mapeamento: objeto ↔ {@code Map<String,Object>}, array ↔ {@code List<Object>},
 * texto ↔ {@code String}, inteiro ↔ {@code Long}, decimal ↔ {@code Double},
 * {@code Boolean} e {@code null}. O leitor é estrito (rejeita vírgula sobrando,
 * aspas simples e conteúdo depois do JSON) e limita o aninhamento a
 * {@value #PROFUNDIDADE_MAXIMA} níveis para evitar estouro de pilha com entrada maliciosa.
 */
public final class Json {

    private static final int PROFUNDIDADE_MAXIMA = 64;

    private Json() {
    }

    /** @throws JsonException se o texto não for JSON válido */
    public static Object parse(String texto) {
        if (texto == null) {
            throw new JsonException("JSON ausente", 0);
        }
        Parser p = new Parser(texto);
        p.pular();
        Object valor = p.valor(0);
        p.pular();
        if (p.i != texto.length()) {
            throw p.erro("conteúdo extra depois do JSON");
        }
        return valor;
    }

    /** Serializa em uma linha. */
    public static String write(Object valor) {
        StringBuilder sb = new StringBuilder();
        escrever(sb, valor, -1, 0);
        return sb.toString();
    }

    /** Serializa com indentação de 2 espaços (útil para arquivos legíveis). */
    public static String writePretty(Object valor) {
        StringBuilder sb = new StringBuilder();
        escrever(sb, valor, 2, 0);
        return sb.append('\n').toString();
    }

    // ------------------------------------------------------------------ escrita

    private static void escrever(StringBuilder sb, Object v, int indent, int nivel) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String s) {
            string(sb, s);
        } else if (v instanceof Boolean || v instanceof Integer || v instanceof Long) {
            sb.append(v);
        } else if (v instanceof Number n) {
            double d = n.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new IllegalArgumentException("Número não representável em JSON");
            }
            sb.append(v);
        } else if (v instanceof Map<?, ?> mapa) {
            if (mapa.isEmpty()) {
                sb.append("{}");
                return;
            }
            sb.append('{');
            boolean primeiro = true;
            for (Map.Entry<?, ?> e : mapa.entrySet()) {
                if (!(e.getKey() instanceof String chave)) {
                    throw new IllegalArgumentException("Chave de objeto JSON deve ser texto");
                }
                if (!primeiro) {
                    sb.append(',');
                }
                primeiro = false;
                quebra(sb, indent, nivel + 1);
                string(sb, chave);
                sb.append(indent < 0 ? ":" : ": ");
                escrever(sb, e.getValue(), indent, nivel + 1);
            }
            quebra(sb, indent, nivel);
            sb.append('}');
        } else if (v instanceof List<?> lista) {
            if (lista.isEmpty()) {
                sb.append("[]");
                return;
            }
            sb.append('[');
            boolean primeiro = true;
            for (Object item : lista) {
                if (!primeiro) {
                    sb.append(',');
                }
                primeiro = false;
                quebra(sb, indent, nivel + 1);
                escrever(sb, item, indent, nivel + 1);
            }
            quebra(sb, indent, nivel);
            sb.append(']');
        } else {
            throw new IllegalArgumentException("Tipo não suportado em JSON: " + v.getClass().getName());
        }
    }

    private static void quebra(StringBuilder sb, int indent, int nivel) {
        if (indent >= 0) {
            sb.append('\n');
            sb.append(" ".repeat(indent * nivel));
        }
    }

    private static void string(StringBuilder sb, String s) {
        sb.append('"');
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    // ------------------------------------------------------------------ leitura

    private static final class Parser {
        final String s;
        int i;

        Parser(String s) {
            this.s = s;
        }

        JsonException erro(String mensagem) {
            return new JsonException(mensagem, i);
        }

        void pular() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    i++;
                } else {
                    break;
                }
            }
        }

        Object valor(int profundidade) {
            if (profundidade > PROFUNDIDADE_MAXIMA) {
                throw erro("aninhamento excessivo");
            }
            if (i >= s.length()) {
                throw erro("fim inesperado");
            }
            char c = s.charAt(i);
            switch (c) {
                case '{':
                    return objeto(profundidade);
                case '[':
                    return lista(profundidade);
                case '"':
                    return string();
                case 't':
                    return literal("true", Boolean.TRUE);
                case 'f':
                    return literal("false", Boolean.FALSE);
                case 'n':
                    return literal("null", null);
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        return numero();
                    }
                    throw erro("caractere inesperado '" + c + "'");
            }
        }

        Object literal(String palavra, Object valor) {
            if (s.startsWith(palavra, i)) {
                i += palavra.length();
                return valor;
            }
            throw erro("valor inválido");
        }

        Map<String, Object> objeto(int profundidade) {
            Map<String, Object> mapa = new LinkedHashMap<>();
            i++; // '{'
            pular();
            if (i < s.length() && s.charAt(i) == '}') {
                i++;
                return mapa;
            }
            while (true) {
                pular();
                if (i >= s.length() || s.charAt(i) != '"') {
                    throw erro("esperava o nome de um campo entre aspas");
                }
                String chave = string();
                pular();
                if (i >= s.length() || s.charAt(i) != ':') {
                    throw erro("esperava ':'");
                }
                i++;
                pular();
                mapa.put(chave, valor(profundidade + 1));
                pular();
                if (i < s.length() && s.charAt(i) == ',') {
                    i++;
                } else if (i < s.length() && s.charAt(i) == '}') {
                    i++;
                    return mapa;
                } else {
                    throw erro("esperava ',' ou '}'");
                }
            }
        }

        List<Object> lista(int profundidade) {
            List<Object> itens = new ArrayList<>();
            i++; // '['
            pular();
            if (i < s.length() && s.charAt(i) == ']') {
                i++;
                return itens;
            }
            while (true) {
                pular();
                itens.add(valor(profundidade + 1));
                pular();
                if (i < s.length() && s.charAt(i) == ',') {
                    i++;
                } else if (i < s.length() && s.charAt(i) == ']') {
                    i++;
                    return itens;
                } else {
                    throw erro("esperava ',' ou ']'");
                }
            }
        }

        String string() {
            i++; // aspas de abertura
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (i >= s.length()) {
                    throw erro("texto sem aspas de fechamento");
                }
                char c = s.charAt(i++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c < 0x20) {
                    throw erro("caractere de controle dentro de texto");
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                if (i >= s.length()) {
                    throw erro("escape incompleto");
                }
                char e = s.charAt(i++);
                switch (e) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (i + 4 > s.length()) {
                            throw erro("escape \\u incompleto");
                        }
                        int codigo = 0;
                        for (int k = 0; k < 4; k++) {
                            int d = Character.digit(s.charAt(i + k), 16);
                            if (d < 0) {
                                throw erro("escape \\u inválido");
                            }
                            codigo = codigo * 16 + d;
                        }
                        i += 4;
                        sb.append((char) codigo);
                    }
                    default -> throw erro("escape inválido '\\" + e + "'");
                }
            }
        }

        boolean digito() {
            return i < s.length() && s.charAt(i) >= '0' && s.charAt(i) <= '9';
        }

        Object numero() {
            int inicio = i;
            if (s.charAt(i) == '-') {
                i++;
            }
            if (i < s.length() && s.charAt(i) == '0') {
                i++;
            } else if (i < s.length() && s.charAt(i) >= '1' && s.charAt(i) <= '9') {
                while (digito()) {
                    i++;
                }
            } else {
                throw erro("número inválido");
            }
            boolean inteiro = true;
            if (i < s.length() && s.charAt(i) == '.') {
                inteiro = false;
                i++;
                int ini = i;
                while (digito()) {
                    i++;
                }
                if (i == ini) {
                    throw erro("número inválido");
                }
            }
            if (i < s.length() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
                inteiro = false;
                i++;
                if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
                    i++;
                }
                int ini = i;
                while (digito()) {
                    i++;
                }
                if (i == ini) {
                    throw erro("número inválido");
                }
            }
            String texto = s.substring(inicio, i);
            if (inteiro) {
                try {
                    return Long.parseLong(texto);
                } catch (NumberFormatException e) {
                    // grande demais para long: cai para double
                }
            }
            return Double.parseDouble(texto);
        }
    }
}
