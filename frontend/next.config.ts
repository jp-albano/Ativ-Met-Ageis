import type { NextConfig } from "next";

/** Endereço da API Java; lido no servidor do Next, nunca exposto ao navegador. */
const backendUrl = (process.env.BACKEND_URL ?? "http://127.0.0.1:8080").replace(/\/+$/, "");

const nextConfig: NextConfig = {
  poweredByHeader: false,
  images: { remotePatterns: [{ protocol: "https", hostname: "**.sndcdn.com" }] },
  // O navegador chama /api/... na própria origem e o Next repassa ao backend Java,
  // evitando CORS e mantendo as credenciais do SoundCloud apenas no servidor.
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${backendUrl}/api/:path*` }];
  },
};

export default nextConfig;
