# Decisões de Arquitetura

Este diretório reúne decisões técnicas relevantes do projeto por meio de Architecture Decision Records (ADRs).

## Estado atual

**Aplicativo acadêmico de terminal:** Java, conforme ADR-0001.  
**Frontend web:** Next.js + React em `frontend/`, consumindo a API Java (ADR-0002).  
**Backend web:** API HTTP em Java com o servidor embutido do JDK, conforme ADR-0002. Backend congelado após essa decisão.  
**Banco de dados/persistência:** PENDENTE (estado em memória).  
**Contratos de integração:** [`docs/API.md`](../../API.md).

A implementação dos Cards 4 e 5 pode prosseguir dentro do escopo aprovado em ADR-0001. Mudanças transversais de arquitetura, como migração de pacotes, introdução de framework web ou configuração de banco, continuam exigindo decisão registrada antes da implementação.

## Decisões registradas

- [ADR-0001 — Aplicativo de terminal em Java](./ADR-0001-java-terminal.md): Java puro para o app de terminal, mantendo os fontes atuais no pacote padrão e adiando a decisão de build/testes externos.
- [ADR-0002 — API HTTP em Java consumida pelo frontend Next.js](./ADR-0002-api-http-java-frontend-next.md): servidor HTTP do JDK expõe o domínio Java; o Next.js repassa `/api` para ele.

## Próximas decisões possíveis

Se o grupo decidir ampliar o escopo, criar ADRs separados para:
- ferramenta de build e testes (Maven/Gradle e eventual JUnit);
- migração para pacotes nomeados;
- persistência e autenticação da API, caso se tornem requisitos.
