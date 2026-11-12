/** Formata segundos como `m:ss` ou `h:mm:ss` para a barra do player. */
export function formatTime(totalSeconds: number): string {
  if (!Number.isFinite(totalSeconds) || totalSeconds < 0) return "0:00";
  const seconds = Math.floor(totalSeconds);
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const rest = seconds % 60;
  if (hours > 0) return `${hours}:${String(minutes).padStart(2, "0")}:${String(rest).padStart(2, "0")}`;
  return `${minutes}:${String(rest).padStart(2, "0")}`;
}

/** A API recusa duração negativa ou acima de 24 horas. */
export function clampDuration(seconds: number): number {
  if (!Number.isFinite(seconds) || seconds < 0) return 0;
  return Math.min(86_400, Math.round(seconds));
}

/**
 * Separa "Artista - Título.mp3" em campos de cadastro.
 * Sem o separador, o arquivo inteiro vira o título.
 */
export function titleFromFileName(fileName: string): { title: string; artist: string } {
  const base = fileName.replace(/\.[^.]+$/, "").replace(/[_]+/g, " ").trim();
  const parts = base.split(/\s+[-–—]\s+/);
  if (parts.length >= 2 && parts[0].trim() && parts.slice(1).join(" - ").trim()) {
    return {
      artist: parts[0].trim(),
      title: parts.slice(1).join(" - ").trim(),
    };
  }
  return { artist: "Artista desconhecido", title: base || "Faixa sem título" };
}

/** Cor estável para a capa gerada quando a faixa não tem imagem. */
export function coverColor(seed: string): string {
  let hash = 0;
  for (let index = 0; index < seed.length; index += 1) hash = (hash * 31 + seed.charCodeAt(index)) >>> 0;
  return `hsl(${hash % 360} 38% 36%)`;
}
