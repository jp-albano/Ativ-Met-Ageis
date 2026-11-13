import type { NextConfig } from "next";

/** Endereço da API Java; lido no servidor do Next, nunca exposto ao navegador. */
const configuredBackendUrl = process.env.BACKEND_URL?.trim();

if (process.env.VERCEL && !configuredBackendUrl) {
  throw new Error("Defina BACKEND_URL nas variáveis do projeto Vercel para apontar para a API Java.");
}

const backendUrl = (configuredBackendUrl ?? "http://127.0.0.1:8080").replace(/\/+$/, "");
let backend: URL;

try {
  backend = new URL(backendUrl);
} catch {
  throw new Error("BACKEND_URL deve ser uma URL absoluta da API Java, como https://api.exemplo.com.");
}

if (!["http:", "https:"].includes(backend.protocol) || backend.username || backend.password || backend.search || backend.hash) {
  throw new Error("BACKEND_URL deve usar HTTP(S) e não pode incluir credenciais, query ou fragmento.");
}

const backendBaseUrl = `${backend.origin}${backend.pathname.replace(/\/+$/, "")}`;

const nextConfig: NextConfig = {
  poweredByHeader: false,
  images: { remotePatterns: [{ protocol: "https", hostname: "**.sndcdn.com" }] },
  // O navegador chama /api/... na própria origem e o Next repassa ao backend Java,
  // evitando CORS e mantendo as credenciais do SoundCloud apenas no servidor.
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${backendBaseUrl}/api/:path*` }];
  },
};

export default nextConfig;
