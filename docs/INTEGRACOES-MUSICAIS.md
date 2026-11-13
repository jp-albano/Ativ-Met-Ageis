# Guia de Integração Musical e Configuração de Provedores

Este documento orienta a instalação, configuração e arquitetura de integração com os serviços externos consumidos pelo catálogo musical: **SoundCloud API**, **LRCLIB** e **MusicBrainz**.

---

## 1. SoundCloud API: Guia de Instalação, Autenticação, Busca e Reprodução

### 1.1. Pré-requisitos

- Java 21+ (recomendado Java 25 LTS).
- Conexão com a internet para comunicação com os hosts oficiais do SoundCloud:
  - Base da API: `https://api.soundcloud.com`
  - Host de Autenticação OAuth 2: `https://secure.soundcloud.com`
  - CDN de Imagens/Áudio: `https://*.sndcdn.com`
  - Widget Player: `https://w.soundcloud.com`
- Uma conta de desenvolvedor no SoundCloud para registrar seu aplicativo.

---

### 1.2. Como registrar e configurar o aplicativo no SoundCloud

O SoundCloud gerencia o acesso de desenvolvedores através do portal oficial:

1. Acesse o portal de desenvolvedores do SoundCloud: [https://developers.soundcloud.com/](https://developers.soundcloud.com/).
2. Faça login com sua conta do SoundCloud.
3. Acesse a seção **"Your Applications"** e clique em **"Register a new application"**.
4. Preencha os dados do aplicativo:
   - **App Name**: Nome identificador do seu app (ex: `MusicCatalogAcademic`).
   - **Website / Redirect URI**: Para autenticação de aplicação (*Client Credentials*), o redirect URI não é utilizado para troca de código, mas pode ser configurado com o domínio local (ex: `http://localhost:3000/callback`).
5. Aceite os Termos de Serviço da API de Desenvolvedor do SoundCloud.
6. Uma vez criado o aplicativo, a plataforma exibirá:
   - **Client ID**: Identificador público do aplicativo.
   - **Client Secret**: Segredo confidencial do aplicativo (armazenado com segurança).

> [!IMPORTANT]
> **Nunca comite credenciais reais no Git nem compartilhe seu Client Secret**. O SoundCloud revoga chaves detectadas em repositórios públicos.

---

### 1.3. Credenciais necessárias e variáveis de ambiente

O backend requer apenas duas credenciais oficiais para o fluxo automatizado de aplicação:

| Variável | Descrição | Onde Obter |
|---|---|---|
| `SOUNDCLOUD_CLIENT_ID` | Identificador público da aplicação | Painel do SoundCloud Developers |
| `SOUNDCLOUD_CLIENT_SECRET` | Segredo para emissão de tokens OAuth 2 | Painel do SoundCloud Developers |
| `SOUNDCLOUD_ACCESS_TOKEN` | *(Opcional)* Token estático temporário para testes rápidos (expira em 1 hora) | Ferramentas de debug / curl |

Se `SOUNDCLOUD_CLIENT_ID` e `SOUNDCLOUD_CLIENT_SECRET` estiverem presentes, o backend utiliza automaticamente o fluxo **Client Credentials**, que gerencia, reutiliza e renova os tokens em segundo plano sem qualquer intervenção manual.

---

### 1.4. Configuração local do ambiente

No arquivo `.env` da raiz do repositório (baseado no `.env.example`):

```dotenv
# SoundCloud API - Credenciais do Aplicativo
SOUNDCLOUD_CLIENT_ID=seu_client_id_aqui
SOUNDCLOUD_CLIENT_SECRET=seu_client_secret_aqui
```

No terminal PowerShell (apenas para a sessão ativa de terminal):

```powershell
$env:SOUNDCLOUD_CLIENT_ID = "seu_client_id_aqui"
$env:SOUNDCLOUD_CLIENT_SECRET = "seu_client_secret_aqui"
```

No Linux / macOS (bash / zsh):

```bash
export SOUNDCLOUD_CLIENT_ID="seu_client_id_aqui"
export SOUNDCLOUD_CLIENT_SECRET="seu_client_secret_aqui"
```

---

### 1.5. Configuração no ambiente de produção / backend

- **Isolamento Total**: Variáveis contendo segredos (`SOUNDCLOUD_CLIENT_SECRET`, tokens de acesso) devem ser cadastradas **exclusivamente nas variáveis de ambiente do backend** (onde o processo Java ou a função de servidor executa).
- **Sem prefixos públicos**: Nunca adicione segredos com prefixo `NEXT_PUBLIC_` no Next.js.
- **Sem eco nos logs**: A classe `SoundCloudSearchService` e o servidor HTTP tratam erros de autenticação sem imprimir tokens, cabeçalhos de autorização ou corpos de resposta de login nos logs.

---

### 1.6. Recursos por tipo de autenticação

| Recurso | Tipo de Autenticação Exigida | Suportado no Projeto |
|---|---|---|
| Busca pública de faixas (`GET /tracks`) | Aplicação (*Client Credentials*) | Sim |
| Paginação de resultados (`next_href`) | Aplicação (*Client Credentials*) | Sim |
| Resolução de permalinks (`GET /resolve`) | Aplicação (*Client Credentials*) | Sim |
| Solicitação de streams autorizados (`GET /tracks/{id}/streams`) | Aplicação (*Client Credentials*) | Sim |
| Widget Oficial e oEmbed | Aberto / Aplicação | Sim |
| Playlists e favoritos de usuário do SoundCloud | Autorização de Usuário (*Authorization Code + PKCE*) | Não (fora do escopo; o projeto mantém favoritos e playlists próprios no backend) |

---

### 1.7. Ciclo de Autenticação: Client Credentials e Renovação

A classe `SoundCloudSearchService` implementa:
1. **Emissão sob demanda**: Ao iniciar, se nenhuma busca for realizada, nenhuma chamada externa de token é feita.
2. **Reutilização e Cache**: O token retornado por `POST https://secure.soundcloud.com/oauth/token` (usando cabeçalho HTTP `Authorization: Basic base64(client_id:client_secret)` e corpo `grant_type=client_credentials`) é mantido em memória junto com o instante de expiração.
3. **Renovação preventiva**: A renovação ocorre automaticamente 60 segundos antes da expiração informada por `expires_in`.
4. **Recuperação pós-401**: Caso o SoundCloud revogue o token antes do prazo previsto, o serviço descarta o cache, solicita um novo token e repete a busca uma única vez antes de acusar erro.

---

### 1.8. Paginação e Resolução de Permalinks

- **Paginação**: As buscas utilizam o parâmetro oficial `linked_partitioning=true`. A resposta da API contém o objeto `collection` com os itens e o campo `next_href`. Ao navegar para a página seguinte, o backend consome a URL do `next_href` após validar o domínio para prevenção de SSRF.
- **Resolução de URLs (`/resolve`)**: Permite que o usuário forneça um link direto do SoundCloud (ex: `https://soundcloud.com/artista/musica`). A rota `/api/discovery/resolve?url=...` consulta `GET /resolve?url=...`, trata o redirecionamento HTTP 302 retornado pelo SoundCloud e devolve os metadados tipados da faixa.

---

### 1.9. Reprodução, Permissões e Decisão Arquitetural (Widget vs Player Próprio)

#### Classificação de Permissões de Reprodução
Nem toda faixa retornada na busca do SoundCloud está disponível para reprodução de áudio integral por aplicativos de terceiros:
- **`PLAYABLE`**: Faixa com licença pública de streaming aberta (`access == "playable"`, `streamable == true`).
- **`PREVIEW`**: Faixas comerciais ou sob acordos especiais que disponibilizam apenas um trecho demonstrativo de 30 segundos (`access == "preview"`, `policy == "SNIP"`).
- **`BLOCKED`**: Faixas protegidas por paywall (SoundCloud Go+), bloqueio geográfico ou desativadas pelo uploader para aplicativos de terceiros (`access == "blocked"`, `streamable == false`, `policy == "BLOCK"`). O sistema não tenta contornar bloqueios nem gerar links de streaming para faixas restritas.

#### Avaliação Técnica: SoundCloud Web Player Widget (iframe / oEmbed) vs Player Próprio
A API do SoundCloud impõe requisitos rigorosos de reprodução e direitos autorais:

| Critério | Player Próprio (via `/streams`) | SoundCloud Web Widget / oEmbed |
|---|---|---|
| **Disponibilidade de Faixas** | Restrito: A maioria dos selos comerciais bloqueia streaming direto para Client IDs padrão (HTTP 403) | **Total**: Reproduz faixas livres e oferece preview autenticado para faixas comerciais |
| **Monetização e Royalties** | Não contabiliza reproduções para os artistas | **Sim**: Contabiliza plays oficiais e monetização |
| **Atribuição Visual** | Exige implementar logotipo oficial, badges, links e respeitar guidelines de design | **Nativa**: O widget oficial já inclui branding, waveforms e links em conformidade |
| **Complexidade e Transcodings** | Exige decodificação de stream HLS / MP3 em memória | **Zero complexidade**: Iframe responsivo incorporável |

**Conclusão e Justificativa de Produto**:
Para a interface web, a integração com o **SoundCloud Web Player Widget** (via URL gerada por `SoundCloudSearchService.widgetEmbedUri`) e com **oEmbed** é a solução tecnicamente recomendada, pois garante 100% de conformidade com os Termos de Uso do SoundCloud, preserva a atribuição oficial e permite ouvir faixas comerciais sem violar direitos autorais. O stream direto é mantido no backend (`urlDeStream`) exclusivamente para o reprodutor de terminal acadêmico em faixas abertas.

---

### 1.10. Atribuição Obrigatória do SoundCloud

Ao exibir qualquer conteúdo originário do SoundCloud, a aplicação deve:
1. Exibir de forma clara o nome do artista/uploader.
2. Fornecer um link clicável direcionando para a página da faixa no SoundCloud (`permalinkUrl`).
3. Exibir a marca oficial do SoundCloud ("Powered by SoundCloud" ou logotipo oficial).
4. Não ocultar nem sobrepor logos presentes nas capas e no widget.

---

### 1.11. Limites da API, Tratamento de Erros e Diagnóstico de Problemas

- **Rate Limits (HTTP 429)**: O SoundCloud impõe limites de requisições por IP e por aplicativo. O backend traduz `429` para uma mensagem amigável: *"Limite de buscas atingido. Aguarde um pouco."* e não realiza retries agressivos em loop.
- **Erros Transitórios (HTTP 502/503/504)**: O serviço realiza 1 retry com backoff de 150ms. Se a indisponibilidade persistir, devolve erro 502/503 controlado.
- **Prevenção de SSRF**: URLs passadas para `/resolve` ou `next_href` são validadas estritamente contra domínios autorizados (`soundcloud.com`, `api.soundcloud.com`).

#### Problemas Comuns e Diagnóstico

1. **`HTTP 503: SoundCloud recusou as credenciais configuradas no servidor`**:
   - *Causa*: `SOUNDCLOUD_CLIENT_ID` ou `SOUNDCLOUD_CLIENT_SECRET` incorretos ou aplicação desativada no portal de desenvolvedor.
   - *Ação*: Verifique se as credenciais cadastradas correspondem às do portal do SoundCloud.
2. **`HTTP 429: Limite de requisições do SoundCloud atingido`**:
   - *Causa*: Excesso de requisições no mesmo intervalo por IP ou Client ID.
   - *Ação*: Aguarde alguns minutos antes de efetuar novas buscas. O backend utiliza cache curto de 15 segundos para aliviar consultas idênticas.
3. **`URL de stream vazia (Optional.empty())`**:
   - *Causa*: Faixa com restrição de direitos autorais ou geobloqueada.
   - *Ação*: Utilize o link do SoundCloud ou o Web Widget para reproduzir na plataforma oficial.

---

## 2. Letras e Verificação de Correspondência (LRCLIB)

A classe `LyricsVerificationService` pesquisa letras sincronizadas e puras através da API aberta do LRCLIB (`https://lrclib.net`).
- **Verificação Forte**: Título e artista normalizados idênticos e duração equivalente (tolerância de até 2 segundos).
- **Aviso e Resiliência**: Falhas no LRCLIB não abortam o catálogo nem a verificação de metadados; o erro é encapsulado no campo `warnings`.

---

## 3. Conferência de Créditos com MusicBrainz

A classe `MusicMetadataVerificationService` consulta metadados no MusicBrainz (`https://musicbrainz.org/ws/2`).
- Respeita o limite de taxa de aproximadamente 1 requisição por segundo.
- Devolve identificadores canônicos (MBID), créditos de artista e nome de álbum.

---

## 4. Testes Automatizados com Mocks

A suíte de testes do backend (`tests/SoundCloudSearchServiceTest.java` e `tests/ApiServerTest.java`) utiliza servidores HTTP locais mockados (`com.sun.net.httpserver.HttpServer`).
- **Não depende de credenciais reais** nem de conexão com a internet para executar.
- Cobre:
  - Autenticação e reaproveitamento de tokens;
  - Renovação após expiração ou revogação (HTTP 401);
  - Paginação com `linked_partitioning` e `next_href`;
  - Resolução de URLs e redirecionamentos 302;
  - Faixas reproduzíveis, prévia e bloqueadas;
  - Respostas 400, 401, 403, 404, 429 e 5xx com retry transitório;
  - Proteção contra SSRF e isolamento de segredos.
