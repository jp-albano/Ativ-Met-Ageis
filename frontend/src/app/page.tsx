"use client";

import { FormEvent, useEffect, useState } from "react";
import {
  api,
  type CatalogTrack,
  type DiscoveryTrack as Track,
  type Playlist,
  type PlaylistSummary,
  type Verification,
} from "@/lib/api";

const errorMessage = (e: unknown, fallback: string) => (e instanceof Error ? e.message : fallback);

const duration = (ms: number) => {
  const seconds = Math.max(0, Math.floor(ms / 1000));
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, "0")}`;
};

export default function Home() {
  const [query, setQuery] = useState("");
  const [tracks, setTracks] = useState<Track[]>([]);
  const [selected, setSelected] = useState<Track | null>(null);
  const [verification, setVerification] = useState<Verification | null>(null);
  const [searching, setSearching] = useState(false);
  const [verifying, setVerifying] = useState(false);
  const [error, setError] = useState("");
  const [showLyrics, setShowLyrics] = useState(false);
  const [catalog, setCatalog] = useState<CatalogTrack[]>([]);
  const [catalogError, setCatalogError] = useState("");
  const [playlists, setPlaylists] = useState<PlaylistSummary[]>([]);
  const [activePlaylist, setActivePlaylist] = useState<Playlist | null>(null);
  const [playlistName, setPlaylistName] = useState("");
  const [playlistLoading, setPlaylistLoading] = useState(false);
  const [playlistBusy, setPlaylistBusy] = useState(false);
  const [playlistError, setPlaylistError] = useState("");

  // Carrega o catálogo do backend Java uma vez; se a API estiver fora, mostra o aviso na seção.
  useEffect(() => {
    api.listTracks().then(setCatalog).catch(e => setCatalogError(errorMessage(e, "Falha ao carregar o catálogo.")));
    api.listPlaylists().then(setPlaylists).catch(e => setPlaylistError(errorMessage(e, "Falha ao carregar as playlists.")));
  }, []);

  async function toggleFavorite(track: CatalogTrack) {
    try {
      await api.setFavorite(track.id, !track.favorite);
      setCatalog(current => current.map(t => (t.id === track.id ? { ...t, favorite: !track.favorite } : t)));
      setCatalogError("");
    } catch (e) {
      setCatalogError(errorMessage(e, "Não foi possível atualizar o favorito."));
    }
  }

  async function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!query.trim()) return;
    setSearching(true); setError(""); setTracks([]); setSelected(null); setVerification(null); setShowLyrics(false);
    try {
      const found = await api.searchDiscovery(query.trim());
      setTracks(found);
      if (!found.length) setError("Nenhuma faixa encontrada. Tente outro título ou artista.");
    } catch (e) {
      setError(errorMessage(e, "Falha inesperada na busca."));
    } finally { setSearching(false); }
  }

  async function verify(track: Track) {
    setSelected(track); setVerification(null); setError(""); setShowLyrics(false); setVerifying(true);
    try {
      setVerification(await api.verifyTrack(track.title, track.artist, track.durationMillis / 1000));
    } catch (e) {
      setError(errorMessage(e, "Falha ao verificar a faixa."));
    } finally { setVerifying(false); }
  }

  async function createPlaylist(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const name = playlistName.trim();
    if (!name) return;
    setPlaylistBusy(true);
    setPlaylistError("");
    try {
      const created = await api.createPlaylist(name);
      setPlaylists(current => [created, ...current]);
      setActivePlaylist(created);
      setPlaylistName("");
    } catch (e) {
      setPlaylistError(errorMessage(e, "Não foi possível criar a playlist."));
    } finally {
      setPlaylistBusy(false);
    }
  }

  async function openPlaylist(id: number) {
    setPlaylistLoading(true);
    setPlaylistError("");
    setActivePlaylist(null);
    try {
      setActivePlaylist(await api.getPlaylist(id));
    } catch (e) {
      setPlaylistError(errorMessage(e, "Não foi possível abrir a playlist."));
    } finally {
      setPlaylistLoading(false);
    }
  }

  async function deletePlaylist(id: number) {
    if (!window.confirm("Excluir esta playlist?")) return;
    setPlaylistBusy(true);
    setPlaylistError("");
    try {
      await api.deletePlaylist(id);
      setPlaylists(current => current.filter(playlist => playlist.id !== id));
      setActivePlaylist(current => current?.id === id ? null : current);
    } catch (e) {
      setPlaylistError(errorMessage(e, "Não foi possível excluir a playlist."));
    } finally {
      setPlaylistBusy(false);
    }
  }

  async function togglePlaylistTrack(track: CatalogTrack) {
    if (!activePlaylist) return;
    const isIncluded = activePlaylist.tracks.some(item => item.id === track.id);
    setPlaylistBusy(true);
    setPlaylistError("");
    try {
      const updated = await api.setPlaylistTrack(activePlaylist.id, track.id, !isIncluded);
      setActivePlaylist(updated);
      setPlaylists(current => current.map(playlist =>
        playlist.id === updated.id ? { ...playlist, trackCount: updated.trackCount } : playlist,
      ));
    } catch (e) {
      setPlaylistError(errorMessage(e, "Não foi possível atualizar a playlist."));
    } finally {
      setPlaylistBusy(false);
    }
  }

  return (
    <main className="app-shell">
      <aside className="sidebar">
        <a className="brand" href="#" aria-label="Sonora início"><span className="brand-mark"><i /><i /><i /></span><span>sonora<span className="brand-dot">.</span></span></a>
        <div className="side-label">SUA BIBLIOTECA</div>
        <a className="side-link active" href="#discover"><span className="side-icon">⌕</span> Descobrir música</a>
        <a className="side-link" href="#playlists"><span className="side-icon">♫</span> Playlists</a>
        <a className="side-link" href="#catalog"><span className="side-icon">♥</span> Sua biblioteca</a>
        <div className="sidebar-bottom"><div className="equalizer"><i /><i /><i /><i /><i /><i /><i /></div><p>Encontre a música.<br /><strong>Descubra a história.</strong></p><span>Pesquisa musical • projeto acadêmico</span></div>
      </aside>

      <section className="main-panel" id="discover">
        <header className="topbar"><span className="eyebrow"><span className="live-dot" /> SEU UNIVERSO MUSICAL</span><span className="topbar-note">Um lugar para descobrir sons</span></header>
        <section className="hero">
          <div className="hero-copy"><div className="pill"><span>✳</span> PESQUISA INTELIGENTE</div><h1>Uma música.<br /><em>Mil descobertas.</em></h1><p>Busque faixas, encontre artistas e confira informações musicais em um só lugar.</p>
            <form className="search-form" onSubmit={search}><span className="search-symbol">⌕</span><input aria-label="Pesquisar música ou artista" maxLength={120} value={query} onChange={e => setQuery(e.target.value)} placeholder="Qual som você está procurando?" /><button type="submit" disabled={searching || !query.trim()}>{searching ? <span className="spinner" /> : "Buscar"} <span aria-hidden="true">↗</span></button></form>
            <div className="search-hint"><span>↳</span> Experimente um título, artista ou trecho do nome</div>
          </div>
          <div className="hero-art" aria-hidden="true"><div className="art-orbit orbit-one" /><div className="art-orbit orbit-two" /><div className="art-disc"><div className="disc-ring"><div className="disc-center"><span>so</span></div></div></div><div className="floating-note note-one">♫</div><div className="floating-note note-two">♪</div><div className="art-caption">SOM EM MOVIMENTO <span>✳</span></div></div>
        </section>

        {error && <div className="notice" role="status"><span>ⓘ</span>{error}</div>}
        <section className="results-section" id="results" aria-busy={searching}>
          <div className="section-heading"><div><span className="section-kicker">EXPLORE O CATÁLOGO</span><h2>{tracks.length ? "Resultados da busca" : "Comece por uma descoberta"}</h2></div>{tracks.length > 0 && <span className="result-count">{tracks.length} FAIXAS</span>}</div>
          {searching ? <div className="loading-line" role="status" aria-live="polite"><span className="spinner dark" /> Buscando faixas no SoundCloud…</div> : tracks.length === 0 ? <div className="empty-state"><div className="empty-icon"><span>♫</span><span>✳</span></div><h3>Seu próximo som está por aqui.</h3><p>Pesquise acima para ver faixas do SoundCloud, suas capas e os dados de cada artista.</p><div className="suggestions"><button onClick={() => setQuery("indie pop")}>indie pop <span>↗</span></button><button onClick={() => setQuery("lofi beats")}>lofi beats <span>↗</span></button><button onClick={() => setQuery("rock brasileiro")}>rock brasileiro <span>↗</span></button></div></div> :
            <div className="track-grid">{tracks.map((track, index) => <article className={selected?.id === track.id ? "track-card selected" : "track-card"} key={track.id}><button className="cover-button" onClick={() => verify(track)} aria-label={`Verificar ${track.title}`}>{track.artworkUrl ? <img src={track.artworkUrl} alt={`Capa de ${track.title}`} /> : <span className="cover-placeholder">♫</span>}<span className="play-overlay">⌕</span><span className="track-index">{String(index + 1).padStart(2, "0")}</span></button><button className="track-title" onClick={() => verify(track)}>{track.title}</button><p className="track-artist">{track.artist}</p><div className="track-meta"><span>{duration(track.durationMillis)}</span><a href={track.permalinkUrl} target="_blank" rel="noreferrer">Abrir faixa ↗</a></div></article>)}</div>}
        </section>

        {selected && <section className="verification-panel"><div className="verify-header"><div><span className="section-kicker">CONFERÊNCIA MUSICAL</span><h2>{selected.title}</h2><p>{selected.artist}</p></div><button className="close-button" onClick={() => {setSelected(null); setVerification(null);}}>×</button></div>
          {verifying ? <div className="loading-line"><span className="spinner dark" /> Conferindo letras e metadados em fontes independentes…</div> : verification && <><div className="verification-grid"><div className="verify-card"><span className="verify-icon">♫</span><div><strong>Letras • LRCLIB</strong><p>{verification.lyrics ? (verification.lyrics.verified ? "Correspondência forte de título, artista e duração." : "Encontramos um resultado aproximado. Confira antes de confiar.") : "Nenhuma letra correspondente encontrada."}</p></div><span className={verification.lyrics?.verified ? "status-good" : "status-neutral"}>{verification.lyrics?.verified ? "CONFERE" : verification.lyrics ? "APROXIMADO" : "SEM RESULTADO"}</span></div><div className="verify-card"><span className="verify-icon">◎</span><div><strong>Créditos • MusicBrainz</strong><p>{verification.recordings.length} registro(s) encontrado(s) para comparação.</p></div></div></div>
          {verification.warnings.map(warning => <p className="warning-text" key={warning}>{warning}</p>)}
          {verification.lyrics && <div className="lyrics-block"><div className="lyrics-title"><div><span className="section-kicker">FONTE: LRCLIB</span><h3>{verification.lyrics.title} — {verification.lyrics.artist}</h3></div>{(verification.lyrics.plainLyrics || verification.lyrics.syncedLyrics) && <button onClick={() => setShowLyrics(!showLyrics)}>{showLyrics ? "Ocultar letra" : "Ver letra"} {showLyrics ? "−" : "+"}</button>}</div>{showLyrics && <pre className="lyrics-text">{verification.lyrics.plainLyrics ?? verification.lyrics.syncedLyrics}</pre>}{verification.lyrics.instrumental && <p className="track-artist">Esta faixa está marcada como instrumental pela fonte.</p>}</div>}
          {verification.recordings.length > 0 && <div className="recordings-list"><h3>Registros relacionados</h3>{verification.recordings.map(record => <div className="recording-row" key={record.id}><span>{record.title}</span><span>{record.artist}</span></div>)}</div>}<p className="legal-note">A correspondência ajuda na conferência de dados, mas não certifica autoria nem direitos autorais.</p></>}
        </section>}

        <section className="results-section" id="catalog">
          <div className="section-heading"><div><span className="section-kicker">API JAVA • CATÁLOGO LOCAL</span><h2>Sua biblioteca</h2></div>{catalog.length > 0 && <span className="result-count">{catalog.length} FAIXAS</span>}</div>
          {catalogError && <div className="notice" role="status"><span>ⓘ</span>{catalogError}</div>}
          {catalog.length > 0 && <div className="recordings-list">{catalog.map(track => <div className="recording-row" key={track.id}><span>{track.title} <small>• {track.duration}</small></span><span>{track.artist} <button className="close-button" aria-pressed={track.favorite} aria-label={track.favorite ? `Desfavoritar ${track.title}` : `Favoritar ${track.title}`} onClick={() => toggleFavorite(track)}>{track.favorite ? "♥" : "♡"}</button></span></div>)}</div>}
        </section>

        <section className="results-section" id="playlists">
          <div className="section-heading"><div><span className="section-kicker">ORGANIZE SEUS SONS</span><h2>Playlists</h2></div>{playlists.length > 0 && <span className="result-count">{playlists.length} PLAYLISTS</span>}</div>
          {playlistError && <div className="notice" role="status"><span>ⓘ</span>{playlistError}</div>}
          <div className="playlist-layout">
            <div className="playlist-list" aria-label="Suas playlists">
              <form className="playlist-create-form" onSubmit={createPlaylist}>
                <label className="visually-hidden" htmlFor="playlist-name">Nome da nova playlist</label>
                <input id="playlist-name" value={playlistName} onChange={event => setPlaylistName(event.target.value)} placeholder="Nome da nova playlist" maxLength={100} />
                <button type="submit" disabled={playlistBusy || !playlistName.trim()}>Criar</button>
              </form>
              {playlists.length === 0 ? <p className="playlist-empty">Crie sua primeira playlist para organizar as faixas.</p> : playlists.map(playlist =>
                <button
                  className={activePlaylist?.id === playlist.id ? "playlist-item active" : "playlist-item"}
                  key={playlist.id}
                  onClick={() => openPlaylist(playlist.id)}
                  disabled={playlistLoading || playlistBusy}
                  aria-current={activePlaylist?.id === playlist.id ? "true" : undefined}
                >
                  <span>{playlist.name}</span><small>{playlist.trackCount} faixas</small>
                </button>,
              )}
            </div>
            <div className="playlist-detail" aria-busy={playlistLoading || playlistBusy}>
              {playlistLoading ? <div className="loading-line"><span className="spinner dark" /> Abrindo playlist…</div> : activePlaylist ? <>
                <div className="playlist-detail-heading">
                  <div><span className="section-kicker">{activePlaylist.trackCount} FAIXAS</span><h3>{activePlaylist.name}</h3></div>
                  <button className="close-button" onClick={() => deletePlaylist(activePlaylist.id)} disabled={playlistBusy} aria-label={`Excluir playlist ${activePlaylist.name}`}>×</button>
                </div>
                {activePlaylist.missingTrackIds.length > 0 && <p className="warning-text">{activePlaylist.missingTrackIds.length} faixa(s) da playlist não estão mais no catálogo.</p>}
                <div className="playlist-track-list">
                  {activePlaylist.tracks.length === 0 ? <p className="playlist-empty">Ainda não há faixas nesta playlist.</p> : activePlaylist.tracks.map(track =>
                    <div className="playlist-track-row" key={track.id}>
                      <span>{track.title}<small>{track.artist}</small></span>
                      <button className="playlist-action" onClick={() => togglePlaylistTrack(track)} disabled={playlistBusy}>Remover</button>
                    </div>,
                  )}
                </div>
                <div className="playlist-catalog">
                  <h4>Adicionar do catálogo</h4>
                  {catalog.length === 0 ? <p className="playlist-empty">O catálogo está vazio.</p> : catalog.map(track => {
                    const included = activePlaylist.tracks.some(item => item.id === track.id);
                    return <div className="playlist-track-row" key={track.id}>
                      <span>{track.title}<small>{track.artist}</small></span>
                      <button className="playlist-action" onClick={() => togglePlaylistTrack(track)} disabled={playlistBusy || included} aria-pressed={included}>
                        {included ? "Adicionada" : "Adicionar"}
                      </button>
                    </div>;
                  })}
                </div>
              </> : <div className="playlist-placeholder"><span>♫</span><p>Crie ou selecione uma playlist para começar.</p></div>}
            </div>
          </div>
        </section>
        <footer><a className="footer-brand" href="#">sonora<span>.</span></a><span>Feito para explorar música com curiosidade.</span><span className="footer-status"><i /> FONTES EXTERNAS</span></footer>
      </section>
    </main>
  );
}
