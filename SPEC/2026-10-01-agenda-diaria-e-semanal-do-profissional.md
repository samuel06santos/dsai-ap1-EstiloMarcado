# Agenda diária e semanal do profissional (2026-10-01)

## O que é e por que existe

A agenda do profissional hoje é uma lista de um único dia, com botões de avançar
e retroceder. Isso responde "o que tenho hoje", mas não responde "como está a
semana" nem "que dias têm atendimento". Esta SPEC define a **visualização em
calendário** da agenda do profissional, no estilo do Google Calendar: uma grade
com datas clicáveis que, ao serem selecionadas, exibem as informações daquele
dia.

A entrega se apoia no painel profissional existente
(`2026-09-30-painel-profissional.md`) e **não** cria, confirma, cancela nem
reagenda atendimentos; o profissional continua somente leitura sobre a agenda,
conforme `2026-10-01-agendamento-confirmacao-cancelamento-e-reagendamento.md`.

## Escopo

- Um componente de calendário reutilizável, próprio do projeto, com três visões:
  **Mês**, **Semana** e **Dia**.
- Datas clicáveis: selecionar um dia mostra, no mesmo painel, os atendimentos
  daquele dia.
- Navegação entre meses/semanas, retorno a "Hoje" e destaque do dia atual e do
  dia selecionado.
- Blocos de atendimento posicionados pelo horário e pela duração, com os estados
  `AGENDADO`, `CONFIRMADO` e `CANCELADO` diferenciados.
- Um endpoint de leitura por **intervalo de datas** no painel profissional,
  para carregar semana e mês sem uma requisição por dia.
- Estados de carregamento, vazio, erro, acessibilidade por teclado e adaptação
  a telas pequenas.

## Situação atual e limites

- `AgendaProfissionalComponent` exibe uma data por vez, com `input date` e setas
  anterior/próximo, consumindo `GET /api/painel/agenda?data=AAAA-MM-DD`.
- `AgendaAtendimentoResponse` devolve `id`, `inicio`, `servico`, `cliente` e
  `status`, sem a hora de término.
- Não existe grade de calendário, visão de semana ou de mês, nem datas
  clicáveis.
- O `package.json` do frontend não possui biblioteca de calendário, e o único
  elemento visual relacionado é o ícone `calendar` de `ui-icon.component.ts`.
- A recepção e a administração têm a `AgendaOperacionalComponent` (lista do dia
  por filial), que também não é um calendário.
- `atendimento` guarda `inicio` local, a duração e o intervalo em minutos
  capturados na reserva, além do `fuso_horario_agendamento`. O término de um
  atendimento é `inicio + duracao_minutos`.

## Decisões

- **Componente próprio, sem dependência externa.** Não há biblioteca de
  calendário nas dependências, e a experiência deve seguir a linguagem visual
  vigente. O componente será criado no projeto, standalone, e não será
  adicionada uma dependência de terceiros apenas por causa da grade.
- **Componente de apresentação.** O calendário recebe os atendimentos e a data
  selecionada por `@Input` e comunica interações por `@Output`; ele não faz
  requisições. Quem busca os dados é a página que o utiliza. Assim, a mesma
  grade poderá ser reaproveitada por recepção e administração no futuro sem
  alterar a agenda do profissional.
- **Três visões, uma fonte de dados.** Mês, Semana e Dia são projeções da mesma
  lista de atendimentos do profissional; trocar de visão não muda a autorização
  nem os dados.
- **Leitura apenas.** Nenhuma ação de escrita é oferecida no calendário.

## Visões do calendário

### Mês

- Grade de 7 colunas por N semanas (5 ou 6), cobrindo o mês exibido e os dias
  necessários das semanas de borda, que aparecem esmaecidos.
- Cabeçalho com os dias da semana, na ordem ISO de segunda a domingo, coerente
  com o resto do sistema.
- Cada célula mostra o número do dia, a contagem de atendimentos ativos e
  marcadores por estado. Dia de hoje e dia selecionado recebem destaque
  distinto.
- Selecionar uma célula escolhe aquele dia e atualiza o painel de detalhes do
  dia, sem recarregar a página inteira.

### Semana

- Sete colunas de dias e uma grade de horários com passos de 30 minutos.
- Cada atendimento é um bloco posicionado por `inicio` e dimensionado por
  `fim = inicio + duracao`, mostrando ao menos serviço e horário.
