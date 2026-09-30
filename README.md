# Estilo Marcado

> Plataforma de agendamento e gestao para saloes de beleza e barbearias.

## O que e

O **Estilo Marcado** e uma aplicacao web para organizar a rotina de saloes de
beleza e barbearias. Ela conecta clientes, profissionais e administradores em
um unico sistema, reduzindo conflitos de horario e facilitando o acompanhamento
dos atendimentos.

## Para que serve

O sistema sera usado para que clientes encontrem servicos e horarios
disponiveis, realizem agendamentos e acompanhem seus proximos atendimentos. Do
lado do estabelecimento, profissionais e recepcionistas poderao administrar a
agenda, os horarios de trabalho e a disponibilidade de cada servico.

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

Ainda nao ha codigo executavel. As instrucoes completas de instalacao,
configuracao e execucao serao adicionadas quando a primeira versao da aplicacao
for implementada.

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
