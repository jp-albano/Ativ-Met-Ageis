# Guia de Configuração da API SoundCloud no Vercel

Este documento descreve **todos os passos necessários** para que a integração da SoundCloud funcione corretamente quando o projeto é implantado no Vercel. Ele cobre variáveis de ambiente, configuração no painel do Vercel, build local, testes e verificação dos endpoints serverless.

---

## 1. Variáveis de Ambiente Obrigatórias
| Nome da variável | Descrição | Onde definir | Exemplo |
|---|---|---|---|
| `SOUNDCLOUD_CLIENT_ID` | ID público da sua aplicação SoundCloud. | Vercel → *Project Settings* → *Environment Variables* (Production e Preview) | `1234567890abcdef` |
| `SOUNDCLOUD_CLIENT_SECRET` | Secret da sua aplicação SoundCloud. **Nunca** exponha ao cliente. | Vercel → *Project Settings* → *Environment Variables* | `abcd1234efgh5678` |
| `SOUNDCLOUD_ACCESS_TOKEN` *(opcional)* | Token de acesso obtido via client‑credentials. Se não for definido, a aplicação gera/renova automaticamente usando `CLIENT_ID`/`CLIENT_SECRET`. | Vercel → *Project Settings* → *Environment Variables* | `eyJhbGciOi...` |
| `BACKEND_URL` *(opcional)* | URL do backend Java que ainda fornece catálogos e favoritos. Caso não seja configurada, as rotas `/api/*` que dependem do backend retornam **503 Service Unavailable**, mas o resto do site continua funcionando. | Vercel → *Project Settings* → *Environment Variables* | `https://my-backend.example.com` |

> **Importante:** As variáveis acima **não** devem ser prefixadas com `NEXT_PUBLIC_`. Elas são consumidas apenas nas *Serverless Functions* e nunca chegam ao navegador.

---

## 2. Configurando no Vercel
1. Acesse o painel do seu projeto no Vercel.
2. Clique em **Settings → Environment Variables**.
3. Adicione cada variável listada acima. Selecione o escopo **Production** (e **Preview** se quiser testar em preview branches).
4. Salve as alterações.
5. (Opcional) Ative **Build & Development Settings → Output Directory** como `.` (padrão) – não é necessário mudar nada.

---

## 3. Como a aplicação lida com a ausência de `BACKEND_URL`
- No arquivo `frontend/next.config.ts` foi inserido um **aviso** (`console.warn`) quando a variável está ausente e o projeto está rodando em modo Vercel (`process.env.VERCEL`).
- Um *fallback* genérico (`/api/[...path]/route.ts`) responde **503** com JSON:
  ```json
  { "error": "Backend URL não configurado. Algumas rotas não estão disponíveis." }
  ```
- Todas as rotas relacionadas ao SoundCloud (`/api/discovery/*`) continuam operacionais, pois utilizam apenas as credenciais da SoundCloud.

---

## 4. Testando Localmente
1. Crie um arquivo `.env.local` na raiz do repositório (não commitado) com o mesmo conjunto de variáveis:
   ```env
   SOUNDCLOUD_CLIENT_ID=seu_id
   SOUNDCLOUD_CLIENT_SECRET=seu_secret
   # SOUNDCLOUD_ACCESS_TOKEN opcional
   # BACKEND_URL opcional
   ```
2. Instale as dependências (se ainda não fez):
   ```bash
   npm ci
   ```
3. Rode a aplicação em modo desenvolvimento:
   ```bash
   npm run dev
   ```
4. Verifique os endpoints serverless via `curl` ou navegador:
   ```bash
   curl "http://localhost:3000/api/discovery/search?q=beatles"
   curl "http://localhost:3000/api/discovery/resolve?url=https://soundcloud.com/artist/track"
   curl "http://localhost:3000/api/discovery/verify?url=https://soundcloud.com/artist/track"
   ```
   Respostas devem ser JSON com os campos esperados (tracks, nextHref, verification).

---

## 5. Deploy para Vercel
- **Deploy rápido (CLI)**:
  ```bash
  vercel --prod
  ```
- O Vercel irá instalar as dependências, compilar o Next.js e gerar as *Serverless Functions* sob `/api/discovery/*`.
- Caso `BACKEND_URL` não esteja configurada, o build ainda **não falha** – apenas o aviso no console aparece.

---

## 6. Verificando o Deploy
1. Após o deploy, abra a URL fornecida (ex.: `https://my-project.vercel.app`).
2. Teste os mesmos `curl`s apontando para o domínio Vercel:
   ```bash
   curl "https://my-project.vercel.app/api/discovery/search?q=radiohead"
   ```
3. Se tudo estiver correto, você receberá uma lista de faixas SoundCloud e o campo `nextHref` (quando houver paginação).
4. Caso receba **503**, verifique se `BACKEND_URL` era necessária para a rota que chamou.

---

## 7. Considerações de Performance & Rate‑Limit
- O cliente interno da SoundCloud (arquivo `src/lib/server/soundcloud.ts`) já implementa:
  - **Refresh automático** do `access_token` usando *client‑credentials*.
  - **Retry** com back‑off quando a API responde `401` ou `429`.
  - **Paginação** via parâmetro `linked_partitioning` – o campo `nextHref` pode ser passado novamente ao endpoint `/api/discovery/search`.
- Não há cache interno; se precisar de caching, adicione um CDN/Edge Cache no Vercel (opcional).

---

## 8. Próximos Passos (já implementados)
- Rotas serverless criadas em:
  - `src/app/api/discovery/search/route.ts`
  - `src/app/api/discovery/resolve/route.ts`
  - `src/app/api/discovery/verify/route.ts`
- Módulos de suporte em `src/lib/server/`.
- Testes unitários (`frontend/src/lib/backend-config.test.ts`) e cobertura de integração.
- Arquitetura documentada em `docs/architecture/decisions/ADR-0004-arquitetura-api-vercel.md`.

> **Tudo está pronto** para ser comitado e enviado ao repositório. Caso precise de mais alguma coisa, avise!