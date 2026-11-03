/**
 * Cliente tipado da API Java (`src/ApiServer.java`). Contrato completo em `docs/API.md`.
 *
 * Todas as chamadas usam caminhos relativos `/api/...`: o Next.js repassa essas requisições
 * para o backend configurado em `BACKEND_URL` (veja `next.config.ts`). Assim o navegador fala
 * só com a própria origem e nenhum segredo do backend chega ao cliente.
 */

/** Faixa do catálogo local mantido pelo backend. */
export type CatalogTrack = {
  id: string;
  title: string;
  artist: string;
  durationSeconds: number;
  duration: string;
  favorite: boolean;
};

export type PlaylistSummary = { id: number; name: string; trackCount: number };
export type Playlist = PlaylistSummary & { tracks: CatalogTrack[]; missingTrackIds: string[] };

/** Resultado de busca no SoundCloud. */
export type DiscoveryTrack = {
  id: string;
  title: string;
  artist: string;
  artworkUrl: string | null;
  permalinkUrl: string;
  durationMillis: number;
};

export type Lyrics = {
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

export type Recording = { id: string; title: string; artist: string; album: string | null; length: number | null };
export type Verification = { lyrics: Lyrics | null; recordings: Recording[]; warnings: string[] };
export type Health = { status: string; soundCloudConfigured: boolean };

/** Erro com mensagem já pronta para exibir ao usuário. */
export class ApiError extends Error {
  constructor(message: string, readonly status: number) {
    super(message);
    this.name = "ApiError";
  }
}

const OFFLINE_MESSAGE =
  "Não foi possível falar com o servidor da API. Confira se o backend Java está rodando (veja o README).";

/**
 * Faz a requisição e devolve o JSON tipado.
 * Respostas que não são JSON (por exemplo, proxy sem backend) viram uma mensagem amigável.
 */
async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(path, {
      ...init,
      headers: init?.body ? { "Content-Type": "application/json", ...init.headers } : init?.headers,
    });
  } catch {
    throw new ApiError(OFFLINE_MESSAGE, 0);
  }
  if (response.status === 204) return undefined as T;

  let data: unknown;
  try {
    data = await response.json();
  } catch {
    throw new ApiError(response.ok ? "Resposta inválida do servidor." : OFFLINE_MESSAGE, response.status);
  }
  if (!response.ok) {
    const message = (data as { error?: unknown })?.error;
    throw new ApiError(typeof message === "string" ? message : "A requisição falhou.", response.status);
  }
  return data as T;
}

const q = encodeURIComponent;

export const api = {
  health: () => request<Health>("/api/health"),

  listTracks: (term?: string) =>
    request<{ tracks: CatalogTrack[] }>(term ? `/api/tracks?q=${q(term)}` : "/api/tracks").then(r => r.tracks),
  addTrack: (title: string, artist: string, durationSeconds: number) =>
    request<CatalogTrack>("/api/tracks", { method: "POST", body: JSON.stringify({ title, artist, durationSeconds }) }),
  removeTrack: (id: string) => request<void>(`/api/tracks/${q(id)}`, { method: "DELETE" }),

  listFavorites: () => request<{ tracks: CatalogTrack[] }>("/api/favorites").then(r => r.tracks),
  setFavorite: (trackId: string, favorite: boolean) =>
    request<{ trackId: string; favorite: boolean; changed: boolean }>(`/api/favorites/${q(trackId)}`, {
      method: favorite ? "PUT" : "DELETE",
    }),

  listPlaylists: () => request<{ playlists: PlaylistSummary[] }>("/api/playlists").then(r => r.playlists),
  getPlaylist: (id: number) => request<Playlist>(`/api/playlists/${id}`),
  createPlaylist: (name: string) =>
    request<Playlist>("/api/playlists", { method: "POST", body: JSON.stringify({ name }) }),
  deletePlaylist: (id: number) => request<void>(`/api/playlists/${id}`, { method: "DELETE" }),
  setPlaylistTrack: (playlistId: number, trackId: string, present: boolean) =>
    request<{ changed: boolean; playlist: Playlist }>(`/api/playlists/${playlistId}/tracks/${q(trackId)}`, {
      method: present ? "PUT" : "DELETE",
    }).then(r => r.playlist),

  searchDiscovery: (query: string, limit = 20) =>
    request<{ tracks: DiscoveryTrack[] }>(`/api/discovery/search?q=${q(query)}&limit=${limit}`).then(r => r.tracks),
  verifyTrack: (title: string, artist: string, durationSeconds: number) =>
    request<Verification>(
      `/api/discovery/verify?title=${q(title)}&artist=${q(artist)}&duration=${Math.round(durationSeconds)}`,
    ),
};
