# Sonora — interface web

Interface em Next.js e TypeScript que consome a API Java do projeto e toca música no navegador.

O player cobre as funções básicas de um programa de música: catálogo pronto para tocar, play, pausa, anterior, próxima, barra de progresso, volume, aleatório, repetir e fila. As 30 faixas iniciais estão em [`src/data/free-catalog.ts`](src/data/free-catalog.ts): gravações com licença Creative Commons ou domínio público, reproduzidas direto do Internet Archive, sem copiar o arquivo para o repositório. A busca em `/music/search` procura mais faixas com o mesmo tipo de licença.

Favoritos, playlists e o catálogo da API Java continuam disponíveis. A busca no SoundCloud e a conferência de letras ficam numa seção separada e não trazem o áudio dessa plataforma. Também é possível importar um arquivo local; ele fica só no IndexedDB deste navegador. Ver [ADR-0003](../docs/architecture/decisions/ADR-0003-player-web-arquivos-locais.md).

## Requisitos
- Node.js 20.9 ou superior
- npm
- API Java rodando (veja "Como rodar a API e a interface web" no [README principal](../README.md))

## Executar localmente

```bash
cd frontend
npm install
# Opcional: copie .env.example para .env.local se a API não estiver em http://127.0.0.1:8080
npm run dev
```

Abra http://localhost:3000.

## Como o frontend fala com a API

- O navegador chama apenas caminhos relativos `/api/...`. O `next.config.ts` repassa essas requisições para `BACKEND_URL` (padrão `http://127.0.0.1:8080`).
- Na Vercel, defina `BACKEND_URL` com a URL absoluta HTTPS da API Java. Sem esse valor o build falha. A API não tem autenticação e não deve ser exposta publicamente.
- Use sempre o cliente tipado [`src/lib/api.ts`](src/lib/api.ts); ele converte erros e falta de conexão em mensagens prontas para exibir.
- O contrato completo das rotas está em [`docs/API.md`](../docs/API.md).
- O frontend não guarda segredos: as credenciais do SoundCloud ficam só no backend. Nunca use variáveis `NEXT_PUBLIC_*` para segredos.
- Se o frontend precisar de algo que a API não oferece, registre o pedido no README principal ("Pedidos para a equipe de backend").

## Verificações

```bash
npm run lint
npm run build
```

A UI identifica correspondências aproximadas e não certifica autoria/direitos autorais. O catálogo pronto transmite gravações cuja página de origem declara Creative Commons ou domínio público. Capas e letras do SoundCloud são exibidas apenas quando o provedor retorna esses dados. Um arquivo importado pelo usuário não é enviado ao servidor Java.
