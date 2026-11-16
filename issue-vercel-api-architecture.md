# [Vercel] Corrigir build e definir arquitetura da API sem Render

## Contexto

O build do frontend Next.js está falhando na Vercel ao carregar `frontend/next.config.ts`.

Erro observado:

```text
Failed to load next.config.ts
Error: Defina BACKEND_URL nas variáveis do projeto Vercel para apontar para a API Java.
```

O projeto usa Next.js 15 no frontend e possui uma API Java implementada em `src/ApiServer.java`, baseada em `com.sun.net.httpserver.HttpServer`.

No estado inspecionado anteriormente:

- `frontend/next.config.ts` exige `BACKEND_URL` quando `process.env.VERCEL` está definido.
- O arquivo configura um rewrite de `/api/:path*` para o endereço definido em `BACKEND_URL`.
- Localmente, o fallback é `http://127.0.0.1:8080`.
- O servidor Java escuta por padrão em `127.0.0.1:8080`.
- A API mantém parte dos dados em memória (por exemplo, catálogo, favoritos e playlists).
- A documentação atual descreve a necessidade de uma URL HTTPS para a API Java na produção.

**Decisão obrigatória:** o frontend deve continuar hospedado na Vercel. Não migrar o projeto para Render. Não presumir que a Vercel executa o servidor Java persistente atual como uma Function compatível.

## Objetivo

Investigar a falha do build e propor/implementar uma arquitetura de API que funcione com o frontend hospedado na Vercel, sem mascarar o erro e sem introduzir uma URL fictícia para `BACKEND_URL`.

## Instruções para execução

1. Leia `AGENTS.md`, `README.md`, a documentação em `docs/` e o ADR-0002 antes de alterar qualquer código.
2. Inspecione o estado real da branch e os arquivos atuais relacionados à API e ao frontend. Não confie apenas neste texto: confirme se os caminhos e comportamentos ainda são válidos.
3. Reproduza e explique a falha do build.
4. Separe a causa fatal dos avisos:
   - A ausência de `BACKEND_URL` é o erro fatal conhecido.
   - Avisos do npm, `esbuild` ou `cache-dir` não devem ser tratados como a causa sem evidência.
5. Consulte a documentação oficial e atual da Vercel sobre runtimes, Functions, Next.js e deploys. Não invente suporte oficial a runtime Java.
6. Mapeie todos os endpoints existentes da API Java e identifique quais funcionalidades dependem de estado em memória.
7. Compare alternativas viáveis, considerando:
   - Migrar endpoints para Route Handlers / Vercel Functions compatíveis.
   - Manter a API Java em um serviço externo compatível, somente se houver infraestrutura aprovada e uma URL real.
   - Outra arquitetura tecnicamente válida, documentada e compatível com as restrições do projeto.
8. Antes de uma reestruturação arquitetural, registre a decisão em um novo ADR dentro de `docs/architecture/decisions/`, explicando alternativas, limitações, custos, persistência, segurança e consequências.
9. Preserve o contrato HTTP consumido pelo frontend sempre que possível. Se for necessário alterá-lo, documente a migração e atualize todos os consumidores e testes.
10. Não remova a validação de `BACKEND_URL` apenas para fazer o build passar. Não use `localhost`, `127.0.0.1`, URLs fictícias ou serviços temporários como destino de produção.
11. Se a solução escolhida exigir uma URL externa, não invente nem configure essa URL. Deixe explícito qual infraestrutura ainda precisa ser provisionada e quais variáveis devem ser cadastradas na Vercel.
12. Considere persistência: dados em memória de um processo não devem ser tratados como armazenamento durável em ambiente serverless ou distribuído.
13. Não introduza Render. Não adicione serviços pagos, bancos ou fornecedores externos sem aprovação explícita.
14. Não altere configurações de produção, segredos, domínios ou deploys sem autorização explícita.

## Segurança e configuração

- Nunca exponha credenciais ou segredos em código frontend.
- Não use prefixo `NEXT_PUBLIC_` para segredos.
- Valide variáveis obrigatórias no ambiente correto e forneça mensagens de erro claras.
- Atualize `.env.example` apenas com nomes e valores ilustrativos seguros.
- Documente separadamente a configuração local e a configuração de produção na Vercel.

## Testes e validação

- Adicione ou atualize testes para a configuração e para os endpoints afetados.
- Teste a ausência e a presença das variáveis obrigatórias.
- Teste os rewrites ou Route Handlers conforme a arquitetura escolhida.
- Execute lint, testes e build do frontend.
- Execute os testes Java aplicáveis.
- Não declare um teste como aprovado se ele não foi executado.
- Se não for possível validar um deploy real, informe isso claramente; build local não equivale a deploy de produção.

## Entregáveis

- Diagnóstico da falha original.
- Mapa dos endpoints atuais e dependências de estado.
- ADR com a arquitetura escolhida e justificativa.
- Implementação da solução aprovada, se puder ser feita sem serviços/credenciais ainda não disponíveis.
- `.env.example` e documentação de configuração atualizados.
- Testes de regressão.
- Resumo final com arquivos alterados, comandos executados, resultados reais e pendências manuais.

## Critérios de aceitação

- [ ] O frontend permanece hospedado na Vercel.
- [ ] Nenhuma migração para Render foi feita.
- [ ] A arquitetura da API é compatível com o ambiente de produção escolhido e está documentada.
- [ ] Nenhuma URL de backend fictícia foi adicionada.
- [ ] O contrato da API e as funcionalidades existentes foram preservados ou sua migração foi documentada.
- [ ] Persistência e limitações de estado foram tratadas corretamente.
- [ ] Segredos não foram expostos ao frontend.
- [ ] Testes, lint e build foram executados e seus resultados foram relatados honestamente.
- [ ] Pendências que dependem de infraestrutura ou autorização estão claramente listadas.

## Restrições de escrita

Siga as regras de `AGENTS.md` e as instruções do repositório. Trabalhe na branch autorizada, sem commit, push, PR, alteração de Actions ou deploy até autorização explícita. Não enfraqueça CI nem remova testes para obter um build verde.
