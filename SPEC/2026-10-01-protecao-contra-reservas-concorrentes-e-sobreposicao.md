# Proteção contra reservas concorrentes e sobreposição de horários (2026-10-01)

## O que é e por que existe

Dois clientes podem ver o mesmo horário livre e tentar reservá-lo quase ao
mesmo tempo. A prévia do motor de disponibilidade não é uma reserva. Esta SPEC
detalha a garantia de que apenas uma escrita efetivada pode ocupar cada
período de um profissional, mesmo diante de requisições simultâneas, tentativas
repetidas e alterações concorrentes da agenda.

Ela complementa a SPEC de agendamento, confirmação, cancelamento e
reagendamento. Mantém os estados, rotas, permissões e regras de elegibilidade
ali definidos; não cria um segundo fluxo de reserva.

## Invariantes de domínio

- A unidade de exclusividade é o **profissional**, independentemente do
  cliente, serviço, filial exibida na interface ou origem da solicitação
  (cliente ou equipe). Profissionais distintos podem atender no mesmo horário.
- `AGENDADO` e `CONFIRMADO` ocupam a agenda. `CANCELADO` não ocupa. Confirmar
  não libera nem duplica o período; cancelar só o libera após o commit.
- O período ocupado é `[inicio, inicio + duracao_minutos +
  intervalo_minutos)`, usando os snapshots persistidos no atendimento. A
  duração da execução e o intervalo posterior contam para conflitos. Dois
  períodos adjacentes, com fim de um igual ao início do outro, não se
  sobrepõem.
- Uma reserva cujo intervalo posterior cruza a meia-noite também impede
  ocupação no dia seguinte. Consultas de conflito não podem limitar-se aos
  atendimentos iniciados na data do novo horário.
- O mesmo início pode estar visível simultaneamente para vários clientes. A
  disponibilidade exibida é provisória até a confirmação da escrita pelo
  servidor. Não há bloqueio temporário de horário nem lista de espera.
- Nenhum erro de concorrência pode deixar atendimento ativo sobreposto,
  cliente avulso órfão, evento de auditoria de sucesso ou resultado de
  idempotência de sucesso para uma reserva que não foi efetivada.

## Protocolo de escrita

Criação e reagendamento devem ocorrer em transação de banco de dados. Antes de
consultar ocupações e janelas, a operação obtém bloqueio transacional da linha
do profissional. Depois reavalia, dentro da mesma transação, os dados atuais
de filial, serviço, habilitação, jornada, exceções, afastamentos, bloqueios,
feriados e atendimentos ativos. A validação feita quando o horário apareceu na
tela não substitui esta etapa.

No reagendamento, a linha do atendimento também é bloqueada, depois da linha
do profissional. Sua ocupação antiga é ignorada apenas ao verificar o novo
período. A troca de horário e o evento correspondente são atômicos: se a nova
posição perder uma corrida ou falhar na validação, o horário antigo permanece.
Reagendar para o mesmo início segue a idempotência por estado da SPEC de
agendamentos.

Escritas de jornada, exceção, afastamento, bloqueio, feriado ou estado que
alterem a disponibilidade participam do mesmo protocolo de bloqueio antes de
validar seus efeitos. Alterações da filial inteira bloqueiam os profissionais
afetados em ordem crescente de ID. Nenhum caminho administrativo pode criar
uma indisponibilidade sobre reservas ativas sem a validação prevista nas
SPECs de agenda.

O bloqueio serializa as decisões da aplicação, mas não é a única defesa. A
restrição PostgreSQL `ex_atendimento_ocupacao`, criada em
`V12__agendamentos.sql`, deve permanecer ativa: `EXCLUDE USING gist` por
`profissional_id` e interseção de `tsrange` semiaberto, apenas para estados
`AGENDADO` e `CONFIRMADO`, calculado com duração e intervalo persistidos. Ela
impede sobreposição mesmo se outro caminho de escrita deixar de obedecer ao
protocolo. Migração ou manutenção não podem removê-la sem uma proteção
equivalente e testes de regressão. Dados legados conflitantes precisam ser
identificados e resolvidos antes de instalar ou reconstruir a restrição; não
se escolhe silenciosamente uma das reservas.

## Resultado de disputas e contrato com a interface

- Para dois pedidos válidos, distintos e concorrentes que disputem o mesmo
  período do mesmo profissional, exatamente um pode ser confirmado no banco.
  O vencedor recebe a resposta normal de criação ou reagendamento. O perdedor
  recebe `409 Conflict` com código `HORARIO_INDISPONIVEL`, tanto quando a
  aplicação identifica o conflito quanto quando a restrição do banco o
  detecta. Não expor identidade ou dados da outra reserva nem detalhes SQL.
- Se a primeira transação abortar antes do commit, ela não ganha o horário;
  outra transação pode reservá-lo após revalidar. Não se responde sucesso
  antes de a operação estar confirmada.
