"use client";

import { FormEvent, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { licenseLabel, readyCatalog, streamUrl, type FreeTrack } from "@/data/free-catalog";
import { api, ApiError, type CatalogTrack, type DiscoveryTrack, type Playlist, type PlaylistSummary, type Verification } from "@/lib/api";
import { clampDuration, coverColor, formatTime, titleFromFileName } from "@/lib/format";
import { clearCatalogLink, listLocalTracks, readDuration, removeByCatalogId, removeLocalTrack, saveLocalAudio, type LocalAudio } from "@/lib/local-audio";
import { PlayerProvider, usePlayer, type PlayableTrack } from "@/components/player-provider";

type View = "home" | "search" | "library" | "liked" | { kind: "playlist"; id: number };

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
    } catch (error) {
      setApiNotice(errorMessage(error, "A API Java não respondeu. Dá para tocar os áudios já importados neste navegador."));
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
      } catch (error) {
        if (!cancelled) setApiNotice(errorMessage(error, "A API Java não respondeu. Dá para tocar os áudios já importados neste navegador."));
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
          <SideButton active={view === "home"} onClick={() => setView("home")}>Início</SideButton>
          <SideButton active={view === "search"} onClick={() => setView("search")}>Buscar</SideButton>
          <SideButton active={view === "library"} onClick={() => setView("library")}>Biblioteca</SideButton>
          <SideButton active={view === "liked"} onClick={() => setView("liked")}>Favoritas</SideButton>
        </nav>
        <form className="playlist-form" onSubmit={event => void createPlaylist(event)}>
          <label htmlFor="playlist-name">Nova playlist</label>
          <div>
            <input id="playlist-name" maxLength={120} value={playlistName} onChange={event => setPlaylistName(event.target.value)} placeholder="Nome da playlist" />
            <button type="submit" disabled={!playlistName.trim()}>Criar</button>
          </div>
        </form>
        <div className="side-label">Playlists</div>
        <div className="playlist-scroll">
          {playlists.length === 0 && <p className="side-empty">Nenhuma playlist na API.</p>}
          {playlists.map(item => (
            <button
              key={item.id}
              type="button"
              className={playlistId === item.id ? "playlist-link active" : "playlist-link"}
              onClick={() => setView({ kind: "playlist", id: item.id })}
            >
              <span>{item.name}</span>
              <small>{item.trackCount}</small>
            </button>
          ))}
        </div>
        <button className="import-button" type="button" onClick={openImport}>Importar áudios</button>
      </aside>

      <div className="content" id="conteudo">
        <header className="topbar">
          <div>
            <p className="eyebrow">Player</p>
            <h1>{titleFor(view, playlist)}</h1>
          </div>
          <button className="text-button" type="button" onClick={openImport}>Adicionar músicas</button>
        </header>
        {notice && (
          <div className="notice" role="status">
            <p>{notice}</p>
            <button type="button" onClick={() => { setActionError(""); player.dismissError(); }}>Fechar</button>
          </div>
        )}
        {busy && <p className="busy" role="status">{busy}</p>}

        {view === "home" && (
          <>
            <section className="view">
              <div className="section-head">
                <div>
                  <h2>Catálogo pronto</h2>
                  <p className="hint">{readyCatalog.length} faixas em Creative Commons ou domínio público. O áudio toca daqui, sem baixar arquivo para o projeto.</p>
                </div>
                <button type="button" className="primary" onClick={() => playFree(readyCatalog, readyCatalog[0])}>Tocar tudo</button>
              </div>
              <FreeTrackList tracks={readyCatalog} playingId={player.playing ? player.current?.audioId : undefined} onPlay={track => playFree(readyCatalog, track)} />
            </section>
          <HomeView
            localTracks={localTracks}
            favorites={favorites}
            playlists={playlists}
            currentId={player.current?.audioId}
            onPlay={playLocal}
            onOpenPlaylist={id => setView({ kind: "playlist", id })}
            onImport={openImport}
            onOpenLiked={() => setView("liked")}
          />
          </>
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
            audioByCatalog={audioByCatalog}
            playlists={playlists}
            currentCatalogId={player.playing ? player.current?.catalogId : undefined}
            onPlayAll={() => playlist && playCatalogList(playlist.tracks, playlist.tracks[0]?.id ?? "")}
            onPlay={start => playlist && playCatalogList(playlist.tracks, start)}
            onAttach={openAttach}
            onFavorite={track => void toggleFavorite(track)}
            onRemove={trackId => void removeFromPlaylist(trackId)}
            onDelete={() => void deletePlaylist(view.id)}
            onAddToPlaylist={(playlistTarget, trackId) => void addToPlaylist(playlistTarget, trackId)}
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

function SideButton({ active, onClick, children }: { active: boolean; onClick: () => void; children: string }) {
  return <button type="button" className={active ? "side-link active" : "side-link"} aria-current={active ? "page" : undefined} onClick={onClick}>{children}</button>;
}

function Cover({ label, artworkUrl }: { label: string; artworkUrl?: string | null }) {
  if (artworkUrl) return <img className="cover" src={artworkUrl} alt="" />;
  return <span className="cover generated" style={{ background: coverColor(label) }} aria-hidden="true">{label.slice(0, 1).toUpperCase()}</span>;
}

function HomeView({ localTracks, favorites, playlists, currentId, onPlay, onOpenPlaylist, onImport, onOpenLiked }: {
  localTracks: LocalAudio[];
  favorites: CatalogTrack[];
  playlists: PlaylistSummary[];
  currentId?: string;
  onPlay: (track: LocalAudio, list: LocalAudio[]) => void;
  onOpenPlaylist: (id: number) => void;
  onImport: () => void;
  onOpenLiked: () => void;
}) {
  return (
    <div className="view">
      {localTracks.length === 0 ? (
        <section>
          <h2>Arquivos do computador</h2>
          <p className="hint">O catálogo acima já toca. Se quiser, também dá para importar um MP3, WAV, OGG ou M4A deste computador.</p>
          <button className="text-button" type="button" onClick={onImport}>Importar áudios</button>
        </section>
      ) : (
        <section>
          <div className="section-head">
            <h2>No seu computador</h2>
            <button type="button" className="text-button" onClick={() => onPlay(localTracks[0], localTracks)}>Tocar tudo</button>
          </div>
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
      <section className="home-row">
        <button type="button" className="summary-card" onClick={onOpenLiked}>
          <span>Favoritas</span>
          <strong>{favorites.length}</strong>
        </button>
        {playlists.slice(0, 4).map(item => (
          <button type="button" className="summary-card" key={item.id} onClick={() => onOpenPlaylist(item.id)}>
            <span>{item.name}</span>
            <strong>{item.trackCount} faixas</strong>
          </button>
        ))}
      </section>
    </div>
  );
}

function FreeTrackList({ tracks, playingId, onPlay }: { tracks: FreeTrack[]; playingId?: string; onPlay: (track: FreeTrack) => void }) {
  return (
    <ol className="catalog-list">
      {tracks.map((track, index) => (
        <li key={track.id}>
          <button type="button" className={playingId === track.id ? "catalog-row current" : "catalog-row"} onClick={() => onPlay(track)} aria-label={`Tocar ${track.title}`}>
            <span className="num">{String(index + 1).padStart(2, "0")}</span>
            <span className="catalog-title"><strong>{track.title}</strong><small>{track.artist}</small></span>
            <span className="meta license">{licenseLabel(track.license)}</span>
            <span className="meta">{formatTime(track.durationSeconds)}</span>
          </button>
          <a href={track.sourceUrl} target="_blank" rel="noreferrer">Fonte</a>
        </li>
      ))}
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
    setSearching(true);
    setError("");
    setSelected(null);
    setVerification(null);
    try {
      const found = await api.searchDiscovery(term);
      setTracks(found);
      if (!found.length) setError("Nenhuma faixa encontrada. Tente outro título ou artista.");
    } catch (reason) {
      setTracks([]);
      setError(errorMessage(reason, "Falha na busca."));
    } finally {
      setSearching(false);
    }
  }

  async function verify(track: DiscoveryTrack) {
    setSelected(track);
    setVerification(null);
    setShowLyrics(false);
    setVerifying(true);
    try {
      setVerification(await api.verifyTrack(track.title, track.artist, track.durationMillis / 1000));
    } catch (reason) {
      setError(errorMessage(reason, "Falha ao conferir a faixa."));
    } finally {
      setVerifying(false);
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
    const value = freeQuery.trim();
    if (value.length < 2) return;
    setFreeSearching(true);
    setFreeError("");
    try {
      const response = await fetch(`/music/search?q=${encodeURIComponent(value)}`);
      const data = (await response.json()) as { tracks?: FreeTrack[]; error?: string };
      if (!response.ok) throw new Error(data.error || "Falha na busca do acervo livre.");
      const tracks = Array.isArray(data.tracks) ? data.tracks.filter(track => track && typeof track.identifier === "string" && typeof track.fileName === "string" && typeof track.title === "string" && typeof track.artist === "string") : [];
      setRemoteTracks(tracks);
    } catch (reason) {
      setRemoteTracks([]);
      setFreeError(errorMessage(reason, "Não foi possível ampliar a busca. O catálogo local continua disponível."));
    } finally {
      setFreeSearching(false);
    }
  }

  return (
    <div className="view">
      <form className="search-form" onSubmit={event => void searchFree(event)}>
        <label className="sr-only" htmlFor="free-query">Pesquisar no catálogo livre</label>
        <input id="free-query" maxLength={80} value={freeQuery} onChange={event => setFreeQuery(event.target.value)} placeholder="Buscar no catálogo: piano, blues, guitar…" />
        <button type="submit" disabled={freeSearching || freeQuery.trim().length < 2}>{freeSearching ? "Buscando…" : "Buscar"}</button>
      </form>
      <p className="hint">A lista de baixo já toca. A busca também procura mais gravações com licença livre no Internet Archive.</p>
      {freeError && <p className="inline-error" role="status">{freeError}</p>}
      {freeResults.length === 0 ? <p className="hint">Nada com esse texto no catálogo pronto. Tente piano, blues ou guitar.</p> : (
        <>
          <div className="section-head">
            <h2>{freeResults.length} faixas</h2>
            <button type="button" className="primary" onClick={() => onPlayFree(freeResults, freeResults[0])}>Tocar</button>
          </div>
          <FreeTrackList tracks={freeResults} playingId={playingId} onPlay={track => onPlayFree(freeResults, track)} />
        </>
      )}
      <details className="more-search">
        <summary>Buscar metadados no SoundCloud</summary>
      <form className="search-form" onSubmit={event => void search(event)}>
        <label className="sr-only" htmlFor="search-query">Pesquisar música ou artista</label>
        <input id="search-query" maxLength={120} value={query} onChange={event => setQuery(event.target.value)} placeholder="Título ou artista no SoundCloud" />
        <button type="submit" disabled={searching || !query.trim()}>{searching ? "Buscando…" : "Buscar"}</button>
      </form>
      <p className="hint">Essa busca não traz o áudio do SoundCloud. Ela serve para conferir letra e créditos e, se quiser, salvar o título no catálogo da API.</p>
      {error && <p className="inline-error" role="status">{error}</p>}
      <div className="card-grid">
        {tracks.map(track => (
          <article className="song-card" key={track.id}>
            <Cover label={track.title} artworkUrl={track.artworkUrl} />
            <h3>{track.title}</h3>
            <p>{track.artist}</p>
            <p className="meta">{formatTime(track.durationMillis / 1000)}</p>
            <div className="card-actions">
              <button type="button" onClick={() => void verify(track)}>Conferir</button>
              <button type="button" onClick={() => void save(track)}>{savedIds.includes(track.id) ? "Salva" : "Salvar"}</button>
              <a href={track.permalinkUrl} target="_blank" rel="noreferrer">Abrir original</a>
            </div>
          </article>
        ))}
      </div>
      {selected && (
        <section className="verify-panel" aria-live="polite">
          <div className="section-head">
            <h2>{selected.title}</h2>
            <button type="button" onClick={() => { setSelected(null); setVerification(null); }}>Fechar</button>
          </div>
          <p>{selected.artist}</p>
          {verifying && <p className="busy">Conferindo letra e metadados…</p>}
          {verification && (
            <>
              <p>{verification.lyrics ? verification.lyrics.message : "Nenhuma letra correspondente."}</p>
              <p>{verification.recordings.length} registro(s) no MusicBrainz para comparação.</p>
              {verification.warnings.map(warning => <p className="inline-error" key={warning}>{warning}</p>)}
              {verification.lyrics?.plainLyrics && (
                <>
                  <button type="button" onClick={() => setShowLyrics(show => !show)}>{showLyrics ? "Ocultar letra" : "Ver letra"}</button>
                  {showLyrics && <pre className="lyrics">{verification.lyrics.plainLyrics}</pre>}
                </>
              )}
              <p className="hint">A conferência não certifica autoria nem direitos autorais.</p>
            </>
          )}
        </section>
      )}
      </details>
    </div>
  );
}

function LibraryView({ localTracks, catalog, playlists, audioByCatalog, currentId, currentCatalogId, onPlayLocal, onPlayCatalog, onAttach, onFavorite, onAddToPlaylist, onDeleteLocal, onDeleteCatalog, onCatalogCreated, onError }: {
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

function PlaylistView({ playlist, audioByCatalog, playlists, currentCatalogId, onPlayAll, onPlay, onAttach, onFavorite, onRemove, onDelete, onAddToPlaylist }: {
  playlist: Playlist | null;
  audioByCatalog: Map<string, LocalAudio>;
  playlists: PlaylistSummary[];
  currentCatalogId?: string;
  onPlayAll: () => void;
  onPlay: (trackId: string) => void;
  onAttach: (track: CatalogTrack) => void;
  onFavorite: (track: CatalogTrack) => void;
  onRemove: (trackId: string) => void;
  onDelete: () => void;
  onAddToPlaylist: (playlistId: number, trackId: string) => void;
}) {
  if (!playlist) return <p className="busy">Carregando playlist…</p>;
  return (
    <div className="view">
      <div className="section-head">
        <p className="hint">{playlist.trackCount} faixas</p>
        <div className="card-actions">
          <button className="primary" type="button" onClick={onPlayAll} disabled={playlist.tracks.length === 0}>Tocar</button>
          <button type="button" onClick={onDelete}>Apagar playlist</button>
        </div>
      </div>
      {playlist.missingTrackIds.length > 0 && <p className="inline-error">Há faixas nesta playlist que não existem mais no catálogo.</p>}
      <TrackTable
        title="Faixas"
        tracks={playlist.tracks}
        audioByCatalog={audioByCatalog}
        playlists={playlists.filter(item => item.id !== playlist.id)}
        currentCatalogId={currentCatalogId}
        onPlay={onPlay}
        onAttach={onAttach}
        onFavorite={onFavorite}
        onAddToPlaylist={onAddToPlaylist}
        onDelete={track => onRemove(track.id)}
        empty="Esta playlist ainda não tem faixas. Adicione pela biblioteca."
      />
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
  const repeatLabel = player.repeat === "off" ? "Repetir desligado" : player.repeat === "all" ? "Repetir fila" : "Repetir faixa";

  return (
    <footer className="player" aria-label="Player">
      <div className="now">
        {track ? <Cover label={track.title} /> : <span className="cover generated" aria-hidden="true">♪</span>}
        <div>
          <strong>{track?.title ?? "Nada tocando"}</strong>
          <span>{track?.artist ?? "Importe um áudio para começar"}</span>
        </div>
        {catalogTrack && (
          <button type="button" aria-pressed={catalogTrack.favorite} aria-label={catalogTrack.favorite ? "Desfavoritar faixa atual" : "Favoritar faixa atual"} onClick={() => onFavorite(catalogTrack)}>
            {catalogTrack.favorite ? "♥" : "♡"}
          </button>
        )}
      </div>
      <div className="transport">
        <div className="transport-buttons">
          <button type="button" aria-pressed={player.shuffle} onClick={player.toggleShuffle}>Aleatório</button>
          <button type="button" onClick={player.previous} disabled={!track} aria-label="Faixa anterior">Anterior</button>
          <button type="button" className="play" onClick={player.toggle} disabled={!track} aria-label={player.playing ? "Pausar" : "Tocar"}>{player.playing ? "Pausa" : "Tocar"}</button>
          <button type="button" onClick={player.next} disabled={!track} aria-label="Próxima faixa">Próxima</button>
          <button type="button" aria-pressed={player.repeat !== "off"} onClick={player.cycleRepeat}>{repeatLabel}</button>
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
          Volume
          <input aria-label="Volume" type="range" min={0} max={1} step={0.01} value={player.volume} onChange={event => player.setVolume(Number(event.target.value))} />
        </label>
        <button type="button" aria-pressed={queueOpen} onClick={onToggleQueue}>Fila</button>
      </div>
    </footer>
  );
}
