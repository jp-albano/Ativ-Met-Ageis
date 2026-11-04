# Relatório técnico de diagnóstico e correções — 2026-11-04

## 1. Objetivo

Registrar o diagnóstico técnico do repositório `Lucas-Belucci-Bellini/Ativ.-Geren-Proj`, explicar com exemplos o que pode falhar na execução e distinguir:

- defeito comprovado por evidência;
- limitação identificada na arquitetura;
- configuração ausente/dependência externa;
- validação ainda não executada;
- correção efetivamente aplicada.

Este relatório não transforma suspeitas em bugs confirmados. A auditoria disponível foi predominantemente estática; não houve execução local de Maven ou npm nesta sessão.

## 2. Estado analisado

- Branch: `main`
- Commit-base analisado: `eab60739bca0c36868b87616ec33d0f52450b846`
- Backend: Java 17 / Maven
- Frontend: Next.js / React / TypeScript
- API: servidor HTTP Java em `src/ApiServer.java`
- Proxy frontend: `frontend/next.config.ts`
- Workflow Java: `mvn --batch-mode --no-transfer-progress clean verify`
- Workflow frontend: `npm install` e `npm run build`

## 3. Diagnóstico: o que está errado ou pode falhar

### 3.1 Frontend não consegue alcançar a API

**Sintoma possível:** a interface apresenta mensagem semelhante a “Não foi possível falar com o servidor da API”.

**Causa técnica identificada:** o frontend encaminha chamadas `/api/...` por meio dos rewrites do Next.js para `BACKEND_URL`. O valor padrão é `http://127.0.0.1:8080`. Esse endereço só funciona quando o backend está ativo e acessível no mesmo ambiente de execução do frontend.

**Exemplo:**
- Next.js está rodando na porta 3000.
- O navegador solicita `/api/catalog`.
- O Next.js encaminha a chamada para `http://127.0.0.1:8080`.
- Se o processo Java não estiver iniciado, estiver em outra porta ou estiver em outro host/container, a conexão falha.

**Correção/configuração necessária:** iniciar o backend antes de testar a integração e configurar `BACKEND_URL` para o endereço interno correto do backend no ambiente de deploy. Em produção, `127.0.0.1` não deve ser presumido como endereço de outro serviço/container.

**Status:** causa de configuração/execução identificada; não há evidência nesta auditoria de que o código do rewrite tenha sido alterado ou de que o deploy esteja corrigido.

### 3.2 Integração SoundCloud depende de credenciais

**Sintoma possível:** backend inicia, mas operações de busca externa falham.

**Causa identificada:** a integração depende de credenciais como `SOUNDCLOUD_CLIENT_ID` e `SOUNDCLOUD_CLIENT_SECRET` ou token compatível. Essas credenciais não podem ser presumidas como existentes no ambiente.

**Exemplo:**
- `ApiServer` inicia normalmente.
- O usuário pesquisa uma faixa.
- A API tenta consultar o serviço externo.
- Sem credencial válida, a consulta externa não pode ser autenticada e a funcionalidade falha mesmo que o servidor HTTP esteja funcionando.

**Correção/configuração necessária:** cadastrar as credenciais válidas como variáveis de ambiente no ambiente em que o backend roda. Não colocar segredos no código, no frontend ou no Git. A aplicação deve retornar erro controlado e explicativo quando a integração não estiver configurada.

**Status:** dependência externa identificada; credenciais não foram criadas nem configuradas nesta auditoria.

### 3.3 Dados são perdidos ao reiniciar o backend

**Problema arquitetural confirmado pela inspeção:** catálogo, favoritos e playlists são mantidos em memória.

**Exemplo:**
1. Usuário adiciona uma música aos favoritos.
2. O backend mantém a informação durante a execução.
3. O processo Java é encerrado ou reiniciado.
4. Como os dados estavam apenas em memória, a lista pode voltar vazia.

**Correção necessária:** implementar persistência em banco de dados ou armazenamento durável, com migrações, tratamento de falhas e testes de reabertura dos dados.

**Status:** limitação confirmada; nenhuma camada de banco/persistência foi implementada nesta alteração.

### 3.4 API sem autenticação e isolamento por usuário

**Risco confirmado pela inspeção:** a API não possui autenticação. Não há garantia de que dados e operações estejam associados a uma identidade autenticada.

**Exemplo:** se a API for exposta publicamente sem uma camada de proteção, clientes não autenticados podem tentar chamar endpoints diretamente. Se houver dados compartilhados em memória, usuários diferentes também podem interferir no mesmo estado.

**Correção necessária antes de exposição pública:** implementar autenticação, autorização por recurso/usuário, validação de entrada, limites de requisição, CORS apropriado e configuração segura de rede. Até isso existir, manter a API em ambiente local ou privado.

**Status:** risco identificado; autenticação não foi implementada nesta alteração.

### 3.5 Arquivos Java na raiz não entram automaticamente no build Maven

**Causa identificada:** a configuração Maven compila os fontes em `src/` e executa testes em `tests/`. Arquivos Java soltos na raiz não são incluídos automaticamente nesse ciclo padrão.

**Exemplo:**
- Um arquivo `Exemplo.java` é editado na raiz.
- O comando `mvn clean verify` termina.
- O resultado não prova que `Exemplo.java` foi compilado, pois ele está fora do diretório de fontes configurado.

