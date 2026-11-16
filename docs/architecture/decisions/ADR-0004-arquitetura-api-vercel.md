# ADR-0004 — Arquitetura da API, deploy na Vercel e desacoplamento de build

- **Status:** Proposta de Lucas Belucci Bellini; sujeita à revisão e aprovação do grupo no Pull Request.
- **Data:** 2026-11-13
- **Complementa:** ADR-0002 (API HTTP em Java) e ADR-0003 (Player web e arquivos locais).

---

## 1. Contexto

Durante o processo de build do frontend Next.js 15 na plataforma Vercel, observou-se a seguinte falha impeditiva:

```text
Failed to load next.config.ts
Error: Defina BACKEND_URL nas variáveis do projeto Vercel para apontar para a API Java.
```

### 1.1. Diagnóstico da Causa Raiz
O arquivo `frontend/next.config.ts` impunha uma trava estrita em tempo de build:
```ts
if (process.env.VERCEL && !configuredBackendUrl) {
  throw new Error("Defina BACKEND_URL nas variáveis do projeto Vercel para apontar para a API Java.");
}
```
Como o ambiente de automação da Vercel define a variável de sistema `VERCEL=1`, a compilação do Next.js abortava de forma fatal quando o deploy era disparado sem a configuração prévia da variável de ambiente `BACKEND_URL` no painel do projeto.

### 1.2. A Realidade Arquitetural Vercel vs Java
- A **Vercel** é uma plataforma serverless voltada ao ecossistema Node.js, Next.js e Edge Runtimes. Ela **não oferece suporte nativo à execução de processos Java persistentes** de longa duração (como o `com.sun.net.httpserver.HttpServer` em execução na porta 8080 em `src/ApiServer.java`).
- O servidor Java atual mantém seu estado (**catálogo de faixas, favoritos e playlists**) em estruturas na memória RAM do processo (`Map`, `List`), o que é inerentemente incompatível com instâncias serverless efêmeras sem uma camada de banco de dados persistente.
- **Restrições obrigatórias do projeto:**
  1. O frontend deve permanecer hospedado na Vercel.
  2. Não é permitida a migração para a plataforma Render nem a introdução de novos provedores ou serviços pagos sem aprovação formal.
  3. Não é permitido mascarar a falha inserindo URLs fictícias, temporárias ou apontando para `127.0.0.1`/`localhost` no ambiente de produção da Vercel.

---

## 2. Mapa dos Endpoints Atuais e Dependência de Estado

A API Java atual expõe os seguintes endpoints mapeados sob `/api`:

| Método | Rota | Descrição | Dependência de Estado / Persistência |
|---|---|---|---|
| GET | `/api/health` | Estado do servidor e verificação do SoundCloud | Consulta em memória `DiscoveryApi.soundCloudConfigured()` |
| GET | `/api/tracks` | Catálogo de faixas (suporta filtro `?q=`) | **Em memória**: `MusicLibrary.listTracks()` |
| POST | `/api/tracks` | Cadastro de nova faixa no catálogo | **Em memória**: `MusicLibrary.addTrack()` |
| GET | `/api/tracks/{id}` | Busca de faixa por ID | **Em memória**: `MusicLibrary.findTrack()` |
| DELETE | `/api/tracks/{id}` | Exclusão de faixa | **Em memória**: `MusicLibrary.removeTrack()` |
| GET | `/api/favorites` | Listagem de faixas favoritadas | **Em memória**: `MusicLibrary.listFavorites()` |
| PUT | `/api/favorites/{trackId}` | Adicionar aos favoritos | **Em memória**: `MusicLibrary.addFavorite()` |
| DELETE | `/api/favorites/{trackId}` | Remover dos favoritos | **Em memória**: `MusicLibrary.removeFavorite()` |
| GET | `/api/playlists` | Listagem de playlists | **Em memória**: `MusicLibrary.listPlaylists()` |
| POST | `/api/playlists` | Criação de playlist | **Em memória**: `MusicLibrary.createPlaylist()` |
| GET | `/api/playlists/{id}` | Detalhes e faixas da playlist | **Em memória**: `MusicLibrary.findPlaylist()` |
| DELETE | `/api/playlists/{id}` | Exclusão de playlist | **Em memória**: `MusicLibrary.deletePlaylist()` |
| PUT | `/api/playlists/{id}/tracks/{trackId}` | Adicionar faixa à playlist | **Em memória**: `MusicLibrary.addToPlaylist()` |
| DELETE | `/api/playlists/{id}/tracks/{trackId}` | Remover faixa da playlist | **Em memória**: `MusicLibrary.removeFromPlaylist()` |
| GET | `/api/discovery/search` | Busca paginada no SoundCloud (`linked_partitioning`) | **Stateless**: Provedor externo SoundCloud com cache curto de 15s |
| GET | `/api/discovery/resolve` | Resolução de permalink do SoundCloud | **Stateless**: Provedor externo SoundCloud com cache curto de 60s |
| GET | `/api/discovery/verify` | Conferência de letras e metadados | **Stateless**: Provedores externos LRCLIB e MusicBrainz com cache curto |

