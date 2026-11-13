import { describe, it } from "node:test";
import assert from "node:assert/strict";

describe("validação arquitetural de backend na Vercel", () => {
  function validateBackend(url: string | undefined, isVercel: boolean) {
    const raw = url?.trim();
    if (!raw) {
      if (isVercel) {
        return { status: "unconfigured_vercel", base: null };
      }
      return { status: "local_default", base: "http://127.0.0.1:8080" };
    }

    let parsed: URL;
    try {
      parsed = new URL(raw);
    } catch {
      throw new Error("BACKEND_URL deve ser uma URL absoluta da API Java, como https://api.exemplo.com.");
    }

    if (isVercel && parsed.protocol !== "https:") {
      throw new Error("Em produção na Vercel, BACKEND_URL deve obrigatoriamente utilizar o protocolo HTTPS.");
    }

    if (
      !["http:", "https:"].includes(parsed.protocol) ||
      parsed.username ||
      parsed.password ||
      parsed.search ||
      parsed.hash
    ) {
      throw new Error("BACKEND_URL deve usar HTTP(S) e não pode incluir credenciais, query ou fragmento.");
    }

    return { status: "configured", base: `${parsed.origin}${parsed.pathname.replace(/\/+$/, "")}` };
  }

  it("permite build na Vercel sem BACKEND_URL sem gerar URL fictícia", () => {
    const res = validateBackend(undefined, true);
    assert.equal(res.status, "unconfigured_vercel");
    assert.equal(res.base, null);
  });

  it("usa 127.0.0.1:8080 em ambiente de desenvolvimento local quando não definida", () => {
    const res = validateBackend(undefined, false);
    assert.equal(res.status, "local_default");
    assert.equal(res.base, "http://127.0.0.1:8080");
  });

  it("aceita URL HTTPS válida na Vercel", () => {
    const res = validateBackend("https://api-music.exemplo.edu.br", true);
    assert.equal(res.status, "configured");
    assert.equal(res.base, "https://api-music.exemplo.edu.br");
  });

  it("rejeita URL HTTP insegura em ambiente de produção da Vercel", () => {
    assert.throws(
      () => validateBackend("http://api-music.exemplo.edu.br", true),
      /utilizar o protocolo HTTPS/
    );
  });

  it("rejeita URLs com credenciais, query strings ou fragmentos", () => {
    assert.throws(
      () => validateBackend("https://user:pass@api.exemplo.com", false),
      /não pode incluir credenciais/
    );
    assert.throws(
      () => validateBackend("https://api.exemplo.com?token=123", false),
      /não pode incluir credenciais/
    );
    assert.throws(
      () => validateBackend("https://api.exemplo.com#secao", false),
      /não pode incluir credenciais/
    );
  });
});
