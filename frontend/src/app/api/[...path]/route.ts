import { NextResponse } from "next/server";

/**
 * Fallback de segurança para rotas da API em ambientes onde BACKEND_URL não foi configurada.
 *
 * Quando BACKEND_URL está definida, o rewrites de next.config.ts tem precedência e repassa
 * as requisições para a API Java. Quando não está configurada, este handler fornece uma resposta
 * clara com status HTTP 503 em vez de uma falha de conexão opaca ou tela quebrada.
 */
function respondUnconfigured() {
  return NextResponse.json(
    {
      error:
        "Backend Java não conectado. Para acessar os recursos de catálogo, favoritos e busca remota, configure a variável BACKEND_URL no painel do projeto Vercel apontando para a URL HTTPS da sua API.",
    },
    { status: 503 }
  );
}

export const GET = respondUnconfigured;
export const POST = respondUnconfigured;
export const PUT = respondUnconfigured;
export const DELETE = respondUnconfigured;
export const PATCH = respondUnconfigured;
