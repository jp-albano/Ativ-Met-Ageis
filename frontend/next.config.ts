import type { NextConfig } from "next";

/** Endereço da API Java; lido no servidor do Next, nunca exposto ao navegador. */
const configuredBackendUrl = process.env.BACKEND_URL?.trim();
const isVercel = Boolean(process.env.VERCEL);

let backendBaseUrl: string | null = null;

if (configuredBackendUrl) {
  let backend: URL;
  try {
    backend = new URL(configuredBackendUrl);
  } catch {
    throw new Error("BACKEND_URL deve ser uma URL absoluta da API Java, como https://api.exemplo.com.");
  }

  if (isVercel && backend.protocol !== "https:") {
    throw new Error("Em produção na Vercel, BACKEND_URL deve obrigatoriamente utilizar o protocolo HTTPS.");
  }

  if (!["http:", "https:"].includes(backend.protocol) || backend.username || backend.password || backend.search || backend.hash) {
    throw new Error("BACKEND_URL deve usar HTTP(S) e não pode incluir credenciais, query ou fragmento.");
  }

  backendBaseUrl = `${backend.origin}${backend.pathname.replace(/\/+$/, "")}`;
} else if (!isVercel) {
  // Em desenvolvimento local, usa o servidor Java padrão em 127.0.0.1:8080
  backendBaseUrl = "http://127.0.0.1:8080";
} else {
  // Na Vercel sem BACKEND_URL cadastrada, emite aviso sem quebrar o build estático da interface
  console.warn(
    "\n[AVISO ARQUITETURAL - VERCEL]\n" +
      "A variável BACKEND_URL não foi definida no ambiente da Vercel.\n" +
      "O build do frontend será concluído com sucesso, mas as rotas /api/* responderão com HTTP 503\n" +
      "até que a URL HTTPS da API Java seja cadastrada em 'Project Settings > Environment Variables'.\n"
  );
}

const nextConfig: NextConfig = {
  poweredByHeader: false,
  images: { remotePatterns: [{ protocol: "https", hostname: "**.sndcdn.com" }] },
  // O navegador chama /api/... na própria origem e o Next repassa ao backend Java quando configurado,
  // mantendo as credenciais do SoundCloud seguras apenas no servidor.
  async rewrites() {
    if (!backendBaseUrl) {
      return [];
    }
    return [{ source: "/api/:path*", destination: `${backendBaseUrl}/api/:path*` }];
  },
};

export default nextConfig;
