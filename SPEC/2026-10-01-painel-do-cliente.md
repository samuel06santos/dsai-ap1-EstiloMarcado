# Painel do cliente com histórico e próximos horários (2026-10-01)

## O que é e por que existe

O painel do cliente é a área inicial da conta `CLIENTE` autenticada. Ele reúne,
em uma única tela, o próximo atendimento, os próximos horários, o histórico
recente e atalhos para as ações que o cliente pode realizar. Hoje o início
autenticado é genérico e "Meus agendamentos" exibe os atendimentos de um mês por
vez, sem destacar o que está por vir nem resumir o histórico.

Esta SPEC define o painel e as capacidades do cliente em torno dele. Ela **não**
redefine o ciclo de vida do agendamento nem o motor de disponibilidade: o
agendamento, o cancelamento e o reagendamento continuam regidos por
`2026-10-01-agendamento-confirmacao-cancelamento-e-reagendamento.md`, e o perfil
e a navegação por `2026-10-01-navegacao-visual-e-meu-perfil.md`. O painel apenas
organiza e apresenta esses recursos para o cliente.

## Escopo

- Página inicial do cliente autenticado com saudação, próximo atendimento,
  próximos horários, histórico recente, resumo e atalhos.
- Endpoint agregador `GET /api/me/painel` para carregar o painel em uma única
  requisição.
- Consulta paginada dos próprios atendimentos com filtros por período, estado,
  filial e serviço, reaproveitando a listagem existente.
- Detalhe de um atendimento do cliente.
- Ações do cliente a partir do painel: cancelar, reagendar e **agendar
  novamente** a partir do histórico.
- Atalho para atualizar o próprio contato (telefone/WhatsApp).
- Estados de carregamento, vazio, erro e acessibilidade coerentes com a
  navegação atual.

## Situação atual e limites

- A rota `/` renderiza a `HomeComponent`; para cliente autenticado ela mostra
  apenas uma saudação genérica e um botão para o perfil.
- A rota `/meus-agendamentos` já existe e lista os atendimentos de um mês, com
  cancelamento e reagendamento. A rota `/conta` já edita nome e telefone.
- A página pública `/unidades/:id` permite escolher serviço, profissional e
  horário, e `/unidades/:id/revisar` conclui a reserva.
- Endpoints existentes reaproveitados: `GET /api/me/agendamentos`
  (`de`, `ate`, `status`, `pagina`, `tamanho`), `GET /api/agendamentos/{id}`,
  `GET /api/agendamentos/{id}/horarios-reagendamento`,
  `POST /api/agendamentos/{id}/cancelamentos`,
  `PATCH /api/agendamentos/{id}/reagendamento`,
  `POST /api/unidades/{unidadeId}/agendamentos` e `GET`/`PATCH
  /api/usuarios/me`.
- Não existe endpoint agregador nem destaque de "próximo atendimento". A
  listagem é limitada a 31 dias por pedido, o que dificulta compor um resumo de
  histórico longo.
- Não há diretório global de filiais nem marketplace. O cliente chega a uma
  filial por link ou página pública; a última filial visitada é contexto de
  navegação guardado no navegador, não vínculo de conta.
- Ainda não há estados `CONCLUIDO` ou `FALTOU`, check-in ou avaliação. Para o
  painel, um atendimento passado e não cancelado é tratado como **realizado**.
- A tabela `atendimento` já possui o índice `(cliente_id, inicio, id)`, criado
  pela migração de agendamentos, suficiente para as consultas do painel.

## Conceitos

Todas as datas e horas de um atendimento são interpretadas no fuso
`fuso_horario_agendamento` capturado na reserva. O "agora" é o relógio do
servidor; o frontend nunca decide sozinho sobre uma ação proibida.

