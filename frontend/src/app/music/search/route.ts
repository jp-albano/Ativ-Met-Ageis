import { NextResponse } from "next/server";
import { searchLicensedTracks } from "@/lib/archive-catalog";

export const dynamic = "force-dynamic";

/** Completa a busca do catálogo livre. O catálogo inicial já vem no cliente, sem esta rota. */
export async function GET(request: Request) {
  const query = new URL(request.url).searchParams.get("q") ?? "";
  try {
    return NextResponse.json({ tracks: await searchLicensedTracks(query) });
  } catch {
    return NextResponse.json({ error: "Não foi possível ampliar a busca no acervo livre agora." }, { status: 502 });
  }
}
