# ADR-0003 — Player web com arquivos locais

- **Status:** Aceita pelo autor da alteração na branch `joao-vitor`; sujeita à revisão do grupo.
- **Data:** 2026-11-12
- **Complementa:** ADR-0002. Não altera o backend Java congelado.

## Contexto

A interface em `frontend/` consultava catálogo, favoritos, playlists e a busca no SoundCloud, mas não reproduzia áudio. O pedido foi um player com as funções básicas de um programa de música, escrito só em TypeScript.

`SoundCloudSearchService` já sabe pedir um stream HTTPS temporário, porém `ApiServer` não expõe essa URL nem aceita upload de arquivo. O README pede para não alterar `src/` e registrar a necessidade na seção de pedidos ao backend.

## Alternativas consideradas

| Opção | Prós | Contras |
|---|---|---|
| **A. Catálogo pronto com licença livre, mais arquivo local no navegador** | Dá para ouvir ao abrir a página; não copia catálogo comercial; não mexe no backend | O acervo não é o de um serviço pago; depende do Internet Archive para o áudio |
| B. Novo endpoint Java de stream ou upload | O player poderia tocar o que a API já autoriza no terminal | Quebra o congelamento do backend e exige ADR de API |
| C. Chamar o SoundCloud direto no Next.js | Evitaria esperar o backend | Recolocaria credencial no frontend, o que o ADR-0002 retirou, e contornaria o contrato atual |

## Decisão

Opção **A** para arquivos do próprio computador, e uma lista pronta de gravações com licença livre para o catálogo inicial.

- A interface abre com as faixas de `frontend/src/data/free-catalog.ts`. O navegador toca o MP3 no Internet Archive; o repositório não guarda esses áudios.
- A busca `/music/search` só devolve itens cuja ficha tem `licenseurl` de Creative Commons ou domínio público, e o player continua apontando para o arquivo original.
- O usuário também pode importar o próprio áudio. O blob fica no IndexedDB e não é enviado à API.
- Quando a API está no ar, a importação pode cadastrar título, artista e duração em `POST /api/tracks`.
- Faixas do SoundCloud mostram o link original. Salvar no catálogo da API grava só metadados.
- A interface usa o layout de um player (lista, busca e barra de reprodução). Não replica marca, código nem catálogo de um serviço comercial.

## Consequências

- Play, pausa, anterior, próxima, seek, volume, aleatório, repetir e fila funcionam nas faixas do catálogo pronto e nos arquivos que o navegador decodifica.
- Se o Internet Archive estiver fora, a lista continua visível, mas o áudio não toca até o servidor de origem responder.
- O backend Java segue sem stream web. O pedido está no README, item 7 de "Pedidos para a equipe de backend".
- Não há login. O IndexedDB é por origem e por navegador, não por usuário da API.