| Conceito | Definição |
| --- | --- |
| Atendimento ativo | Status `AGENDADO` ou `CONFIRMADO` com início futuro. |
| Próximo atendimento | Atendimento ativo de menor instante absoluto de início. |
| Próximos horários | Atendimentos ativos em ordem crescente de início. |
| Realizado | Atendimento `AGENDADO` ou `CONFIRMADO` cujo início já passou. |
| Histórico | Atendimentos realizados e cancelados, em ordem decrescente de início. |

Como uma conta cliente pode agendar em filiais de fusos diferentes, o painel
ordena os atendimentos pelo **instante absoluto** derivado de `inicio` e do
`fuso_horario_agendamento` de cada registro, e não pelo valor de parede.

## O que o cliente pode fazer

| Capacidade | Estado | Origem |
| --- | --- | --- |
| Consultar filial, serviços, profissionais e horários | Existente | Motor de disponibilidade e página pública |
| Criar um agendamento para si | Existente | SPEC de agendamento |
| Ver o painel com próximo, próximos e histórico | Nova | Esta SPEC |
| Ver o detalhe de um atendimento próprio | Existente | SPEC de agendamento |
| Cancelar um atendimento próprio | Existente | SPEC de agendamento |
| Reagendar um atendimento próprio | Existente | SPEC de agendamento |
| Agendar novamente a partir do histórico | Nova | Esta SPEC |
| Consultar e atualizar nome e telefone de contato | Existente | SPEC de navegação e perfil |
| Sair da conta | Existente | SPEC de autenticação |

Fora desta versão, e registradas como evolução futura: notificações e lembretes,
lista de espera, avaliação do atendimento, pagamento e sinal, favoritos
persistidos no servidor, recomendações e calendário externo.

## Painel (contrato)

`GET /api/me/painel` — acesso exclusivo de cliente autenticado com conta ativa.
Resposta `200 OK` com `Cache-Control: no-store`.

| Campo | Conteúdo |
| --- | --- |
| `agora` | Instante atual do servidor em ISO-8601 com offset, para cálculo de ações no cliente. |
| `cliente` | `nome` e `telefoneContato` (pode ser nulo) da própria conta. |
| `proximo` | O próximo atendimento ativo, ou `null`. |
| `proximos` | Até N atendimentos ativos, em ordem crescente de início. |
| `historico` | Até N atendimentos realizados e cancelados recentes, em ordem decrescente. |
| `resumo` | `proximosAtivos`, `realizados` e `cancelados` da conta. |

- `N` é 5 por padrão e pode ser ajustado por `limiteProximos` e `limiteHistorico`,
  inteiros entre 1 e 20; valores fora da faixa retornam `400`.
- Cada item usa o DTO `PainelAgendamento`: `id`, `unidadeId`, `unidadeNome`,
  `unidadeAtiva`, `servicoId`, `servicoNome`, `servicoAtivo`, `profissionalId`,
  `profissionalNome`, `inicio`, `fim`, `fusoHorario`, `status`, `precoAcordado`,
  `canceladoEm` e `motivoCancelamento`.
- `unidadeAtiva` e `servicoAtivo` permitem esconder "agendar novamente" quando a
  filial ou o serviço não está mais disponível. Nomes de filial e profissional
  são dados públicos de recurso ativo e podem ser exibidos.
- O painel nunca expõe `clienteId` de terceiros, e-mail, telefone, observações
  internas de outra filial, tokens ou dados de outro cliente.
- O `clienteId` do próprio usuário continua resolvido pela sessão; o corpo e a
  rota não aceitam identificadores de cliente.
- Conta sem vínculo `cliente` ainda responde `200` com listas vazias e
  `proximo: null`; o vínculo é criado no primeiro agendamento, conforme a SPEC
  de agendamento.

## Listagem e filtros

A tela "Meus agendamentos" continua usando a listagem existente, ampliada de
forma retrocompatível:

`GET /api/me/agendamentos?de=&ate=&status=&unidadeId=&servicoId=&pagina=&tamanho=`

- `de` e `ate` permanecem obrigatórios, locais, com `de <= ate` e abrangência
  máxima de 31 dias por pedido; a paginação mantém o limite máximo de 100 itens.
