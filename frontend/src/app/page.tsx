"use client";

import { FormEvent, useEffect, useState } from "react";
import { api, type CatalogTrack, type DiscoveryTrack as Track, type Verification } from "@/lib/api";

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

  // Carrega o catálogo do backend Java uma vez; se a API estiver fora, mostra o aviso na seção.
  useEffect(() => {
    api.listTracks().then(setCatalog).catch(e => setCatalogError(errorMessage(e, "Falha ao carregar o catálogo.")));
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

  return (
    <main className="app-shell">
      <aside className="sidebar">
        <a className="brand" href="#" aria-label="Sonora início"><span className="brand-mark"><i /><i /><i /></span><span>sonora<span className="brand-dot">.</span></span></a>
        <div className="side-label">SUA BIBLIOTECA</div>
        <a className="side-link active" href="#discover"><span className="side-icon">⌕</span> Descobrir música</a>
        <a className="side-link" href="#results"><span className="side-icon">♫</span> Pesquisas recentes</a>
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
        <footer><a className="footer-brand" href="#">sonora<span>.</span></a><span>Feito para explorar música com curiosidade.</span><span className="footer-status"><i /> FONTES EXTERNAS</span></footer>
      </section>
    </main>
  );
}
