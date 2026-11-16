# [SoundCloud API] Documentar instalação e integrar autenticação, busca e reprodução

## Contexto

O projeto possui funcionalidades de catálogo musical e serviços relacionados ao SoundCloud. Foi fornecido ao responsável pela implementação o guia oficial da API:

- Guia: https://developers.soundcloud.com/docs/api/guide
- Seção de reprodução: https://developers.soundcloud.com/docs/api/guide#playing
- API base: `https://api.soundcloud.com`
- Host de autenticação: `https://secure.soundcloud.com`

Use a documentação oficial atual e, quando disponível, a especificação OpenAPI oficial como fonte de verdade. O resumo abaixo serve como orientação inicial e deve ser confirmado antes da implementação.

## Objetivo

Documentar como configurar as credenciais do aplicativo SoundCloud e integrar, de forma segura, autenticação de aplicação, busca de faixas, resolução de URLs, paginação e reprodução compatível com as permissões da plataforma.

A integração deve respeitar a arquitetura real do projeto e não pode expor segredos no navegador.

## Instruções para execução

1. Leia `AGENTS.md`, `README.md`, a documentação em `docs/` e os ADRs relevantes.
2. Inspecione a implementação atual antes de criar ou substituir serviços:
   - Serviços Java relacionados ao SoundCloud.
   - Rotas/endpoints da API.
   - DTOs, modelos e contratos existentes.
   - Chamadas feitas pelo frontend.
   - Testes e configuração de ambiente.
3. Faça um inventário do que já funciona e do que está faltando. Evite duplicar serviços ou criar uma segunda implementação sem necessidade.
4. Consulte a documentação oficial atual do SoundCloud. Confirme endpoints, parâmetros, autenticação, limites, permissões, formatos de resposta e regras de reprodução.
5. Documente o processo de cadastro/configuração do aplicativo SoundCloud e quais credenciais são necessárias. Não invente credenciais nem valores reais.
6. Use variáveis de ambiente com nomes claros e adicione apenas placeholders seguros ao `.env.example`.
7. Mantenha `client_secret`, tokens de acesso e refresh tokens exclusivamente no backend. Nunca os envie ao frontend, nunca os coloque em variáveis `NEXT_PUBLIC_` e nunca os registre em logs.
8. Escolha o fluxo de autenticação de acordo com a funcionalidade:
   - Client Credentials para recursos públicos, quando permitido pela documentação.
   - Authorization Code com PKCE somente se uma funcionalidade realmente precisar agir em nome de um usuário.
   - Não implemente login de usuário sem necessidade funcional definida.
9. Para Client Credentials, se esse fluxo estiver confirmado na documentação atual:
   - Solicite token no host oficial de autenticação.
   - Use o método de autenticação exigido pela documentação.
   - Reutilize e armazene o token no backend enquanto válido.
   - Trate expiração e renovação sem solicitar um token a cada requisição.
   - Considere limites de emissão de tokens e limites por IP/aplicativo.
10. Implemente ou adapte busca de faixas usando o endpoint oficial apropriado (por exemplo, `GET /tracks` com parâmetros de busca), respeitando a resposta real da API.
11. Implemente paginação conforme o mecanismo oficial, incluindo `linked_partitioning`, `limit` e/ou `next_href` quando aplicáveis. Não simule resultados adicionais.
12. Implemente resolução de links/permalinks com o endpoint oficial `/resolve`, se necessário ao fluxo do produto.
13. Trate reprodução de acordo com os dados de stream/transcodings e permissões retornados pela API:
   - Diferencie faixas reproduzíveis, disponíveis apenas para prévia e bloqueadas.
   - Respeite restrições geográficas, paywall, disponibilidade e permissões do uploader.
   - Não tente contornar bloqueios, paywalls, autenticação ou restrições regionais.
   - Não presuma que toda faixa encontrada possui URL de stream utilizável.
14. Verifique as exigências de atribuição do SoundCloud para qualquer player próprio:
   - Identificação do criador/uploader.
   - Identificação do SoundCloud como fonte, com a marca exigida.
   - Link para a faixa ou perfil correspondente, conforme o caso.
   - Demais exigências atuais dos termos e documentação.