- `unidadeId` e `servicoId` passam a ser filtros opcionais **apenas** sobre os
  atendimentos do próprio cliente.
- A ordenação continua por início crescente e identificador para desempate.
- Os campos `unidadeNome`, `unidadeAtiva`, `profissionalNome` e `servicoAtivo`
  passam a acompanhar a resposta de listagem e de detalhe, como adição que não
  quebra consumidores atuais.

## Ações do cliente

- **Cancelar**: reutiliza `POST /api/agendamentos/{id}/cancelamentos` com motivo
  opcional. Só é permitido antes do início; depois disso o backend responde
  `409`, e o painel recarrega o atendimento.
- **Reagendar**: reutiliza `GET /api/agendamentos/{id}/horarios-reagendamento` e
  `PATCH /api/agendamentos/{id}/reagendamento`. O atendimento volta a `AGENDADO`
  e as regras da SPEC de agendamento permanecem válidas.
- **Agendar novamente**: a partir de um item do histórico ou de um próximo
  atendimento, a interface abre a página pública da filial com o serviço
  pré-selecionado quando ele ainda estiver ativo, e o cliente escolhe
  profissional e horário. A ação **não** clona o atendimento, não reutiliza
  preço, duração ou `profissional` antigos e não cria nada sem uma nova
  confirmação de reserva. Se a filial ou o serviço estiver inativo, o painel
  explica o motivo e não oferece o atalho.
- **Atualizar contato**: atalho para `/conta`. Se `telefoneContato` estiver
  ausente, o painel sugere preenchê-lo para que a equipe consiga contatar o
  cliente, sem bloquear o uso.
- Após qualquer ação, o painel consulta novamente o resumo e as listas. Botões
  ficam desabilitados durante o envio e apresentam resultado de sucesso ou erro.

## Interface e fluxos

- A rota inicial `/` passa a renderizar o painel para `CLIENTE` autenticado; os
  demais perfis mantêm seus respectivos inícios. A sidebar do cliente permanece
  com **Início**, **Meus agendamentos**, **Meu perfil** e, quando houver filial
  pública selecionada, **Filial e serviços**.
- O painel apresenta, de cima para baixo: saudação com o nome do cliente; cartão
  do próximo atendimento com data e hora no fuso da filial, serviço,
  profissional, filial, valor e estado; lista dos próximos horários; resumo com
  contadores; histórico recente com "agendar novamente"; e atalhos para novo
  agendamento, perfil e contato.
- Estado vazio: sem atendimentos ativos, o painel convida a agendar, usando a
  última filial visitada quando ela ainda estiver pública; sem histórico, mostra
  mensagem explicativa. Sem filial conhecida, orienta a abrir a página da filial
  por link.
- O horário é sempre exibido com indicação da filial e do fuso, destacando
  quando o fuso da filial difere do navegador.
- Erros de rede preservam o que já foi carregado e oferecem nova tentativa.
  Nenhum dado sensível de outro cliente é exibido, e a interface nunca assume
  autorização apenas por ocultar botões.
- Acessibilidade: cartões e listas com títulos e rótulos compreensíveis, estados
  anunciados em regiões `aria-live` para carregamento e mensagens, foco visível
  e alvos de toque adequados, seguindo a linguagem visual vigente.

## Dados, desempenho e segurança

- Nenhuma tabela nova é obrigatória. O painel agrega `atendimento`, `cliente` e
  o vínculo de `usuario`. O índice `(cliente_id, inicio, id)` já existente
  atende às consultas; se o filtro por estado se tornar frequente, um índice
  composto pode ser avaliado em migração futura, sem alterar o contrato.
- As duas listas e as contagens são obtidas em consultas limitadas e indexadas,
  sem carregar todo o histórico do cliente nem executar uma consulta por item.
- Toda resposta usa `Cache-Control: no-store`, pois cancelamentos, confirmações
  e reagendamentos mudam o resultado imediatamente.