- A grade rola para o primeiro atendimento da semana quando ela abre.
- Selecionar um dia da semana abre o painel de detalhes do dia; selecionar um
  bloco abre o detalhe daquele atendimento.

### Dia

- Lista ordenada por horário crescente, com `inicio`, `fim`, serviço, cliente e
  estado. É a visão padrão quando a tela é estreita.
- Um dia sem atendimentos exibe um estado vazio claro, distinguindo "sem
  atendimentos" de "erro ao carregar".

## Seleção de dia e informações do dia

- Clicar em uma data (em qualquer visão) seleciona o dia e mostra, em um painel
  ao lado ou abaixo, os atendimentos daquele dia com horário, serviço, cliente e
  estado.
- Cada atendimento selecionado mostra um detalhe de leitura: horário de início e
  término, serviço, cliente, estado e duração. Não há link para outro perfil nem
  ação de escrita.
- O estado selecionado é refletido na URL por parâmetros de consulta
  (`?data=AAAA-MM-DD&visao=semana`), de modo que recarregar ou voltar no
  navegador preserve a visão e o dia escolhidos.
- "Hoje" retorna ao dia atual e o seleciona.
- As setas de navegação avançam ou retrocedem um mês na visão de Mês e uma
  semana nas visões de Semana e Dia.

## Contexto e legibilidade

- Os estados usam cor **e** texto ou ícone: `AGENDADO`, `CONFIRMADO` e
  `CANCELADO` nunca se distinguem apenas pela cor.
- Atendimentos cancelados permanecem visíveis e visualmente atenuados, sem
  ocupar destaque de agenda ativa.
- Horários são sempre exibidos no fuso da filial da sessão. Se o fuso do
  navegador for diferente, a tela indica a filial e o fuso usados.
- A ordem e o agrupamento usam o horário local; não há conversão para o fuso do
  navegador.

## Dados e contrato HTTP

O painel profissional permanece somente leitura e restrito ao próprio
profissional da sessão, filial e vínculo ativos.

| Método e rota | Acesso | Resultado |
| --- | --- | --- |
| `GET /api/painel/agenda?data=AAAA-MM-DD` | Profissional autenticado | Mantém o comportamento atual: atendimentos do dia, em ordem crescente. |
| `GET /api/painel/agenda?de=AAAA-MM-DD&ate=AAAA-MM-DD` | Profissional autenticado | Atendimentos do intervalo, em ordem crescente por início e depois por id. |

- Exatamente uma forma é aceita: `data` **ou** o par `de`/`ate`. Enviar as duas
  ou nenhuma retorna `400 Bad Request`.
- O intervalo exige `de <= ate` e abrange no máximo 42 dias, cobrindo a maior
  grade mensal. Fora disso, `400`.
- Cada item passa a incluir `fim` (`inicio + duracao_minutos` do snapshot), sem
  remover nem renomear os campos atuais; `AgendaAtendimentoResponse` continua
  com `id`, `inicio`, `servico`, `cliente` e `status`.
- As respostas usam `Cache-Control: no-store`, pois um cancelamento ou
  reagendamento muda a agenda imediatamente. Não há paginação nesta versão,
  dado o horizonte limitado.
- O fuso exibido é obtido do vínculo da sessão (por exemplo,
  `GET /api/unidades/me`, que já devolve `fusoHorario`); o intervalo retorna
  apenas horários locais da filial.
- Sessão ausente retorna `401`; perfil sem permissão ou profissional inativo,
  filial inativa ou vínculo divergente retornam `403`. Nenhum dado de outro
  profissional é exposto.

## Componente de calendário reutilizável

- Nome de trabalho: `CalendarioAgendaComponent`, standalone, sem dependência
  externa.
- Entradas: lista de atendimentos (`id`, `inicio`, `fim`, `servico`, `cliente`,
  `status`), `fusoHorario`, `visao` (`MES` | `SEMANA` | `DIA`), `diaSelecionado`
  e `carregando`.
- Saídas: mudança de `visao`, mudança de `diaSelecionado`, mudança do intervalo
  visível (para a página carregar os dados) e seleção de um atendimento.
- O componente é puramente visual: não chama a API, não decide autorização e não
  guarda estado de negócio. A página `AgendaProfissionalComponent` busca o
  intervalo e injeta os resultados.
- A visão padrão é **Semana** em telas largas e **Dia** em telas estreitas
  (≤ 767 px); a última visão escolhida pode ser lembrada no navegador, sem
  virar dado de conta.

## Interface, navegação e acessibilidade

