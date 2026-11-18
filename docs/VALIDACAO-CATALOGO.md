# Validação do catálogo

## Regras adotadas

`Track` é a unidade de identidade compartilhada pelo catálogo, favoritos e playlists. Seu ID é uma `String` obrigatória, normalizada com `strip()` e usada como identidade em `equals` e `hashCode`. O `CatalogService` rejeita faixas nulas e IDs repetidos, preserva a ordem de inserção e não aceita uma coleção inicial com duplicatas.

Título e artista são obrigatórios. Valores nulos são rejeitados e valores vazios ou compostos apenas por espaços são rejeitados; espaços nas extremidades de valores válidos são removidos.

A duração de uma faixa cadastrada deve ser um inteiro positivo. Zero e valores negativos são inválidos. A regra vale tanto para o construtor de `Track` quanto para o cadastro feito pela `MusicLibrary`. Serviços de verificação externa podem continuar usando zero para indicar duração desconhecida, pois esse valor não representa uma faixa cadastrada.

O caminho de áudio é obrigatório e não pode ser vazio. A capa é opcional; quando informada, seu caminho também não pode ser vazio. A existência física dos arquivos não é exigida no cadastro: o catálogo inicial usa referências locais de demonstração e a ausência de um arquivo não deve impedir a listagem ou a busca da faixa.

`getAllTracks()` devolve uma coleção não modificável. Buscas por ID inexistente retornam `Optional.empty()` e não alteram o catálogo.

## Verificação

Os testes em `tests/CatalogAndFavoritesTest.java` cobrem campos inválidos, duração não positiva, normalização de texto, referências de arquivos ausentes, catálogo vazio, ordem do catálogo inicial, IDs repetidos, busca ausente e isolamento da coleção exposta.
