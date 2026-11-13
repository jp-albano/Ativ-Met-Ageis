# Contrato da API HTTP

Implementação: [`src/ApiServer.java`](../src/ApiServer.java). Decisão: [ADR-0002](architecture/decisions/ADR-0002-api-http-java-frontend-next.md).
Cliente tipado do frontend: [`frontend/src/lib/api.ts`](../frontend/src/lib/api.ts).

## Convenções

- Base: `http://127.0.0.1:8080/api` (configurável por `API_HOST`/`API_PORT`). No frontend, use caminhos relativos `/api/...`: o Next.js repassa para o backend.
- Corpo e respostas em JSON UTF-8. Corpo máximo de 16 KB (acima disso: `413`).
- Erros sempre no formato `{"error": "mensagem para exibir ao usuário"}`.
- `405` inclui o cabeçalho `Allow` com os métodos aceitos. Rotas inexistentes: `404`.
- CORS só para as origens em `API_ALLOWED_ORIGINS` (padrão `http://localhost:3000`, `http://127.0.0.1:3000`).
- Estado em memória, sem autenticação. Não exponha a API publicamente.

## Modelos

```ts
Track            = { id: string, title: string, artist: string, durationSeconds: number, duration: "mm:ss", favorite: boolean }
PlaylistSummary  = { id: number, name: string, trackCount: number }
Playlist         = PlaylistSummary & { tracks: Track[], missingTrackIds: string[] }
PlaybackState    = "PLAYABLE" | "PREVIEW" | "BLOCKED"
DiscoveryTrack   = {
  id: string,
  title: string,
  artist: string,
  artworkUrl: string|null,
  permalinkUrl: string,
  durationMillis: number,
  playbackState?: PlaybackState,
  streamable?: boolean,
  waveformUrl?: string|null
}
DiscoveryPage    = { tracks: DiscoveryTrack[], nextHref?: string }
Verification     = { lyrics: Lyrics|null, recordings: Recording[], warnings: string[] }
Lyrics           = { title, artist, album|null, duration, verified, matchType: "strong"|"approximate", message, plainLyrics|null, syncedLyrics|null, instrumental, source }
Recording        = { id, title, artist, album|null, length: number|null /* ms */ }
```

## Rotas

| Método | Rota | Corpo | Resposta |
|---|---|---|---|
| GET | `/api/health` | — | `{status: "ok", soundCloudConfigured: boolean}` |
| GET | `/api/tracks?q=` | — | `{tracks: Track[]}` (`q` opcional filtra por título/artista) |
| POST | `/api/tracks` | `{title, artist, durationSeconds}` | `201 Track` · `400` validação |
| GET | `/api/tracks/{id}` | — | `Track` · `404` |
| DELETE | `/api/tracks/{id}` | — | `204` (remove também de favoritos e playlists) · `404` |
| GET | `/api/favorites` | — | `{tracks: Track[]}` |
| PUT / DELETE | `/api/favorites/{trackId}` | — | `{trackId, favorite, changed}` · `404` faixa inexistente |
| GET | `/api/playlists` | — | `{playlists: PlaylistSummary[]}` |
| POST | `/api/playlists` | `{name}` | `201 Playlist` |
| GET / DELETE | `/api/playlists/{id}` | — | `Playlist` / `204` · `404` |
| PUT / DELETE | `/api/playlists/{id}/tracks/{trackId}` | — | `{changed, playlist: Playlist}` · `404` playlist ou faixa |
| GET | `/api/discovery/search?q=&limit=&next_href=` | — | `DiscoveryPage` (`limit` 1–50, padrão 20; `next_href` para próxima página) |
| GET | `/api/discovery/resolve?url=` | — | `DiscoveryTrack` (`url` permalink canônico do SoundCloud) · `400`/`404` |
| GET | `/api/discovery/verify?title=&artist=&duration=` | — | `Verification` (`duration` em segundos, opcional) |

### Status específicos da descoberta

- `503`: SoundCloud sem credencial configurada, credencial recusada (401/403 do provedor) ou excesso de consultas simultâneas.
- `429`: limite de requisições do SoundCloud atingido.
- `502`: falha ou indisponibilidade do provedor.
- Na verificação, a falha de uma fonte vira item em `warnings` sem apagar o resultado da outra.
- Na resolução (`/resolve`), URLs que não sejam do domínio `soundcloud.com` ou de faixas inexistentes retornam `400` ou `404`.