15. Avalie se o Widget oficial ou oEmbed é mais apropriado do que implementar um player próprio. Registre a justificativa técnica e de produto.
16. Preserve os contratos já consumidos pelo frontend. Se algum DTO ou endpoint precisar mudar, atualize todos os consumidores e documente a alteração.
17. Trate erros HTTP de maneira explícita, incluindo autenticação, falta de permissão, recurso inexistente, limite de requisições e indisponibilidade temporária. Use retry com backoff apenas para falhas transitórias e de forma limitada.
18. Não instale bibliotecas aleatórias antes de verificar o que o projeto já utiliza e se a dependência é realmente necessária.
19. Garanta compatibilidade com a arquitetura de deploy definida para o projeto. O frontend usa Vercel; não presuma que um servidor Java persistente ou armazenamento em memória funcionará diretamente em ambiente serverless. Consulte o ADR da API e, se necessário, coordene a decisão arquitetural em uma ADR separada.
20. Não migre para Render nem introduza infraestrutura paga ou um fornecedor novo sem aprovação explícita.

## Segurança

- Segredos e tokens devem existir apenas no servidor.
- Não comite arquivos `.env` reais.
- Não registre tokens, client secrets ou cabeçalhos de autorização.
- Valide entradas fornecidas pelo usuário antes de consultar a API.
- Defina timeouts e limites de resposta.
- Evite chamadas redundantes à emissão de tokens.
- Não exponha endpoints internos que permitam usar credenciais do aplicativo arbitrariamente.

## Testes

Crie ou atualize testes automatizados para:

- Configuração ausente ou inválida.
- Obtenção e reutilização de token.
- Expiração/renovação de token.
- Busca de faixas e mapeamento de respostas.
- Paginação.
- Resolução de permalink, se implementada.
- Faixas reproduzíveis, prévias e bloqueadas.
- Respostas 400, 401, 403, 404, 429 e erros transitórios 5xx.
- Timeouts e falhas de rede.
- Garantia de que segredos e tokens não aparecem nas respostas do frontend nem nos logs.

Use mocks HTTP nos testes; não dependa de credenciais reais nem de chamadas externas para a suíte normal. Não invente resultados de testes: execute-os e informe os resultados reais.

## Documentação obrigatória

Crie ou atualize a documentação de instalação/configuração contendo:

1. Pré-requisitos.
2. Como registrar/configurar o aplicativo no SoundCloud, conforme o processo oficial atual.
3. Quais credenciais são necessárias e onde obtê-las.
4. Como configurar as variáveis de ambiente localmente.
5. Como configurar variáveis no ambiente de backend/produção, sem expor valores secretos.
6. Como iniciar o projeto e testar a integração.
7. Quais recursos exigem autenticação de usuário e quais funcionam com autenticação de aplicação.
8. Limitações de reprodução, faixas bloqueadas/prévias e atribuição obrigatória.
9. Limites conhecidos da API e comportamento de tratamento de erros.
10. Problemas comuns e como diagnosticá-los.

Inclua no `.env.example` somente nomes de variáveis e valores demonstrativos, por exemplo:

```dotenv
SOUNDCLOUD_CLIENT_ID=
SOUNDCLOUD_CLIENT_SECRET=
```

Adapte os nomes ao padrão já existente no repositório. Não adicione valores reais.

## Entregáveis

- Diagnóstico da integração SoundCloud existente.
- Guia de instalação e configuração atualizado.
- Configuração de ambiente documentada.
- Implementação ou correção dos serviços/rotas necessários.
- Busca e paginação funcionando conforme a API oficial.
- Resolução de URLs, se necessária ao produto.
- Fluxo de reprodução que respeite permissões e atribuição.
- Testes automatizados com mocks.
- Resumo dos arquivos alterados, testes executados e limitações restantes.

## Critérios de aceitação

- [ ] A implementação foi baseada na documentação oficial atual.
- [ ] O guia explica como configurar as credenciais do SoundCloud.
- [ ] Nenhum segredo ou token é enviado ao frontend ou incluído no repositório.
- [ ] Tokens são reutilizados e renovados de forma adequada ao fluxo implementado.
- [ ] Busca e paginação tratam corretamente as respostas reais da API.
- [ ] Estados de reprodução (reproduzível, prévia ou bloqueado) são tratados sem contornar restrições.
- [ ] Atribuição exigida pelo SoundCloud está presente, ou o Widget/oEmbed oficial foi escolhido e documentado.
- [ ] Os contratos existentes do frontend foram preservados ou migrados de forma documentada.
- [ ] Testes automatizados cobrem os principais fluxos e erros.
- [ ] Compatibilidade com a arquitetura de deploy do projeto foi avaliada.
- [ ] Não houve migração para Render nem contratação/configuração de serviço externo sem aprovação.

## Restrições de escrita

Siga `AGENTS.md` e as regras do repositório. Trabalhe na branch autorizada. Não faça commit, push, PR, alterações em Actions ou deploy sem autorização explícita. Não enfraqueça CI nem remova testes para obter aprovação artificial.
