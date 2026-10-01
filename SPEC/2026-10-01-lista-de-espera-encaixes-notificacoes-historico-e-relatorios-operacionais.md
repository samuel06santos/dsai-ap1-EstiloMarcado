# Lista de espera, encaixes, notificações, histórico e relatórios operacionais (2026-10-01)

## O que é e por que existe

Esta SPEC permite registrar a intenção de um cliente quando nenhum horário
adequado está disponível, avisá-lo quando surgir uma oportunidade e ajudar a
equipe a preencher vagas reais na agenda. Também reúne notificações
transacionais, histórico auditável e indicadores operacionais da filial.

Ela amplia o ciclo de agendamentos sem substituir suas regras: um aviso ou uma
posição na lista de espera **não** é reserva. O horário só pertence ao cliente
depois que a criação do atendimento é confirmada pelo banco. Nenhum encaixe
pode ignorar a restrição de sobreposição do profissional.

## Escopo e decisões

- Lista de espera por filial e serviço para clientes com conta `ATIVA`, com
  profissional opcional, intervalo de datas e faixa de horário preferidos.
- Aviso de oportunidade por e-mail e na área autenticada, com convite para
  tentar reservar, sem retenção temporária do horário.
- Encaixe assistido por recepção ou administração: seleção de uma solicitação
  da lista e criação de atendimento em horário livre, com autorização do
  cliente registrada pela equipe.
- Notificações de criação, confirmação, cancelamento, reagendamento e lembrete
  de atendimento. Nesta versão, e-mail é o único canal externo; WhatsApp/SMS
  não são enviados.
- Linha do tempo de eventos de um atendimento, histórico da lista de espera e
  relatórios agregados da própria filial.

Não há prioridade paga, reserva provisória, escolha automática e definitiva
de outro profissional, envio de marketing, confirmação por resposta ao e-mail
ou conclusão/falta automática de um atendimento.

## Lista de espera

Uma solicitação pertence ao cliente autenticado, a uma filial ativa e a um
serviço ativo. O profissional informado precisa ser ativo, da mesma filial e
habilitado para o serviço. Sem profissional informado, qualquer profissional
habilitado da filial pode atender. O cliente define `dataInicio` e `dataFim`
entre hoje e o 60º dia futuro da filial, com intervalo máximo de 31 dias, e
opcionalmente `horaInicio` e `horaFim` (`horaInicio < horaFim`). Uma oferta só
corresponde se a execução inteira do serviço couber nessa faixa e em uma
janela válida da agenda. O intervalo posterior também participa da checagem
de conflitos, conforme o motor de disponibilidade.

Não é obrigatório que todos os horários estejam ocupados para entrar na
lista: o cliente pode não gostar das opções disponíveis. É permitida uma
solicitação `ATIVA` por cliente, filial e serviço; alterações de preferência
atualizam a solicitação existente sem criar uma segunda posição. A posição
FIFO é o instante da última ativação: mudar preferências mantém a posição;
cancelar e entrar novamente cria uma posição nova. Empates são resolvidos por
ID crescente. A ordenação é por serviço e filial, mas só concorrem a uma vaga
as solicitações cujos filtros realmente correspondem a ela.

Estados da solicitação:

| Estado | Significado |
| --- | --- |
| `ATIVA` | Pode receber uma oportunidade; nenhuma vaga está garantida. |
| `ATENDIDA` | Gerou um atendimento por aceite ou encaixe. |
| `CANCELADA` | Retirada pelo cliente ou por equipe autorizada a pedido dele. |
| `EXPIRADA` | A data final passou no fuso da filial sem atendimento. |

Toda transição registra autor, instante e estado anterior/novo. Cliente vê
apenas as próprias solicitações; recepção e administração veem as da própria
filial. Profissional não vê contatos nem a fila de outros clientes. Contas
desativadas deixam de ser elegíveis para avisos, sem apagar o histórico.

## Oportunidades e encaixe

