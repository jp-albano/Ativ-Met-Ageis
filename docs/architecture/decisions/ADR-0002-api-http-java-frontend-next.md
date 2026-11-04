# ADR-0002 — API HTTP em Java consumida pelo frontend Next.js

- **Status:** Aceita pelo autor da alteração (Lucas Belucci Bellini); sujeita à revisão do grupo no PR.
- **Data:** 2026-11-01
- **Complementa:** ADR-0001 (o app de terminal continua) e resolve o item "Backend web: PENDENTE" do índice de decisões.

## Contexto

O projeto tinha duas implementações paralelas da mesma lógica:

- o domínio Java (catálogo, favoritos, playlists e integrações SoundCloud/LRCLIB/MusicBrainz), usado só pelo menu de terminal;
- rotas de servidor do Next.js (`/api/search`, `/api/verify`) que chamavam os provedores externos diretamente, duplicando a lógica Java e exigindo o token do SoundCloud no processo do frontend.

Além disso, o catálogo, os favoritos e as playlists não estavam acessíveis pela interface web. O pedido foi "conectar a API" e congelar o backend para que o trabalho seguinte fosse apenas no frontend.

## Alternativas consideradas

| Opção | Prós | Contras |
|---|---|---|
| **A. Servidor HTTP embutido do JDK (`com.sun.net.httpserver`)** | Nenhuma dependência nova; reaproveita todo o domínio Java; pequeno e testável | Roteamento manual; sem recursos prontos de framework |
| B. Spring Boot / Javalin | Roteamento e validação prontos | Nova dependência e estrutura; exigiria migração para pacotes nomeados; muito maior que o escopo acadêmico |
| C. Manter a lógica no Next.js | Nenhuma mudança no Java | Duplicação permanente; domínio Java (playlists, favoritos) continuaria inacessível pela web |

## Decisão

Opção **A**. A classe `ApiServer` expõe o domínio por HTTP/JSON sob `/api` (contrato em [`docs/API.md`](../../API.md)):

- `MusicLibrary` é uma fachada `synchronized` sobre os serviços em memória, devolvendo records imutáveis.
- `DiscoveryApi` concentra as proteções que antes estavam em `frontend/src/lib/resilient-fetch.ts`: limite de 8 chamadas externas simultâneas, cache curto e tolerância a falha parcial.
- `SoundCloudSearchService` passa a aceitar `SOUNDCLOUD_CLIENT_ID`/`SOUNDCLOUD_CLIENT_SECRET` (OAuth client credentials com renovação automática), além do token fixo.
- O Next.js repassa `/api/:path*` para `BACKEND_URL` via `rewrites`; as rotas de servidor antigas foram removidas. Segredos ficam apenas no backend.

## Consequências

- Uma única implementação das regras e integrações; o frontend só consome HTTP.
- Estado em memória: reiniciar a API perde favoritos, playlists e faixas cadastradas.
- Sem autenticação: a API escuta em `127.0.0.1` por padrão e **não deve ser exposta publicamente** nesse estado.
- O backend é considerado **congelado** após esta alteração; mudanças futuras devem ser pedidas na seção "Pedidos para a equipe de backend" do README.
- Persistência, autenticação e migração para pacotes nomeados continuam pendentes e exigem ADRs próprios.
