# Ativ.-Geren-Proj

Projeto acadêmico de catálogo musical para a matéria de Gerenciamento de Projetos / Metodologias Ágeis.

favor atualizar o site (https://spotify-clone-omega-eosin.vercel.app)

## O que existe no código

- **Catálogo:** lista inicial de músicas e operações para consultar, adicionar e remover faixas.
- **Pesquisa local:** procura músicas pelo título ou artista, sem diferenciar maiúsculas de minúsculas.
- **Descoberta externa:** busca faixas no SoundCloud e confere título, artista e letra (LRCLIB/MusicBrainz) pela API HTTP e pela interface web.
- **Favoritos:** mantém faixas favoritas e impede duplicatas.
- **Playlists:** cria playlists, lista e busca por ID e permite adicionar/remover IDs de faixas.
- **Player:** toca, pausa, retoma e para. WAV, AIFF, AU e MP3 locais saem pelo mixer do Java. Uma faixa adicionada pela pesquisa pede o stream HTTPS que o SoundCloud autorizar e toca sem gravar o arquivo. Se não houver arquivo nem stream, o terminal usa um tom com a duração cadastrada.
- **Pesquisa online:** a opção 19 do menu busca o nome no SoundCloud e adiciona título, artista, duração e o id da faixa ao catálogo, com o link original.
- **API HTTP:** `ApiServer` expõe catálogo, favoritos, playlists e descoberta em JSON (contrato em [`docs/API.md`](docs/API.md), decisão em [ADR-0002](docs/architecture/decisions/ADR-0002-api-http-java-frontend-next.md)).
- **Testes:** testes automatizados com JUnit 5 para as regras principais e para a API.

O menu de terminal (`Main`) continua cobrindo catálogo e playlists. A interface web em `frontend/` consome a API Java e já abre com um catálogo de gravações em licença livre, tocadas no navegador a partir do Internet Archive. Também toca arquivos que a pessoa importa. A API não envia o stream do SoundCloud; essas faixas continuam abrindo no link original.

> **Backend congelado.** A alteração que conectou a API foi a última mudança planejada no backend. Daqui em diante o trabalho é só no frontend. Se o frontend precisar de algo novo do backend, registre em [Pedidos para a equipe de backend](#pedidos-para-a-equipe-de-backend) em vez de alterar `src/` diretamente.

## Requisitos

- JDK 25
- Maven 3.9 ou compatível

## Como compilar e testar

Na raiz do repositório, execute:

```bash
mvn clean verify
```

O comando compila os fontes e executa os testes automatizados.

No Visual Studio Code, abra a pasta do projeto e use **Run and Debug** com a configuração `Main (terminal)`. É preciso o Extension Pack for Java. No IntelliJ, abra o `pom.xml` ou o projeto e execute a configuração `Main`.

Para iniciar o menu de terminal pelo Maven:

```bash
mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java -Dexec.mainClass=Main
```

## Como rodar a API e a interface web

1. Configure as variáveis de ambiente do backend (veja [`.env.example`](.env.example)). Para a busca no SoundCloud, defina `SOUNDCLOUD_CLIENT_ID` e `SOUNDCLOUD_CLIENT_SECRET`; sem elas, a API funciona e a busca responde `503` com uma mensagem explicativa.
2. Inicie a API (porta 8080 por padrão):

   ```bash
   mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java -Dexec.mainClass=ApiServer
   ```

3. Em outro terminal, inicie o frontend conforme [`frontend/README.md`](frontend/README.md) e abra http://localhost:3000.

> No Windows, se a API ou os testes falharem com `Unable to establish loopback connection`, o caminho da pasta temporária está impedindo o JDK de criar sockets locais. Defina `-Djdk.net.unixdomain.tmpdir=C:\Users\<seu-usuario>` (por exemplo, em `MAVEN_OPTS` ou `-DargLine=...` nos testes).

## Branches

- `main`: código original, já com as funções do grupo. Só recebe o que passou na branch de teste.
- `integracao`: branch global para testar junto antes de ir para a `main`.
- `joao-paulo`, `joao-vitor`, `lucas-belucci-bellini`, `miguel`, `henrique` e `caue`: uma branch por participante, para alterar sem mexer direto no original.

## Organização atual

A raiz guarda só a configuração do projeto (`pom.xml`, `README.md`, `AGENTS.md`). O código que roda não fica mais solto nessa pasta.

- `src/`: aplicação Java do terminal, da API e do player.
- `tests/`: testes automatizados com JUnit 5.
- `docs/`: regras, decisões de arquitetura, API e relatórios.
- `frontend/`: interface web Next.js que consome a API Java.
- `lib/`: bibliotecas usadas pelo IntelliJ (Jackson e JLayer). O Maven baixa as mesmas dependências pelo `pom.xml`.
- `legado/prototipos/`: rascunhos antigos, fora do build.
- `.github/workflows/`: compilação, testes e verificação do repositório.

> **Nota:** os arquivos Java permanecem no pacote padrão por enquanto, para manter compatibilidade com a estrutura acadêmica existente. Uma migração para pacotes nomeados pode ser feita em uma alteração separada.

## Referências de estudo

- [Atividade base e issues](https://github.com/jp-albano/Ativ-Met-Ageis/issues/3)
- [Catálogo de estudos de Python](https://github.com/Lucas-Belucci-Bellini/Projetos-Futuros/tree/estudos/catalogo-programacao/estudos/conteudos/05-python)
- [Catálogo de estudos de Java](https://github.com/Lucas-Belucci-Bellini/Projetos-Futuros/tree/estudos/catalogo-programacao/estudos/conteudos/06-java)
- [Catálogo de estudos de JavaScript](https://github.com/Lucas-Belucci-Bellini/Projetos-Futuros/tree/estudos/catalogo-programacao/estudos/conteudos/01-javascript)
- [Catálogo de estudos de HTML](https://github.com/Lucas-Belucci-Bellini/Projetos-Futuros/tree/estudos/catalogo-programacao/estudos/conteudos/03-html)
- [Catálogo de estudos de CSS](https://github.com/Lucas-Belucci-Bellini/Projetos-Futuros/tree/estudos/catalogo-programacao/estudos/conteudos/04-css)

## Integrações musicais (em desenvolvimento)

- **SoundCloud:** `SoundCloudSearchService` pesquisa faixas na API oficial e retorna título, usuário/artista, URL da faixa, capa e duração. Prefira `SOUNDCLOUD_CLIENT_ID` + `SOUNDCLOUD_CLIENT_SECRET` (o token é obtido e renovado automaticamente); `SOUNDCLOUD_ACCESS_TOKEN` fixo também funciona, mas expira em cerca de uma hora. Nunca grave credenciais no código ou no Git.
- **Letras e conferência:** `LyricsVerificationService` consulta LRCLIB e compara título, artista e duração. A resposta informa se a correspondência é forte ou aproximada; não se deve tratar resultados aproximados como confirmação.
- **Créditos independentes:** `MusicMetadataVerificationService` consulta MusicBrainz para comparar título e créditos de artista.
- **Fachadas Java:** `MusicDiscoveryService` combina busca e verificação; `DiscoveryApi` faz o mesmo para a API HTTP, com limite de chamadas simultâneas, cache curto e tolerância a falha parcial.
- **Interface web:** veja `frontend/README.md`. As credenciais ficam só no backend.
- **Configuração:** veja `.env.example` para os nomes das variáveis de ambiente. O exemplo não contém credenciais reais.

As APIs dependem de conexão, disponibilidade, limites e termos de cada provedor. Capas e URLs são metadados de origem; o projeto não deve baixar/republicar imagens nem contornar controles de acesso. O uso da API não garante que toda faixa, letra ou capa esteja disponível. O verificador auxilia a conferência, mas não certifica direitos autorais.

## Pedidos para a equipe de backend

O frontend não altera `src/`. Pendências que dependem do backend ficam registradas aqui:

1. **Persistência:** catálogo, favoritos e playlists vivem em memória e somem ao reiniciar a API. Precisa de ADR e implementação de armazenamento.
2. **Autenticação/usuários:** a API não tem login nem separação por usuário. Necessário antes de qualquer publicação fora de `127.0.0.1`.
3. **Renomear/editar playlist e faixa:** a API só cria e remove; não há `PATCH`.
4. **Menu de descoberta no terminal:** as opções de busca no SoundCloud e verificação de letra sumiram do `Main` em um merge anterior (estavam no commit `aafd084`). A funcionalidade existe na API; restaurar no terminal é opcional.
5. **Arquivos Java soltos na raiz** (`FavoriteSongsKeeper.java`, `LikedTracksRegistry (1).java`, `TrackCatalog.java`, `TrackSource.java`, `TrackNotFoundException.java`, `UnknownTrackException.java`): não são compilados pelo Maven e parte deles nem compila. Decidir se devem ser removidos ou integrados.
6. **Pacotes nomeados:** os fontes continuam no pacote padrão (ver ADR-0001).
7. **Stream para o player web:** a API não devolve a URL temporária que `SoundCloudSearchService.urlDeStream` já obtém. Sem esse endpoint, ou um proxy de áudio, o frontend não consegue tocar faixas do SoundCloud e só reproduz arquivos locais escolhidos no navegador.

## Se o grupo decidir não usar a API

A API foi entregue em um commit **separado** das correções de compilação. Desfazer esse commit remove a API e mantém o projeto compilando. Escolha um dos caminhos abaixo.

### Caminho mais fácil — pedir para um agente de IA (recomendado)

Não precisa saber Git nem GitHub. Abra o Claude Code (ou outro agente) nesta pasta e **copie e cole** a frase abaixo:

> Reverta o commit "feat: expor API HTTP Java e conectar o frontend" seguindo a seção "Se o grupo decidir não usar a API" do README. Mantenha o commit "fix: corrigir compilação". Rode os testes e me mostre o resultado antes de enviar.

Se não souber fazer isso, peça ao Lucas.

### Caminho 1 — GitHub Desktop (sem digitar comandos)

1. Abra o repositório no [GitHub Desktop](https://desktop.github.com/).
2. Clique na aba **History** (Histórico), à esquerda.
3. Procure o commit com o título **"feat: expor API HTTP Java e conectar o frontend"**.
4. Clique nele com o **botão direito** e escolha **Revert changes in commit** (Reverter alterações do commit).
5. O GitHub Desktop cria um novo commit desfazendo a API. Clique em **Push origin** para enviar.

Não use o commit **"fix: corrigir compilação..."**: ele contém as correções que fazem o projeto funcionar e deve ficar.

### Caminho 2 — terminal (para quem já usa Git)

```bash
git log --oneline --grep "expor API HTTP"
git revert <código-do-commit-mostrado-acima>
```

### O que o reverter remove (para conferência ou remoção manual)

Se preferir apagar à mão, estes são os arquivos envolvidos:

- **Backend (apagar):** `src/ApiServer.java`, `src/DiscoveryApi.java`, `src/MusicLibrary.java`, `tests/ApiServerTest.java`.
- **Backend (opcional):** `src/SoundCloudSearchService.java` e `tests/SoundCloudSearchServiceTest.java` podem ficar; o suporte a client credentials é compatível com o token fixo antigo.
- **Frontend (apagar):** `frontend/src/lib/api.ts` e o bloco `rewrites` em `frontend/next.config.ts`.
- **Frontend (restaurar do Git):** `frontend/src/app/api/search/route.ts`, `frontend/src/app/api/verify/route.ts` e `frontend/src/lib/resilient-fetch.ts`, que chamam o SoundCloud direto (exigem `SOUNDCLOUD_ACCESS_TOKEN` no frontend). Depois, em `frontend/src/app/page.tsx`, voltar as chamadas para `/api/search` e `/api/verify` e remover a seção "Sua biblioteca".
- **Docs:** apagar `docs/API.md`, marcar o ADR-0002 como rejeitado e remover deste README as seções da API.

O menu de terminal (`Main`) e os demais serviços Java não dependem da API e continuam funcionando.
