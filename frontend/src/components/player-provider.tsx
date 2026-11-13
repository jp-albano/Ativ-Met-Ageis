"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { getAudioBlob } from "@/lib/local-audio";

export type RepeatMode = "off" | "all" | "one";

/**
 * Faixa da fila. Com `streamUrl`, o navegador toca o endereço remoto.
 * Sem esse campo, o áudio vem do arquivo importado no IndexedDB (`audioId`).
 */
export type PlayableTrack = {
  audioId: string;
  title: string;
  artist: string;
  durationSeconds: number;
  catalogId?: string;
  streamUrl?: string;
};

type PlayerContextValue = {
  queue: PlayableTrack[];
  index: number;
  current: PlayableTrack | null;
  playing: boolean;
  currentTime: number;
  duration: number;
  volume: number;
  shuffle: boolean;
  repeat: RepeatMode;
  error: string;
  playQueue: (tracks: PlayableTrack[], startIndex?: number) => void;
  toggle: () => void;
  next: () => void;
  previous: () => void;
  seek: (seconds: number) => void;
  setVolume: (volume: number) => void;
  toggleShuffle: () => void;
  cycleRepeat: () => void;
  enqueue: (track: PlayableTrack) => void;
  clearQueue: () => void;
  dismissError: () => void;
};

const PlayerContext = createContext<PlayerContextValue | null>(null);
const VOLUME_KEY = "sonora-volume";

export function pickNext(length: number, index: number, shuffle: boolean): number | null {
  if (length <= 0) return null;
  if (shuffle) {
    if (length === 1) return null;
    let pick = index;
    while (pick === index) pick = Math.floor(Math.random() * length);
    return pick;
  }
  if (index + 1 < length) return index + 1;
  return null;
}

