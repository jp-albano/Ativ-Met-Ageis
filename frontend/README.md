# Sonora — interface web

Interface responsiva em Next.js + React que consome a API Java do projeto: catálogo local com favoritos, playlists (criação e organização de faixas do catálogo), busca de faixas no SoundCloud, opção para salvar resultados no catálogo e conferência de letras/metadados (LRCLIB e MusicBrainz). A API mantém catálogo e playlists em memória; eles são perdidos quando o backend reinicia.

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
- Use sempre o cliente tipado [`src/lib/api.ts`](src/lib/api.ts); ele converte erros e falta de conexão em mensagens prontas para exibir.
- O contrato completo das rotas está em [`docs/API.md`](../docs/API.md).
- O frontend não guarda segredos: as credenciais do SoundCloud ficam só no backend. Nunca use variáveis `NEXT_PUBLIC_*` para segredos.
- Se o frontend precisar de algo que a API não oferece, registre o pedido no README principal ("Pedidos para a equipe de backend").

## Verificações

```bash
npm run lint
npm run build
```

A UI identifica correspondências aproximadas e não certifica autoria/direitos autorais. A reprodução de áudio não está implementada; o link abre a faixa na plataforma de origem. Capas e letras são exibidas apenas quando o provedor retorna esses dados; respeite os termos e direitos aplicáveis.
