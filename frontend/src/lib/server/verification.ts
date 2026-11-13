/**
 * Serviços de verificação de letras (LRCLIB) e metadados/créditos (MusicBrainz)
 * para execução nas rotas de servidor do Next.js / Vercel.
 */

export type LyricsResult = {
  title: string;
  artist: string;
  album: string | null;
  duration: number;
  verified: boolean;
  matchType: "strong" | "approximate";
  message: string;
  plainLyrics: string | null;
  syncedLyrics: string | null;
  instrumental: boolean;
  source: string;
};

export type RecordingResult = {
  id: string;
  title: string;
  artist: string;
  album: string | null;
  length: number | null;
};

export type VerificationResult = {
  lyrics: LyricsResult | null;
  recordings: RecordingResult[];
  warnings: string[];
};

function normalize(s: string): string {
  return s
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^\w\s]/g, "")
    .trim();
}

/** Consulta LRCLIB para letras sincronizadas ou texto simples. */
export async function fetchLyrics(
  title: string,
  artist: string,
  durationSeconds = 0
): Promise<LyricsResult | null> {
  const normTitle = normalize(title);
  const normArtist = normalize(artist);

  try {
    const searchUrl = new URL("https://lrclib.net/api/search");
    searchUrl.searchParams.set("track_name", title);
    searchUrl.searchParams.set("artist_name", artist);

    const res = await fetch(searchUrl, {
      headers: { "User-Agent": "MusicCatalogWeb/1.0" },
      cache: "no-store",
    });

    if (!res.ok) return null;
    const items = (await res.json()) as Array<{
      trackName?: string;
      artistName?: string;
      albumName?: string;
      duration?: number;
      plainLyrics?: string;
      syncedLyrics?: string;
      instrumental?: boolean;
    }>;

    if (!Array.isArray(items) || items.length === 0) return null;

    let bestItem = items[0];
    let isStrong = false;

    for (const candidate of items) {
      const cTitle = normalize(candidate.trackName ?? "");
      const cArtist = normalize(candidate.artistName ?? "");
      const cDur = Math.round(candidate.duration ?? 0);

      const titleMatch = cTitle === normTitle;
      const artistMatch = cArtist === normArtist;
      const durationMatch =
        durationSeconds <= 0 || Math.abs(cDur - durationSeconds) <= 2;

      if (titleMatch && artistMatch && durationMatch) {
        bestItem = candidate;
        isStrong = true;
        break;
      }
    }

    return {
      title: bestItem.trackName ?? title,
      artist: bestItem.artistName ?? artist,
      album: bestItem.albumName ?? null,
      duration: Math.round(bestItem.duration ?? durationSeconds),
      verified: isStrong,
      matchType: isStrong ? "strong" : "approximate",
      message: isStrong
        ? "Letra confirmada com correspondência exata."
        : "Correspondência aproximada encontrada.",
      plainLyrics: bestItem.plainLyrics ?? null,
      syncedLyrics: bestItem.syncedLyrics ?? null,
      instrumental: Boolean(bestItem.instrumental),
      source: "LRCLIB",
    };
  } catch {
    return null;
  }
}

/** Consulta MusicBrainz para créditos canônicos independentes. */
export async function fetchRecordings(
  title: string,
  artist: string
): Promise<RecordingResult[]> {
  try {
    const mbUrl = new URL("https://musicbrainz.org/ws/2/recording");
    mbUrl.searchParams.set(
      "query",
      `recording:"${title.replace(/"/g, "")}" AND artist:"${artist.replace(/"/g, "")}"`
    );
    mbUrl.searchParams.set("fmt", "json");
    mbUrl.searchParams.set("limit", "5");

    const res = await fetch(mbUrl, {
      headers: { "User-Agent": "MusicCatalogAcademic/1.0 ( lucasbb2007@gmail.com )" },
      cache: "no-store",
    });

    if (!res.ok) return [];
    const data = (await res.json()) as {
      recordings?: Array<{
        id?: string;
        title?: string;
        length?: number;
        "artist-credit"?: Array<{ name?: string }>;
        releases?: Array<{ title?: string }>;
      }>;
    };

    if (!Array.isArray(data.recordings)) return [];

    return data.recordings.map((rec) => {
      const artistName = rec["artist-credit"]?.map((a) => a.name).join(" ") ?? artist;
      const albumName = rec.releases?.[0]?.title ?? null;
      return {
        id: rec.id ?? "",
        title: rec.title ?? title,
        artist: artistName,
        album: albumName,
        length: rec.length ? Number(rec.length) : null,
      };
    });
  } catch {
    return [];
  }
}

/** Reúne a verificação de letras e metadados com tolerância a falha parcial. */
export async function verifyTrack(
  title: string,
  artist: string,
  durationSeconds = 0
): Promise<VerificationResult> {
  const warnings: string[] = [];

  const [lyricsResult, recordingsResult] = await Promise.allSettled([
    fetchLyrics(title, artist, durationSeconds),
    fetchRecordings(title, artist),
  ]);

  let lyrics: LyricsResult | null = null;
  if (lyricsResult.status === "fulfilled") {
    lyrics = lyricsResult.value;
  } else {
    warnings.push("A consulta de letras não respondeu; os metadados podem continuar disponíveis.");
  }

  let recordings: RecordingResult[] = [];
  if (recordingsResult.status === "fulfilled") {
    recordings = recordingsResult.value;
  } else {
    warnings.push("A consulta MusicBrainz não respondeu; tente novamente mais tarde.");
  }

  return {
    lyrics,
    recordings,
    warnings,
  };
}
