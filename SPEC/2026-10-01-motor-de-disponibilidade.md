# Motor de disponibilidade (2026-10-01)

## O que é e por que existe

O motor de disponibilidade transforma as janelas de trabalho de um profissional
em horários concretos nos quais um serviço pode começar. A resposta precisa
considerar a duração do serviço, o intervalo entre atendimentos, as reservas já
existentes e o fuso da filial. Assim, o cliente vê apenas opções que, no momento
da consulta, podem ser atendidas sem conflito.

Esta SPEC **consome** as janelas produzidas por
`2026-10-01-jornada-folgas-feriados-e-bloqueios.md`; não redefine jornada,
feriados ou bloqueios. A consulta é uma prévia, não uma reserva. A criação e o
reagendamento de atendimentos deverão repetir a mesma validação de forma
transacional, conforme uma SPEC própria de agendamento.

## Escopo e decisões

- Consultar horários de um serviço em uma filial ativa, para uma data local.
- Consultar um profissional específico ou todos os profissionais ativos e
  habilitados para o serviço na mesma filial.
- Aplicar janelas de trabalho, duração do serviço, intervalo entre atendimentos,
  atendimentos ativos e a grade de horários de início.
- Usar uma única regra de elegibilidade para a consulta e para a futura
  confirmação de agendamento.
- Expor apenas dados necessários para a escolha do horário, sem revelar clientes,
  motivos de bloqueio ou detalhes da agenda interna.

Não há escolha automática de "melhor profissional": sem filtro, cada combinação
de horário e profissional é uma opção distinta. Não há capacidade compartilhada
entre profissionais nem limite de atendimentos simultâneos da filial nesta
versão.

## Entradas e pré-condições

- `unidadeId`, `servicoId` e `data` são obrigatórios. `profissionalId` é opcional.
- A data é interpretada no fuso IANA de `unidade.fuso_horario`, nunca no fuso do
  navegador ou do servidor.
- A consulta aceita hoje até o 60º dia futuro, inclusive, segundo a data local
  da filial. Data passada ou além desse horizonte retorna `400 Bad Request`.
- A filial, o serviço e o profissional devem estar ativos. O serviço pertence à
  filial; o profissional, quando informado, pertence à mesma filial e está
  habilitado em `servico_profissional`. Recurso inexistente, inativo ou fora
  dessa relação não gera horário e retorna `404 Not Found` quando identificado
  explicitamente na rota ou no filtro.
- Sem `profissionalId`, a inexistência de profissionais elegíveis retorna lista
  vazia, não erro. Profissional elegível sem jornada também retorna lista vazia.
- A duração do serviço é positiva em minutos. `intervaloMinutos` nulo equivale
  a zero e nunca é negativo, conforme a SPEC do catálogo.

## Modelo temporal

- Todos os intervalos são semiabertos: `[início, fim)`. Um atendimento que
  termina às 10:00 não ocupa o instante 10:00, ressalvado seu intervalo após o
  atendimento.
- O horário oferecido é o **início** do serviço. O fim exibido é `início +
  duracaoMinutos`, sem incluir o intervalo posterior.
- O serviço inteiro deve caber em **uma única** janela de trabalho resultante.
  Não pode atravessar pausa, bloqueio nem meia-noite. Não se somam trechos de
  janelas distintas, mesmo que sejam adjacentes.
- Os inícios seguem uma grade fixa de 15 minutos, alinhada à hora local
  (`00`, `15`, `30`, `45`). Uma janela que comece fora da grade oferece o
  primeiro início alinhado dentro dela. Duração e intervalo do serviço não
  precisam ser múltiplos de 15 minutos.
- Para a data atual, o início precisa ser maior ou igual ao instante corrente
  obtido por relógio injetável no backend. Não há antecedência mínima adicional
  nesta versão. O servidor nunca confia no relógio enviado pelo cliente.
- Horas locais inexistentes em uma transição de fuso não são oferecidas. Horas
  locais ambíguas em uma repetição de relógio também não são oferecidas, pois o
  modelo atual de `atendimento.inicio` não guarda o deslocamento. Um candidato
  cujo período de execução cruze uma dessas transições é descartado. Assim,
  cada horário retornado identifica um único instante e mantém a duração real
  prometida.

## Cálculo de horários

Para cada profissional elegível e a data consultada:

1. Obter as janelas `[início, fim)` da composição definida pela SPEC de
   jornadas: estado da filial e do profissional, feriado, afastamento, exceção
   de data ou jornada semanal, e subtração dos bloqueios.
2. Gerar inícios na grade de 15 minutos dentro de cada janela. Descartar início
   passado, temporalmente ambíguo ou cujo serviço completo não caiba na mesma
   janela.
3. Carregar atendimentos do profissional com status `AGENDADO` ou `CONFIRMADO`
   cujos períodos ocupados possam tocar o dia consultado. `CANCELADO` não ocupa
   horário.
4. Para cada atendimento existente, considerar ocupado o intervalo
   `[início, início + duração do atendimento + intervalo posterior do
   atendimento)`. O intervalo posterior pertence ao **serviço já marcado**,
   não ao serviço agora consultado.
