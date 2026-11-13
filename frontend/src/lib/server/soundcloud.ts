/**
 * Cliente oficial da API do SoundCloud para execução no servidor Next.js / Vercel.
 *
 * Gerencia o fluxo OAuth 2 Client Credentials no servidor, protegendo client_id e
 * client_secret, renovando o token automaticamente e mapeando metadados de reprodução.
 */

const API_BASE = "https://api.soundcloud.com";
const TOKEN_URL = "https://secure.soundcloud.com/oauth/token";
const TOKEN_REFRESH_MARGIN_MS = 60_000;
const DEFAULT_TOKEN_LIFETIME_MS = 3_600_000;
const MAX_QUERY_LENGTH = 120;
const MAX_PAGE_LIMIT = 50;

const ALLOWED_HOSTS = new Set([
  "soundcloud.com",
  "m.soundcloud.com",
  "on.soundcloud.com",
  "api.soundcloud.com",
  "api-v2.soundcloud.com",
]);

export type PlaybackState = "PLAYABLE" | "PREVIEW" | "BLOCKED";

export type SoundCloudTrack = {
  id: string;
  title: string;
  artist: string;
  artworkUrl: string | null;
  permalinkUrl: string;
  durationMillis: number;
  playbackState: PlaybackState;
  streamable: boolean;
  waveformUrl?: string | null;
};

export type PagedResult<T> = {
  tracks: T[];
  nextHref?: string | null;
};

// Cache em memória do token OAuth do processo / serverless container
let cachedToken: string | null = null;
let cachedTokenExpiresAt = 0;

export function isSoundCloudConfigured(): boolean {
  const clientId = process.env.SOUNDCLOUD_CLIENT_ID?.trim();
  const clientSecret = process.env.SOUNDCLOUD_CLIENT_SECRET?.trim();
  const staticToken = process.env.SOUNDCLOUD_ACCESS_TOKEN?.trim();
  return Boolean((clientId && clientSecret) || staticToken);
}

/**
 * Obtém o token de acesso. Com client credentials, reaproveita o token em cache
 * e o renova antes de expirar ou após um HTTP 401.
 */
async function getAccessToken(forceRefresh = false): Promise<string> {
  const clientId = process.env.SOUNDCLOUD_CLIENT_ID?.trim();
  const clientSecret = process.env.SOUNDCLOUD_CLIENT_SECRET?.trim();
  const staticToken = process.env.SOUNDCLOUD_ACCESS_TOKEN?.trim();

  if (!clientId || !clientSecret) {
    if (staticToken) return staticToken;
    throw new Error("SoundCloud não configurado no servidor.");
  }

  const now = Date.now();
  if (!forceRefresh && cachedToken && now < cachedTokenExpiresAt) {
    return cachedToken;
  }

  const credentials = Buffer.from(`${clientId}:${clientSecret}`).toString("base64");
  const response = await fetch(TOKEN_URL, {
    method: "POST",
    headers: {
      Authorization: `Basic ${credentials}`,
      "Content-Type": "application/x-www-form-urlencoded",
      Accept: "application/json; charset=utf-8",
    },
    body: "grant_type=client_credentials",
    cache: "no-store",
  });

  if (response.status === 429) {
    throw new Error("RATE_LIMIT");
  }
  if (!response.ok) {
    throw new Error("AUTH_FAILED");
  }

  const data = (await response.json()) as { access_token?: string; expires_in?: number };
  if (!data.access_token) {
    throw new Error("NO_TOKEN");
  }

  const lifetimeMs = (data.expires_in ?? 3600) * 1000;
  cachedToken = data.access_token;
  cachedTokenExpiresAt = now + Math.max(0, lifetimeMs - TOKEN_REFRESH_MARGIN_MS);
  return cachedToken;
}

/** Valida URLs externas para evitar Server-Side Request Forgery (SSRF). */
function validateExternalUri(rawUrl: string): URL {
  let url: URL;
  try {
    url = new URL(rawUrl);
  } catch {
    throw new Error("URL inválida.");
  }
  if (!["http:", "https:"].includes(url.protocol)) {
    throw new Error("Apenas URLs HTTP(S) são aceitas.");
  }
  const hostname = url.hostname.toLowerCase();
  const allowed =
    ALLOWED_HOSTS.has(hostname) ||
    Array.from(ALLOWED_HOSTS).some((h) => hostname.endsWith(`.${h}`));
  if (!allowed) {
    throw new Error("Apenas links do domínio soundcloud.com são permitidos.");
  }
  return url;
}

