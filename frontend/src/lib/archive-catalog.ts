import type { FreeTrack } from "@/data/free-catalog";

type SearchDoc = { identifier?: unknown; title?: unknown; creator?: unknown; licenseurl?: unknown };
type ArchiveFile = { name?: unknown; format?: unknown; title?: unknown; creator?: unknown; length?: unknown; size?: unknown };

const USER_AGENT = "SonoraAcademicPlayer/0.1 (trabalho academico; +https://github.com/jp-albano/Ativ-Met-Ageis)";

function text(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

function safeTerm(raw: string): string {
  return raw.replace(/[^\p{L}\p{N}\s-]/gu, " ").replace(/\s+/g, " ").trim().slice(0, 80);
}

function parseLength(value: unknown): number {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value !== "string") return 0;
  if (value.includes(":")) {
    const parts = value.split(":").map(Number);
    if (parts.some(part => !Number.isFinite(part))) return 0;
    return parts.reduce((total, part) => total * 60 + part, 0);
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function isUsefulFile(file: ArchiveFile): boolean {
  const name = text(file.name);
  const format = text(file.format);
  if (!name.toLowerCase().endsWith(".mp3") || !/mp3/i.test(format)) return false;
  if (/spectrogram|thumb|_files\.xml/i.test(name)) return false;
  const size = Number(text(file.size));
  if (Number.isFinite(size) && (size < 30_000 || size > 20_000_000)) return false;
  const length = parseLength(file.length);
  return length >= 45 && length <= 480;
}

async function archiveJson(url: string): Promise<unknown> {
  const response = await fetch(url, {
    headers: { Accept: "application/json", "User-Agent": USER_AGENT },
    signal: AbortSignal.timeout(12_000),
    cache: "no-store",
  });
  if (!response.ok) throw new Error(`Internet Archive respondeu ${response.status}.`);
  return response.json();
}

async function tracksForDoc(doc: SearchDoc): Promise<FreeTrack[]> {
  const identifier = text(doc.identifier);
  const license = text(doc.licenseurl);
  if (!/^[A-Za-z0-9][A-Za-z0-9._-]{0,180}$/.test(identifier)) return [];
  if (!/creativecommons\.org|publicdomain/i.test(license)) return [];
  const payload = await archiveJson(`https://archive.org/metadata/${encodeURIComponent(identifier)}`);
  const files = (payload as { files?: unknown }).files;
  if (!Array.isArray(files)) return [];
  const tracks: FreeTrack[] = [];
  for (const file of files) {
    if (!file || typeof file !== "object" || !isUsefulFile(file as ArchiveFile)) continue;
    const entry = file as ArchiveFile;
    const fileName = text(entry.name);
    const title = text(entry.title) || fileName.replace(/\.mp3$/i, "");
    const artist = (text(entry.creator) || text(doc.creator) || "Artista desconhecido").slice(0, 160);
    tracks.push({
      id: `${identifier}|${fileName}`,
      title: title.slice(0, 200),
      artist,
      durationSeconds: Math.round(parseLength(entry.length)),
      license,
      sourceUrl: `https://archive.org/details/${identifier}`,
      identifier,
      fileName,
    });
    if (tracks.length >= 2) break;
  }
  return tracks;
}

/** Busca mais faixas com licença livre. A lista pronta da interface não depende desta chamada. */
export async function searchLicensedTracks(rawQuery: string): Promise<FreeTrack[]> {
  const term = safeTerm(rawQuery);
  if (term.length < 2) return [];
  const query = `mediatype:audio AND format:MP3 AND (licenseurl:*creativecommons* OR licenseurl:*publicdomain*) AND (title:("${term}") OR creator:("${term}"))`;
  const url = `https://archive.org/advancedsearch.php?q=${encodeURIComponent(query)}&fl[]=identifier&fl[]=title&fl[]=creator&fl[]=licenseurl&rows=8&page=1&output=json`;
  const payload = await archiveJson(url);
  const docs = (payload as { response?: { docs?: unknown } }).response?.docs;
  if (!Array.isArray(docs)) return [];
  const settled = await Promise.allSettled(docs.slice(0, 8).map(doc => tracksForDoc(doc as SearchDoc)));
  const tracks: FreeTrack[] = [];
  for (const result of settled) {
    if (result.status !== "fulfilled") continue;
    tracks.push(...result.value);
    if (tracks.length >= 16) break;
  }
  return tracks.slice(0, 16);
}
