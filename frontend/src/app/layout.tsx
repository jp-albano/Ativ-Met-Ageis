import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Sonora — player de música",
  description: "Toque arquivos locais, organize playlists e favoritos e pesquise faixas no catálogo.",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="pt-BR"><body>{children}</body></html>;
}
