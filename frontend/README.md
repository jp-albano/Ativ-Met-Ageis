# Sonora — interface web

Interface em Next.js e TypeScript que consome a API Java do projeto e toca música no navegador.

O player cobre as funções básicas de um programa de música: catálogo pronto para tocar, play, pausa, anterior, próxima, barra de progresso, volume, aleatório, repetir e fila. As 30 faixas iniciais estão em [`src/data/free-catalog.ts`](src/data/free-catalog.ts): gravações com licença Creative Commons ou domínio público, reproduzidas direto do Internet Archive, sem copiar o arquivo para o repositório. A busca em `/music/search` procura mais faixas com o mesmo tipo de licença.

Favoritos, playlists e o catálogo da API Java continuam disponíveis. A busca no SoundCloud e a conferência de letras ficam numa seção separada e não trazem o áudio dessa plataforma. Também é possível importar um arquivo local; ele fica só no IndexedDB deste navegador. Ver [ADR-0003](../docs/architecture/decisions/ADR-0003-player-web-arquivos-locais.md) e [ADR-0004](../docs/architecture/decisions/ADR-0004-arquitetura-api-vercel.md).

## Requisitos
- Node.js 20.9 ou superior
- npm
- API Java rodando para catálogo/descoberta (veja "Como rodar a API e a interface web" no [README principal](../README.md))

## Executar localmente

```bash
cd frontend
npm install
# Opcional: copie .env.example para .env.local se a API não estiver em http://127.0.0.1:8080
npm run dev
```

Abra http://localhost:3000.

## Como o frontend fala com a API e deploy na Vercel

- **Chamadas relativas**: O navegador chama apenas caminhos relativos `/api/...`.
- **Ambiente local**: O `next.config.ts` repassa as requisições para `BACKEND_URL` (padrão local `http://127.0.0.1:8080`).
- **Ambiente de produção (Vercel)**:
  - O build estático da Vercel compila com sucesso mesmo que o backend ainda não tenha sido cadastrado.
  - Para conectar a interface ao servidor Java em produção, cadastre a variável `BACKEND_URL` no painel da Vercel (`Project Settings > Environment Variables`) contendo a URL pública HTTPS da sua API Java (ex: `https://api.meudominio.edu.br`).
  - Em produção na Vercel, o uso de `https:` é obrigatório para evitar bloqueios de conteúdo misto (*Mixed Content*).
  - Se `BACKEND_URL` não for configurada na Vercel, o sistema não inventa URLs falsas: as rotas `/api/*` respondem com status HTTP 503 controlado explicando a necessidade da configuração, enquanto o player de músicas locais e o catálogo livre continuam operando normalmente.
- Use sempre o cliente tipado [`src/lib/api.ts`](src/lib/api.ts); ele converte erros e falta de conexão em mensagens prontas para exibir.
- O contrato completo das rotas está em [`docs/API.md`](../docs/API.md).
- **Segurança**: O frontend não armazena chaves ou tokens. Segredos de provedores externos (`SOUNDCLOUD_CLIENT_ID`, `SOUNDCLOUD_CLIENT_SECRET`) permanecem restritos ao backend. Nunca use variáveis `NEXT_PUBLIC_*` para segredos.

## Verificações

```bash
npm test
npm run lint
npm run build
```

A UI identifica correspondências aproximadas e não certifica autoria/direitos autorais. O catálogo pronto transmite gravações cuja página de origem declara Creative Commons ou domínio público. Capas e letras do SoundCloud são exibidas apenas quando o provedor retorna esses dados. Um arquivo importado pelo usuário não é enviado ao servidor Java.
