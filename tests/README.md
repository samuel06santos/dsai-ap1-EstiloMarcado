# Testes

## Backend

Os testes JUnit ficam em `tests/backend/` e são compilados pelo projeto Maven
em `src/backend/`. A suíte inclui testes unitários e de integração com
PostgreSQL via Testcontainers. Os arquivos anteriores em
`src/backend/src/test/java/` permanecem no projeto; o Maven executa as cópias
em `tests/backend/`. Para rodar todos a partir da raiz:

```powershell
mvn -f src/backend/pom.xml test
```

Pré-requisitos: JDK 21, Maven 3.9 ou superior e Docker disponível para os
testes de integração. Para executar somente uma classe, por exemplo:

```powershell
mvn -f src/backend/pom.xml -Dtest=OperacaoIntegrationTest test
```

## Frontend

Os testes Node ficam em `tests/frontend/` e usam o TypeScript instalado em
`src/frontend/node_modules`. Os arquivos anteriores em `src/frontend/tests/`
também permanecem no projeto; `npm test` executa as cópias em
`tests/frontend/`. Instale as dependências e execute:

```powershell
npm --prefix src/frontend ci
npm --prefix src/frontend test
npm --prefix src/frontend run build
```

`npm test` executa todos os arquivos `*.test.cjs` no diretório raiz de testes.
O build valida também os templates Angular em modo estrito.