/** Mapeia nó JSON do SoundCloud para o modelo tipado de faixa. */
function parseTrack(item: unknown): SoundCloudTrack | null {
  if (!item || typeof item !== "object") return null;
  const raw = item as Record<string, unknown>;
  const user =
    raw.user && typeof raw.user === "object"
      ? (raw.user as Record<string, unknown>)
      : {};

  const title = typeof raw.title === "string" ? raw.title.trim() : null;
  const artist = typeof user.username === "string" ? user.username.trim() : null;
  if (!title || !artist) return null;

  const id = String(raw.urn ?? raw.id ?? "").trim();
  const permalinkUrl = typeof raw.permalink_url === "string" ? raw.permalink_url.trim() : null;
  if (!id || !permalinkUrl) return null;

  const streamable = Boolean(raw.streamable ?? true);
  const access = typeof raw.access === "string" ? raw.access.toLowerCase() : "";
  const policy = typeof raw.policy === "string" ? raw.policy.toUpperCase() : "";

  let playbackState: PlaybackState;
  if (access === "blocked" || policy === "BLOCK" || !streamable) {
    playbackState = "BLOCKED";
  } else if (access === "preview" || policy === "SNIP") {
    playbackState = "PREVIEW";
  } else {
    playbackState = "PLAYABLE";
  }

  return {
    id,
    title,
    artist,
    artworkUrl: typeof raw.artwork_url === "string" ? raw.artwork_url : null,
    permalinkUrl,
    durationMillis: Number(raw.duration ?? 0),
    playbackState,
    streamable,
    waveformUrl: typeof raw.waveform_url === "string" ? raw.waveform_url : null,
  };
}

/**
 * Envia requisição com token de autenticação, renovação após 401 e tolerância a erros.
 */
async function sendSoundCloudGet(url: URL | string): Promise<Response> {
  let token = await getAccessToken(false);
  let response = await fetch(url, {
    headers: {
      Authorization: `OAuth ${token}`,
      Accept: "application/json",
    },
    cache: "no-store",
  });

  if (response.status === 401 && process.env.SOUNDCLOUD_CLIENT_ID) {
    token = await getAccessToken(true);
    response = await fetch(url, {
      headers: {
        Authorization: `OAuth ${token}`,
        Accept: "application/json",
      },
      cache: "no-store",
    });
  }

  return response;
}

/**
 * Pesquisa faixas públicas com suporte oficial a paginação (linked_partitioning).
 */
export async function searchSoundCloudPaged(
  query: string | undefined,
  limit = 20,
  nextHref?: string | null
): Promise<PagedResult<SoundCloudTrack>> {
  if (!isSoundCloudConfigured()) {
    throw new Error("NOT_CONFIGURED");
  }

  let requestUrl: URL;
  if (nextHref && nextHref.trim()) {
    requestUrl = validateExternalUri(nextHref.trim());
  } else {
    const cleanQuery = query?.trim() ?? "";
    if (!cleanQuery) {
      throw new Error("EMPTY_QUERY");
    }
    if (cleanQuery.length > MAX_QUERY_LENGTH) {
      throw new Error("QUERY_TOO_LONG");
    }
    const safeLimit = Math.min(Math.max(1, limit), MAX_PAGE_LIMIT);
    requestUrl = new URL(`${API_BASE}/tracks`);
    requestUrl.searchParams.set("q", cleanQuery);
    requestUrl.searchParams.set("limit", String(safeLimit));
    requestUrl.searchParams.set("linked_partitioning", "true");
  }

  const response = await sendSoundCloudGet(requestUrl);

  if (response.status === 429) throw new Error("RATE_LIMIT");
  if (response.status === 401 || response.status === 403) throw new Error("AUTH_REJECTED");
  if (!response.ok) throw new Error(`UPSTREAM_ERROR_${response.status}`);

  const data = (await response.json()) as {
    collection?: unknown[];
    next_href?: string;
  };

  const rawCollection = Array.isArray(data)
    ? data
    : Array.isArray(data.collection)
    ? data.collection
    : [];

  const tracks: SoundCloudTrack[] = [];
  for (const item of rawCollection) {
    const track = parseTrack(item);
    if (track) tracks.push(track);
  }

  return {
    tracks,
    nextHref: typeof data.next_href === "string" && data.next_href.trim() ? data.next_href.trim() : null,
  };
}

/**
 * Resolve um permalink canônico do SoundCloud para a faixa correspondente.
 */
export async function resolveSoundCloudTrack(permalinkUrl: string): Promise<SoundCloudTrack | null> {
  if (!permalinkUrl?.trim()) {
    throw new Error("EMPTY_URL");
  }
  if (!isSoundCloudConfigured()) {
    throw new Error("NOT_CONFIGURED");
  }

  const validatedUrl = validateExternalUri(permalinkUrl.trim());
  const requestUrl = new URL(`${API_BASE}/resolve`);
  requestUrl.searchParams.set("url", validatedUrl.toString());

  let response = await sendSoundCloudGet(requestUrl);

  // Trata redirecionamento 301/302 para a URL final do recurso
  if (response.status === 301 || response.status === 302) {
    const location = response.headers.get("location");
    if (!location) return null;
    const targetUrl = validateExternalUri(location);
    response = await sendSoundCloudGet(targetUrl);
  }

  if (response.status === 404) return null;
  if (response.status === 429) throw new Error("RATE_LIMIT");
  if (!response.ok) throw new Error(`UPSTREAM_ERROR_${response.status}`);

  const data = await response.json();
  return parseTrack(data);
}