- Cabeçalho com alternância de visão (Mês, Semana, Dia), rótulo do período e
  ações Anterior, Hoje e Próximo.
- A grade usa `role="grid"`; cada dia é uma célula focável com rótulo completo
  ("terça-feira, 6 de outubro de 2026, 3 atendimentos"). Setas movem o foco
  entre dias, `Enter` ou `Espaço` selecionam o dia. O dia de hoje recebe
  `aria-current="date"` e o selecionado `aria-selected="true"`.
- Estados de carregamento, vazio e erro são anunciados em região `aria-live`.
- Foco visível, contraste mínimo WCAG AA e alvos de toque adequados, seguindo
  `2026-10-01-navegacao-visual-e-meu-perfil.md`.
- Em telas pequenas a visão de Semana vira a lista do Dia, sem rolagem
  horizontal; abaixo de 320 px não há conteúdo cortado.
- Transições curtas respeitam `prefers-reduced-motion`.

## Critérios de aceitação e testes

- O profissional vê apenas os próprios atendimentos; outro profissional, a
  recepção sem escopo ou uma conta sem vínculo não acessam a agenda alheia
  (`401`/`403`).
- A visão de Mês mostra a contagem por dia e destaca hoje e o dia selecionado;
  clicar em uma data atualiza os detalhes daquele dia.
- A visão de Semana posiciona cada bloco por início e duração e rola para o
  primeiro atendimento; a visão de Dia lista em ordem crescente.
- O intervalo de leitura respeita `de <= ate`, o máximo de 42 dias e a regra de
  "data ou intervalo"; violações retornam `400`.
- Cada item retorna `fim` calculado da duração capturada na reserva; alterar a
  duração do serviço depois não muda o `fim` de atendimentos já registrados.
- Dias sem atendimento mostram estado vazio distinto de erro; atendimentos
  cancelados aparecem atenuados e com texto/ícone, nunca só por cor.
- Navegar entre meses/semanas, acionar "Hoje" e recarregar a URL preservam a
  visão e o dia selecionados.
- O calendário funciona por teclado; leitores de tela anunciam dia, contagem e
  estado; telas estreitas usam a visão de Dia sem rolagem horizontal.
- Testes de integração do backend cobrem o intervalo, os limites, a ordenação,
  o cálculo de `fim` e `401`/`403`. Testes de interface cobrem troca de visão,
  clique em data, detalhe do dia, navegação, estado vazio, deep-link por URL,
  teclado e responsividade.

## Fora do escopo

- Criar, confirmar, cancelar ou reagendar atendimentos pelo calendário; isso
  pertence a `2026-10-01-agendamento-confirmacao-cancelamento-e-reagendamento.md`.
- Arrastar para criar ou mover, redimensionar blocos e edição embutida.
- Sincronização com Google Calendar, Outlook, iCal ou exportação de calendário.
- Calendário de recepção/administração e visão de vários profissionais,
  filiais, salas ou recursos em uma mesma grade.
- Sombreamento de horários fora da jornada, folgas, feriados e bloqueios na
  grade; a composição de janelas é definida por
  `2026-10-01-jornada-folgas-feriados-e-bloqueios.md` e pode ser integrada
  depois, sem alterar o contrato desta SPEC.
- Notificações, lembretes, check-in, conclusão, falta do cliente e avaliação.
- Paginação, cache de slots e pré-cálculo do calendário.

## Relação com outras SPECs e rastreabilidade

- Amplia `2026-09-30-painel-profissional.md`, mantendo a agenda por dia e os
  estados já exibidos, e acrescenta as visões de semana e mês e o intervalo de
  leitura.
- Respeita a autorização e os estados de
  `2026-10-01-agendamento-confirmacao-cancelamento-e-reagendamento.md`, sem
  oferecer ações de escrita ao profissional.
- Segue a linguagem visual, a navegação por perfil e os requisitos de
  acessibilidade de `2026-10-01-navegacao-visual-e-meu-perfil.md`.
- Usa fuso, estado de filial e vínculo de
  `2026-09-30-estabelecimentos-filiais-profissionais-e-permissoes.md`.
- Situa-se ao lado do painel do cliente
  (`2026-10-01-painel-do-cliente.md`), reutilizando o mesmo cuidado de
  apresentação de agenda e histórico.

Toda implementação derivada desta SPEC deve usar o trailer:

```text
Spec: SPEC/2026-10-01-agenda-diaria-e-semanal-do-profissional.md
```
