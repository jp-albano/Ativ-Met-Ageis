import { NextRequest, NextResponse } from "next/server";
import { searchSoundCloudPaged, isSoundCloudConfigured } from "@/lib/server/soundcloud";

export const runtime = "nodejs";

export async function GET(request: NextRequest) {
  const query = request.nextUrl.searchParams.get("q") ?? undefined;
  const limitParam = request.nextUrl.searchParams.get("limit");
  const limit = limitParam ? Number.parseInt(limitParam, 10) : 20;
  const nextHref = request.nextUrl.searchParams.get("next_href");

  if (!nextHref && (!query || !query.trim())) {
    return NextResponse.json(
      { error: "Digite o nome de uma música ou artista." },
      { status: 400 }
    );
  }

  if (!isSoundCloudConfigured()) {
    return NextResponse.json(
      {
        error:
          "A busca está pronta, mas falta configurar as credenciais do SoundCloud (SOUNDCLOUD_CLIENT_ID e SOUNDCLOUD_CLIENT_SECRET) nas variáveis de ambiente.",
      },
      { status: 503 }
    );
  }

  try {
    const result = await searchSoundCloudPaged(query, limit, nextHref);
    return NextResponse.json(result);
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : String(err);
    if (message === "RATE_LIMIT") {
      return NextResponse.json(
        { error: "Limite de buscas atingido. Aguarde um pouco." },
        { status: 429 }
      );
    }
    if (message === "AUTH_REJECTED" || message === "AUTH_FAILED") {
      return NextResponse.json(
        { error: "O SoundCloud recusou as credenciais configuradas no servidor." },
        { status: 503 }
      );
    }
    if (message === "EMPTY_QUERY") {
      return NextResponse.json(
        { error: "Digite o nome de uma música ou artista." },
        { status: 400 }
      );
    }
    return NextResponse.json(
      { error: "O SoundCloud não conseguiu atender à busca. Tente novamente." },
      { status: 502 }
    );
  }
}
