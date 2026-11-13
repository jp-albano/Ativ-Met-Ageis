# Guia de integração musical

## 1. SoundCloud: pesquisar faixas e capas

A classe `SoundCloudSearchService` usa a API oficial `GET https://api.soundcloud.com/tracks`.
Ela não baixa capas nem áudio: devolve os metadados e as URLs que a API disponibiliza.

1. Cadastre/configure o aplicativo conforme as regras do SoundCloud Developers.
2. Configure `SOUNDCLOUD_CLIENT_ID` e `SOUNDCLOUD_CLIENT_SECRET` no ambiente (recomendado). O serviço obtém o token pelo fluxo OAuth *client credentials*, reaproveita-o até perto do vencimento e o renova após um `401`.
3. Alternativa para testes rápidos: `SOUNDCLOUD_ACCESS_TOKEN` com um token fixo (expira em cerca de uma hora). Se as duas formas existirem, client credentials tem prioridade.

Nunca cole credenciais no código ou em commits. Windows PowerShell (somente na sessão atual):

```powershell
$env:SOUNDCLOUD_CLIENT_ID = "COLOQUE_LOCALMENTE"
$env:SOUNDCLOUD_CLIENT_SECRET = "COLOQUE_LOCALMENTE"
```

Exemplo Java:

```java
var soundCloud = new SoundCloudSearchService();
var results = soundCloud.search("Daft Punk", 10);
for (var track : results) {
    System.out.println(track.title() + " — " + track.artist());
    System.out.println("Capa: " + track.artworkUrl());
    System.out.println("Abrir no SoundCloud: " + track.permalinkUrl());
}
```

A aplicação deve oferecer um link para a faixa original e respeitar permissões, disponibilidade e termos do provedor. A presença de uma URL de capa não transfere direitos sobre a imagem.

No menu do terminal, a opção 19 pesquisa pelo nome e grava a faixa escolhida no catálogo local (`InclusaoPorPesquisa`). A inclusão copia título, artista, duração, o link e o id da faixa. Ela não grava o arquivo de áudio nem a capa.

Ao tocar, `AudioPlayerService` chama `SoundCloudSearchService.urlDeStream`. A API devolve uma URL HTTPS temporária (`http_mp3_128_url` ou, se for o caso, `preview_mp3_128_url`). O player decodifica esse fluxo em memória e não cria cópia no disco. Se o aplicativo não tiver permissão de stream, a reprodução volta ao tom local.

## 2. Letras e verificação de correspondência (LRCLIB)

`LyricsVerificationService` consulta título e artista e ranqueia candidatos com base em título, artista e duração. A duração é opcional no modelo: use `0` quando não estiver disponível.

```java
var lyricsService = new LyricsVerificationService();
var result = lyricsService.findLyrics("Imagine", "John Lennon", 183);

if (result.found() && result.verified()) {
    System.out.println("Correspondência forte: " + result.title() + " — " + result.artist());
    System.out.println(result.plainLyrics());
} else {
    System.out.println(result.message());
}
```

A verificação forte exige título e artista normalizados iguais e duração igual com tolerância de até dois segundos quando a duração é conhecida. Resultados aproximados devem ser apresentados como sugestões, não como confirmação. O provedor pode não ter letra para uma música; isso não deve interromper o catálogo.

Respeite a política de requisições do LRCLIB, envie um User-Agent identificável e trate HTTP 429 sem repetir a chamada em loop.

## 3. Conferir créditos com MusicBrainz

`MusicMetadataVerificationService.search(titulo, artista)` consulta o catálogo MusicBrainz e retorna título, créditos, álbum quando disponível e duração. Use-o como uma segunda fonte de metadados, não como prova de titularidade ou autorização de uso.

MusicBrainz pede um User-Agent significativo e limita consultas a aproximadamente uma requisição por segundo. Para pesquisa em lote, introduza uma fila com intervalo entre consultas.

## 4. Tratamento de falhas

- Token ausente ou inválido: mostre mensagem de configuração/autorização, sem imprimir token.
- Sem resultados: mostre “nenhuma correspondência encontrada” e permita busca manual.
- HTTP 429: aguarde conforme a política do serviço; não repita continuamente.
- Erros de rede/5xx: preserve o catálogo local e ofereça tentar novamente.
- Campos de capa, álbum, duração ou letras ausentes: trate-os como opcionais.

## 5. Fachada unificada para a aplicação

A classe `MusicDiscoveryService` reúne os provedores numa API única, para que a futura interface não precise conhecer os detalhes de cada cliente:

```java
var music = new MusicDiscoveryService();
var tracks = music.search("Imagine John Lennon", 10);

for (var track : tracks) {
    System.out.println(track.title() + " — " + track.artist());
    System.out.println("Capa: " + track.artworkUrl());
    System.out.println("Faixa original: " + track.permalinkUrl());
}

// Após o usuário selecionar uma faixa:
var verification = music.verify("Imagine", "John Lennon", 183);
System.out.println(verification.lyrics().message());
System.out.println("Metadados independentes: "
        + verification.metadataMatches().size());
```

`TrackVerification` fornece os resultados de letras e os candidatos do MusicBrainz separadamente. Uma letra aproximada não deve ser apresentada como verificada; resultados de metadados são referências para comparação, não prova legal de autoria. Quando o LRCLIB retornar texto de letra, o menu de terminal pergunta se você deseja exibi-lo e identifica a fonte. A pesquisa e a verificação fazem chamadas de rede e podem falhar por autenticação, limites ou indisponibilidade.

## 6. O que ainda não está implementado

A interface web exibe resultados, capas, links e letras através da API HTTP (`DiscoveryApi`, rotas `/api/discovery/*` em [`docs/API.md`](API.md)). O player do navegador abre com um catálogo de gravações em Creative Commons ou domínio público e também toca arquivos locais importados pela pessoa (ver [ADR-0003](architecture/decisions/ADR-0003-player-web-arquivos-locais.md)). Ainda não existem login de usuário, cache persistente no servidor nem reprodução do stream do SoundCloud na web: a API não expõe `urlDeStream`.