5. Descartar um candidato se seu período de execução
   `[início, início + duração do serviço)` intersectar um período ocupado, ou
   se `[início, início + duração do serviço + intervalo posterior do serviço)`
   intersectar o **início de outro atendimento ativo**. Isso garante a pausa
   necessária depois do novo serviço antes do próximo atendimento. O intervalo
   posterior não precisa caber na janela de trabalho nem impede um último
   atendimento de terminar exatamente no fim da jornada.
6. Ordenar a resposta por início crescente e, em empate, por
   `profissionalId` crescente. Não retornar duplicatas.

O passo 5 equivale a exigir que o novo serviço não comece durante a ocupação
anterior e que o próximo atendimento não comece antes do fim da pausa do novo
serviço. O intervalo posterior pode estar fora da jornada, mas nunca dentro do
tempo de outro atendimento ativo.

Exemplo: jornada `09:00–12:00`, serviço de 45 minutos com 15 minutos de
intervalo. Um atendimento existente de `09:00–09:45`, também com 15 minutos de
intervalo, ocupa até `10:00`; o primeiro início possível depois dele é `10:00`.
Se houver outro atendimento às `11:00`, o candidato `10:15` é descartado porque
sua pausa terminaria às `11:15`; `10:00–10:45`, com pausa até `11:00`, cabe.

## Duração histórica dos atendimentos

O esquema atual guarda `atendimento.inicio` e referência ao serviço, mas não
guarda duração nem intervalo usados quando a reserva foi criada. Ler sempre o
catálogo atual faria uma edição posterior do serviço alterar retroativamente a
ocupação da agenda. Para evitar isso:

- Uma migração adiciona a `atendimento` os campos obrigatórios
  `duracao_minutos` e `intervalo_minutos`, com as mesmas validações do serviço.
- Para registros existentes, a migração preenche esses campos a partir do
  serviço atualmente vinculado; é uma aproximação única para dados legados,
  documentada, pois não há duração histórica recuperável.
- Novos atendimentos copiam duração e intervalo do serviço no momento da
  confirmação. Editar o catálogo depois não muda a ocupação dos atendimentos
  já registrados. O motor lê os valores da reserva, não os valores atuais do
  serviço associado.
- Uma alteração de serviço ainda afeta os **novos** horários oferecidos e os
  novos atendimentos. A criação de atendimento deve usar os mesmos valores
  lidos na validação transacional para evitar diferença entre validação e
  persistência.

A migração também adiciona índice para procurar atendimentos ativos por
`(profissional_id, inicio)`. Como não há atendimento que atravesse a meia-noite
nesta versão, a busca pode ser delimitada ao dia local, mas deve incluir
intervalos de ocupação cujo início esteja antes do início consultado e alcance
esse início. O backend não deve fazer uma consulta por candidato.

## Contrato HTTP

`GET /api/unidades/{unidadeId}/servicos/{servicoId}/horarios?data=AAAA-MM-DD&profissionalId={id}`

- Leitura pública, inclusive para cliente não autenticado, restrita a filial,
  serviço e profissionais ativos. Não exige CSRF. O backend aplica os mesmos
  filtros independentemente de quem chama.
- Resposta `200 OK` com `unidadeId`, `servicoId`, `data`, `fusoHorario` e
  `horarios`: lista de `{profissionalId, inicio, fim}`. `inicio` e `fim` são
  data-hora local ISO-8601 sem offset; o fuso da resposta é obrigatório.
- Com `profissionalId`, todos os itens pertencem àquele profissional. Sem ele,
  horários iguais de profissionais diferentes aparecem como itens separados.
- A resposta não contém nomes de clientes, status ou IDs de atendimentos,
  motivos de bloqueio, afastamentos nem regras internas de jornada.
- `400` para data ausente, inválida ou fora do horizonte; `404` para filial,
  serviço ou profissional explicitamente selecionado que não seja elegível.
  Falha inesperada segue o formato de erro comum da API.
- A resposta deve declarar `Cache-Control: no-store`, porque outra reserva ou
  mudança de jornada pode tornar a prévia obsoleta imediatamente.

Exemplo ilustrativo de resposta:

```json
{
  "unidadeId": 1,
  "servicoId": 12,
  "data": "2026-10-05",
  "fusoHorario": "America/Sao_Paulo",
  "horarios": [
    {
      "profissionalId": 7,
      "inicio": "2026-10-05T10:00:00",
      "fim": "2026-10-05T10:45:00"
    }
  ]
}
```

## Interface e experiência

- Na escolha de horário, a interface pede filial e serviço, permite filtrar
  profissional e consulta uma data por vez. Mostra o fuso ou a localidade da
  filial de forma clara, especialmente se o navegador estiver em outro fuso.
- Agrupa as opções por data e horário; quando há vários profissionais no mesmo
  horário, permite selecionar explicitamente um deles. Não promete que o
  horário estará reservado até a confirmação do agendamento.
- Estado vazio distingue: não há profissionais habilitados, não há jornada
  configurada, ou não há horário livre na data, quando isso puder ser apurado
  sem expor regras internas. Pode sugerir consultar outro dia ou profissional.
