# Visao geral — Estilo Marcado (2026-09-30)

## O que e e por que existe

O Estilo Marcado e uma plataforma web de agendamento e operacao para saloes de
beleza e barbearias. O sistema centraliza a disponibilidade dos profissionais,
os servicos oferecidos e os atendimentos marcados, para que clientes encontrem
horarios validos e o estabelecimento opere a agenda sem conflitos.

O problema principal e evitar que a agenda seja administrada apenas por
mensagens e anotacoes dispersas, situacao que pode causar sobreposicao de
horarios, perda de informacoes e dificuldade de acompanhar atendimentos.

## Perfis de usuario

- **Cliente:** consulta servicos e horarios, realiza e acompanha seus
  agendamentos.
- **Profissional:** consulta sua agenda, sua disponibilidade e os atendimentos
  sob sua responsabilidade.
- **Recepcao:** organiza atendimentos do estabelecimento e auxilia clientes na
  marcacao de horarios.
- **Administrador:** configura o estabelecimento, seus profissionais, servicos
  e regras operacionais.

## Escopo da primeira versao

A primeira versao deve permitir:

- identificar usuarios e aplicar permissoes por perfil;
- cadastrar profissionais, servicos, duracoes, precos e horarios de trabalho;
- configurar indisponibilidades, como folgas e bloqueios de agenda;
- consultar horarios disponiveis para um servico e profissional;
- criar, confirmar, cancelar e reagendar atendimentos;
- impedir reservas que se sobreponham para o mesmo profissional ou recurso;
- apresentar paineis adequados para cliente, profissional/recepcao e
  administracao;
- manter historico e informacoes operacionais basicas dos atendimentos.

## Criterios de aceitacao da visao geral

- A URL publica abre a pagina inicial do sistema sem etapa de instalacao no dia
  da apresentacao.
- Um cliente consegue percorrer um fluxo demonstravel de escolha de servico,
  profissional, horario e criacao de agendamento.
- Um administrador consegue configurar os dados necessarios para que o fluxo
  de agendamento funcione.
- O sistema nao permite dois agendamentos ativos para o mesmo profissional no
  mesmo intervalo de tempo.
- Cada funcionalidade implementada possui uma spec propria, datada e commitada
  antes do codigo correspondente.
- Cada funcionalidade possui testes coerentes com seus criterios de aceitacao.

## Fora do escopo inicial

- Processamento de pagamentos reais.
- Integracao direta com WhatsApp ou outros mensageiros externos.
- Aplicativos nativos para Android e iOS.
- Marketplace entre estabelecimentos diferentes.
- Importacao de dados de sistemas de terceiros.

## Decisoes de arquitetura

- A aplicacao sera desenvolvida como um monolito modular, com frontend web,
  API backend e banco de dados relacional.
- As partes do sistema serao especificadas e implementadas incrementalmente.
- Regras que previnem conflitos de agenda devem ser protegidas tanto na camada
  de aplicacao quanto no banco de dados.
- O codigo de aplicacao, testes e documentos de cada parte devera permanecer
  rastreavel por commits pequenos e frequentes.

## Partes que receberao specs proprias

Esta visao geral nao substitui as specs de implementacao. Antes de criar codigo,
cada uma das partes abaixo recebera seu proprio arquivo datado em `SPEC/`:

1. autenticacao e autorizacao;
2. estabelecimentos, profissionais e servicos;
3. jornadas, folgas e bloqueios;
4. motor de disponibilidade;
5. agendamentos e prevencao de conflitos;
6. paineis por perfil;
7. notificacoes, lista de espera, historico e relatorios.
