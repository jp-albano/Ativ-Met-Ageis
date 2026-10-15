# Backend de playlists (Java)

API HTTP/JSON para **criar playlist** (tarefas 1 a 3 do card "3- Criar e organizar playlists"):
modelo da playlist, validação do nome e salvamento em arquivo no servidor.

Só precisa do **JDK 17 ou superior**. Sem Maven, sem Gradle, sem bibliotecas externas.

## Compilar e rodar

```bash
javac --release 17 -encoding UTF-8 -d out src/main/java/playlist/*.java
java -cp out playlist.Main
```

(`-encoding UTF-8` evita acentos quebrados no Windows.) O servidor sobe em
`http://127.0.0.1:8080/api/playlists` e grava em `data/playlists.json`.

## Testar

```bash
javac --release 17 -encoding UTF-8 -d out-test -cp out src/test/java/playlist/*.java
java -cp out:out-test playlist.TestRunner
```

No Windows, troque `out:out-test` por `out;out-test`.

## Configuração (variáveis de ambiente, todas opcionais)

| Variável | Padrão | Para quê |
|---|---|---|
| `HOST` | `127.0.0.1` | Endereço de escuta. O padrão aceita só conexões desta máquina. |
| `PORT` | `8080` | Porta. |
| `PLAYLIST_DATA` | `data/playlists.json` | Arquivo de dados. |
| `CORS_ORIGIN` | (nenhum) | Origem do frontend, se ele rodar em outro endereço (ex.: `http://localhost:5500`). Sem ela, o navegador bloqueia chamadas de outra origem. |

## Endpoints

| Método e caminho | Resposta |
|---|---|
| `POST /api/playlists` com `{"nome":"Treino"}` | `201` + playlist + cabeçalho `Location` |
| `GET /api/playlists` | `200` + lista |
| `GET /api/playlists/{id}` | `200` + playlist, ou `404` |

Erros: `400` (JSON ou nome inválido), `404`, `405`, `409` (nome duplicado),
`413` (corpo acima de 10.000 bytes), `500` (falha ao ler/gravar). Todo erro vem como
`{"erro": "mensagem"}`.

Exemplo:

```bash
curl -X POST localhost:8080/api/playlists -H "Content-Type: application/json" -d '{"nome":"Treino"}'
```

```json
{"id":"pl_…","nome":"Treino","musicas":[],"criadaEm":"2026-10-15T12:00:00.000Z"}
```

## Regras do nome

Obrigatório; espaços das pontas são removidos; no máximo 50 caracteres; sem caracteres de
controle; único sem diferenciar maiúsculas/minúsculas (acentos compostos e decompostos contam
como iguais). *O limite de 50 e a unicidade são suposições a confirmar com o responsável.*

## Estrutura

| Arquivo | Função |
|---|---|
| `Playlist` | Modelo imutável: `id`, `nome`, `musicas` (ids), `criadaEm`. |
| `PlaylistService` | Regras: validação, criação, consulta. |
| `PlaylistRepository` | Contrato de armazenamento. |
| `FilePlaylistRepository` | Armazenamento em arquivo JSON, gravação atômica. |
| `PlaylistApi` | Rotas HTTP. |
| `Json`, `PlaylistJson` | Leitura/escrita de JSON sem dependências. |
| `Main` | Configuração e inicialização. |

## Limitações

- Um único processo por arquivo de dados. Não rode duas instâncias no mesmo arquivo.
- Sem autenticação: pensado para uso local/atividade, não para a internet.
- Ainda não há músicas nem associação delas às playlists (tarefas 4 a 8 do card).
