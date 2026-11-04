# Relatório de compilação e auditoria — 2026-11-04

## Escopo

Auditoria do estado publicado na branch `main` do repositório Ativ.-Geren-Proj, cobrindo:

- Backend Java 17 / Maven (`src/` e `tests/`);
- Frontend Next.js / React / TypeScript (`frontend/`);
- Workflows de integração contínua;
- Integração entre frontend e API HTTP Java;
- Documentação e riscos operacionais.

## Identificação do estado analisado

- Branch: `main`
- Commit-base observado: `eab60739bca0c36868b87616ec33d0f52450b846`
- Build Java definido no CI: `mvn --batch-mode --no-transfer-progress clean verify`
- Build frontend definido no CI: `npm install` seguido de `npm run build`
- Runtime Java: 17
- Runtime Node configurado no workflow: 20

## Resultado da verificação disponível

| Área | Verificação | Resultado |
|---|---|---|
| Metadados do repositório | Branch, permissões e configuração | OK |
| Configuração Maven | Diretórios de fontes/testes, Java 17, JUnit 5 e Jackson | Configurada |
| Workflow Java | Existe e executa `mvn clean verify` em push/PR | Configurado |
| Configuração frontend | Next.js, React, TypeScript e scripts de build/lint | Configurada |
| Workflow frontend | Existe e executa `npm run build` para alterações em `frontend/**` | Configurado |
| Status do commit-base | Check reportado pelo GitHub | Vercel: success |
| Compilação Java executada nesta auditoria | Não foi possível iniciar um processo Maven pelo conector disponível | Pendente de CI |
| Build Next.js executado nesta auditoria | Não foi possível iniciar um processo npm pelo conector disponível | Pendente de CI |

**Importante:** o status Vercel do commit-base não comprova a compilação Java. Nenhum resultado de Maven ou npm é declarado como aprovado sem log de execução correspondente.

## Arquitetura confirmada

- O Maven compila os fontes localizados em `src/` e executa os testes localizados em `tests/`.
- O backend expõe a API HTTP em `src/ApiServer.java`.
- O frontend chama caminhos relativos `/api/...`; o Next.js encaminha as requisições para `BACKEND_URL`, cujo padrão é `http://127.0.0.1:8080`.
- O contrato HTTP está documentado em `docs/API.md`.
- O backend mantém catálogo, favoritos e playlists em memória. Os dados são perdidos quando o processo reinicia.
- A API não possui autenticação e, por padrão, escuta em `127.0.0.1`. Não deve ser publicada diretamente na internet sem controles adicionais.

## Pontos que exigem atenção

1. **Credenciais externas:** a busca SoundCloud depende de `SOUNDCLOUD_CLIENT_ID` e `SOUNDCLOUD_CLIENT_SECRET` (ou token compatível). Sem credenciais, a API pode iniciar normalmente, mas a busca deve responder com erro controlado.
2. **Persistência:** catálogo, favoritos e playlists são voláteis; persistência em banco ainda não está implementada.
3. **Autenticação:** não há identidade ou isolamento por usuário. Manter a API restrita a ambiente local.
4. **Arquivos Java fora de `src/`:** o README lista arquivos Java soltos na raiz que não fazem parte da compilação Maven atual. Eles não devem ser considerados compilados nem entregues como parte do build sem decisão explícita de integração ou remoção.
5. **Execução local no Windows:** o README registra possível falha de loopback do JDK em diretórios temporários específicos. Se ocorrer, aplicar a configuração de `jdk.net.unixdomain.tmpdir` documentada no README e repetir os testes.
6. **Lint do frontend:** existe script `npm run lint`; o workflow frontend atual executa o build, mas não chama explicitamente o lint. A validação de tipos ocorre no fluxo de build do Next.js/TypeScript conforme a configuração do projeto.

## Comandos oficiais de validação

Na raiz do repositório:

```bash
mvn --batch-mode --no-transfer-progress clean verify
```

No diretório `frontend/`:

```bash
npm install
npm run lint
npm run build
```

Para execução integrada, iniciar primeiro `ApiServer` e depois o frontend, conforme os READMEs.

## Critério para declarar conclusão

A compilação só será considerada aprovada após a confirmação dos resultados dos workflows Java e frontend para o commit que contém este relatório. Falhas devem ser corrigidas e os workflows repetidos. Falhas causadas por ausência de credenciais externas devem ser separadas de erros de compilação/testes.

## Conclusão inicial

A estrutura de build e os workflows necessários existem. A inspeção estática confirma a separação entre backend e frontend e identifica limitações operacionais/documentais, mas não substitui a execução real dos comandos. O estado de compilação permanece **PENDENTE DE CONFIRMAÇÃO PELO CI** até que os logs do commit atualizado estejam disponíveis.