---

## 3. Alternativas Consideradas

| Alternativa | Prós | Contras |
|---|---|---|
| **A. Manter API Java em infraestrutura externa dedicada (servidor próprio / VM acadêmica)** | Mantém o código de domínio Java intacto; compatível com o aplicativo de terminal (ADR-0001) e ADR-0002. | Exige provisionar e operar uma máquina/servidor 24/7 com HTTPS; perda de dados em memória se o processo reiniciar. Sem essa URL, a Vercel quebra o build. |
| **B. Migração total para Next.js Route Handlers / Serverless Functions na Vercel** | Execução 100% nativa na Vercel a custo zero; elimina a necessidade de infraestrutura Java externa para o frontend. | Incompatível com o estado em memória (funções serverless são efêmeras); duplicação de regras de domínio entre Java e TypeScript; banco de dados (PostgreSQL) continua pendente. |
| **C. Arquitetura Desacoplada com Tolerância de Build e Fallback Controlado de Runtime (Escolhida)** | O build da Vercel nunca quebra por falta de infraestrutura; interface estática e player de áudio local funcionam imediatamente; segurança estrita mantida; validação rigorosa de `BACKEND_URL` quando fornecida. | Requer configurar `BACKEND_URL` no painel da Vercel para que as chamadas às rotas `/api/*` se conectem à API Java. |

---

## 4. Decisão

Adotou-se a **Alternativa C**:

1. **Desacoplamento do Ciclo de Build**:
   - `frontend/next.config.ts` não deve interromper catastroficamente o comando `next build` na ausência de `BACKEND_URL`. Em vez de disparar `throw new Error`, emite-se um aviso detalhado de compilação alertando que o deploy na Vercel está ocorrendo sem um backend conectado.
   - Quando `BACKEND_URL` estiver presente, suas regras de validação permanecem rígidas: deve ser uma URL absoluta, segura (`https:` em produção), sem credenciais, sem query string e sem fragmentos.
2. **Proxy Condicional e Prevenção de URLs Fictícias**:
   - Se `BACKEND_URL` for configurada na Vercel, o `rewrites()` do Next.js repassa `/api/:path*` diretamente para a URL do backend Java.
   - Se `BACKEND_URL` não for configurada na Vercel, **nenhuma URL fictícia ou localhost é utilizada**. O Next.js não ativa o proxy para destinos inválidos. Em vez disso, requisições do navegador para `/api/*` recebem uma resposta controlada HTTP 503 com mensagem JSON clara orientando o operador a cadastrar `BACKEND_URL` nas configurações do projeto Vercel.
3. **Preservação de Desenvolvimento Local**:
   - Fora do ambiente da Vercel (desenvolvimento local), o comportamento padrão de fallback para `http://127.0.0.1:8080` permanece preservado, permitindo que a equipe execute o servidor Java localmente sem necessidade de configuração adicional.
4. **Isolamento de Segredos**:
   - Credenciais de provedores (`SOUNDCLOUD_CLIENT_ID`, `SOUNDCLOUD_CLIENT_SECRET`) permanecem estritamente no backend e nunca são acessadas ou vazadas para o bundle estático do frontend.

---

## 5. Consequências

- **Build Verde na Vercel**: O pipeline de CI e deploy da Vercel conclui a compilação estática com sucesso.
- **Experiência do Usuário Resiliente**: A interface web (incluindo o player de arquivos locais e o catálogo livre via ADR-0003) carrega e funciona plenamente mesmo se o backend Java não estiver no ar ou ainda não tiver sido provisionado.
- **Transparência Operacional**: Se o usuário tentar acessar recursos do catálogo ou busca online sem backend conectado, a interface exibe mensagens claras de indisponibilidade em vez de falhas silenciosas ou telas brancas.
- **Pendência de Infraestrutura**: Para que os recursos do catálogo Java funcionem em produção, a equipe deve provisionar um servidor ou serviço compatível para executar `ApiServer.java` e cadastrar sua URL HTTPS em `BACKEND_URL` nas configurações do projeto na Vercel (`Project Settings > Environment Variables`).
- **Persistência Futura**: Quando o grupo aprovar formalmente uma camada de banco de dados (ex: PostgreSQL conforme diretrizes de `docs/PROJECT_RULES.md`), tanto o backend Java quanto uma eventual camada de Route Handlers poderão persistir dados de forma durável.
