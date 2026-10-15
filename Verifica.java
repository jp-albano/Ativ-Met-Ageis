package playlist;

import java.util.Objects;

/** Mini-framework de testes (sem dependências): casos, contagem e asserções. */
final class Verifica {

    interface Teste {
        void rodar() throws Exception;
    }

    static int total;
    static int falhas;

    private Verifica() {
    }

    static void caso(String nome, Teste teste) {
        total++;
        try {
            teste.rodar();
            System.out.println("OK    " + nome);
        } catch (Throwable e) {
            falhas++;
            System.out.println("FALHA " + nome + "\n      " + e);
        }
    }

    static void igual(Object esperado, Object atual) {
        if (!Objects.equals(esperado, atual)) {
            throw new AssertionError("esperado <" + esperado + "> mas foi <" + atual + ">");
        }
    }

    static void verdade(boolean condicao, String mensagem) {
        if (!condicao) {
            throw new AssertionError(mensagem);
        }
    }

    static <T extends Throwable> T lanca(Class<T> tipo, Teste teste) {
        try {
            teste.rodar();
        } catch (Throwable e) {
            if (tipo.isInstance(e)) {
                return tipo.cast(e);
            }
            throw new AssertionError("esperava " + tipo.getSimpleName() + " mas lançou " + e);
        }
        throw new AssertionError("esperava " + tipo.getSimpleName() + " mas nada foi lançado");
    }
}