Cancelamento, reagendamento que libera a posição antiga e ampliação de
disponibilidade podem gerar uma verificação assíncrona da filial, profissional
e data afetados. A verificação também roda periodicamente para recuperar
eventos perdidos e expirar solicitações/ofertas. Ela consulta o motor com os
dados atuais, aplica os filtros da lista e escolhe a primeira solicitação
elegível ainda não avisada sobre aquele profissional, serviço e início. Antes
de notificar, revalida disponibilidade e estado da conta, filial e serviço.
Não se envia repetidamente a mesma oportunidade para a mesma solicitação.

Uma `oferta` registra solicitação, filial, serviço, profissional, início local,
fuso, instante de emissão, validade de 15 minutos e estado (`ENVIADA`,
`ACEITA`, `EXPIRADA` ou `INDISPONIVEL`). A validade limita o uso do convite,
**não** bloqueia o horário. Após o prazo, se a vaga continuar livre, o
processador pode avisar o próximo cliente elegível. Uma vaga tomada por
reserva comum, bloqueio ou outra oferta torna a oferta anterior indisponível;
o cliente permanece na lista se suas preferências ainda forem válidas. Várias
oportunidades de serviços diferentes podem apontar para períodos que se
cruzam; todas são provisórias e somente uma reserva conflitante pode vencer.

O link do e-mail leva ao aplicativo autenticado, sem token de acesso ou dados
de outro cliente. O cliente pode aceitar uma oferta válida; o servidor
confere proprietário, prazo e compatibilidade e usa a **mesma** validação,
transação, `Idempotency-Key` e restrição de banco da criação normal. Aceite
bem-sucedido cria `AGENDADO`, marca a solicitação `ATENDIDA` e a oferta
`ACEITA` na mesma transação. Se o horário se perdeu, retorna
`409 HORARIO_INDISPONIVEL`, marca a oferta `INDISPONIVEL` sem inventar reserva
e oferece alternativas; a solicitação continua `ATIVA` enquanto válida.
Aceites concorrentes da mesma ou de outras ofertas não geram dois
atendimentos nem dois resultados de sucesso para a solicitação.

Recepção ou administração podem fazer o encaixe a partir de uma solicitação
`ATIVA` da sua filial. A equipe escolhe explicitamente profissional e início
compatíveis com as preferências, confirma que o cliente autorizou a marcação
e registra o operador. O encaixe chama o serviço transacional de agendamento,
não insere atendimento diretamente. Sem autorização do cliente, a equipe pode
apenas enviar a oportunidade. Tentativa de sobreposição, indisponibilidade
superveniente ou falta de permissão segue os mesmos códigos do agendamento.
Não existe opção de “forçar encaixe” sobre horário ocupado.

## Notificações

Eventos de agendamento confirmados em banco geram, para cliente autenticado,
uma notificação interna e um e-mail operacional de criação, confirmação,
cancelamento ou reagendamento. O e-mail de reagendamento mostra novo horário
e fuso; o de cancelamento não inclui motivo sensível. Clientes avulsos sem
conta não recebem envio automático: a equipe mantém o contato pelo fluxo
operacional existente. Oferta da lista também gera notificação interna e
e-mail, desde que o cliente tenha aceitado receber avisos da lista.

O lembrete é gerado uma vez para atendimento ainda `AGENDADO` ou
`CONFIRMADO`, com alvo em 24 horas antes do início absoluto. Reserva criada
depois desse alvo não gera lembrete retroativo. Cancelamento ou reagendamento
invalida lembrete pendente do horário antigo; o novo horário pode gerar outro
se ainda houver antecedência. Preferências do cliente permitem desligar
lembretes e avisos da lista, sem suprimir mensagens transacionais sobre uma
reserva efetiva. E-mail só é enviado para conta `ATIVA` com e-mail confirmado.

