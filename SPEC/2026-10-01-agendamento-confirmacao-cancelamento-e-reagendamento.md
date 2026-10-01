# Agendamento, confirmação, cancelamento e reagendamento (2026-10-01)

## O que é e por que existe

Esta SPEC transforma um horário oferecido pelo motor de disponibilidade em um
atendimento persistido. Define quem pode reservar, confirmar, cancelar e
reagendar, como essas ações aparecem para cliente e equipe, e como impedir duas
reservas concorrentes do mesmo profissional. A consulta de horários é uma
prévia; somente uma gravação transacional bem-sucedida garante a reserva.

O modelo atual já possui `atendimento` com `AGENDADO`, `CONFIRMADO` e
`CANCELADO`, mas foi criado apenas para exibir o painel profissional. Esta
SPEC completa seu ciclo de vida sem apagar registros históricos.

## Escopo e decisões

- Agendar um serviço para um profissional e horário **específicos** de uma
  filial, a partir de uma conta cliente ou por recepção/administração da filial.
- Consultar os próprios agendamentos ou a agenda operacional da própria filial.
- Confirmar um atendimento agendado, cancelar um atendimento ativo e reagendar
  um atendimento ativo para outro horário elegível.
- Registrar autor, momento e motivo opcional das alterações para auditoria.
- Vincular cada cliente autenticado a um registro `cliente` sem perder os
  registros legados nem criar uma segunda conta de usuário.

Não há reserva provisória com prazo de expiração. `AGENDADO` já ocupa a agenda.
Não há escolha automática de profissional, pagamento ou fluxo de aprovação do
cliente nesta versão.

## Identidade do cliente e alcance da filial

- `cliente` ganha `usuario_id` opcional e único, apontando para `usuario` de
  perfil `CLIENTE`. O campo é nulo para clientes avulsos anteriores ou criados
  pela equipe. Ganha também telefone de contato opcional (até 20 caracteres)
  para clientes avulsos. O backend valida o perfil ao criar o vínculo; a
  associação é imutável pela API de agendamentos.
- O primeiro agendamento de uma conta `CLIENTE` ativa localiza ou cria de forma
  atômica seu registro `cliente`. O nome inicial vem da conta. Atualizações
  posteriores do perfil podem sincronizar o nome de exibição, sem trocar o ID.
  Duas requisições simultâneas da mesma conta nunca criam dois registros.
- O cliente autenticado **não** envia `clienteId` para reservar, consultar,
  cancelar ou reagendar: a identidade é resolvida da sessão e do vínculo
  `cliente.usuario_id` no servidor. Conhecer o ID de outro atendimento não dá
  acesso a ele.
- Recepção e administrador atuam apenas na filial da sessão. Na criação, podem
  usar `clienteId` de um cliente já atendido pela própria filial ou criar um
  cliente avulso no mesmo pedido, informando nome (2–120 caracteres) e telefone
  opcional de contato (até 20 caracteres). Exatamente uma dessas duas formas é
  aceita. O cliente avulso não recebe conta, senha ou acesso ao sistema.
- A equipe só consulta detalhes de clientes associados a atendimentos de sua
  filial. Um `clienteId` de outra filial, sem vínculo prévio com a filial atual,
  não pode ser usado para descobrir dados pessoais; o backend responde `404`.
  Uma conta cliente pode agendar em filiais diferentes por autoatendimento.
- Um registro legado `cliente` sem `usuario_id` permanece avulso até eventual
  vinculação controlada, fora do fluxo de agendamento. Correspondência apenas
  por nome, e-mail ou telefone nunca faz vínculo automático.

## Estado e transições

| Estado atual | Ação | Próximo estado | Quem pode executar |
| --- | --- | --- | --- |
| Inexistente | Agendar | `AGENDADO` | Cliente próprio, recepção ou administrador da filial |
| `AGENDADO` | Confirmar | `CONFIRMADO` | Recepção ou administrador da filial |
| `CONFIRMADO` | Confirmar novamente | `CONFIRMADO`, sem novo evento | Recepção ou administrador da filial |
| `AGENDADO` ou `CONFIRMADO` | Cancelar | `CANCELADO` | Cliente proprietário, recepção ou administrador da filial |
| `CANCELADO` | Cancelar novamente | `CANCELADO`, sem novo evento | Mesmo escopo autorizado |
| `AGENDADO` ou `CONFIRMADO` | Reagendar | `AGENDADO` | Cliente proprietário, recepção ou administrador da filial |