export function PlayerProvider({ children }: { children: ReactNode }) {
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const urlRef = useRef<string | null>(null);
  const queueRef = useRef<PlayableTrack[]>([]);
  const indexRef = useRef(-1);
  const repeatRef = useRef<RepeatMode>("off");
  const shuffleRef = useRef(false);
  const historyRef = useRef<number[]>([]);
  const volumeRef = useRef(0.8);
  const generation = useRef(0);
  const suppressEnded = useRef(false);

  const [queue, setQueue] = useState<PlayableTrack[]>([]);
  const [index, setIndex] = useState(-1);
  const [playing, setPlaying] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [volume, setVolumeState] = useState(0.8);
  const [shuffle, setShuffle] = useState(false);
  const [repeat, setRepeat] = useState<RepeatMode>("off");
  const [error, setError] = useState("");

  const stopAudio = useCallback(() => {
    const audio = audioRef.current;
    if (!audio) return;
    suppressEnded.current = true;
    audio.pause();
    audio.removeAttribute("src");
    audio.load();
    if (urlRef.current) URL.revokeObjectURL(urlRef.current);
    urlRef.current = null;
  }, []);

  const playIndex = useCallback(async (nextIndex: number, list: PlayableTrack[], pushHistory: boolean) => {
    const track = list[nextIndex];
    const audio = audioRef.current;
    if (!track || !audio) {
      setPlaying(false);
      return;
    }
    if (pushHistory && indexRef.current >= 0 && indexRef.current !== nextIndex) historyRef.current.push(indexRef.current);
    const token = ++generation.current;
    let url = track.streamUrl ?? "";
    if (track.streamUrl) {
      if (urlRef.current) {
        URL.revokeObjectURL(urlRef.current);
        urlRef.current = null;
      }
    } else {
      const blob = await getAudioBlob(track.audioId);
      if (token !== generation.current) return;
      if (!blob) {
        setError("Não encontrei o arquivo desta faixa. Importe o áudio de novo.");
        setPlaying(false);
        return;
      }
      if (urlRef.current) URL.revokeObjectURL(urlRef.current);
      url = URL.createObjectURL(blob);
      urlRef.current = url;
    }
    if (token !== generation.current) return;
    suppressEnded.current = true;
    audio.src = url;
    audio.volume = volumeRef.current;
    try {
      await audio.play();
      if (token !== generation.current) return;
      suppressEnded.current = false;
      setError("");
      setPlaying(true);
    } catch (reason) {
      if (token !== generation.current) return;
      suppressEnded.current = false;
      setPlaying(false);
      if (reason instanceof DOMException && reason.name === "NotAllowedError") return;
      setError(track.streamUrl
        ? "Não foi possível reproduzir esta gravação agora. Tente outra faixa do catálogo."
        : "O navegador não conseguiu reproduzir este arquivo. Use MP3, WAV, OGG ou M4A.");
    }
    indexRef.current = nextIndex;
    queueRef.current = list;
    setIndex(nextIndex);
    setQueue(list);
    setCurrentTime(0);
  }, []);

  const goNext = useCallback((fromEnded: boolean) => {
    const list = queueRef.current;
    if (fromEnded && repeatRef.current === "one") {
      const audio = audioRef.current;
      if (!audio) return;
      audio.currentTime = 0;
      void audio.play();
      return;
    }
    const following = pickNext(list.length, indexRef.current, shuffleRef.current);
    if (following === null) {
      if ((fromEnded ? repeatRef.current === "all" : repeatRef.current !== "off") && list.length > 0) {
        const start = shuffleRef.current ? Math.floor(Math.random() * list.length) : 0;
        void playIndex(start, list, true);
        return;
      }
      if (fromEnded) setPlaying(false);
      return;
    }
    void playIndex(following, list, true);
  }, [playIndex]);

  useEffect(() => {
    const audio = new Audio();
    audio.preload = "metadata";
    audioRef.current = audio;
    try {
      const stored = Number(localStorage.getItem(VOLUME_KEY));
      if (Number.isFinite(stored) && stored >= 0 && stored <= 1) {
        volumeRef.current = stored;
        audio.volume = stored;
        setVolumeState(stored);
      }
    } catch {
      // Volume padrão quando o navegador bloqueia o localStorage.
    }
    const onTime = () => setCurrentTime(audio.currentTime || 0);
    const onMeta = () => setDuration(Number.isFinite(audio.duration) ? audio.duration : 0);
    const onPlay = () => setPlaying(true);
    const onPause = () => setPlaying(false);
    const onEnded = () => {
      if (suppressEnded.current) return;
      goNext(true);
    };
    audio.addEventListener("timeupdate", onTime);
    audio.addEventListener("loadedmetadata", onMeta);
    audio.addEventListener("durationchange", onMeta);
    audio.addEventListener("play", onPlay);
    audio.addEventListener("pause", onPause);
    audio.addEventListener("ended", onEnded);
    return () => {
      generation.current += 1;
      audio.pause();
      audio.removeEventListener("timeupdate", onTime);
      audio.removeEventListener("loadedmetadata", onMeta);
      audio.removeEventListener("durationchange", onMeta);
      audio.removeEventListener("play", onPlay);
      audio.removeEventListener("pause", onPause);
      audio.removeEventListener("ended", onEnded);
      audio.removeAttribute("src");
      if (urlRef.current) URL.revokeObjectURL(urlRef.current);
    };
  }, [goNext]);

  const playQueue = useCallback((tracks: PlayableTrack[], startIndex = 0) => {
    if (!tracks.length) return;
    historyRef.current = [];
    const safe = Math.min(Math.max(startIndex, 0), tracks.length - 1);
    void playIndex(safe, tracks, false);
  }, [playIndex]);

  const toggle = useCallback(() => {
    const audio = audioRef.current;
    if (!audio || indexRef.current < 0) return;
    if (audio.paused) void audio.play().catch(() => setPlaying(false));
    else audio.pause();
  }, []);

  const next = useCallback(() => goNext(false), [goNext]);

  const previous = useCallback(() => {
    const audio = audioRef.current;
    if (audio && audio.currentTime > 3) {
      audio.currentTime = 0;
      return;
    }
    const prev = historyRef.current.pop();
    if (prev !== undefined) {
      void playIndex(prev, queueRef.current, false);
      return;
    }
    if (indexRef.current > 0) void playIndex(indexRef.current - 1, queueRef.current, false);
    else if (audio) audio.currentTime = 0;
  }, [playIndex]);

  const seek = useCallback((seconds: number) => {
    const audio = audioRef.current;
    if (!audio || indexRef.current < 0) return;
    const limit = Number.isFinite(audio.duration) ? audio.duration : Math.max(seconds, 0);
    audio.currentTime = Math.min(Math.max(seconds, 0), limit);
    setCurrentTime(audio.currentTime);
  }, []);

  const setVolume = useCallback((value: number) => {
    const nextVolume = Math.min(1, Math.max(0, value));
    volumeRef.current = nextVolume;
    if (audioRef.current) audioRef.current.volume = nextVolume;
    setVolumeState(nextVolume);
    try {
      localStorage.setItem(VOLUME_KEY, String(nextVolume));
    } catch {
      // A reprodução continua mesmo sem gravar a preferência.
    }
  }, []);

  const toggleShuffle = useCallback(() => {
    setShuffle(current => {
      shuffleRef.current = !current;
      return !current;
    });
  }, []);

  const cycleRepeat = useCallback(() => {
    setRepeat(current => {
      const nextMode: RepeatMode = current === "off" ? "all" : current === "all" ? "one" : "off";
      repeatRef.current = nextMode;
      return nextMode;
    });
  }, []);

  const enqueue = useCallback((track: PlayableTrack) => {
    const list = [...queueRef.current, track];
    queueRef.current = list;
    setQueue(list);
    if (indexRef.current < 0) void playIndex(0, list, false);
  }, [playIndex]);

  const clearQueue = useCallback(() => {
    generation.current += 1;
    stopAudio();
    queueRef.current = [];
    indexRef.current = -1;
    historyRef.current = [];
    setQueue([]);
    setIndex(-1);
    setPlaying(false);
    setCurrentTime(0);
    setDuration(0);
  }, [stopAudio]);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      const target = event.target;
      if (target instanceof HTMLElement) {
        const tag = target.tagName;
        if (tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT" || target.isContentEditable) return;
      }
      if (event.code === "Space") {
        event.preventDefault();
        toggle();
      } else if (event.code === "ArrowRight" || event.code === "ArrowLeft") {
        const audio = audioRef.current;
        if (!audio || indexRef.current < 0) return;
        event.preventDefault();
        const delta = event.code === "ArrowRight" ? 5 : -5;
        const base = audio.currentTime || 0;
        const limit = Number.isFinite(audio.duration) ? audio.duration : base + 5;
        audio.currentTime = Math.min(limit, Math.max(0, base + delta));
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [toggle]);

  useEffect(() => {
    if (!("mediaSession" in navigator)) return;
    const track = index >= 0 ? queue[index] : undefined;
    navigator.mediaSession.metadata = track ? new MediaMetadata({ title: track.title, artist: track.artist }) : null;
    const setHandler = (action: MediaSessionAction, handler: () => void) => {
      try {
        navigator.mediaSession.setActionHandler(action, handler);
      } catch {
        // O navegador pode não oferecer o atalho.
      }
    };
    setHandler("play", toggle);
    setHandler("pause", toggle);
    setHandler("nexttrack", next);
    setHandler("previoustrack", previous);
  }, [queue, index, toggle, next, previous]);

  const current = index >= 0 ? queue[index] ?? null : null;
  const value = useMemo<PlayerContextValue>(() => ({
    queue,
    index,
    current,
    playing,
    currentTime,
    duration,
    volume,
    shuffle,
    repeat,
    error,
    playQueue,
    toggle,
    next,
    previous,
    seek,
    setVolume,
    toggleShuffle,
    cycleRepeat,
    enqueue,
    clearQueue,
    dismissError: () => setError(""),
  }), [queue, index, current, playing, currentTime, duration, volume, shuffle, repeat, error, playQueue, toggle, next, previous, seek, setVolume, toggleShuffle, cycleRepeat, enqueue, clearQueue]);

  return <PlayerContext.Provider value={value}>{children}</PlayerContext.Provider>;
}

export function usePlayer(): PlayerContextValue {
  const context = useContext(PlayerContext);
  if (!context) throw new Error("usePlayer precisa ficar dentro de PlayerProvider.");
  return context;
}
