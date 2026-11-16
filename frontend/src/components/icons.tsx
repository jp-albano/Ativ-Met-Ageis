/**
 * Ícones em SVG inline usados no app Sonora.
 *
 * Por que SVG e não emoji (♪ ♥ ♡): emoji muda de desenho entre sistemas,
 * não herda a cor do texto e alguns leitores de tela leem o nome do símbolo.
 * Aqui todo ícone é decorativo (`aria-hidden`); o nome acessível fica sempre
 * no botão ou link que o contém (`aria-label` ou texto visível).
 */

export type IconName =
  | "home"
  | "search"
  | "library"
  | "heart"
  | "plus"
  | "upload"
  | "play"
  | "pause"
  | "previous"
  | "next"
  | "shuffle"
  | "repeat"
  | "repeat-one"
  | "queue"
  | "volume"
  | "trash"
  | "close"
  | "external"
  | "music";

/** Ícones desenhados com preenchimento sólido; os demais usam só contorno. */
const FILLED: ReadonlySet<IconName> = new Set(["play", "pause", "previous", "next"]);

const PATHS: Record<IconName, string[]> = {
  home: ["M3 10.5 12 3l9 7.5", "M5 9.5V21h5v-6h4v6h5V9.5"],
  search: ["M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14Z", "m20 20-4-4"],
  library: ["M4 4v16", "M9 4v16", "m14 4.5 4.5 15"],
  heart: ["M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10Z"],
  plus: ["M12 5v14", "M5 12h14"],
  upload: ["M12 16V4", "m7 9 5-5 5 5", "M4 20h16"],
  play: ["M7 4.5v15l12-7.5z"],
  pause: ["M6 5h4v14H6z", "M14 5h4v14h-4z"],
  previous: ["M6 5h2v14H6z", "M19 5v14L9 12z"],
  next: ["M16 5h2v14h-2z", "M5 5v14l10-7z"],
  shuffle: ["M16 4h4v4", "M4 20 20 4", "M20 16v4h-4", "m15 15 5 5", "m4 4 5 5"],
  repeat: ["M17 2l3 3-3 3", "M4 11V9a4 4 0 0 1 4-4h12", "M7 22l-3-3 3-3", "M20 13v2a4 4 0 0 1-4 4H4"],
  "repeat-one": ["M17 2l3 3-3 3", "M4 11V9a4 4 0 0 1 4-4h12", "M7 22l-3-3 3-3", "M20 13v2a4 4 0 0 1-4 4H4", "M11 10h1v5"],
  queue: ["M4 6h12", "M4 12h12", "M4 18h8", "M18 14v6", "m15 17 3 3 3-3"],
  volume: ["M4 9v6h4l5 4V5L8 9H4Z", "M16.5 8.5a5 5 0 0 1 0 7"],
  trash: ["M4 7h16", "M10 11v6", "M14 11v6", "M6 7l1 13h10l1-13", "M9 7V4h6v3"],
  close: ["M6 6l12 12", "M18 6 6 18"],
  external: ["M14 4h6v6", "M20 4 10 14", "M18 14v6H4V6h6"],
  music: ["M9 18V5l11-2v13", "M6 21a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z", "M17 19a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z"],
};

/**
 * Desenha um ícone pelo nome.
 *
 * @param name  qual ícone desenhar.
 * @param size  lado em pixels (padrão 20).
 * @param filled força preenchimento sólido; usado no coração de "favoritada".
 */
export function Icon({ name, size = 20, filled }: { name: IconName; size?: number; filled?: boolean }) {
  const solid = filled ?? FILLED.has(name);
  return (
    <svg
      aria-hidden="true"
      focusable="false"
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill={solid ? "currentColor" : "none"}
      stroke={solid && FILLED.has(name) ? "none" : "currentColor"}
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      {PATHS[name].map(path => <path key={path} d={path} />)}
    </svg>
  );
}