- `CANCELADO` é terminal. Reagendar um cancelado exige criar outro atendimento
  com novo ID; ele não volta a ocupar a agenda.
- Um reagendamento sempre volta a `AGENDADO`, inclusive quando o horário
  anterior estava `CONFIRMADO`, pois a equipe precisa confirmar o novo horário.
- Cliente e profissional não podem confirmar. O profissional consulta apenas
  seus atendimentos pelo painel próprio; não altera o agendamento nesta versão.
- Confirmação, cancelamento e reagendamento só são permitidos **antes** do
  instante de início do atendimento no fuso da filial. Ações tardias retornam
  `409 Conflict`. Atendimentos passados permanecem no histórico com seu último
  estado; conclusão, falta e check-in terão SPEC própria.
- Confirmar não altera horário, duração, preço nem ocupação. Cancelar libera a
  ocupação assim que a transação confirmar e não remove a linha do banco.
- Motivo de cancelamento é opcional, com até 500 caracteres, visível apenas ao
  cliente proprietário e à equipe autorizada da filial; não aparece na
  disponibilidade pública nem no painel de outro profissional.

## Elegibilidade de uma reserva

O corpo de criação contém `servicoId`, `profissionalId` e `inicio` local
ISO-8601, além da identificação do cliente **somente para a equipe**. O
`unidadeId` vem da rota e precisa coincidir com a filial do serviço e do
profissional. Para reagendamento, o corpo contém apenas o novo `inicio`;
serviço, profissional, cliente e filial não mudam. Trocar serviço ou
profissional exige cancelar e criar um novo atendimento, preservando o
histórico.

- Filial, serviço e profissional devem estar ativos; o profissional deve estar
  habilitado para o serviço na mesma filial. O cliente autenticado deve ter
  conta `ATIVA`.
- `inicio` segue a grade de 15 minutos e o intervalo de hoje ao 60º dia futuro
  definido pela SPEC do motor. Para hoje, precisa ser maior ou igual ao
  instante atual. Horas inexistentes ou ambíguas no fuso da filial e serviços
  que cruzem transições de fuso são recusados.
- A execução do serviço cabe inteira em **uma** janela de trabalho composta
  para o profissional, sem atravessar pausa, bloqueio ou meia-noite.
- Não há interseção entre o novo período ocupado e períodos ocupados por
  atendimentos `AGENDADO` ou `CONFIRMADO` do profissional. Cada período ocupado
  é `[inicio, inicio + duracao_minutos + intervalo_minutos)`, com limites
  semiabertos. O intervalo posterior pode ultrapassar o fim da jornada, mas
  não alcançar o início de outro atendimento ativo, inclusive no dia seguinte.
- Na criação, duração e intervalo são copiados do **serviço lido dentro da
  transação** para `atendimento.duracao_minutos` e
  `atendimento.intervalo_minutos`. Isso esclarece o termo "confirmação" na SPEC
  do motor: o snapshot ocorre quando a reserva `AGENDADO` é gravada, não quando
  seu estado muda para `CONFIRMADO`. O nome e o preço oferecidos também são
  copiados para preservar o histórico comercial; não há cobrança aqui.
- No reagendamento, os snapshots originais de serviço, duração, intervalo,
  nome e preço são **mantidos**. A validação usa esses valores históricos, não
  os valores atuais do catálogo. O serviço ainda precisa estar ativo e o
  profissional habilitado. Uma edição posterior do catálogo não alonga nem
  encurta retroativamente uma reserva.
- A própria linha reagendada é excluída da checagem de conflitos. Se o novo
  horário for igual ao atual, a operação retorna o estado atual sem novo evento;
  não reseta uma confirmação.

Se a prévia pública mostrou um horário que já foi ocupado ou deixou de ser
elegível, a escrita retorna `409 Conflict` com código estável
`HORARIO_INDISPONIVEL`, sem identificar o atendimento concorrente. O cliente
deve consultar horários novamente.

## Concorrência e consistência

Uma consulta prévia jamais reserva um horário. Toda criação ou reagendamento
deve executar, na mesma transação:

1. Resolver sessão, cliente e escopo da filial.
2. Obter bloqueio transacional do profissional da agenda, sempre antes de ler
   janelas e ocupações; no reagendamento, bloquear também o atendimento a
   alterar. Usar ordem fixa de bloqueios para evitar deadlock.
3. Revalidar estado da filial, serviço, profissional, habilitação, janela,
   fuso, data, duração e conflitos com dados atuais.
4. Gravar atendimento e evento de auditoria, ou não gravar nada se houver erro.

Todas as operações que mudam a disponibilidade do mesmo profissional —
jornada, exceção, afastamento, bloqueio, feriado, ativação e desativação —
devem participar do mesmo protocolo de bloqueio **antes** de sua verificação
de conflitos. Para regras de filial inteira, bloquear seus profissionais em
ordem crescente de ID. Assim, uma regra e uma reserva concorrentes não podem
ambas validar contra o estado anterior e deixar a agenda inválida.

Como última defesa no PostgreSQL, a migração instala `btree_gist` e uma
restrição `EXCLUDE` para impedir interseção entre `profissional_id` igual e
intervalos `tsrange` ocupados de atendimentos ativos. A restrição usa os
snapshots de duração e intervalo e ignora `CANCELADO`. Conflitos detectados
pela aplicação ou pelo banco são traduzidos para `409 HORARIO_INDISPONIVEL`.
O intervalo posterior pode cruzar a meia-noite; a busca de ocupações deve
incluir o dia anterior quando necessário, não apenas atendimentos iniciados
na data consultada. A consulta pública do motor deve ser ajustada para seguir
a mesma regra.

Repetir uma criação após perda da resposta não deve produzir reserva duplicada.
`POST` de criação exige cabeçalho `Idempotency-Key` (UUID gerado pelo cliente).
O servidor guarda por 24 horas a chave, o autor, a filial, o hash do pedido e o
resultado. Mesma chave e mesmo pedido retornam o resultado anterior; mesma
chave com pedido diferente retorna `409 CHAVE_IDEMPOTENCIA_REUTILIZADA`. A
chave não é segredo, mas não aparece em logs públicos. Cancelamento e
confirmação são idempotentes pelo estado; reagendamento repetido para o mesmo
início também não gera novo evento.

## Contrato HTTP inicial

Todos os endpoints abaixo exigem sessão válida. Leituras e escritas usam JSON;
as escritas exigem CSRF. Datas de entrada e saída são locais da filial, com
`fusoHorario` IANA explícito na resposta. Não se aceita `usuarioId`, `perfil`
ou `unidadeId` no corpo como fonte de autorização.

| Método e rota | Acesso | Resultado |
| --- | --- | --- |
| `POST /api/unidades/{unidadeId}/agendamentos` | Cliente próprio; recepção/admin da filial | Cria `AGENDADO`; `201 Created`, `Location` e corpo. Exige `Idempotency-Key`. |
| `GET /api/me/agendamentos?de=&ate=&status=` | Cliente | Lista apenas os próprios atendimentos, inclusive cancelados, ordenados por início. |
| `GET /api/unidades/{unidadeId}/agendamentos?de=&ate=&profissionalId=&status=` | Recepção/admin da filial | Agenda da filial, limitada por datas e filtros. |
| `GET /api/agendamentos/{id}` | Cliente proprietário; recepção/admin da filial | Detalhe autorizado do atendimento. |
| `POST /api/agendamentos/{id}/confirmacoes` | Recepção/admin da filial | Confirma ou retorna o estado já confirmado; `200 OK`. |
| `POST /api/agendamentos/{id}/cancelamentos` | Cliente proprietário; recepção/admin da filial | Cancela com motivo opcional ou retorna o estado já cancelado; `200 OK`. |
| `PATCH /api/agendamentos/{id}/reagendamento` | Cliente proprietário; recepção/admin da filial | Troca apenas o início; `200 OK`, ou `409` com motivo estável. |

- Listagens exigem `de` e `ate` locais, `de <= ate` e abrangência máxima de 31
  dias por pedido. Paginação por cursor ou página com limite máximo de 100 itens
  impede resposta sem limite; ordenar por início e ID para desempate. Para o
  cliente, o filtro pode abranger histórico; o horizonte de 60 dias limita
  **novas reservas**, não leitura de registros antigos.
