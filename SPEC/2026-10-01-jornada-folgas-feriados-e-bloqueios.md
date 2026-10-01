# Jornada, folgas, feriados, férias e bloqueios de agenda (2026-10-01)

## O que é e por que existe

A disponibilidade de um profissional não é apenas o horário de abertura da
filial. Ela resulta da jornada semanal combinada com exceções pontuais (folgas
e jornadas especiais), ausências prolongadas (férias e outros afastamentos),
feriados da filial e bloqueios de agenda. Sem essa configuração, o motor de
disponibilidade não consegue decidir quais horários podem ser oferecidos nem o
agendamento consegue recusar reservas fora do expediente.

Esta SPEC define como o Estilo Marcado representa essas cinco regras e como elas
se combinam para produzir as **janelas de trabalho** de cada profissional em
cada dia. Ela resolve os itens que as SPECs de catálogo, painel profissional e
estabelecimentos deliberadamente deixaram fora de escopo ("regras de jornada,
folgas, feriados e bloqueios"). Ela **não** calcula horários oferecidos ao
cliente: esse é o motor de disponibilidade, que consome as janelas definidas
aqui.

## Escopo

- jornada semanal de cada profissional, por dia da semana e por intervalo;
- exceções de data de um profissional: folga (dia inteiro) ou jornada especial;
- feriados da filial, que fecham a unidade inteira em datas específicas;
- férias e demais afastamentos, por período, de um profissional;
- bloqueios de agenda pontuais, por profissional ou para a filial inteira;
- regras de precedência e composição dessas fontes em janelas de trabalho;
- consulta e manutenção dessas regras por administrador e pelo próprio
  profissional, respeitando perfil, vínculo e filial;
- tratamento de conflito com atendimentos futuros já marcados.

## Conceitos

| Conceito | Abrangência | Unidade temporal | Efeito |
| --- | --- | --- | --- |
| Jornada semanal | Profissional | Dia da semana (1–7), com 1 a 4 intervalos | Padrão repetitivo de trabalho |
| Exceção de data | Profissional | Uma data | `FOLGA` fecha o dia; `JORNADA_ESPECIAL` substitui a jornada |
| Feriado | Filial | Uma data | Fecha o dia para todos os profissionais da filial |
| Férias / afastamento | Profissional | Período (início a fim, inclusive) | Fecha o período inteiro para o profissional |
| Bloqueio de agenda | Filial ou profissional | Data com intervalo opcional | Remove uma janela do dia sem alterar a jornada |

Termos com o mesmo significado técnico já usado no projeto: uma **filial** é a
tabela `unidade`; um **profissional** é a linha de `profissional`; um
**atendimento** é a linha de `atendimento` com status `AGENDADO`, `CONFIRMADO`
ou `CANCELADO`.

## Representação do tempo e fuso horário

- Toda regra usa **data** e **hora de parede locais da filial**, sem indicar
  deslocamento. A autoridade do fuso é `unidade.fuso_horario` (IANA),
  introduzida pela SPEC de estabelecimentos.
- Datas são armazenadas como `DATE` e horários como `TIME`. Uma janela não pode
  atravessar a meia-noite nesta versão; cada dia é configurado isoladamente.
- O dia da semana segue a norma ISO: `1` = segunda-feira e `7` = domingo.
- Semanas com transição de horário de verão são resolvidas pelo motor de
  disponibilidade no fuso da filial; esta SPEC apenas fornece horas locais.
- Alterar o fuso da filial **não** converte os horários já cadastrados: eles
  continuam sendo a hora de parede da filial e podem deslocar o instante
  absoluto dos atendimentos existentes. A alteração exige decisão da
  administração e fica registrada na auditoria.

## Regras de composição e precedência

As fontes desta SPEC são combinadas, nessa ordem, para obter as janelas de
trabalho de um profissional em uma data:

1. **Filial e profissional precisam estar ativos.** Filial `INATIVA` ou
   profissional inativo produzem zero janelas.
2. **Feriado da filial na data** produz zero janelas para todos os
   profissionais da filial.
3. **Afastamento que cobre a data** produz zero janelas para o profissional,
   independentemente da jornada.
4. **Exceção de data do profissional** substitui a base:
   - `FOLGA`: zero janelas;
   - `JORNADA_ESPECIAL`: valem os intervalos da própria exceção;
   - sem exceção: valem os intervalos da jornada semanal do dia da semana.
5. **Bloqueios de agenda da data** são subtraídos das janelas resultantes: um
   bloqueio de filial (sem profissional) vale para todos; um bloqueio de
   profissional vale apenas para ele. A subtração pode partir uma janela em
   duas.

Resultado: uma lista ordenada de janelas `[início, fim)` no mesmo dia, sem
sobreposição. A configuração nunca cria janelas; apenas remove ou restringe.

Exemplo de composição para uma terça-feira, com jornada `09:00–12:00` e
`13:00–18:00`:

| Situação | Janelas resultantes |
| --- | --- |
| Semana normal, sem outras regras | `09:00–12:00`, `13:00–18:00` |
| Feriado na data | nenhuma |
| `FOLGA` na data | nenhuma |
| `JORNADA_ESPECIAL` `10:00–15:00` | `10:00–15:00` |
| Férias cobrindo a data | nenhuma |
| Bloqueio do profissional `13:00–14:00` | `09:00–12:00`, `14:00–18:00` |
| Bloqueio de filial `09:00–10:00` | `10:00–12:00`, `13:00–18:00` |

## Jornada semanal

- A jornada pertence a um profissional e é composta por intervalos, cada um com
  dia da semana, hora de início e hora de fim.
- Um intervalo exige `início < fim`, no mesmo dia, com granularidade de minutos.
  Não é permitido atravessar a meia-noite.
- Até quatro intervalos por dia da semana. Dias sem intervalo são dias não
  trabalhados.
- Intervalos do mesmo profissional no mesmo dia da semana não podem se
  sobrepor. Intervalos adjacentes são permitidos, mas o sistema não os mescla
  automaticamente.
- Atualizar a jornada é uma operação **atômica de substituição**: o corpo envia
  o conjunto completo de intervalos e o backend substitui o anterior na mesma
  transação.
- Alterar a jornada **não** cancela nem altera atendimentos existentes. Se a
  nova jornada deixar de cobrir atendimentos futuros ativos, a operação é
  recusada com `409 Conflict` e a lista de atendimentos conflitantes, para que
  a recepção ou a administração os remaneje antes.
- A alteração da jornada vale para datas futuras na hora da consulta; registros
  passados e atendimentos já realizados permanecem inalterados.
- Como conveniência, a interface pode **copiar** a jornada de um profissional
  para outros da mesma filial. A cópia gera registros normais e não cria vínculo
  entre eles.

## Exceções de data: folgas e jornadas especiais

- Uma exceção de data pertence a um profissional e a uma data específica. Há no
  máximo uma exceção por profissional e data.
- `FOLGA` fecha o dia inteiro e não possui intervalos.
- `JORNADA_ESPECIAL` substitui a jornada do dia da semana por um conjunto
  próprio de intervalos, seguindo os mesmos limites da jornada semanal (início
  menor que fim, sem meia-noite, no máximo quatro intervalos, sem sobreposição).
- A exceção aceita um motivo curto opcional (por exemplo, "consulta médica" ou
  "evento externo"), visível apenas internamente.
- Criar ou alterar uma exceção que deixe atendimentos futuros ativos fora das
  novas janelas retorna `409 Conflict` com a lista de atendimentos, salvo se a
  jornada resultante ainda os cobrir.
- Uma exceção pode ser criada para datas passadas apenas por administrador, para
  correção de histórico; o profissional só opera exceções de hoje em diante.

## Feriados

- Um feriado pertence a uma filial e a uma data, e possui um nome de exibição
  obrigatório de 2 a 120 caracteres.
- Não há dois feriados na mesma filial e data.
- O feriado fecha a filial inteira naquela data. Não há, nesta versão, feriado
  com horário reduzido nem feriado que preserve a jornada de alguns
  profissionais.
- Feriados não são recorrentes automaticamente: cada ano é cadastrado
  explicitamente. A interface pode oferecer um atalho para repetir um feriado em
  outro ano, criando um novo registro de data.
- Criar feriado sobre atendimentos futuros ativos na data retorna `409 Conflict`
  com a lista de atendimentos.
- Apenas o administrador da própria filial mantém feriados. Profissionais,
  recepção e clientes não alteram feriados.
- A ausência de cadastro de feriados nacionais é aceitável nesta versão; não há
  integração com calendário externo.

## Férias e afastamentos

- Um afastamento pertence a um profissional e a um período com `data_inicio` e
  `data_fim`, ambas inclusivas, e exige `data_inicio <= data_fim`.
- O tipo é obrigatório e um destes: `FERIAS`, `LICENCA`, `AFASTAMENTO` ou
  `OUTRO`. O tipo `OUTRO` aceita descrição livre.
- O motivo/descrição é opcional, com até 500 caracteres, e não aparece na
  consulta pública de disponibilidade.
- Dois afastamentos do mesmo profissional não podem se sobrepor.
- O afastamento simplesmente remove as janelas do profissional no período; ele
  não altera jornada, folgas nem bloqueios, que continuam cadastrados.
- Criar ou estender um afastamento sobre atendimentos futuros ativos retorna
  `409 Conflict` com a lista de atendimentos.
- Não há, nesta versão, fluxo de solicitação e aprovação de férias. O
  administrador da filial pode criar, alterar e remover afastamentos de qualquer
  profissional da própria filial; o profissional pode fazer o mesmo apenas para
  si. Um fluxo de pedido/aprovação poderá ser especificado depois.

## Bloqueios de agenda

- Um bloqueio pertence a uma filial e, opcionalmente, a um profissional. Sem
  profissional, o bloqueio vale para toda a filial.
- O bloqueio ocorre em uma única data. Quando `dia_inteiro = true`, não há
  horários. Quando `dia_inteiro = false`, exige `hora_inicio < hora_fim` no
  mesmo dia.
- O bloqueio aceita um motivo curto opcional, visível apenas internamente.
- Bloqueios da mesma abrangência (mesma filial e mesmo profissional, ou ambos de
  filial) não podem se sobrepor na mesma data. Um bloqueio de filial e um
  bloqueio de profissional na mesma data podem coexistir; ambos são subtraídos.
- Bloqueios só podem ser criados para a data atual ou futura, no fuso da filial.
  Podem ser editados ou removidos enquanto não tiverem terminado; registros
  encerrados permanecem para auditoria.
- Criar ou ampliar um bloqueio sobre atendimentos futuros ativos na janela
  retorna `409 Conflict` com a lista de atendimentos.
- O administrador da filial mantém bloqueios de filial e de qualquer
  profissional da própria filial. O profissional mantém apenas bloqueios de si
  mesmo e não pode criar bloqueio de filial. Recepção e clientes não mantêm
  bloqueios.

## Perfis e permissões

`Administrador` significa uma conta `ADMINISTRADOR` vinculada à filial do
recurso. `Profissional` significa uma conta `PROFISSIONAL` com vínculo ativo ao
profissional em questão. Contas de outra filial não ampliam este escopo.

| Ação | Cliente | Profissional | Recepção | Administrador |
| --- | ---: | ---: | ---: | ---: |
| Ver a própria jornada, exceções, afastamentos e bloqueios | Não | Sim | Não | Sim, na filial |
| Editar a própria jornada semanal | Não | Sim | Não | Sim, na filial |
| Manter as próprias exceções, afastamentos e bloqueios | Não | Sim | Não | Sim, na filial |
| Manter exceções, afastamentos e bloqueios de outro profissional | Não | Não | Não | Sim, na filial |
| Manter feriados da filial | Não | Não | Não | Sim, na filial |
| Manter bloqueio de filial inteira | Não | Não | Não | Sim, na filial |
| Consultar a configuração de disponibilidade da filial | Não | Somente a própria | Sim, somente leitura | Sim, na filial |

- A identidade e o escopo vêm da sessão validada no servidor. Enviar
  `profissionalId` ou `unidadeId` no corpo de uma requisição não autoriza o
  acesso a outro recurso.
- Para IDs fora do escopo, o backend pode responder `404 Not Found` a fim de não
  revelar a existência do recurso.
- Requisição sem sessão válida recebe `401 Unauthorized`; sem permissão,
  `403 Forbidden`. Escritas usam a proteção CSRF da SPEC de autenticação.

## Conflitos com atendimentos existentes

- "Atendimento futuro ativo" é um atendimento com status `AGENDADO` ou
  `CONFIRMADO` cujo instante de início é igual ou posterior ao instante da
  operação.
- Quando uma alteração desta SPEC tornaria um atendimento futuro ativo inválido
  (fora das janelas de trabalho), a operação é recusada com `409 Conflict` e o
  corpo lista os atendimentos em conflito (`id`, `inicio`, `servicoId`,
  `clienteId`), sem dados sensíveis.
- A resolução é operacional: a recepção ou a administração remaneja ou cancela
  os atendimentos pelo fluxo próprio e repete a operação. Esta SPEC não oferece
  reagendamento automático.
- Atendimentos `CANCELADO` e atendimentos passados nunca bloqueiam a operação.

## Contrato HTTP inicial

Todos os endpoints usam JSON. As leituras são permitidas conforme a matriz de
permissões; as escritas exigem CSRF. Os nomes exatos dos campos podem evoluir
sem alterar as regras deste documento.

Acesso administrativo, sempre com a filial da sessão:

| Método e rota | Acesso | Resultado |
| --- | --- | --- |
| `GET /api/unidades/{unidadeId}/profissionais/{profissionalId}/jornada` | Administrador, Profissional da própria agenda, Recepção (leitura) | Intervalos da jornada semanal. |
| `PUT /api/unidades/{unidadeId}/profissionais/{profissionalId}/jornada` | Administrador da filial, Profissional do próprio vínculo | Substitui a jornada; `200 OK` ou `409`. |
| `GET /api/unidades/{unidadeId}/profissionais/{profissionalId}/excecoes?de=&ate=` | Administrador, Profissional, Recepção (leitura) | Exceções no intervalo de datas. |
| `POST /api/unidades/{unidadeId}/profissionais/{profissionalId}/excecoes` | Administrador, Profissional (próprio) | Cria exceção; `201 Created` ou `409`. |
| `PATCH /api/unidades/{unidadeId}/profissionais/{profissionalId}/excecoes/{id}` | Administrador, Profissional (próprio) | Atualiza exceção. |
| `DELETE /api/unidades/{unidadeId}/profissionais/{profissionalId}/excecoes/{id}` | Administrador, Profissional (próprio) | Remove exceção futura; `204 No Content`. |
| `GET /api/unidades/{unidadeId}/profissionais/{profissionalId}/afastamentos?de=&ate=` | Administrador, Profissional, Recepção (leitura) | Afastamentos no intervalo. |
| `POST /api/unidades/{unidadeId}/profissionais/{profissionalId}/afastamentos` | Administrador, Profissional (próprio) | Cria afastamento; `201 Created` ou `409`. |
| `PATCH /api/unidades/{unidadeId}/profissionais/{profissionalId}/afastamentos/{id}` | Administrador, Profissional (próprio) | Atualiza afastamento. |
| `DELETE /api/unidades/{unidadeId}/profissionais/{profissionalId}/afastamentos/{id}` | Administrador, Profissional (próprio) | Remove afastamento; `204`. |
| `GET /api/unidades/{unidadeId}/feriados?de=&ate=` | Conta interna da filial | Feriados no intervalo. |
| `POST /api/unidades/{unidadeId}/feriados` | Administrador da filial | Cria feriado; `201 Created` ou `409`. |
| `PATCH /api/unidades/{unidadeId}/feriados/{id}` | Administrador da filial | Atualiza feriado. |
| `DELETE /api/unidades/{unidadeId}/feriados/{id}` | Administrador da filial | Remove feriado; `204`. |
| `GET /api/unidades/{unidadeId}/bloqueios?de=&ate=&profissionalId=` | Conta interna da filial | Bloqueios no intervalo. |
| `POST /api/unidades/{unidadeId}/bloqueios` | Administrador da filial | Cria bloqueio de filial ou de profissional; `201` ou `409`. |
| `PATCH /api/unidades/{unidadeId}/bloqueios/{id}` | Administrador da filial | Atualiza bloqueio. |
| `DELETE /api/unidades/{unidadeId}/bloqueios/{id}` | Administrador da filial | Remove bloqueio; `204`. |

Acesso do próprio profissional, resolvido pela sessão (não aceita
`profissionalId` arbitrário):

| Método e rota | Acesso | Resultado |
| --- | --- | --- |
| `GET /api/profissionais/me/jornada` | Profissional ativo | Jornada do próprio vínculo. |
| `PUT /api/profissionais/me/jornada` | Profissional ativo | Substitui a própria jornada. |
| `GET/POST/PATCH/DELETE /api/profissionais/me/excecoes[/{id}]` | Profissional ativo | Mantém as próprias exceções de data. |
| `GET/POST/PATCH/DELETE /api/profissionais/me/afastamentos[/{id}]` | Profissional ativo | Mantém os próprios afastamentos. |
| `GET/POST/PATCH/DELETE /api/profissionais/me/bloqueios[/{id}]` | Profissional ativo | Mantém os próprios bloqueios; nunca cria bloqueio de filial. |

Erros: validação retorna `400 Bad Request` com indicação do campo; conflito de
sobreposição, duplicidade ou atendimento futuro retorna `409 Conflict`;
requisição sem sessão, `401`; sem permissão, `403`; recurso fora do escopo,
`404`. As respostas seguem o formato de erro comum da API.

## Interface e fluxos

- **Profissional** ganha uma área "Minha disponibilidade" (por exemplo,
  `/profissional/disponibilidade`) com a jornada da semana em um editor de
  intervalos por dia, a lista de folgas e jornadas especiais próximas, os
  afastamentos e os bloqueios próprios. A área mostra a prévia das janelas de um
  dia escolhido para tornar a composição visível.
- **Administrador da filial** administra a mesma configuração por profissional,
  a partir da seção de profissionais da própria filial, além de manter
  **Feriados** da filial e **Bloqueios** de filial inteira.
- **Recepção** consulta a disponibilidade da filial em modo somente leitura,
  para orientar o atendimento, sem poder alterar jornada, feriados ou
  bloqueios.
- A interface impede escolher datas passadas para novas exceções, afastamentos e
  bloqueios, apresenta os horários no fuso da filial e traduz o `409` em uma
  lista acionável de atendimentos conflitantes.
- Sobreposições dentro da mesma fonte são impedidas no formulário e novamente no
  backend; o frontend nunca é a autoridade da regra.
- Estados vazios explicam a consequência: um profissional sem jornada não gera
  disponibilidade; uma filial sem feriados segue a jornada normal.

## Migração e integridade

Uma migração Flyway posterior à versão `V9` cria as tabelas abaixo, preservando
todos os dados existentes. A aplicação valida os mesmos limites que o banco,
que é a última linha de defesa das invariantes.

- `jornada_intervalo`: `profissional_id`, `dia_semana` (`1`–`7`),
  `hora_inicio`, `hora_fim`, com chave estrangeira para `profissional`,
  `CHECK (hora_inicio < hora_fim)` e índice por `(profissional_id, dia_semana)`.
- `excecao_jornada`: `profissional_id`, `data`, `tipo` (`FOLGA` ou
  `JORNADA_ESPECIAL`), `motivo`, datas de criação/atualização, unicidade por
  `(profissional_id, data)`.
- `excecao_jornada_intervalo`: intervalos de uma `JORNADA_ESPECIAL`, com chave
  estrangeira `ON DELETE CASCADE` para a exceção e `CHECK (hora_inicio <
  hora_fim)`.
- `afastamento`: `profissional_id`, `data_inicio`, `data_fim`, `tipo`,
  `descricao`, datas de criação/atualização,
  `CHECK (data_inicio <= data_fim)` e índice por `(profissional_id,
  data_inicio, data_fim)`.
- `feriado`: `unidade_id`, `data`, `nome`, `criado_em`, unicidade por
  `(unidade_id, data)`.
- `bloqueio_agenda`: `unidade_id`, `profissional_id` (nulo = filial inteira),
  `data`, `dia_inteiro`, `hora_inicio`, `hora_fim`, `motivo`, `criado_por`,
  `criado_em`, com coerência entre `dia_inteiro` e os horários e chaves
  estrangeiras para `unidade`, `profissional` e `usuario`.

Regras complementares:

- A exclusão física de um profissional ou filial já é tratada por suas SPECs;
  esta migração não introduz exclusão física de regras de disponibilidade. A
  remoção é feita pela própria API e registra auditoria.
- Sobreposições de jornada, bloqueios, feriados duplicados e afastamentos
  sobrepostos são recusados na aplicação com `409 Conflict`; chaves únicas do
  banco impedem duplicidade exata.
- Alterações de jornada, exceção, feriado, afastamento e bloqueio registram
  autor, recurso e instante, sem dados sensíveis.
- A migração não gera jornada padrão para profissionais existentes: um
  profissional sem jornada simplesmente não produz disponibilidade até que a
  administração a configure.

## Critérios de aceitação

- Um profissional sem jornada não gera janelas; após cadastrar a jornada, as
  janelas do dia correspondente passam a existir.
- A atualização de jornada é atômica: uma lista inválida não altera a anterior.
- `FOLGA` fecha o dia; `JORNADA_ESPECIAL` substitui a jornada daquele dia sem
  apagar a jornada semanal.
- Um feriado remove as janelas de todos os profissionais ativos da filial
  naquela data; um feriado não é aplicado a outra filial.
- Um afastamento remove as janelas de todo o período, inclusive início e fim.
- Um bloqueio de filial afeta todos os profissionais; um bloqueio de
  profissional afeta apenas ele; ambos podem partir uma janela em duas.
- Filial inativa ou profissional inativo produzem zero janelas,
  independentemente das demais regras.
- Criar feriado, folga, afastamento ou bloqueio sobre atendimento futuro ativo
  retorna `409` com a lista de conflitos, e a operação não é persistida.
- Profissional não consegue alterar disponibilidade de outro profissional nem
  criar feriado ou bloqueio de filial; tentativas adulterando IDs são negadas
  com `403`/`404`.
- Profissional sem vínculo ativo ou de filial inativa não passa pelos endpoints
  de autoatendimento.
- Testes de integração cobrem composição das regras, sobreposição, conflito com
  atendimentos e isolamento por filial e proprietário; testes de interface
  cobrem o editor de jornada e a tradução do `409`.
- Testes de migração comprovam que os dados anteriores permanecem íntegros e que
  as restrições de unicidade e coerência são aplicadas.

## Fora do escopo

- Cálculo de horários oferecidos ao cliente (motor de disponibilidade), que
  consumirá as janelas definidas aqui.
- Criação, confirmação, cancelamento e reagendamento de atendimentos.
- Jornada que atravessa a meia-noite e escalas rotativas por ciclo (por
  exemplo, 12x36).
- Feriado recorrente automático, feriado com horário reduzido e importação de
  calendário oficial.
- Fluxo de solicitação e aprovação de férias.
- Banco de horas, ponto, controle de jornada trabalhada e folha de pagamento.
- Recursos compartilhados (cadeira, sala, equipamento) e bloqueio por recurso.
- Sincronização com Google Calendar, Outlook ou qualquer calendário externo.
- Notificações, lembretes e lista de espera.

## Relação com outras SPECs

- Depende de `2026-09-30-autenticacao-e-controle-de-acesso.md` para identidade,
  sessão, CSRF e respostas `401`/`403`.
- Depende de `2026-09-30-estabelecimentos-filiais-profissionais-e-permissoes.md`
  para `unidade` (filial), `profissional`, fuso horário e estado ativo/inativo.
- Resolve os itens deixados fora de escopo em
  `2026-09-30-catalogo-de-servicos.md` ("regras de jornada, folgas, feriados e
  bloqueios de agenda") e em `2026-09-30-painel-profissional.md`
  ("configuração de jornadas, folgas, feriados e bloqueios").
- É consumida pelo motor de disponibilidade e pelo agendamento, que devem
  aplicar a mesma composição de janelas e a mesma recusa de conflitos.
- A `2026-10-01-navegacao-visual-e-meu-perfil.md` define a navegação; os itens
  de "Minha disponibilidade" e "Feriados" só aparecem na sidebar quando as
  respectivas telas e APIs forem implementadas, sem links vazios.

## Rastreabilidade

Toda alteração derivada desta spec deve ser commitada com o trailer:

```text
Spec: SPEC/2026-10-01-jornada-folgas-feriados-e-bloqueios.md
```
