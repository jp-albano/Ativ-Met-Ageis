"use client";

import Image from "next/image";
import { FormEvent, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { licenseLabel, readyCatalog, streamUrl, type FreeTrack } from "@/data/free-catalog";
import { api, ApiError, type CatalogTrack, type DiscoveryTrack, type Playlist, type PlaylistSummary, type Verification } from "@/lib/api";
import { clampDuration, coverColor, formatTime, titleFromFileName } from "@/lib/format";
import { clearCatalogLink, listLocalTracks, readDuration, removeByCatalogId, removeLocalTrack, saveLocalAudio, type LocalAudio } from "@/lib/local-audio";
import { applyIfCurrent, createRequestTracker } from "@/lib/request-tracking";
import { PlayerProvider, usePlayer, type PlayableTrack } from "@/components/player-provider";
import { Icon, type IconName } from "@/components/icons";

type View = "home" | "search" | "library" | "liked" | { kind: "playlist"; id: number };

/**
 * Situação da API Java mostrada no selo do topo.
 * "checking" existe para não anunciar "fora do ar" antes da primeira resposta.
 */
type ApiStatus = "checking" | "online" | "offline";

/** Atalhos da busca: o rótulo é o que aparece; o termo é o que vai para a busca (o acervo está em inglês). */
const SEARCH_CHIPS: { label: string; term: string }[] = [
  { label: "Tudo", term: "" },
  { label: "Piano", term: "piano" },
  { label: "Blues", term: "blues" },
  { label: "Bossa", term: "bossa" },
  { label: "Violão", term: "guitar" },
  { label: "Ambiente", term: "ambient" },
];

const errorMessage = (error: unknown, fallback: string) => (error instanceof Error ? error.message : fallback);

function freeToPlayable(track: FreeTrack): PlayableTrack {
  return {
    audioId: track.id,
    title: track.title,
    artist: track.artist,
    durationSeconds: track.durationSeconds,
    streamUrl: streamUrl(track),
  };
}

function toPlayable(track: LocalAudio): PlayableTrack {
  return {
    audioId: track.id,
    title: track.title,
    artist: track.artist,
    durationSeconds: track.durationSeconds,
    catalogId: track.catalogId,
  };
}

export function SonoraApp() {
  return (
    <PlayerProvider>
      <Shell />
    </PlayerProvider>
  );
}

function Shell() {
  const player = usePlayer();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const attachRef = useRef<{ catalogId: string; title: string; artist: string } | null>(null);

  const [view, setView] = useState<View>("home");
  const [localTracks, setLocalTracks] = useState<LocalAudio[]>([]);
  const [catalog, setCatalog] = useState<CatalogTrack[]>([]);
  const [favorites, setFavorites] = useState<CatalogTrack[]>([]);
  const [playlists, setPlaylists] = useState<PlaylistSummary[]>([]);
  const [playlist, setPlaylist] = useState<Playlist | null>(null);
  const [apiNotice, setApiNotice] = useState("");
  const [apiStatus, setApiStatus] = useState<ApiStatus>("checking");
  const [actionError, setActionError] = useState("");
  const [busy, setBusy] = useState("");
  const [playlistName, setPlaylistName] = useState("");
  const [showQueue, setShowQueue] = useState(false);

  const playlistId = typeof view === "object" ? view.id : null;

  const reloadLocal = useCallback(async () => {
    setLocalTracks(await listLocalTracks());
  }, []);

  const reloadApi = useCallback(async () => {
    try {
      const [tracks, liked, lists] = await Promise.all([api.listTracks(), api.listFavorites(), api.listPlaylists()]);
      setCatalog(tracks);
      setFavorites(liked);
      setPlaylists(lists);
      setApiNotice("");
      setApiStatus("online");
    } catch (error) {
      setApiNotice(errorMessage(error, "A API Java não respondeu. Dá para tocar os áudios já importados neste navegador."));
      setApiStatus("offline");
    }
  }, []);

  const reloadPlaylist = useCallback(async (id: number) => {
    try {
      setPlaylist(await api.getPlaylist(id));
    } catch (error) {
      setActionError(errorMessage(error, "Não foi possível abrir a playlist."));
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        const tracks = await listLocalTracks();
        if (!cancelled) setLocalTracks(tracks);
      } catch {
        if (!cancelled) setActionError("Este navegador bloqueou o armazenamento local de áudio.");
      }
      try {
        const [tracks, liked, lists] = await Promise.all([api.listTracks(), api.listFavorites(), api.listPlaylists()]);
        if (cancelled) return;
        setCatalog(tracks);
        setFavorites(liked);
        setPlaylists(lists);
        setApiStatus("online");
      } catch (error) {
        if (cancelled) return;
        setApiNotice(errorMessage(error, "A API Java não respondeu. Dá para tocar os áudios já importados neste navegador."));
        setApiStatus("offline");
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (playlistId === null) return;
    let cancelled = false;
    api.getPlaylist(playlistId).then(data => {
      if (!cancelled) setPlaylist(data);
    }).catch(error => {
      if (!cancelled) setActionError(errorMessage(error, "Não foi possível abrir a playlist."));
    });
    return () => {
      cancelled = true;
    };
  }, [playlistId]);

  const audioByCatalog = useMemo(() => {
    const map = new Map<string, LocalAudio>();
    for (const track of localTracks) {
      if (track.catalogId) map.set(track.catalogId, track);
    }
    return map;
  }, [localTracks]);

  function openImport() {
    attachRef.current = null;
    fileInputRef.current?.click();
  }

  function openAttach(track: CatalogTrack) {
    attachRef.current = { catalogId: track.id, title: track.title, artist: track.artist };
    fileInputRef.current?.click();
  }

  async function onFiles(files: FileList | null) {
    if (!files?.length) return;
    const target = attachRef.current;
    attachRef.current = null;
    const selected = target ? [files[0]] : Array.from(files);
    const problems: string[] = [];
    setBusy("Importando áudios…");
    for (const file of selected) {
      try {
        const guessed = titleFromFileName(file.name);
        const title = (target?.title || guessed.title).slice(0, 200);
        const artist = (target?.artist || guessed.artist).slice(0, 160);
        const durationSeconds = clampDuration(await readDuration(file));
        let catalogId = target?.catalogId;
        if (!catalogId) {
          try {
            catalogId = (await api.addTrack(title, artist, durationSeconds)).id;
          } catch (error) {
            if (!(error instanceof ApiError)) throw error;
          }
        }
        if (catalogId) await removeByCatalogId(catalogId);
        await saveLocalAudio({ file, title, artist, durationSeconds, catalogId });
      } catch (error) {
        problems.push(errorMessage(error, `Falha ao importar ${file.name}.`));
      }
    }
    if (fileInputRef.current) fileInputRef.current.value = "";
    setBusy("");
    setActionError(problems.join(" "));
    await reloadLocal();
    await reloadApi();
  }

  function playFree(list: FreeTrack[], track: FreeTrack) {
    const queue = list.map(freeToPlayable);
    const start = Math.max(0, queue.findIndex(item => item.audioId === track.id));
    if (player.current?.audioId === track.id) player.toggle();
    else player.playQueue(queue, start);
  }

  /**
   * Botão "Aleatório": liga o modo aleatório do player (se estiver desligado)
   * e começa por uma faixa sorteada, para a primeira música também variar.
   */
  function playFreeShuffled(list: FreeTrack[]) {
    if (list.length === 0) return;
    if (!player.shuffle) player.toggleShuffle();
    player.playQueue(list.map(freeToPlayable), Math.floor(Math.random() * list.length));
  }

  /** Mesmo comportamento de {@link playFreeShuffled}, para faixas do catálogo da API com áudio local. */
  function playCatalogShuffled(list: CatalogTrack[]) {
    const playable = list.flatMap(item => {
      const local = audioByCatalog.get(item.id);
      return local ? [toPlayable(local)] : [];
    });
    if (playable.length === 0) {
      setActionError("Nenhuma faixa desta lista tem áudio neste navegador. Use “Anexar áudio” para escolher o arquivo.");
      return;
    }
    if (!player.shuffle) player.toggleShuffle();
    player.playQueue(playable, Math.floor(Math.random() * playable.length));
  }

  function playLocal(track: LocalAudio, list: LocalAudio[]) {
    const playable = list.map(toPlayable);
    const start = Math.max(0, playable.findIndex(item => item.audioId === track.id));
    if (player.current?.audioId === track.id) player.toggle();
    else player.playQueue(playable, start);
  }

  function playCatalogList(list: CatalogTrack[], startId: string) {
    const playable = list.flatMap(item => {
      const local = audioByCatalog.get(item.id);
      return local ? [toPlayable(local)] : [];
    });
    const start = playable.findIndex(item => item.catalogId === startId);
    if (start < 0) {
      setActionError("Esta faixa ainda não tem áudio neste navegador. Use “Anexar áudio” e escolha o arquivo no computador.");
      return;
    }
    if (player.current?.catalogId === startId) player.toggle();
    else player.playQueue(playable, start);
  }

  async function toggleFavorite(track: CatalogTrack) {
    try {
      await api.setFavorite(track.id, !track.favorite);
      setActionError("");
      await reloadApi();
      if (playlistId !== null) await reloadPlaylist(playlistId);
    } catch (error) {
      setActionError(errorMessage(error, "Não foi possível atualizar o favorito."));
    }
  }

  async function createPlaylist(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const name = playlistName.trim();
    if (!name) return;
    try {
      const created = await api.createPlaylist(name);
      setPlaylistName("");
      setActionError("");
      await reloadApi();
      setView({ kind: "playlist", id: created.id });
    } catch (error) {
      setActionError(errorMessage(error, "Não foi possível criar a playlist."));
    }
  }

  async function addToPlaylist(playlistTarget: number, trackId: string) {
    try {
      await api.setPlaylistTrack(playlistTarget, trackId, true);
      setActionError("");
      if (playlistId === playlistTarget) await reloadPlaylist(playlistTarget);
      await reloadApi();
    } catch (error) {
      setActionError(errorMessage(error, "Não foi possível adicionar a faixa à playlist."));
    }
  }

  async function removeFromPlaylist(trackId: string) {
    if (playlistId === null) return;
    try {
      await api.setPlaylistTrack(playlistId, trackId, false);
      await reloadPlaylist(playlistId);
      await reloadApi();
    } catch (error) {
      setActionError(errorMessage(error, "Não foi possível tirar a faixa da playlist."));
    }
  }

  async function deletePlaylist(id: number) {
    try {
      await api.deletePlaylist(id);
      setView("library");
      setPlaylist(null);
      await reloadApi();
    } catch (error) {
      setActionError(errorMessage(error, "Não foi possível apagar a playlist."));
    }
  }

  async function deleteCatalogTrack(track: CatalogTrack) {
    try {
      await api.removeTrack(track.id);
      await clearCatalogLink(track.id);
      await reloadLocal();
      await reloadApi();
      if (playlistId !== null) await reloadPlaylist(playlistId);
    } catch (error) {
      setActionError(errorMessage(error, "Não foi possível remover a faixa."));
    }
  }

  async function deleteLocal(track: LocalAudio) {
    await removeLocalTrack(track.id);
    if (player.current?.audioId === track.id) player.clearQueue();
    await reloadLocal();
  }

  const notice = actionError || player.error || apiNotice;

  return (
    <div className={showQueue ? "app with-queue" : "app"}>
      <a className="skip" href="#conteudo">Ir para o conteúdo</a>
      <input
        ref={fileInputRef}
        className="file-input"
        type="file"
        accept="audio/*,.mp3,.wav,.ogg,.m4a,.aac,.flac,.aiff,.aif,.au"
        multiple
        aria-hidden="true"
        tabIndex={-1}
        onChange={event => void onFiles(event.target.files)}
      />

      <aside className="sidebar">
        <button className="brand" type="button" onClick={() => setView("home")}>
          <span className="brand-mark" aria-hidden="true"><i /><i /><i /></span>
          sonora<span className="brand-dot">.</span>
        </button>
        <nav className="side-nav" aria-label="Seções">
          <SideButton icon="home" active={view === "home"} onClick={() => setView("home")}>Início</SideButton>
          <SideButton icon="search" active={view === "search"} onClick={() => setView("search")}>Buscar</SideButton>
          <SideButton icon="library" active={view === "library"} onClick={() => setView("library")}>Biblioteca</SideButton>
          <SideButton icon="heart" active={view === "liked"} onClick={() => setView("liked")}>Favoritas</SideButton>
        </nav>
        <div className="side-label">Playlists</div>
        <div className="playlist-scroll">
          {playlists.length === 0 && <p className="side-empty">{apiStatus === "offline" ? "Playlists ficam na API, que está fora do ar." : "Nenhuma playlist ainda."}</p>}
          {playlists.map(item => (
            <button
              key={item.id}
              type="button"
              className={playlistId === item.id ? "playlist-link active" : "playlist-link"}
              aria-current={playlistId === item.id ? "page" : undefined}
              onClick={() => setView({ kind: "playlist", id: item.id })}
            >
              <span>{item.name}</span>
              <small aria-label={`${item.trackCount} faixas`}>{item.trackCount}</small>
            </button>
          ))}
        </div>
        <form className="playlist-form" onSubmit={event => void createPlaylist(event)}>
          <label htmlFor="playlist-name">Nova playlist</label>
          <div>
            <input id="playlist-name" maxLength={120} value={playlistName} onChange={event => setPlaylistName(event.target.value)} placeholder="Nome da playlist" />
            <button type="submit" disabled={!playlistName.trim()} aria-label="Criar playlist"><Icon name="plus" size={18} /></button>
          </div>
        </form>
        <button className="import-button" type="button" onClick={openImport}>
          <Icon name="upload" size={18} />
          Importar do computador
        </button>
      </aside>

      <div className="content" id="conteudo">
        <header className="topbar">
          <h1>{titleFor(view, playlist)}</h1>
          <ApiStatusChip status={apiStatus} />
        </header>
        {notice && (
          <div className="notice" role="status">
            <p>{notice}</p>
            <button type="button" onClick={() => { setActionError(""); setApiNotice(""); player.dismissError(); }}>Fechar</button>
          </div>
        )}
        {busy && <p className="busy" role="status">{busy}</p>}

        {view === "home" && (
          <HomeView
            localTracks={localTracks}
            favorites={favorites}
            playlists={playlists}
            apiStatus={apiStatus}
            currentId={player.current?.audioId}
            playingId={player.playing ? player.current?.audioId : undefined}
            onPlayAll={() => playFree(readyCatalog, readyCatalog[0])}
            onShuffle={() => playFreeShuffled(readyCatalog)}
            onPlayFree={track => playFree(readyCatalog, track)}
            onPlay={playLocal}
            onOpenPlaylist={id => setView({ kind: "playlist", id })}
            onImport={openImport}
            onOpenLiked={() => setView("liked")}
          />
        )}
        {view === "search" && <SearchView onSaved={reloadApi} onPlayFree={playFree} playingId={player.playing ? player.current?.audioId : undefined} />}
        {view === "library" && (
          <LibraryView
            localTracks={localTracks}
            catalog={catalog}
            playlists={playlists}
            audioByCatalog={audioByCatalog}
            currentId={player.playing ? player.current?.audioId : undefined}
            currentCatalogId={player.playing ? player.current?.catalogId : undefined}
            onPlayLocal={playLocal}
            onPlayCatalog={playCatalogList}
            onAttach={openAttach}
            onFavorite={track => void toggleFavorite(track)}
            onAddToPlaylist={(playlistTarget, trackId) => void addToPlaylist(playlistTarget, trackId)}
            onDeleteLocal={track => void deleteLocal(track)}
            onDeleteCatalog={track => void deleteCatalogTrack(track)}
            onCatalogCreated={() => void reloadApi()}
            onError={setActionError}
            onPlayFree={playFree}
            playingFreeId={player.playing ? player.current?.audioId : undefined}
          />
        )}
        {view === "liked" && (
          <TrackTable
            title="Músicas curtidas"
            tracks={favorites}
            audioByCatalog={audioByCatalog}
            playlists={playlists}
            currentCatalogId={player.playing ? player.current?.catalogId : undefined}
            onPlay={start => playCatalogList(favorites, start)}
            onAttach={openAttach}
            onFavorite={track => void toggleFavorite(track)}
            onAddToPlaylist={(playlistTarget, trackId) => void addToPlaylist(playlistTarget, trackId)}
            onDelete={track => void deleteCatalogTrack(track)}
            empty="Nenhuma favorita ainda. Marque uma faixa da biblioteca com o coração."
          />
        )}
        {typeof view === "object" && (
          <PlaylistView
            playlist={playlist}
            catalog={catalog}
            audioByCatalog={audioByCatalog}
            currentCatalogId={player.playing ? player.current?.catalogId : undefined}
            onPlayAll={() => {
              if (!playlist) return;
              // Começa pela primeira faixa que tem áudio aqui, não só pela primeira da lista.
              const first = playlist.tracks.find(item => audioByCatalog.has(item.id)) ?? playlist.tracks[0];
              playCatalogList(playlist.tracks, first?.id ?? "");
            }}
            onShuffle={() => playlist && playCatalogShuffled(playlist.tracks)}
            onPlay={start => playlist && playCatalogList(playlist.tracks, start)}
            onAttach={openAttach}
            onFavorite={track => void toggleFavorite(track)}
            onRemove={trackId => void removeFromPlaylist(trackId)}
            onDelete={() => void deletePlaylist(view.id)}
            onAdd={trackId => void addToPlaylist(view.id, trackId)}
          />
        )}
      </div>

      {showQueue && (
        <aside className="queue" aria-label="Fila de reprodução">
          <div className="queue-head">
            <h2>Fila</h2>
            <button type="button" onClick={player.clearQueue} disabled={player.queue.length === 0}>Limpar</button>
          </div>
          {player.queue.length === 0 && <p className="side-empty">A fila aparece quando você dá play.</p>}
          <ol>
            {player.queue.map((track, position) => (
              <li key={`${track.audioId}-${position}`}>
                <button type="button" className={position === player.index ? "queue-item current" : "queue-item"} onClick={() => player.playQueue(player.queue, position)}>
                  <strong>{track.title}</strong>
                  <span>{track.artist}</span>
                </button>
              </li>
            ))}
          </ol>
        </aside>
      )}

      <PlayerBar
        catalog={catalog}
        queueOpen={showQueue}
        onToggleQueue={() => setShowQueue(open => !open)}
        onFavorite={track => void toggleFavorite(track)}
      />
    </div>
  );
}

function titleFor(view: View, playlist: Playlist | null): string {
  if (view === "home") return "Início";
  if (view === "search") return "Buscar";
  if (view === "library") return "Biblioteca";
  if (view === "liked") return "Favoritas";
  return playlist?.name ?? "Playlist";
}

function SideButton({ icon, active, onClick, children }: { icon: IconName; active: boolean; onClick: () => void; children: string }) {
  return (
    <button type="button" className={active ? "side-link active" : "side-link"} aria-current={active ? "page" : undefined} onClick={onClick}>
      <Icon name={icon} />
      {children}
    </button>
  );
}

/**
 * Selo de status da API Java no topo.
 * Quando a API cai, o texto lembra que o catálogo livre continua tocando,
 * porque ele vem do Internet Archive e não depende do backend.
 */
function ApiStatusChip({ status }: { status: ApiStatus }) {
  const label = status === "online"
    ? "API Java conectada"
    : status === "offline"
      ? "API fora do ar · catálogo livre segue tocando"
      : "Conectando à API…";
  return (
    <p className={`api-chip ${status}`} role="status">
      <span className="api-dot" aria-hidden="true" />
      {label}
    </p>
  );
}

function Cover({ label, artworkUrl }: { label: string; artworkUrl?: string | null }) {
  if (artworkUrl) return <Image className="cover" src={artworkUrl} alt="" width={80} height={80} unoptimized />;
  return <span className="cover generated" style={{ background: coverColor(label) }} aria-hidden="true">{label.slice(0, 1).toUpperCase()}</span>;
}

/**
 * Tela inicial: destaque do catálogo livre, atalhos da biblioteca e a lista do catálogo.
 * O catálogo livre fica em primeiro lugar porque é o único conteúdo que toca
 * mesmo com a API Java fora do ar.
 */
function HomeView({ localTracks, favorites, playlists, apiStatus, currentId, playingId, onPlayAll, onShuffle, onPlayFree, onPlay, onOpenPlaylist, onImport, onOpenLiked }: {
  localTracks: LocalAudio[];
  favorites: CatalogTrack[];
  playlists: PlaylistSummary[];
  apiStatus: ApiStatus;
  currentId?: string;
  playingId?: string;
  onPlayAll: () => void;
  onShuffle: () => void;
  onPlayFree: (track: FreeTrack) => void;
  onPlay: (track: LocalAudio, list: LocalAudio[]) => void;
  onOpenPlaylist: (id: number) => void;
  onImport: () => void;
  onOpenLiked: () => void;
}) {
  return (
    <div className="view">
      <section className="hero" aria-labelledby="hero-title">
        <p className="eyebrow">Catálogo livre</p>
        <h2 id="hero-title">Música para tocar agora</h2>
        <p className="hint">{readyCatalog.length} gravações em Creative Commons ou domínio público, tocadas direto do Internet Archive. Não precisa da API nem de arquivo no computador.</p>
        <div className="pill-row">
          <button type="button" className="pill primary" onClick={onPlayAll}><Icon name="play" size={18} />Tocar tudo</button>
          <button type="button" className="pill" onClick={onShuffle}><Icon name="shuffle" size={18} />Aleatório</button>
        </div>
      </section>

      <section aria-labelledby="library-title">
        <h2 id="library-title">Sua biblioteca</h2>
        <div className="home-row">
          <button type="button" className="summary-card" onClick={onOpenLiked}>
            <span className="summary-icon" aria-hidden="true"><Icon name="heart" filled /></span>
            <span>Favoritas</span>
            <small>{favorites.length} {favorites.length === 1 ? "faixa" : "faixas"}</small>
          </button>
          {playlists.slice(0, 4).map(item => (
            <button type="button" className="summary-card" key={item.id} onClick={() => onOpenPlaylist(item.id)}>
              <Cover label={item.name} />
              <span>{item.name}</span>
              <small>{item.trackCount} {item.trackCount === 1 ? "faixa" : "faixas"}</small>
            </button>
          ))}
          <button type="button" className="summary-card local" onClick={localTracks.length ? () => onPlay(localTracks[0], localTracks) : onImport}>
            <span className="summary-icon" aria-hidden="true"><Icon name={localTracks.length ? "play" : "upload"} /></span>
            <span>No seu computador</span>
            <small>{localTracks.length ? `${localTracks.length} ${localTracks.length === 1 ? "arquivo" : "arquivos"} · tocar` : "Importar MP3, WAV, OGG ou M4A"}</small>
          </button>
        </div>
        {apiStatus === "offline" && <p className="hint">Favoritas e playlists aparecem quando a API Java voltar.</p>}
      </section>

      <section aria-labelledby="catalog-title">
        <h2 id="catalog-title">Catálogo</h2>
        <FreeTrackList tracks={readyCatalog} playingId={playingId} onPlay={onPlayFree} />
      </section>

      {localTracks.length > 0 && (
        <section aria-labelledby="local-title">
          <h2 id="local-title">No seu computador</h2>
          <div className="card-grid">
            {localTracks.map(track => (
              <article className={currentId === track.id ? "song-card current" : "song-card"} key={track.id}>
                <button type="button" className="cover-button" onClick={() => onPlay(track, localTracks)} aria-label={`Tocar ${track.title}`}>
                  <Cover label={track.title} />
                </button>
                <h3>{track.title}</h3>
                <p>{track.artist}</p>
              </article>
            ))}
          </div>
        </section>
      )}
    </div>
  );
}

/**
 * Lista numerada do catálogo livre: número, capa, título/artista, licença e duração.
 * O link "Fonte" fica fora do botão da linha para não aninhar elementos interativos.
 */
function FreeTrackList({ tracks, playingId, onPlay }: { tracks: FreeTrack[]; playingId?: string; onPlay: (track: FreeTrack) => void }) {
  return (
    <ol className="catalog-list">
      {tracks.map((track, index) => {
        const playing = playingId === track.id;
        return (
          <li key={track.id}>
            <button type="button" className={playing ? "catalog-row current" : "catalog-row"} onClick={() => onPlay(track)} aria-label={playing ? `Pausar ${track.title}` : `Tocar ${track.title}`}>
              <span className="num" aria-hidden="true">{playing ? <Icon name="pause" size={16} /> : String(index + 1).padStart(2, "0")}</span>
              <Cover label={track.title} />
              <span className="catalog-title"><strong>{track.title}</strong><small>{track.artist}</small></span>
              <span className="license-pill">{licenseLabel(track.license)}</span>
              <span className="meta time">{formatTime(track.durationSeconds)}</span>
            </button>
            <a className="icon-link" href={track.sourceUrl} target="_blank" rel="noreferrer" aria-label={`Fonte e licença de ${track.title} (abre em nova aba)`}>
              <Icon name="external" size={18} />
            </a>
          </li>
        );
      })}
    </ol>
  );
}

function SearchView({ onSaved, onPlayFree, playingId }: { onSaved: () => Promise<void>; onPlayFree: (list: FreeTrack[], track: FreeTrack) => void; playingId?: string }) {
  const [freeQuery, setFreeQuery] = useState("");
  const [remoteTracks, setRemoteTracks] = useState<FreeTrack[]>([]);
  const [freeSearching, setFreeSearching] = useState(false);
  const [freeError, setFreeError] = useState("");
  const [query, setQuery] = useState("");
  const [tracks, setTracks] = useState<DiscoveryTrack[]>([]);
  const searchRequest = useMemo(() => createRequestTracker(), []);
  const freeSearchRequest = useMemo(() => createRequestTracker(), []);
  const verificationRequest = useMemo(() => createRequestTracker(), []);
  const [searching, setSearching] = useState(false);
  const [error, setError] = useState("");
  const [selected, setSelected] = useState<DiscoveryTrack | null>(null);
  const [verification, setVerification] = useState<Verification | null>(null);
  const [verifying, setVerifying] = useState(false);
  const [showLyrics, setShowLyrics] = useState(false);
  const [savedIds, setSavedIds] = useState<string[]>([]);

  async function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const term = query.trim();
    if (!term) return;
    const requestId = searchRequest.start();
    setSearching(true);
    setError("");
    setSelected(null);
    setVerification(null);
    try {
      const found = await api.searchDiscovery(term);
      if (!searchRequest.isCurrent(requestId)) return;
      setTracks(found);
      if (!found.length) setError("Nenhuma faixa encontrada. Tente outro título ou artista.");
    } catch (reason) {
      if (!searchRequest.isCurrent(requestId)) return;
      setTracks([]);
      setError(errorMessage(reason, "Falha na busca."));
    } finally {
      if (searchRequest.isCurrent(requestId)) setSearching(false);
    }
  }

  async function verify(track: DiscoveryTrack) {
    const requestId = verificationRequest.start();
    setSelected(track);
    setVerification(null);
    setShowLyrics(false);
    setVerifying(true);
    try {
      const verification = await api.verifyTrack(track.title, track.artist, track.durationMillis / 1000);
      if (!verificationRequest.isCurrent(requestId)) return;
      setVerification(verification);
    } catch (reason) {
      if (!verificationRequest.isCurrent(requestId)) return;
      setError(errorMessage(reason, "Falha ao conferir a faixa."));
    } finally {
      if (verificationRequest.isCurrent(requestId)) setVerifying(false);
    }
  }

  async function save(track: DiscoveryTrack) {
    try {
      await api.addTrack(track.title.slice(0, 200), track.artist.slice(0, 160), clampDuration(track.durationMillis / 1000));
      setSavedIds(current => current.includes(track.id) ? current : [...current, track.id]);
      setError("");
      await onSaved();
    } catch (reason) {
      setError(errorMessage(reason, "Não foi possível salvar no catálogo."));
    }
  }

  const term = freeQuery.trim().toLowerCase();
  const localMatches = term
    ? readyCatalog.filter(track => `${track.title} ${track.artist}`.toLowerCase().includes(term))
    : readyCatalog;
  const seen = new Set(localMatches.map(track => track.id));
  const extra = remoteTracks.filter(track => !seen.has(track.id) && !readyCatalog.some(item => item.identifier === track.identifier && item.fileName === track.fileName));
  const freeResults = [...localMatches, ...extra];

  async function searchFree(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await runFreeSearch(freeQuery);
  }

  /**
   * Atalho de gênero: preenche a busca e já procura no acervo remoto.
   * "Tudo" limpa o texto e os resultados remotos, voltando ao catálogo pronto.
   */
  function pickChip(term: string) {
    setFreeQuery(term);
    if (!term) {
      freeSearchRequest.start(); // invalida uma busca remota ainda em andamento
      setRemoteTracks([]);
      setFreeError("");
      setFreeSearching(false);
      return;
    }
    void runFreeSearch(term);
  }

  /** Busca gravações livres no Internet Archive via rota `/music/search` do próprio Next. */
  async function runFreeSearch(raw: string) {
    const value = raw.trim();
    if (value.length < 2) return;
    const requestId = freeSearchRequest.start();
    setFreeSearching(true);
    setFreeError("");
    try {
      const response = await fetch(`/music/search?q=${encodeURIComponent(value)}`);
      if (!freeSearchRequest.isCurrent(requestId)) return;
      const data = (await response.json()) as { tracks?: FreeTrack[]; error?: string };
      if (!response.ok) throw new Error(data.error || "Falha na busca do acervo livre.");
      const tracks = Array.isArray(data.tracks) ? data.tracks.filter(track => track && typeof track.identifier === "string" && typeof track.fileName === "string" && typeof track.title === "string" && typeof track.artist === "string") : [];
      applyIfCurrent(freeSearchRequest, requestId, () => setRemoteTracks(tracks));
    } catch (reason) {
      if (!freeSearchRequest.isCurrent(requestId)) return;
      setRemoteTracks([]);
      setFreeError(errorMessage(reason, "Não foi possível ampliar a busca. O catálogo local continua disponível."));
    } finally {
      if (freeSearchRequest.isCurrent(requestId)) setFreeSearching(false);
    }
  }

  const activeChip = SEARCH_CHIPS.find(chip => chip.term === term)?.term;

  return (
    <div className="view">
      <form className="search-form pill-search" onSubmit={event => void searchFree(event)}>
        <label className="sr-only" htmlFor="free-query">Pesquisar no catálogo livre</label>
        <Icon name="search" />
        <input id="free-query" maxLength={80} value={freeQuery} onChange={event => setFreeQuery(event.target.value)} placeholder="Buscar no catálogo: piano, blues, guitar…" />
        <button type="submit" disabled={freeSearching || freeQuery.trim().length < 2}>{freeSearching ? "Buscando…" : "Buscar"}</button>
      </form>
      <div className="chip-row" role="group" aria-label="Atalhos de gênero">
        {SEARCH_CHIPS.map(chip => (
          <button key={chip.label} type="button" className="chip" aria-pressed={activeChip === chip.term} onClick={() => pickChip(chip.term)}>
            {chip.label}
          </button>
        ))}
      </div>

      <div className="search-layout">
        <section aria-labelledby="free-results-title">
          <div className="section-head">
            <div>
              <h2 id="free-results-title">{freeResults.length} {freeResults.length === 1 ? "faixa" : "faixas"}</h2>
              <p className="hint">Tudo aqui toca. A busca também procura mais gravações de licença livre no Internet Archive.</p>
            </div>
            {freeResults.length > 0 && <button type="button" className="pill primary" onClick={() => onPlayFree(freeResults, freeResults[0])}><Icon name="play" size={18} />Tocar</button>}
          </div>
          {freeError && <p className="inline-error" role="status">{freeError}</p>}
          {freeResults.length === 0
            ? <p className="hint">Nada com esse texto no catálogo pronto. Tente piano, blues ou guitar.</p>
            : <FreeTrackList tracks={freeResults} playingId={playingId} onPlay={track => onPlayFree(freeResults, track)} />}
        </section>

        <aside className="verify-column" aria-labelledby="soundcloud-title">
          <h2 id="soundcloud-title">Conferir no SoundCloud</h2>
          <p className="hint">Traz só metadados, não o áudio. Serve para conferir letra e créditos e, se quiser, salvar o título no catálogo da API.</p>
          <form className="search-form" onSubmit={event => void search(event)}>
            <label className="sr-only" htmlFor="search-query">Pesquisar música ou artista no SoundCloud</label>
            <input id="search-query" maxLength={120} value={query} onChange={event => setQuery(event.target.value)} placeholder="Título ou artista" />
            <button type="submit" disabled={searching || !query.trim()}>{searching ? "Buscando…" : "Buscar"}</button>
          </form>
          {error && <p className="inline-error" role="status">{error}</p>}
          {tracks.length > 0 && (
            <ul className="discovery-list">
              {tracks.map(track => (
                <li key={track.id}>
                  <button type="button" className={selected?.id === track.id ? "discovery-row current" : "discovery-row"} aria-pressed={selected?.id === track.id} onClick={() => void verify(track)}>
                    <Cover label={track.title} artworkUrl={track.artworkUrl} />
                    <span className="catalog-title"><strong>{track.title}</strong><small>{track.artist} · {formatTime(track.durationMillis / 1000)}</small></span>
                  </button>
                </li>
              ))}
            </ul>
          )}

          {selected && (
            <section className="verify-panel" aria-live="polite" aria-labelledby="verify-title">
              <div className="section-head">
                <div>
                  <h3 id="verify-title">{selected.title}</h3>
                  <p className="hint">{selected.artist}</p>
                </div>
                <button type="button" className="icon-button" aria-label="Fechar conferência" onClick={() => { setSelected(null); setVerification(null); }}>
                  <Icon name="close" size={18} />
                </button>
              </div>
              {verifying && <p className="busy">Conferindo letra e metadados…</p>}
              {verification && <VerificationDetails verification={verification} showLyrics={showLyrics} onToggleLyrics={() => setShowLyrics(show => !show)} />}
              <div className="pill-row">
                <button type="button" className="pill primary" onClick={() => void save(selected)} disabled={savedIds.includes(selected.id)}>
                  {savedIds.includes(selected.id) ? "Salva no catálogo" : "Salvar no catálogo"}
                </button>
                <a className="pill" href={selected.permalinkUrl} target="_blank" rel="noreferrer">
                  <Icon name="external" size={18} />Abrir original
                </a>
              </div>
            </section>
          )}
        </aside>
      </div>
    </div>
  );
}

/**
 * Resultado da conferência LRCLIB + MusicBrainz.
 * Regra de negócio: correspondência aproximada (duração ou título diferentes)
 * nunca é apresentada como confirmação; o aviso fica sempre visível nesse caso.
 */
function VerificationDetails({ verification, showLyrics, onToggleLyrics }: { verification: Verification; showLyrics: boolean; onToggleLyrics: () => void }) {
  const lyrics = verification.lyrics;
  const approximate = lyrics?.matchType === "approximate";
  return (
    <>
      <dl className="verify-facts">
        <div>
          <dt>Letra (LRCLIB)</dt>
          <dd>
            {lyrics
              ? <span className={approximate ? "badge approx" : "badge exact"}>{approximate ? "≈ aproximada" : "Igual"}</span>
              : <span className="badge">Não encontrada</span>}
          </dd>
        </div>
        <div>
          <dt>MusicBrainz</dt>
          <dd>{verification.recordings.length} {verification.recordings.length === 1 ? "registro" : "registros"}</dd>
        </div>
      </dl>
      {lyrics && <p className="hint">{lyrics.message}</p>}
      {approximate && <p className="warn-box">Correspondência aproximada não é confirmação. Confira título, artista e duração antes de salvar.</p>}
      {verification.warnings.map(warning => <p className="inline-error" key={warning}>{warning}</p>)}
      {lyrics?.plainLyrics && (
        <>
          <button type="button" className="text-button" aria-expanded={showLyrics} onClick={onToggleLyrics}>{showLyrics ? "Ocultar letra" : "Ver letra"}</button>
          {showLyrics && <pre className="lyrics">{lyrics.plainLyrics}</pre>}
        </>
      )}
      <p className="hint">A conferência não certifica autoria nem direitos autorais.</p>
    </>
  );
}

function LibraryView({ localTracks, catalog, playlists, audioByCatalog, currentId, currentCatalogId, onPlayLocal, onPlayCatalog, onAttach, onFavorite, onAddToPlaylist, onDeleteLocal, onDeleteCatalog, onCatalogCreated, onError, onPlayFree, playingFreeId }: {
  localTracks: LocalAudio[];
  catalog: CatalogTrack[];
  playlists: PlaylistSummary[];
  audioByCatalog: Map<string, LocalAudio>;
  currentId?: string;
  currentCatalogId?: string;
  onPlayLocal: (track: LocalAudio, list: LocalAudio[]) => void;
  onPlayCatalog: (list: CatalogTrack[], startId: string) => void;
  onAttach: (track: CatalogTrack) => void;
  onFavorite: (track: CatalogTrack) => void;
  onAddToPlaylist: (playlistId: number, trackId: string) => void;
  onDeleteLocal: (track: LocalAudio) => void;
  onDeleteCatalog: (track: CatalogTrack) => void;
  onCatalogCreated: () => void;
  onError: (message: string) => void;
  onPlayFree: (list: FreeTrack[], track: FreeTrack) => void;
  playingFreeId?: string;
}) {
  const [filter, setFilter] = useState("");
  const [title, setTitle] = useState("");
  const [artist, setArtist] = useState("");
  const [duration, setDuration] = useState("180");
  const term = filter.trim().toLowerCase();
  const locals = localTracks.filter(track => !term || `${track.title} ${track.artist}`.toLowerCase().includes(term));
  const catalogRows = catalog.filter(track => !term || `${track.title} ${track.artist}`.toLowerCase().includes(term));
  const onlyLocal = locals.filter(track => !track.catalogId);

  async function createCatalog(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    try {
      await api.addTrack(title.trim(), artist.trim(), clampDuration(Number(duration)));
      setTitle("");
      setArtist("");
      onError("");
      onCatalogCreated();
    } catch (error) {
      onError(errorMessage(error, "Não foi possível cadastrar a faixa."));
    }
  }

  return (
    <div className="view">
      <section>
        <div className="section-head">
          <div>
            <h2>Prontas para tocar</h2>
            <p className="hint">{readyCatalog.length} faixas já salvas. Clique em uma ou em Tocar tudo.</p>
          </div>
          <button className="primary" type="button" onClick={() => onPlayFree(readyCatalog, readyCatalog[0])}>Tocar tudo</button>
        </div>
        <FreeTrackList tracks={readyCatalog} playingId={playingFreeId} onPlay={track => onPlayFree(readyCatalog, track)} />
      </section>
      <form className="inline-form" onSubmit={event => void createCatalog(event)}>
        <input aria-label="Título da faixa" maxLength={200} value={title} onChange={event => setTitle(event.target.value)} placeholder="Título" required />
        <input aria-label="Artista" maxLength={160} value={artist} onChange={event => setArtist(event.target.value)} placeholder="Artista" required />
        <input aria-label="Duração em segundos" type="number" min={0} max={86400} value={duration} onChange={event => setDuration(event.target.value)} />
        <button type="submit">Cadastrar</button>
      </form>
      <label className="filter">
        Filtrar biblioteca
        <input value={filter} onChange={event => setFilter(event.target.value)} placeholder="Título ou artista" />
      </label>
      <LocalMusicFolder />
      {onlyLocal.length > 0 && (
        <section>
          <h2>Arquivos só neste navegador</h2>
          <ul className="plain-list">
            {onlyLocal.map(track => (
              <li key={track.id}>
                <button type="button" className={currentId === track.id ? "row-play current" : "row-play"} onClick={() => onPlayLocal(track, onlyLocal)} aria-label={`Tocar ${track.title}`}>
                  <Cover label={track.title} />
                  <span><strong>{track.title}</strong><small>{track.artist}</small></span>
                </button>
                <span className="meta">{formatTime(track.durationSeconds)}</span>
                <button type="button" onClick={() => onDeleteLocal(track)}>Remover arquivo</button>
              </li>
            ))}
          </ul>
        </section>
      )}
      <TrackTable
        title="Catálogo"
        tracks={catalogRows}
        audioByCatalog={audioByCatalog}
        playlists={playlists}
        currentCatalogId={currentCatalogId}
        onPlay={start => onPlayCatalog(catalogRows, start)}
        onAttach={onAttach}
        onFavorite={onFavorite}
        onAddToPlaylist={onAddToPlaylist}
        onDelete={onDeleteCatalog}
        empty="O catálogo da API está vazio. Cadastre uma faixa ou importe um áudio."
      />
    </div>
  );
}

function TrackTable({ title, tracks, audioByCatalog, playlists, currentCatalogId, onPlay, onAttach, onFavorite, onAddToPlaylist, onDelete, empty }: {
  title: string;
  tracks: CatalogTrack[];
  audioByCatalog: Map<string, LocalAudio>;
  playlists: PlaylistSummary[];
  currentCatalogId?: string;
  onPlay: (trackId: string) => void;
  onAttach: (track: CatalogTrack) => void;
  onFavorite: (track: CatalogTrack) => void;
  onAddToPlaylist: (playlistId: number, trackId: string) => void;
  onDelete: (track: CatalogTrack) => void;
  empty: string;
}) {
  return (
    <section>
      <h2>{title}</h2>
      {tracks.length === 0 ? <p className="hint">{empty}</p> : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th scope="col">Tocar</th>
                <th scope="col">Título</th>
                <th scope="col">Artista</th>
                <th scope="col">Tempo</th>
                <th scope="col">Ações</th>
              </tr>
            </thead>
            <tbody>
              {tracks.map(track => {
                const local = audioByCatalog.get(track.id);
                const playing = currentCatalogId === track.id;
                return (
                  <tr key={track.id} className={playing ? "current" : undefined}>
                    <td>
                      <button type="button" onClick={() => onPlay(track.id)} aria-label={local ? `Tocar ${track.title}` : `Sem áudio para ${track.title}`}>
                        {playing ? "Pausar" : "Tocar"}
                      </button>
                    </td>
                    <td>{track.title}</td>
                    <td>{track.artist}</td>
                    <td>{track.duration}</td>
                    <td className="actions">
                      <button type="button" aria-pressed={track.favorite} onClick={() => onFavorite(track)}>{track.favorite ? "Desfavoritar" : "Favoritar"}</button>
                      <button type="button" onClick={() => onAttach(track)}>{local ? "Trocar áudio" : "Anexar áudio"}</button>
                      {playlists.length > 0 && (
                        <select aria-label={`Adicionar ${track.title} a uma playlist`} defaultValue="" onChange={event => {
                          const id = Number(event.target.value);
                          event.currentTarget.value = "";
                          if (id) onAddToPlaylist(id, track.id);
                        }}>
                          <option value="">À playlist…</option>
                          {playlists.map(item => <option value={item.id} key={item.id}>{item.name}</option>)}
                        </select>
                      )}
                      <button type="button" onClick={() => onDelete(track)}>Remover</button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

/**
 * Tela de uma playlist da API: cabeçalho com mosaico de capas, ações e faixas.
 *
 * As faixas vêm do catálogo da API, que guarda só metadados. Uma faixa toca
 * aqui apenas quando há áudio anexado neste navegador (IndexedDB); por isso a
 * coluna de origem diz "Neste navegador" ou "API · sem áudio".
 */
function PlaylistView({ playlist, catalog, audioByCatalog, currentCatalogId, onPlayAll, onShuffle, onPlay, onAttach, onFavorite, onRemove, onDelete, onAdd }: {
  playlist: Playlist | null;
  catalog: CatalogTrack[];
  audioByCatalog: Map<string, LocalAudio>;
  currentCatalogId?: string;
  onPlayAll: () => void;
  onShuffle: () => void;
  onPlay: (trackId: string) => void;
  onAttach: (track: CatalogTrack) => void;
  onFavorite: (track: CatalogTrack) => void;
  onRemove: (trackId: string) => void;
  onDelete: () => void;
  onAdd: (trackId: string) => void;
}) {
  if (!playlist) return <p className="busy">Carregando playlist…</p>;
  const inPlaylist = new Set(playlist.tracks.map(track => track.id));
  const candidates = catalog.filter(track => !inPlaylist.has(track.id));
  const playableHere = playlist.tracks.filter(track => audioByCatalog.has(track.id)).length;
  const totalMinutes = Math.round(playlist.tracks.reduce((sum, track) => sum + track.durationSeconds, 0) / 60);
  const mosaic = playlist.tracks.slice(0, 4);

  return (
    <div className="view">
      <header className="playlist-head">
        <div className="mosaic" aria-hidden="true">
          {Array.from({ length: 4 }, (_, index) => {
            const track = mosaic[index];
            return <span key={index} style={{ background: track ? coverColor(track.title) : "var(--panel-2)" }}>{track?.title.slice(0, 1).toUpperCase()}</span>;
          })}
        </div>
        <div className="playlist-info">
          <p className="eyebrow">Playlist</p>
          <h2 className="playlist-title">{playlist.name}</h2>
          <p className="hint">
            {playlist.trackCount} {playlist.trackCount === 1 ? "faixa" : "faixas"} · {totalMinutes} min · {playableHere} {playableHere === 1 ? "toca" : "tocam"} aqui
          </p>
          <div className="pill-row">
            <button className="pill primary" type="button" onClick={onPlayAll} disabled={playableHere === 0}><Icon name="play" size={18} />Tocar</button>
            <button className="pill" type="button" onClick={onShuffle} disabled={playableHere === 0}><Icon name="shuffle" size={18} />Aleatório</button>
            <button className="pill danger" type="button" onClick={onDelete}><Icon name="trash" size={18} />Excluir playlist</button>
          </div>
        </div>
      </header>

      {playlist.missingTrackIds.length > 0 && <p className="inline-error">Há faixas nesta playlist que não existem mais no catálogo.</p>}

      {playlist.tracks.length === 0 ? <p className="hint">Esta playlist ainda não tem faixas. Adicione abaixo.</p> : (
        <ol className="track-rows">
          {playlist.tracks.map((track, index) => {
            const hasAudio = audioByCatalog.has(track.id);
            const playing = currentCatalogId === track.id;
            return (
              <li key={track.id} className={playing ? "track-row current" : "track-row"}>
                <span className="num" aria-hidden="true">{index + 1}</span>
                <button
                  type="button"
                  className="row-cover"
                  onClick={() => (hasAudio ? onPlay(track.id) : onAttach(track))}
                  aria-label={hasAudio ? `${playing ? "Pausar" : "Tocar"} ${track.title}` : `Anexar áudio para ${track.title}`}
                >
                  <Cover label={track.title} />
                  <span className="row-cover-icon"><Icon name={hasAudio ? (playing ? "pause" : "play") : "upload"} size={18} /></span>
                </button>
                <span className="catalog-title"><strong>{track.title}</strong><small>{track.artist}</small></span>
                <span className={hasAudio ? "origin" : "origin muted"}>{hasAudio ? "Neste navegador" : "API · sem áudio"}</span>
                <span className="meta time">{track.duration}</span>
                <span className="row-actions">
                  <button type="button" className="icon-button" aria-pressed={track.favorite} aria-label={track.favorite ? `Desfavoritar ${track.title}` : `Favoritar ${track.title}`} onClick={() => onFavorite(track)}>
                    <Icon name="heart" filled={track.favorite} size={18} />
                  </button>
                  <button type="button" className="icon-button" aria-label={`Tirar ${track.title} da playlist`} onClick={() => onRemove(track.id)}>
                    <Icon name="close" size={18} />
                  </button>
                </span>
              </li>
            );
          })}
        </ol>
      )}

      <section aria-labelledby="add-tracks-title">
        <h2 id="add-tracks-title">Adicionar faixas</h2>
        {candidates.length === 0
          ? <p className="hint">Todas as faixas do catálogo da API já estão aqui. Cadastre novas na Biblioteca.</p>
          : (
            <ul className="track-rows compact">
              {candidates.slice(0, 12).map(track => (
                <li key={track.id} className="track-row">
                  <Cover label={track.title} />
                  <span className="catalog-title"><strong>{track.title}</strong><small>{track.artist}</small></span>
                  <span className="meta time">{track.duration}</span>
                  <button type="button" className="icon-button" aria-label={`Adicionar ${track.title} à playlist`} onClick={() => onAdd(track.id)}>
                    <Icon name="plus" size={18} />
                  </button>
                </li>
              ))}
            </ul>
          )}
        {candidates.length > 12 && <p className="hint">Mostrando 12 de {candidates.length}. As demais estão na Biblioteca.</p>}
      </section>
    </div>
  );
}

function PlayerBar({ catalog, queueOpen, onToggleQueue, onFavorite }: {
  catalog: CatalogTrack[];
  queueOpen: boolean;
  onToggleQueue: () => void;
  onFavorite: (track: CatalogTrack) => void;
}) {
  const player = usePlayer();
  const track = player.current;
  const catalogTrack = track?.catalogId ? catalog.find(item => item.id === track.catalogId) : undefined;
  const duration = player.duration || track?.durationSeconds || 0;
  // O rótulo diz o estado atual; o clique avança desligado → fila → faixa.
  const repeatLabel = player.repeat === "off" ? "Repetir: desligado" : player.repeat === "all" ? "Repetir: fila" : "Repetir: faixa";

  return (
    <footer className="player" aria-label="Player">
      <div className="now">
        {track ? <Cover label={track.title} /> : <span className="cover generated empty" aria-hidden="true"><Icon name="music" /></span>}
        <div>
          <strong>{track?.title ?? "Nada tocando"}</strong>
          <span>{track?.artist ?? "Escolha uma faixa do catálogo"}</span>
        </div>
        {catalogTrack && (
          <button type="button" className="icon-button fav" aria-pressed={catalogTrack.favorite} aria-label={catalogTrack.favorite ? "Desfavoritar faixa atual" : "Favoritar faixa atual"} onClick={() => onFavorite(catalogTrack)}>
            <Icon name="heart" filled={catalogTrack.favorite} />
          </button>
        )}
      </div>
      <div className="transport">
        <div className="transport-buttons">
          <button type="button" className="icon-button toggle" aria-pressed={player.shuffle} aria-label="Aleatório" onClick={player.toggleShuffle}><Icon name="shuffle" /></button>
          <button type="button" className="icon-button" onClick={player.previous} disabled={!track} aria-label="Faixa anterior"><Icon name="previous" /></button>
          <button type="button" className="play" onClick={player.toggle} disabled={!track} aria-label={player.playing ? "Pausar" : "Tocar"}><Icon name={player.playing ? "pause" : "play"} size={22} /></button>
          <button type="button" className="icon-button" onClick={player.next} disabled={!track} aria-label="Próxima faixa"><Icon name="next" /></button>
          <button type="button" className="icon-button toggle" aria-pressed={player.repeat !== "off"} aria-label={repeatLabel} title={repeatLabel} onClick={player.cycleRepeat}>
            <Icon name={player.repeat === "one" ? "repeat-one" : "repeat"} />
          </button>
        </div>
        <div className="seek">
          <span>{formatTime(player.currentTime)}</span>
          <input
            aria-label="Posição da faixa"
            type="range"
            min={0}
            max={duration || 0}
            step={0.1}
            value={Math.min(player.currentTime, duration || 0)}
            disabled={!track}
            onChange={event => player.seek(Number(event.target.value))}
          />
          <span>{formatTime(duration)}</span>
        </div>
      </div>
      <div className="volume">
        <label>
          <Icon name="volume" />
          <span className="sr-only">Volume</span>
          <input type="range" min={0} max={1} step={0.01} value={player.volume} onChange={event => player.setVolume(Number(event.target.value))} />
        </label>
        <button type="button" className="icon-button toggle" aria-pressed={queueOpen} aria-label="Fila de reprodução" onClick={onToggleQueue}><Icon name="queue" /></button>
      </div>
    </footer>
  );
}

/** Faixa de public/local-music, descrita pelo index.json que `npm run musicas` gera. */
type FolderTrack = { file: string; artist: string; title: string };

/**
 * Músicas da pasta public/local-music, só para testar o player.
 * A pasta é ignorada pelo Git (direitos autorais); sem o index.json a seção não aparece.
 */
function LocalMusicFolder() {
  const player = usePlayer();
  const [tracks, setTracks] = useState<FolderTrack[]>([]);

  useEffect(() => {
    let cancelled = false;
    fetch("/local-music/index.json")
      .then(response => (response.ok ? response.json() : []))
      .then((data: FolderTrack[]) => { if (!cancelled) setTracks(Array.isArray(data) ? data : []); })
      .catch(() => { if (!cancelled) setTracks([]); });
    return () => { cancelled = true; };
  }, []);

  if (tracks.length === 0) return null;
  const queue: PlayableTrack[] = tracks.map(track => ({ audioId: `pasta:${track.file}`, title: track.title, artist: track.artist, durationSeconds: 0, streamUrl: track.file }));

  function play(start: number) {
    if (player.current?.audioId === queue[start].audioId) player.toggle();
    else player.playQueue(queue, start);
  }

  return (
    <section>
      <div className="section-head">
        <div>
          <h2>Pasta de músicas locais</h2>
          <p className="hint">{tracks.length} faixas de teste em public/local-music.</p>
        </div>
        <button className="primary" type="button" onClick={() => play(0)}>Tocar tudo</button>
      </div>
      <ul className="plain-list">
        {queue.map((track, position) => (
          <li key={track.audioId}>
            <button type="button" className={player.current?.audioId === track.audioId ? "row-play current" : "row-play"} onClick={() => play(position)} aria-label={`Tocar ${track.title}`}>
              <Cover label={track.title} />
              <span><strong>{track.title}</strong><small>{track.artist}</small></span>
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}