- Ao voltar da confirmação ou do cancelamento, a interface consulta novamente.
  Se o horário escolhido desapareceu, avisa e solicita nova escolha.
- O frontend não calcula disponibilidade localmente nem usa uma resposta antiga
  para autorizar uma reserva.

## Consistência, desempenho e segurança

- Uma consulta usa um retrato consistente dos dados no momento da leitura, mas
  não bloqueia horários. Duas pessoas podem receber a mesma opção; só a futura
  operação transacional de agendamento decide qual reserva vence.
- A validação de criação/reagendamento deverá usar a mesma regra do motor,
  dentro de uma transação que impeça dupla reserva do profissional, incluindo
  o intervalo posterior. Revalidar somente no frontend é insuficiente.
- Carregar as janelas e os atendimentos dos profissionais elegíveis em lotes
  por data/filial, evitando consultas por slot. A data única e o horizonte
  limitado protegem a consulta pública de buscas excessivas; aplicar também os
  controles gerais de taxa e observabilidade da implantação.
- Registrar métricas de tempo de cálculo e quantidade de opções, sem dados
  pessoais. Não registrar a agenda completa em logs públicos.
- Não persistir slots pré-calculados nesta versão: toda consulta reflete as
  regras e atendimentos confirmados no banco. Otimizações com cache exigem
  invalidação explícita antes de serem adotadas.

## Critérios de aceitação e testes

- Serviço inativo, filial inativa e profissional inativo ou não habilitado não
  produzem horários; IDs explícitos inelegíveis recebem `404`.
- A consulta sem profissional retorna cada opção com seu `profissionalId`, em
  ordem determinística; com filtro, não mistura agendas.
- Jornada sem exceções gera a grade de 15 minutos; uma janela `09:07–10:02`
  para serviço de 30 minutos oferece `09:15` e `09:30`, mas não `09:45`.
- Folga, feriado e afastamento zeram o dia; jornada especial substitui a
  semanal; bloqueio recorta a janela, conforme a SPEC de jornadas.
- Serviço que termina exatamente no limite da janela é permitido. Serviço que
  atravessa pausa, bloqueio ou meia-noite é recusado.
- Atendimento `AGENDADO` ou `CONFIRMADO` remove inícios conflitantes, incluindo
  o intervalo após o atendimento; `CANCELADO` não remove nenhum.
- A pausa do **novo** serviço impede um início que encoste no próximo
  atendimento, mas pode ultrapassar o fim da jornada se não houver outro
  atendimento.
- Alterar a duração ou intervalo do serviço não altera a ocupação de
  atendimentos já registrados; a migração preserva os registros legados e
  preenche seus valores de ocupação.
- Consultas no fuso da filial não dependem do fuso do servidor ou navegador.
  Horários inexistentes ou ambíguos de transições não são oferecidos.
- A data de hoje exclui horários passados segundo relógio controlado em teste;
  datas fora do horizonte retornam `400`.
- A resposta pública não expõe informações pessoais nem motivos internos e
  inclui `Cache-Control: no-store`.
- Testes unitários cobrem limites semiabertos, grade, duração não múltipla de
  15, intervalo posterior, ordenação e transições de fuso. Testes de integração
  cobrem filtros por filial/serviço/profissional, estados ativos, composição de
  janelas, atendimentos, migração e contrato HTTP. Testes de interface cobrem
  filtro, escolha explícita de profissional, estado vazio e atualização após
  horário indisponível.

## Fora do escopo

- Criar, confirmar, cancelar ou reagendar atendimentos, inclusive a proteção
  transacional contra dupla reserva; isso pertence à SPEC de agendamento.
- Pagamento, sinal, lista de espera, encaixe manual, prioridade de clientes e
  distribuição automática de profissionais.
- Reserva de cadeira, sala ou equipamento, e limitação de capacidade da filial.
- Serviços em sequência, pacotes, serviços com múltiplos profissionais e
  atendimentos que atravessam a meia-noite.
- Intervalos de grade configuráveis por filial ou serviço, antecedência mínima
  configurável e horários recorrentes pré-materializados.
- Sincronização com calendários externos ou sugestões personalizadas.

## Relação com outras SPECs e rastreabilidade

- Consome as janelas e a precedência de
  `2026-10-01-jornada-folgas-feriados-e-bloqueios.md` sem substituí-las.
- Usa filial, fuso e estado do profissional definidos em
  `2026-09-30-estabelecimentos-filiais-profissionais-e-permissoes.md`.
- Usa duração, intervalo, estado e habilitação definidos em
  `2026-09-30-catalogo-de-servicos.md`.
- Usa os atendimentos inicialmente criados para
  `2026-09-30-painel-profissional.md`, ampliando-os com os valores históricos
  necessários para calcular ocupação. A futura SPEC de agendamento deverá
  reutilizar a regra e garantir atomicidade na reserva.

Toda implementação derivada desta SPEC deve usar o trailer:

```text
Spec: SPEC/2026-10-01-motor-de-disponibilidade.md
```
