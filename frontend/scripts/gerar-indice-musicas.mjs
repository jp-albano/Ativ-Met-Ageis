// Gera public/local-music/index.json a partir dos arquivos de áudio dessa pasta.
// A pasta é ignorada pelo Git (músicas têm direitos autorais): cada pessoa coloca
// as próprias músicas lá e roda `npm run musicas` para o player de teste enxergá-las.
import { readdirSync, writeFileSync, existsSync } from "node:fs";
import { join, extname, basename } from "node:path";

const dir = join(import.meta.dirname, "..", "public", "local-music");
if (!existsSync(dir)) {
  console.log("Pasta public/local-music não existe; nada a indexar.");
  process.exit(0);
}

const audio = new Set([".mp3", ".m4a", ".ogg", ".wav", ".flac", ".aac"]);
const tracks = readdirSync(dir)
  .filter(file => audio.has(extname(file).toLowerCase()))
  .sort((a, b) => a.localeCompare(b, "pt-BR"))
  .map(file => {
    // Convenção dos nomes: "Artista - Título.ext". Sem " - ", o nome inteiro vira o título.
    const name = basename(file, extname(file));
    const sep = name.indexOf(" - ");
    return {
      file: `/local-music/${encodeURIComponent(file)}`,
      artist: sep > 0 ? name.slice(0, sep) : "Desconhecido",
      title: sep > 0 ? name.slice(sep + 3) : name,
    };
  });

writeFileSync(join(dir, "index.json"), JSON.stringify(tracks, null, 2));
console.log(`${tracks.length} música(s) indexada(s) em public/local-music/index.json`);