- Resposta de detalhe contém `id`, `unidadeId`, `clienteId` apenas para equipe
  autorizada, `servicoId`, nome e preço acordados, `profissionalId`, `inicio`,
  `fim`, `fusoHorario`, `status`, `criadoEm`, `atualizadoEm` e dados de
  cancelamento quando aplicáveis. Para o cliente, omitir identificadores
  internos desnecessários de outros usuários. Nunca retornar senha, e-mail de
  outro cliente, token ou observações internas de outra filial.
- `400` cobre formato, campos incompatíveis, grade, horizonte e ausência de
  `Idempotency-Key`; `401`, sessão ausente; `403`, perfil sem permissão;
  `404`, recurso fora do escopo ou inexistente; `409`, estado ou horário
  conflitante. Os erros seguem o formato comum da API e incluem código de
  negócio estável, sem detalhes de outra reserva.
- Respostas de detalhes e listas usam `Cache-Control: no-store`.

## Interface e fluxos

- Na página pública da filial, a escolha de serviço, profissional e horário
  leva à revisão da reserva. Visitante é conduzido ao login/cadastro e retorna
  à escolha, mas o horário é consultado novamente após autenticar. A tela
  mostra filial, profissional, serviço, duração, preço e fuso antes de enviar.
- Após `201`, mostrar comprovante com ID, estado `AGENDADO` e próximo passo.
  A interface não apresenta a prévia como reserva garantida.
- Cliente ganha "Meus agendamentos" na navegação: futuros e histórico, estados
  legíveis, detalhe, cancelamento com confirmação explícita e reagendamento
  mediante nova escolha de horário. Não mostra controles de confirmação.
- Recepção e administrador da filial ganham agenda operacional com filtro de
  data/profissional, criação para cliente já conhecido ou avulso, confirmação,
  cancelamento e reagendamento. IDs de outra filial não são exibidos nem
  acionáveis. A agenda profissional existente continua somente leitura.
- Um `409 HORARIO_INDISPONIVEL` preserva serviço e profissional selecionados,
  informa que o horário deixou de estar livre e recarrega alternativas. Outro
  `409` explica se o atendimento já começou, foi cancelado ou a chave de
  idempotência foi reutilizada.
- Após confirmação, cancelamento ou reagendamento, todas as telas consultam os
  dados novamente. Botões são desabilitados durante o envio, com mensagens de
  sucesso/erro acessíveis; isso melhora a experiência, mas o backend continua
  sendo a autoridade.

## Migração, histórico e auditoria

- `cliente` recebe `usuario_id` único e `telefone_contato` opcional, mantendo
  nulos os vínculos dos registros avulsos anteriores. `atendimento` recebe
  vínculo do cliente autenticado por meio de `cliente.usuario_id`, snapshots
  `servico_nome`, `preco_acordado` e
  `fuso_horario_agendamento`, além de `criado_em`, `atualizado_em`,
  `cancelado_em`, `cancelado_por` e motivo de cancelamento. Os campos
  obrigatórios de registros legados são preenchidos do catálogo e da filial
  atuais em uma migração documentada, pois o valor original não pode ser
  recuperado. IDs e estados existentes são preservados.
- Uma tabela de eventos de agendamento registra criação, confirmação,
  cancelamento e reagendamento com atendimento, autor, instante, estado
  anterior/novo e início anterior/novo. Apenas informações mínimas entram no
  log; motivos sensíveis ficam protegidos no próprio atendimento. Não há
  exclusão física do atendimento nem dos eventos.
- Antes de instalar a restrição de não sobreposição, a migração detecta dados
  legados ativos conflitantes e falha com diagnóstico operacional. Não altera
  nem cancela atendimentos automaticamente. A equipe resolve o histórico e
  reaplica a migração.
- Mudança de fuso da filial com atendimentos futuros ativos passa a retornar
  `409`, para não reinterpretar instantes já prometidos. Atendimentos passados
  continuam exibidos no fuso capturado na reserva.
- A tabela de idempotência mantém chave, escopo, hash e resposta por 24 horas;
  uma rotina de limpeza remove apenas chaves expiradas, não atendimentos ou
  auditoria.