**Correção necessária:** decidir se cada arquivo deve ser migrado para a estrutura Maven, configurado como módulo/fonte adicional ou removido se for legado. Não basta ver o arquivo no repositório para concluir que integra o executável.

**Status:** discrepância estrutural/documental identificada; nenhum arquivo Java foi movido ou removido nesta alteração.

### 3.6 Workflow frontend não chama explicitamente o lint

**Causa identificada:** há script `npm run lint`, mas o workflow frontend observado executa `npm install` e `npm run build`, sem uma etapa explícita de lint.

**Exemplo:** um problema de estilo ou regra do ESLint pode não ser detectado por uma etapa de CI que não execute o lint, mesmo que o build compile.

**Correção recomendada:** incluir `npm run lint` como etapa independente do workflow e manter `npm run build` como verificação de build. Confirmar também a versão/configuração do ESLint usada pelo projeto.

**Status:** lacuna do pipeline identificada; workflow não foi modificado nesta alteração.

### 3.7 Build e testes ainda não têm resultado confirmado nesta auditoria

**O que ocorreu:** o conector GitHub disponível permitiu inspecionar arquivos e configurações, mas não executar diretamente `mvn` ou `npm` em uma máquina de build nesta sessão. O status Vercel anteriormente observado não é evidência de que o Maven ou o build Next.js passaram.

**Exemplo:** um check Vercel verde pode significar que o deploy frontend foi concluído, mas não prova que os testes Java foram executados.

**Correção necessária:** aguardar/acionar os workflows corretos e examinar os logs de cada job. Se houver falha, registrar a mensagem exata, corrigir a causa e repetir o workflow.

**Status:** pendente. Nenhuma falha de compilação específica foi confirmada porque não foi obtido log de execução Maven/npm.

## 4. Correções efetivamente realizadas nesta sessão

| Item | Alteração efetiva | Resultado |
|---|---|---|
| Registro técnico | Criado/atualizado este relatório em `docs/RELATORIO-TECNICO-DIAGNOSTICO-CORRECOES-2026-11-04.md` | Diagnóstico e próximos passos documentados |
| Relatório de compilação | Mantido `docs/RELATORIO-COMPILACAO-2026-11-04.md` | Estado de validação e comandos registrados |
| Código Java | Nenhuma alteração de implementação | Não declarar correção de bug Java |
| Código TypeScript/Next.js | Nenhuma alteração de implementação | Não declarar correção do proxy ou do frontend |
| Workflows CI | Nenhuma alteração | Lint ainda não foi adicionado ao workflow |
| Credenciais SoundCloud | Nenhuma alteração/configuração | Depende de configuração segura no ambiente |
| Persistência/autenticação | Nenhuma implementação | Permanecem como trabalho futuro |

**Transparência:** nesta sessão, a correção efetivamente aplicada foi documental: registrar os problemas, seus cenários de falha e os passos de correção. Não foi aplicada uma correção de código porque não havia logs de compilação/execução suficientes para apontar com segurança uma linha defeituosa. Fazer uma alteração especulativa poderia introduzir regressões.

## 5. Como validar depois das correções

### Backend

Na raiz do repositório:

```bash
mvn --batch-mode --no-transfer-progress clean verify
```

Critério: processo termina com código zero e todos os testes são aprovados. Se falhar, guardar o primeiro erro causal e o stack trace relevante.

### Frontend

Dentro de `frontend/`:

```bash
npm install
npm run lint
npm run build
```

Critério: lint e build terminam sem erro.

### Integração

1. Configurar `BACKEND_URL` com o endereço real do backend no ambiente.
2. Iniciar o backend Java na porta configurada.
3. Verificar endpoint de saúde/API diretamente.
4. Iniciar o Next.js.
5. Fazer uma chamada pela rota relativa `/api/...` e confirmar que chega ao backend.
6. Testar separadamente a integração SoundCloud com credenciais válidas.
7. Reiniciar o backend e verificar a persistência quando ela for implementada.

## 6. Ordem recomendada de trabalho

1. Obter logs reais dos workflows Maven e frontend.
2. Corrigir primeiro qualquer erro de compilação/teste reproduzível.
3. Corrigir configuração de `BACKEND_URL` e validar frontend ↔ backend no ambiente de deploy.
4. Configurar credenciais SoundCloud com secrets/variáveis protegidas.
5. Adicionar lint ao CI.
6. Implementar persistência durável.
7. Implementar autenticação/autorização antes de disponibilizar a API publicamente.
8. Revisar os arquivos Java soltos e definir se são código legado ou parte do projeto.

## 7. Conclusão

A auditoria identificou problemas e riscos concretos de configuração e arquitetura, especialmente a dependência do endereço do backend, credenciais externas, dados voláteis e ausência de autenticação. Também identificou lacunas de validação no CI e uma possível discrepância entre arquivos Java na raiz e a estrutura Maven.

Entretanto, não foi confirmado um erro específico de compilação Java ou TypeScript nesta sessão. Portanto, o relatório diferencia explicitamente o que foi observado do que ainda precisa ser reproduzido. A única alteração efetivamente aplicada nesta etapa foi a documentação técnica; as correções de código listadas permanecem pendentes até haver evidência suficiente e execução dos testes.