Uma outbox persistida é gravada na mesma transação do evento de negócio; o
envio externo ocorre **após** o commit e nunca enquanto o profissional está
bloqueado. Falha do SMTP não desfaz atendimento ou cancelamento. Um worker
seleciona lotes com bloqueio apropriado (`SKIP LOCKED` em múltiplas
instâncias), limite de tentativas e espera progressiva, deixando falhas finais
visíveis para operação. Uma chave de deduplicação por evento, destinatário,
canal e tipo impede a criação de trabalhos duplicados por repetição de
requisição ou worker. O estado `ENVIADO` indica entrega ao provedor SMTP, não
leitura pelo usuário. Se o provedor aceitar um e-mail, mas sua confirmação se
perder antes de gravar `ENVIADO`, uma repetição externa ainda pode ocorrer.
Reutilizar `Message-ID` estável e limitar tentativas, sem prometer entrega
exatamente uma vez por SMTP.
Não armazenar senha, token de sessão ou corpo com dados sensíveis no log de
falha; guardar apenas destinatário por ID, tipo, referência e metadados
mínimos. A caixa de notificações internas permite marcar itens como lidos.

## Histórico e relatórios

O histórico de atendimento reutiliza `agendamento_evento`: criação,
confirmação, cancelamento e reagendamento ficam em ordem de ocorrência e ID,
com autor permitido pelo escopo, estados e horários anterior/novo. O cliente
consulta a linha do tempo do próprio atendimento; recepção e administração
consultam somente atendimentos da sua filial. O profissional consulta apenas
a linha do tempo dos seus atendimentos e não recebe dados de contato nem
motivos internos além do permitido no painel profissional. Eventos são
imutáveis; corrigir um erro operacional acrescenta novo evento, não reescreve
o anterior. Registros passados em `AGENDADO`/`CONFIRMADO` não são rotulados
como “concluídos”: não há estado `CONCLUIDO` ou `FALTOU` nesta SPEC.

O relatório inicial é **agregado por filial**, acessível apenas ao
administrador da própria filial; o administrador principal não agrega dados
das demais filiais. Filtros: `de`, `ate` (até 90 dias), serviço e profissional
opcionais da mesma filial, com agrupamento por dia e total do período. Datas
de agenda usam a data local do início atual do atendimento no fuso capturado;
datas de atividade usam o instante do evento convertido para o fuso capturado
no respectivo atendimento. A resposta explicita `geradoEm`, fusos aplicados,
filtros e definições:

| Indicador | Definição |
| --- | --- |
| `agendados` | Atendimentos com início no período e estado atual `AGENDADO`. |
| `confirmados` | Atendimentos com início no período e estado atual `CONFIRMADO`. |
| `cancelados` | Atendimentos com início no período e estado atual `CANCELADO`. |
| `criacoes` | Eventos `CRIACAO` ocorridos no período. |
| `confirmacoes` | Eventos `CONFIRMACAO` ocorridos no período. |
| `cancelamentos` | Eventos `CANCELAMENTO` ocorridos no período. |
| `reagendamentos` | Eventos `REAGENDAMENTO` ocorridos no período. |
| `encaixes` | Criações originadas de solicitações da lista de espera no período. |
| `solicitacoesAtivas` | Solicitações ainda `ATIVA` no instante de geração, sem contar ofertas como reservas. |

Contagens de estado e de evento têm eixos temporais diferentes e não devem
ser somadas como se descrevessem a mesma coorte. Reagendar muda a data de
agenda do atendimento, mas preserva seus eventos históricos. Não calcular
receita, taxa de comparecimento, faturamento ou ocupação realizada sem dados
que sustentem esses conceitos. Respostas agregadas não incluem nome, contato
ou identificador de cliente. Histórico detalhado permanece em rotas
autorizadas separadas. `solicitacoesAtivas` aparece só no total do período,
por ser um retrato atual, e não é distribuído por dia. Não há exportação CSV
nesta primeira versão.

## Contrato HTTP inicial

Todas as rotas abaixo exigem sessão, e escritas exigem CSRF. IDs de cliente,
perfil e filial no corpo nunca são fonte de autorização. Respostas pessoais
usam `Cache-Control: no-store`; listas são paginadas (máximo 100 itens).