## Critérios de aceitação e testes

- Cliente autenticado cria reserva para si mesmo sem enviar `clienteId`; uma
  tentativa de fornecer outro ID não altera o proprietário. Sua primeira
  reserva cria exatamente um vínculo `cliente.usuario_id` mesmo sob corrida.
- Recepção e administração criam reservas apenas em sua filial e com cliente
  permitido; podem criar cliente avulso no mesmo pedido. Profissional não cria,
  confirma, cancela nem reagenda.
- Um horário dentro da jornada é aceito somente se serviço e profissional
  estiverem ativos, vinculados e habilitados, e se respeitar grade, fuso,
  duração, pausa e horizonte. Folga, feriado, afastamento e bloqueio retiram a
  opção também na escrita.
- Dois pedidos simultâneos para a mesma ocupação produzem exatamente uma
  reserva ativa. O outro recebe `409`, sem dados do vencedor. Corrida entre
  alteração de disponibilidade e reserva não deixa um atendimento fora das
  janelas finais.
- Pausas antes e depois do candidato, limites semiabertos e pausa que cruza a
  meia-noite impedem sobreposição; `CANCELADO` libera o horário.
- Confirmar `AGENDADO` muda para `CONFIRMADO`; repetir a confirmação não cria
  evento. Cliente não consegue confirmar.
- Cancelar `AGENDADO` ou `CONFIRMADO` muda para `CANCELADO`, libera a agenda e
  mantém o histórico; repetir não duplica evento. Atendimento iniciado não
  pode ser cancelado nem reagendado por este fluxo.
- Reagendar um `CONFIRMADO` para horário elegível mantém snapshots e ID,
  registra evento e volta a `AGENDADO`. Repetir o mesmo horário não cria evento.
  Reagendar `CANCELADO` é recusado.
- Alterar nome, duração, intervalo ou preço do serviço não reescreve os
  snapshots da reserva; confirmar atendimento antigo continua possível se o
  serviço foi desativado, mas reagendá-lo exige serviço ativo.
- A mesma chave de idempotência e mesmo corpo retornam o mesmo atendimento;
  mesma chave com outro corpo retorna `409`, sem duplicar clientes ou reservas.
- Testes de integração verificam `401`, `403` e `404` por proprietário e
  filial, CSRF, transições, filtros/paginação, migração, restrição de banco e
  concorrência real em transações separadas. Testes de interface cobrem
  revisão, login com retorno, criação, estados vazios, confirmação interna,
  cancelamento, reagendamento e recuperação de horário perdido.

## Fora do escopo

- Pagamento, sinal, reembolso, comissão e política financeira de cancelamento.
- Lembretes, confirmação por e-mail/WhatsApp, notificações automáticas e
  calendário externo.
- Lista de espera, encaixe manual sobre conflito, recorrência, pacotes e
  atendimentos com múltiplos profissionais ou recursos compartilhados.
- Check-in, início/conclusão do serviço, falta do cliente e avaliações.
- Transferência de um atendimento para outra filial, serviço ou profissional
  sem cancelar e criar outro registro.
- Edição ampla de dados de clientes avulsos e vinculação automática de
  registros avulsos a contas existentes.

## Relação com outras SPECs e rastreabilidade

- Reutiliza identidade, sessão, CSRF e matriz de acesso de
  `2026-09-30-autenticacao-e-controle-de-acesso.md`.
- Aplica filial, vínculo e estado de profissional de
  `2026-09-30-estabelecimentos-filiais-profissionais-e-permissoes.md`, e
  duração, preço e habilitação de `2026-09-30-catalogo-de-servicos.md`.
- Consome as janelas de
  `2026-10-01-jornada-folgas-feriados-e-bloqueios.md` e a regra de
  `2026-10-01-motor-de-disponibilidade.md`; esta SPEC acrescenta a proteção
  transacional e de banco que a consulta pública deliberadamente não oferece.
- Amplia o modelo mínimo de `2026-09-30-painel-profissional.md`, mantendo sua
  agenda e os três estados já exibidos.

Toda implementação derivada desta SPEC deve usar o trailer:

```text
Spec: SPEC/2026-10-01-agendamento-confirmacao-cancelamento-e-reagendamento.md
```
