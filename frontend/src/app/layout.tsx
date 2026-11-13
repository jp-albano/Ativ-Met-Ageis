import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Sonora — player de música",
  description: "Toque arquivos locais, organize playlists e favoritos e pesquise faixas no catálogo.",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  // Extensões do navegador inserem atributos em <html> e <body> antes da hidratação.
  // Sem este aviso, o React trata essa diferença como erro e cobre a página.
  return (
    <html lang="pt-BR" suppressHydrationWarning>
      <body suppressHydrationWarning>{children}</body>
    </html>
  );
}
