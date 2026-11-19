# Registro de testes: Favoritas

Responsável: Miguel Flores (cards 6 e 7)
Ambiente: OpenJDK 25.0.5, compilação com `javac *.java` e execução por linha de comando.
Catálogo usado: `DemoSongCatalog` (IDs 001, 002 e 003).

## 1. Testes automatizados (classes `Confere...`)

| Arquivo | Cenário | Resultado |
|---|---|---|
| `ConfereFavoritoListado` | Favoritar faixa existente (001) e ver na listagem | PASSOU |
| `ConfereSemDuplicata` | Favoritar a faixa 002 duas vezes, sem duplicação | PASSOU |
| `ConfereRemocaoDaLista` | Desfavoritar a faixa 003 e ver que sai da listagem | PASSOU |
| `ConfereRemocaoSemEfeito` | Remover faixa fora da lista (002 e 999): devolve false e não altera a lista | PASSOU |
| `ConfereIdRecusado` | Favoritar ID inexistente (999): recusado com mensagem clara | PASSOU |

## 2. Testes do menu (`FavoritesApp`), entradas simuladas

| Nº | Passos | Resultado esperado | Resultado obtido | Status |
|---|---|---|---|---|
| M1 | Menu 1, opção 1, ID `001` | "Faixa favoritada!" | "Faixa favoritada!" | OK |
| M2 | Favoritar `001` de novo | "Essa faixa já está nas favoritas." | Igual ao esperado | OK |
| M3 | Favoritar `999` | Recusa com mensagem clara | "Não foi possível favoritar: não existe nenhuma faixa com ID 999 no catálogo." | OK |
| M4 | Favoritar `1` (sem os zeros) | Recusa, sem converter o ID | Recusado: "...ID 1 no catálogo." | OK |
| M5 | Favoritar ID vazio (só Enter) | Recusa com mensagem clara | "...ID  no catálogo." (o ID aparece em branco) | ATENÇÃO |
| M6 | Desfavoritar `001` | "Faixa removida das favoritas!" | Igual ao esperado | OK |
| M7 | Desfavoritar `001` de novo | "Essa faixa não estava nas favoritas." | Igual ao esperado | OK |
| M8 | Opção inválida no menu Favoritas (`9`, `x`) | "Opção inválida!" e volta ao menu | Igual ao esperado | OK |
| M9 | Opção inválida no menu principal (`9`, `abc`) | "Opção inválida!" e volta ao menu | Igual ao esperado | OK |
| M10 | Voltar (0) e sair (0) | Volta ao menu e mostra "Até logo!" | Igual ao esperado | OK |

## 3. Observações

- M4 confirma que o ID é enviado como texto, sem conversão: `1` não é tratado como `001`.
- M5: o ID vazio é recusado, mas a mensagem fica com um espaço em branco no lugar do ID. Sugestão para depois: validar ID vazio e mostrar "O ID da faixa não pode ser vazio.".
- O menu atual não tem a opção de ver as músicas curtidas. Por isso a presença na listagem é conferida pelos testes automatizados (seção 1), e não pelo menu.