- Mudança concorrente da agenda que torne o horário inelegível produz o mesmo
  `409 HORARIO_INDISPONIVEL` previsto para a prévia desatualizada. Erros
  técnicos transitórios que não comprovam conflito não devem ser apresentados
  como horário ocupado; devem seguir a política geral de erro da API, sem
  criar sucesso parcial. Não há repetição automática ilimitada.
- A interface mantém serviço e profissional selecionados, informa que o
  horário deixou de estar disponível e consulta alternativas atualizadas.
  Desabilitar o botão durante o envio reduz cliques duplicados, mas não é
  mecanismo de integridade. O cliente pode tentar outro horário com nova
  chave de idempotência.
- O `POST` de criação mantém `Idempotency-Key` UUID por 24 horas, conforme a
  SPEC de agendamentos. Repetir a mesma chave com o mesmo pedido devolve o
  mesmo resultado, sem segunda reserva. Reutilizá-la com pedido diferente
  retorna `409 CHAVE_IDEMPOTENCIA_REUTILIZADA`. Chaves diferentes não
  contornam a exclusividade, inclusive para o mesmo cliente.

## Matriz mínima de verificação

Os testes de concorrência usam PostgreSQL real, transações e conexões
independentes, início sincronizado e verificação do estado persistido após o
commit. Teste unitário de cálculo de intervalos, sozinho, não demonstra
segurança sob corrida.

| Cenário | Resultado esperado |
| --- | --- |
| Dois clientes, mesmo profissional e período, chaves diferentes | Um sucesso e um `409 HORARIO_INDISPONIVEL`; exatamente um atendimento ativo e um evento de criação. |
| Mesmo cliente repete a mesma chave e o mesmo pedido, inclusive após perda da resposta | Mesmo atendimento e resultado; nenhum segundo evento ou cliente. |
| Mesmo cliente usa chaves diferentes para períodos coincidentes | Um sucesso e um `409`, como para clientes distintos. |
| Dois profissionais no mesmo horário | Ambos podem ser reservados se cada agenda for elegível. |
| Serviços de durações ou intervalos diferentes com períodos parcialmente sobrepostos | A segunda escrita falha, mesmo se os inícios forem diferentes. |
| Períodos adjacentes em limites semiabertos | Ambos podem existir se as janelas de trabalho permitirem. |
| Intervalo posterior atravessa a meia-noite | Reserva do dia seguinte que o intercepte falha. |
| Cancelamento versus nova reserva do mesmo período | A nova reserva só vence depois de observar o cancelamento confirmado; resultado final sem sobreposição. |
| Reagendamento versus criação no destino | Um ocupa o destino; se o reagendamento perder, seu horário original e estado permanecem. |
| Alteração de jornada, exceção, afastamento, bloqueio ou feriado versus criação | Ordem de commits coerente; não pode restar reserva ativa fora da disponibilidade válida. |
| Escrita direta no banco, sem bloqueio da aplicação | A restrição `EXCLUDE` rejeita dois registros ativos sobrepostos; `CANCELADO` não causa falso conflito. |

Além do resultado HTTP, verificar em cada falha que não houve cliente avulso,
evento ou registro de idempotência de sucesso residual. Exercitar a tradução
da violação da restrição para `409` em um teste de integração separado.
Repetir a disputa principal em execuções suficientes para revelar interleavings
sem depender de `sleep` como sincronização. Testes de interface cobrem a
mensagem de horário perdido, atualização das alternativas e prevenção de
envios duplicados; a garantia de exclusividade continua no backend e no banco.

## Aceite

Esta SPEC está atendida quando a matriz acima passa, nenhuma sequência
testada termina com intervalos ativos sobrepostos do mesmo profissional, e o
comportamento de erro e repetição permanece estável para cliente e equipe.
Também devem continuar passando os testes de disponibilidade, ciclo de vida
do agendamento, migração e autorização já existentes.

## Fora do escopo

- Reserva temporária durante navegação, fila de espera e encaixe que ignore
  conflitos.
- Capacidade compartilhada por sala, equipamento ou múltiplos profissionais.
- Mudança de profissional, serviço ou filial de um atendimento existente.
- Política de duração de bloqueio HTTP, limitação de taxa e carga máxima;
  podem ser tratadas separadamente sem enfraquecer os invariantes acima.

## Relação com outras SPECs e rastreabilidade

- Detalha a seção de concorrência de
  `2026-10-01-agendamento-confirmacao-cancelamento-e-reagendamento.md`.
- Usa a definição de janelas e prévia de
  `2026-10-01-motor-de-disponibilidade.md` e as regras de alterações da agenda
  de `2026-10-01-jornada-folgas-feriados-e-bloqueios.md`.
- Não modifica a matriz de acesso definida pelas SPECs de autenticação,
  estabelecimentos e agendamento.

Toda implementação derivada desta SPEC deve usar o trailer:

```text
Spec: SPEC/2026-10-01-protecao-contra-reservas-concorrentes-e-sobreposicao.md
```