- A autorização usa a identidade da sessão e o vínculo `cliente.usuario_id` no
  servidor. Requisição sem sessão retorna `401`; perfil sem permissão, `403`;
  recurso de outro cliente ou inexistente, `404`.
- A listagem e o painel não aceitam `clienteId`, `usuarioId` ou `perfil`
  enviados pelo cliente como fonte de autorização, coerente com as SPECs de
  autenticação e de agendamento.

## Critérios de aceitação e testes

- Um cliente ativo autentica, recebe o painel com próximo, próximos, histórico e
  resumo coerentes com seus atendimentos, e não vê dados de outro cliente.
- Um cliente sem atendimentos recebe listas vazias, `proximo: null` e contadores
  zerados, sem erro.
- `proximos` contém apenas `AGENDADO` e `CONFIRMADO` com início futuro, em ordem
  crescente; `historico` contém realizados e cancelados, em ordem decrescente.
- Atendimentos de filiais em fusos diferentes são ordenados pelo instante
  absoluto e exibidos com o fuso correto de cada filial.
- `limiteProximos` e `limiteHistorico` respeitam a faixa 1–20 e valores fora
  dela retornam `400`.
- Cancelar e reagendar a partir do painel usam as regras da SPEC de agendamento:
  permitidos apenas antes do início, com os mesmos códigos de erro; após a ação,
  o painel reflete o novo estado.
- "Agendar novamente" só aparece quando filial e serviço estão ativos; nunca
  cria um atendimento sem nova confirmação nem reutiliza preço, duração ou
  profissional antigos.
- A listagem filtra por período, estado, filial e serviço apenas sobre os
  registros do próprio cliente, mantendo o limite de 31 dias por pedido e a
  paginação máxima de 100 itens.
- Respostas de painel, listagem e detalhe usam `Cache-Control: no-store` e não
  expõem identificadores internos desnecessários de outros usuários.
- Testes de integração cobrem painel vazio e preenchido, ordenação por instante,
  fusos distintos, filtros, contadores, `401`/`403`/`404` por proprietário,
  `400` de limites e a integração com cancelamento e reagendamento. Testes de
  interface cobrem estado vazio, cartão do próximo atendimento, histórico,
  "agendar novamente", atualização de contato e acessibilidade dos estados.

## Fora do escopo

- Notificações, lembretes, confirmação ou cancelamento por e-mail/WhatsApp e
  calendário externo.
- Lista de espera, encaixe manual, fila de prioridade e reagendamento automático.
- Estado `CONCLUIDO`/`FALTOU`, check-in, início e conclusão do serviço, e
  avaliação do atendimento.
- Pagamento, sinal, reembolso, cupom, fidelidade e histórico financeiro.
- Favoritos persistidos no servidor, recomendação por histórico e perfil de
  preferências.
- Marketplace, diretório global de filiais e transferência de atendimento entre
  filiais, serviços ou profissionais sem cancelar e criar outro registro.
- Exportação de dados pessoais e exclusão de conta.

## Relação com outras SPECs e rastreabilidade

- Reutiliza identidade, sessão, CSRF e matriz de acesso de
  `2026-09-30-autenticacao-e-controle-de-acesso.md`.
- Consome o ciclo de vida, os endpoints e os snapshots de
  `2026-10-01-agendamento-confirmacao-cancelamento-e-reagendamento.md` sem
  alterá-los; apenas acrescenta campos de leitura e filtros retrocompatíveis.
- Depende da disponibilidade de `2026-10-01-motor-de-disponibilidade.md` para o
  atalho de novo agendamento e de
  `2026-10-01-navegacao-visual-e-meu-perfil.md` para navegação, avatar e a
  página de perfil.
- É simétrica ao painel do profissional de
  `2026-09-30-painel-profissional.md`, adaptada às necessidades do cliente.
- Complementa `2026-09-30-visao-geral.md`, que prevê painéis por perfil.

Toda implementação derivada desta SPEC deve usar o trailer:

```text
Spec: SPEC/2026-10-01-painel-do-cliente.md
```