| Método e rota | Acesso | Resultado |
| --- | --- | --- |
| `POST /api/unidades/{unidadeId}/lista-espera` | Cliente próprio | Cria solicitação `ATIVA` ou atualiza preferências da ativa; `201`/`200`. |
| `GET /api/me/lista-espera` | Cliente | Solicitações e ofertas próprias, inclusive histórico paginado. |
| `DELETE /api/me/lista-espera/{id}` | Cliente proprietário | Cancela solicitação ativa; repetição autorizada é idempotente. |
| `POST /api/me/lista-espera/{id}/ofertas/{ofertaId}/aceitacoes` | Cliente proprietário | Reserva por oferta com `Idempotency-Key`; `201`, ou `409`. |
| `GET /api/unidades/{unidadeId}/lista-espera` | Recepção/admin da filial | Fila filtrável e paginada, sem dados de outra filial. |
| `POST /api/unidades/{unidadeId}/encaixes` | Recepção/admin da filial | Reserva assistida a partir de solicitação, com `Idempotency-Key`; `201`, ou `409`. |
| `GET /api/me/notificacoes` | Usuário autenticado | Caixa própria, paginada, com estado de leitura. |
| `PATCH /api/me/notificacoes/{id}/leitura` | Destinatário | Marca item próprio como lido; repetição idempotente. |
| `GET /api/agendamentos/{id}/eventos` | Proprietário, profissional atribuído ou equipe da filial | Linha do tempo autorizada e paginada. |
| `GET /api/unidades/{unidadeId}/relatorios/operacionais` | Admin da filial | Indicadores agregados com filtros e agrupamento diário. |

`400` cobre preferências inválidas, período excessivo, ausência de chave de
idempotência e filtros inconsistentes; `401`/`403` seguem sessão e perfil;
`404` evita revelar recurso fora do escopo; `409` cobre horário perdido,
oferta expirada, solicitação encerrada ou chave reutilizada. Códigos de
negócio são estáveis e não revelam cliente concorrente. A ausência de e-mail
ou falha de envio não altera a resposta de uma reserva efetivada.

## Interface e acessibilidade

- Cliente encontra “Lista de espera” ao consultar serviço e horários da
  filial, e em “Meus agendamentos”; pode criar/editar preferências, sair da
  lista, ver posição aproximada e aceitar oportunidade. A interface explica
  que posição e aviso não garantem vaga. Após `409`, atualiza alternativas e
  preserva preferências. Caixa de notificações no cabeçalho indica itens não
  lidos, com navegação por teclado e mensagens `aria-live`.
- Recepção e administração veem fila da filial, filtros, data da entrada,
  preferências, contato permitido, ofertas pendentes e ação de encaixe com
  confirmação explícita da autorização do cliente. O fluxo apresenta o
  horário e o profissional antes de gravar. Não há controle de sobreposição.
- Administrador vê cartões e série diária dos indicadores, filtros e legenda
  de cada métrica, com estados vazio, carregando e erro. Profissional mantém
  apenas sua agenda e histórico dos próprios atendimentos.
- Horários mostram data e fuso da filial; eventos e notificações mostram
  instantes de modo compreensível e preservam o fuso original do atendimento.

## Persistência, desempenho e segurança

- Criar tabelas para solicitação de lista, oferta, eventos da lista,
  notificação interna e outbox. `agendamento_evento` continua a fonte do
  histórico de reservas. Uma referência de origem no atendimento ou no evento
  de criação identifica encaixes sem inferência por texto livre.
- Restrições e índices garantem uma solicitação ativa por cliente/filial/
  serviço, deduplicação de oferta e outbox, busca da fila ativa por
  filial/serviço/data/posição, varredura de outbox pendente por próximo envio,
  e consulta de eventos por atendimento/data. Usar índices parciais quando
  apenas estados ativos/pendentes forem consultados; conferir planos das
  consultas agregadas antes de acrescentar índices redundantes.
- Aceite e encaixe mantêm transações curtas e ordem fixa de bloqueios:
  identidade do autor quando aplicável, profissional, solicitação, oferta e
  atendimento. A restrição `ex_atendimento_ocupacao` permanece a defesa final.
  O envio SMTP não participa da transação. Workers concorrentes não podem
  produzir duas reservas nem dois trabalhos para o mesmo evento; a entrega
  externa tem a limitação de SMTP descrita acima.
- O worker resolve o e-mail atual no momento do envio e valida o estado da
  conta. Logs e relatórios não expõem e-mail, telefone, tokens ou motivos
  sensíveis. A API filtra por sessão e filial no servidor, nunca somente no
  frontend. Dados de outras filiais respondem `404` quando identificados por
  ID. A limpeza de ofertas/notificações operacionais expiradas não apaga
  atendimento nem seus eventos; retenção e eventual exclusão seguem a
  política de dados do projeto.
