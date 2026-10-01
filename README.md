# Estilo Marcado

> Plataforma de agendamento e gestao para saloes de beleza e barbearias.

## O que e

O **Estilo Marcado** e uma aplicacao web para organizar a rotina de saloes de
beleza e barbearias. Ela conecta clientes, profissionais e administradores em
um unico sistema, reduzindo conflitos de horario e facilitando o acompanhamento
dos atendimentos.

## Para que serve

O sistema será usado para que clientes encontrem serviços e horários
disponíveis, realizem agendamentos e acompanhem seus próximos atendimentos. Do
lado do estabelecimento, profissionais e recepcionistas poderão administrar a
agenda, os horários de trabalho e a disponibilidade de cada serviço.

## Principais funcionalidades planejadas

- Cadastro e autenticacao de clientes, profissionais e administradores.
- Cadastro de unidades, profissionais, servicos, duracoes e precos.
- Configuracao de jornadas, folgas, feriados e bloqueios de agenda.
- Consulta de disponibilidade por profissional, servico e horario.
- Criacao, confirmacao, cancelamento e reagendamento de atendimentos.
- Prevencao de conflitos e sobreposicao de horarios.
- Painel para clientes, profissionais/recepcao e administracao.
- Lista de espera, notificacoes, historico e relatorios operacionais.

## Publicacao

- URL publica: **a definir durante a implantacao**.
- Estado atual: estrutura inicial do repositorio; a aplicacao ainda nao possui
  codigo executavel.

## Stack planejada

| Camada | Tecnologia planejada |
| --- | --- |
| Backend | Java e Spring Boot |
| Frontend | Angular e TypeScript |
| Banco de dados | PostgreSQL |
| Migracoes | Flyway |
| Testes | JUnit, Testcontainers e Playwright |
| Publicacao | Docker Compose e proxy reverso |

As tecnologias e versoes efetivamente utilizadas serao atualizadas nesta secao
quando a implementacao for iniciada.

## Como rodar

Com Docker Desktop em execucao, copie o arquivo de exemplo e inicie os
servicos:

```powershell
Copy-Item .env.example .env
docker compose up --build
```

Servicos locais disponiveis:

| Servico | URL |
| --- | --- |
| Frontend Angular | `http://localhost:4200` |
| API Spring Boot | `http://localhost:8080` |
| Saude da API | `http://localhost:8080/actuator/health` |
| pgAdmin | `http://localhost:5050` |
| Mailpit | `http://localhost:8025` |

As credenciais locais de PostgreSQL e pgAdmin estao em `.env`. Nao use esses
valores em producao e nao versione esse arquivo.

## Seed de dados mock (desenvolvimento)

Depois de subir os containers, popule o banco com dados de exemplo (filiais,
profissionais, servicos, contas de acesso, clientes vinculados e avulsos,
jornadas, atendimentos e historico de agendamento):

```powershell
# Windows / PowerShell
docker compose up --build -d
scripts/seed.ps1
```

```bash
# Linux / macOS
docker compose up --build -d
scripts/seed.sh
```

A seed cria o estabelecimento **Estilo Marcado (Mock)** e usa a faixa de IDs
reservada `1000+`, entao nao colide nem sobrescreve dados reais ja existentes.
Ela e idempotente: pode ser executada varias vezes sem duplicar registros.

Para recriar os dados do zero, use a opcao de reset (apaga todos os dados de
dominio, mas preserva as migracoes do Flyway):

```powershell
scripts/seed.ps1 -Reset
```

```bash
scripts/seed.sh --reset
```

Contas mock criadas (senha unica: `Estilo@2026`):

| E-mail | Perfil |
| --- | --- |
| `admin@estilomarcado.dev` | ADMINISTRADOR (Unidade Centro) |
| `recepcao@estilomarcado.dev` | RECEPCAO (Unidade Centro) |
| `admin.batista@estilomarcado.dev` | ADMINISTRADOR (Unidade Batista Campos) |
| `ana.souza@estilomarcado.dev` | PROFISSIONAL (Ana Souza) |
| `carlos.lima@estilomarcado.dev` | PROFISSIONAL (Carlos Lima) |
| `beatriz.rocha@estilomarcado.dev` | PROFISSIONAL (Beatriz Rocha) |
| `diego.mendes@estilomarcado.dev` | PROFISSIONAL (Diego Mendes) |
| `cliente@estilomarcado.dev` | CLIENTE |
| `joao.pereira@estilomarcado.dev` | CLIENTE |
| `maria.oliveira@estilomarcado.dev` | CLIENTE |

## Desenvolvimento orientado por especificacoes

Cada parte do sistema tera uma especificacao datada em `SPEC/`. A especificacao
sera commitada antes do codigo que ela descreve. Mudancas substanciais de rumo
receberao uma nova especificacao, indicando qual documento foi substituido.

## Rastreabilidade de IA

Os prompts e exportacoes brutas das sessoes de IA serao guardados em
`prompts/sessoes/`, incluindo tentativas que falharam, correcoes e prompts
curtos. Os modelos e ferramentas efetivamente usados serao registrados abaixo
e tambem nos trailers dos commits correspondentes.

| Ferramenta | Modelo | Uso no projeto |
| --- | --- | --- |
| Codex | A registrar conforme a exportacao da sessao | Desenvolvimento e documentacao |
| DeepSeek | A registrar conforme a exportacao da sessao | Desenvolvimento e documentacao |

Todo commit de implementacao deve terminar com trailers semelhantes a:

```text
Agent: codex/<modelo-exato> + manual
Spec: SPEC/AAAA-MM-DD-nome-da-parte.md
```

## Contagem de linhas

A contagem oficial sera executada apenas sobre arquivos versionados, usando o
comando definido pela atividade. O resultado final, incluindo a separacao de
linhas de testes, sera registrado nesta secao antes da apresentacao.

```text
Pendente: aplicacao ainda nao implementada.
```

## Equipe

- Joao Samuel Dias Santos
- Renan Vieira