- Migrar sem criar notificações retroativas para eventos antigos. Solicitações
  e ofertas começam vazias; histórico de atendimento já existente continua
  disponível. Dados legados não são convertidos em “encaixes”.

## Critérios de aceitação e testes

- Cliente cria, edita e cancela uma solicitação própria; duplicata ativa não
  muda sua posição, reentrada após cancelamento recebe nova posição. Datas,
  fuso, profissional, serviço e limites são validados no servidor.
- Fila é FIFO entre candidatos compatíveis, mas uma oferta não ocupa agenda.
  Oferta expirada, cliente desativado, serviço inativo e horário perdido não
  produzem reserva. Após oportunidade não aproveitada, o próximo elegível é
  avisado somente se a vaga continuar disponível.
- Dois clientes aceitam ofertas conflitantes, ou um aceita enquanto outro
  reserva normalmente: apenas uma criação vence; o perdedor recebe `409`,
  sem atendimento, evento ou resultado de idempotência falso. Corrida entre
  encaixe manual, cancelamento, reagendamento e mudança de disponibilidade
  conserva os invariantes da SPEC de proteção contra sobreposição.
- Repetição de aceite/encaixe com mesma chave e pedido retorna o mesmo
  atendimento. Chave reutilizada com pedido diferente retorna `409`. Nenhum
  envio de e-mail ocorre antes do commit. Falha/repetição do worker não cria
  nova reserva nem duplica itens internos ou trabalhos da outbox; o teste
  admite a incerteza de entrega externa após aceite pelo SMTP.
- Criação, confirmação, cancelamento e reagendamento geram notificações
  adequadas; lembrete antigo é invalidado em cancelamento/reagendamento e
  avulsos não recebem e-mail sem conta. Preferências de lembrete/oferta são
  respeitadas. Caixa interna e leitura só mostram itens do destinatário.
- Cliente, profissional e equipe veem apenas eventos permitidos; relatório
  da filial não contém dados de cliente e produz contagens corretas nos dois
  eixos de data, inclusive em reagendamento, cancelamento e mudança de fuso.
- Testes unitários cobrem correspondência de preferências, FIFO, prazos,
  fusos e agregações. Testes de integração com PostgreSQL real cobrem
  transações concorrentes, restrições, autorização, outbox, retries e
  migração. Testes de interface cobrem fila vazia, convite vencido, horário
  perdido, caixa de avisos, encaixe e leitura de relatórios acessíveis.

## Fora do escopo

- Overbooking, exceção que ignore bloqueio, prioridade paga e garantia de
  horário por posição na fila.
- WhatsApp/SMS, push móvel, campanhas de marketing e resposta a e-mail como
  operação de agendamento.
- `CONCLUIDO`, `FALTOU`, check-in, avaliação, receita, comissões, metas e
  relatórios financeiros.
- Relatório consolidado entre filiais, exportação CSV/PDF e BI externo.

## Relação com outras SPECs e rastreabilidade

- Usa o ciclo de vida, a idempotência e `agendamento_evento` de
  `2026-10-01-agendamento-confirmacao-cancelamento-e-reagendamento.md`.
- Consulta o motor de `2026-10-01-motor-de-disponibilidade.md` e respeita a
  exclusão de `2026-10-01-protecao-contra-reservas-concorrentes-e-sobreposicao.md`.
- Reutiliza permissões por filial de
  `2026-09-30-estabelecimentos-filiais-profissionais-e-permissoes.md` e a
  identidade de `2026-09-30-autenticacao-e-controle-de-acesso.md`.
- Amplia o histórico mostrado em `2026-10-01-painel-do-cliente.md` e a agenda
  de `2026-09-30-painel-profissional.md`, sem alterar seus estados.

Toda implementação derivada desta SPEC deve usar o trailer:

```text
Spec: SPEC/2026-10-01-lista-de-espera-encaixes-notificacoes-historico-e-relatorios-operacionais.md
```
