# Seed de dados mock para desenvolvimento (2026-10-01)

## O que é e por que existe

A seed de dados mock popula o banco de desenvolvimento do Estilo Marcado com um
conjunto coerente e realista de estabelecimentos, filiais, profissionais,
serviços, contas, vínculos entre clientes e contas, jornadas, folgas, feriados,
bloqueios, atendimentos, histórico de eventos de agendamento, lista de espera,
ofertas de encaixe e notificações. Sem ela, cada desenvolvedor precisa cadastrar
tudo manualmente pela interface antes de conseguir exercitar catálogo,
disponibilidade, agenda diária/semanal do profissional, painel do cliente,
agendamento, lista de espera e login.

Esta SPEC define **o que a seed contém**, **quais invariantes ela respeita** e
**como executá-la** depois de subir o ambiente com Docker Compose. Ela é um
recurso de apoio ao desenvolvimento: não faz parte do fluxo de produção e não
substitui as migrações Flyway, que continuam sendo a única autoridade do schema.

A seed evolui junto com o sistema. A parte de agendamento foi incorporada depois
da migração `V12__agendamentos.sql` (vínculo `cliente.usuario_id`, snapshots
comerciais e de ocupação, cancelamento e `agendamento_evento`). A parte
operacional foi incorporada depois de `V13__lista_espera_e_notificacoes.sql`
(lista de espera, ofertas, notificações internas e outbox) e do painel do
cliente, que exige histórico de atendimentos. As novas filiais, profissionais,
serviços e a agenda ampliada existem para que a agenda em calendário, os
relatórios operacionais e o painel do cliente tenham dados representativos.

## Escopo

- Um arquivo SQL de seed versionado no repositório.
- Um script de execução para Windows/PowerShell e um para Linux/macOS.
- Dados mock de: dois estabelecimentos, quatro filiais (`unidade`), oito
  profissionais, serviços, vínculos serviço–profissional, contas de acesso
  (`usuario`), fichas de cliente (`cliente`), jornada semanal, exceções de
  jornada, afastamentos, feriados, bloqueios de agenda e atendimentos.
- **Agenda ampliada**: atendimentos distribuídos entre dias passados
  (histórico, consumido pelo painel do cliente e pelos relatórios) e próximos
  dias (agenda diária/semanal/mensal do profissional e agenda operacional). O
  encaixe derivado da lista de espera é representado por um atendimento com
  `lista_espera_id`.
- **Parte de agendamento**: vínculo de clientes autenticados a contas
  (`cliente.usuario_id`), telefone de contato de clientes avulsos, snapshots
  comerciais e de ocupação do atendimento (`servico_nome`, `preco_acordado`,
  `duracao_minutos`, `intervalo_minutos`, `fuso_horario_agendamento`), instantes
  de criação/atualização e dados de cancelamento (`cancelado_em`,
  `cancelado_por`, `motivo_cancelamento`) e o histórico de eventos
  (`agendamento_evento`) de criação, confirmação, cancelamento e reagendamento.
- **Lista de espera**: solicitações em todos os estados e seus eventos
  (`lista_espera`, `lista_espera_evento`), ofertas de encaixe
  (`lista_espera_oferta`) em estados distintos e o vínculo de um encaixe aceito.
- **Notificações**: preferências (`notificacao_preferencia`), notificações
  internas lidas e não lidas (`notificacao_interna`) e itens de saída
  (`notificacao_outbox`) em estados distintos, sem disparar envios reais.
- Comportamento **idempotente** por padrão e opção de **reset** dos dados de
  domínio.
- Documentação no `README.md`.

## Fora do escopo

- Carga de dados em produção ou em qualquer ambiente que não seja o de
  desenvolvimento local.
- Criação de schema, tabelas, colunas, índices ou restrições: isso continua sob
  responsabilidade das migrações Flyway.
- Criação de contas por convite, ativação por e-mail, tokens ou eventos de
  auditoria. As contas mock já nascem `ATIVA` e com senha definida.
- Executar as regras de negócio de agendamento (validação de janela, conflito,
  idempotência e transições) pelos serviços da aplicação. Os atendimentos e os
  eventos mock são inseridos diretamente no banco, já respeitando as invariantes
  do schema, e servem para popular as telas e o painel, não para substituir os
  testes de integração do fluxo real.
- Popular `agendamento_idempotencia`: são dados efêmeros, com validade de 24
  horas, criados apenas pelo fluxo real de escrita.
- Executar os agendadores (`@Scheduled`) de lista de espera e de notificações
  como parte da seed. O processamento real do outbox é responsabilidade da
  aplicação; a seed apenas grava estados plausíveis e usa prazos/`enviar_apos`
  futuros para que a rotina não reescreva os dados semeados imediatamente.
- Importação de dados reais, anonimização de bases existentes ou geração
  massiva de registros para testes de carga.
- Sincronização automática da seed com mudanças futuras de schema.

## Princípios de projeto

1. **Não destrutiva por padrão.** A execução normal nunca apaga registros e não
   sobrescreve dados já presentes, exceto as colunas de agendamento dos próprios
   registros mock da faixa reservada, convergidas para o valor esperado.
2. **Idempotente.** Executar a seed repetidas vezes produz o mesmo estado, sem
   duplicar registros.
3. **Isolada por faixa de IDs.** Todos os registros mock usam a faixa reservada
   `1000+` e estabelecimentos próprios. Como a sequência de IDs é compartilhada
   com a aplicação, um ID da faixa pode já pertencer a um dado real (por exemplo,
   serviços criados pela revisão de catálogo); por isso a seed escolhe IDs livres
   no momento da escrita e nunca sobrescreve um registro existente que não seja
   da própria seed. Os novos serviços usam `1020+` e as novas contas `1030+`
   justamente para ficar acima do que já existe.
4. **Atômica.** Toda a inserção ocorre em uma única transação
   (`BEGIN`/`COMMIT`); qualquer erro aborta o conjunto.
5. **Somente desenvolvimento.** As contas usam domínio fictício
   (`@estilomarcado.dev`) e uma senha pública conhecida, inadequada para
   produção.
6. **Fonte de verdade no SQL.** Os scripts são apenas invólucros finos de
   execução; toda a lógica de dados fica em `scripts/seed.sql`.

## Artefatos

| Arquivo | Papel |
| --- | --- |
| `scripts/seed.sql` | Define e insere os dados mock. Idempotente e transacional. |
| `scripts/seed.ps1` | Executor para Windows/PowerShell. |
| `scripts/seed.sh` | Executor para Linux/macOS. |
| `README.md` | Seção "Seed de dados mock (desenvolvimento)" com o passo a passo. |

Os scripts não conhecem credenciais: eles leem `POSTGRES_USER` e `POSTGRES_DB`
de dentro do próprio container PostgreSQL e usam `psql` com
`ON_ERROR_STOP=1`.

## Como executar

Pré-requisitos:

- Docker em execução.
- Ambiente local no ar com `docker compose up --build -d`, de modo que o
  container `estilo-marcado-postgres` esteja saudável e as migrações Flyway já
  tenham sido aplicadas pelo backend. A seed pressupõe o schema até
  `V12__agendamentos.sql`; em um banco cuja migração de agendamento ainda não
  tenha rodado, as colunas de snapshot e o histórico não existem e a seed falha.

Windows/PowerShell, a partir da raiz do repositório:

```powershell
docker compose up --build -d
scripts/seed.ps1
```

Linux/macOS:

```bash
docker compose up --build -d
scripts/seed.sh
```

Para recriar os dados do zero (apaga todos os dados de domínio, mas preserva as
migrações Flyway e as sessões):

```powershell
scripts/seed.ps1 -Reset
```

```bash
scripts/seed.sh --reset
```

### Opções dos scripts

| Script | Opção | Efeito |
| --- | --- | --- |
| `seed.ps1` | `-Reset` | Limpa os dados de domínio antes de semear. |
| `seed.ps1` | `-Container <nome>` | Container do PostgreSQL. Padrão: `estilo-marcado-postgres`. |
| `seed.sh` | `--reset` | Limpa os dados de domínio antes de semear. |
| `seed.sh` | `-c`, `--container <nome>` | Container do PostgreSQL. Padrão: `estilo-marcado-postgres`. |
| `seed.sh` | `-h`, `--help` | Mostra a ajuda. |

Ambos os scripts aceitam a variável de ambiente `SEED_CONTAINER` para trocar o
container padrão.

### Fluxo dos executores

1. Verificam que `docker` está disponível e que o container está em execução;
   caso contrário, encerram com mensagem orientando a rodar
   `docker compose up --build -d`.
2. Leem `POSTGRES_USER` e `POSTGRES_DB` do container.
3. Se o reset foi pedido, executam o `TRUNCATE` da seção de reset.
4. Copiam `scripts/seed.sql` para `/tmp/estilo-marcado-seed.sql` dentro do
   container.
5. Executam `psql -v ON_ERROR_STOP=1 ... -f /tmp/estilo-marcado-seed.sql`.
6. Removem o arquivo temporário do container, inclusive em caso de falha.
7. Imprimem um resumo das contas mock criadas.

Erros de ambiente e falhas de SQL resultam em saída diferente de zero e não
deixam o banco em estado parcial, pois a seed é transacional.

## Estratégia de idempotência

- Tabelas com restrição de unicidade natural (`unidade`, `servico`, `usuario`,
  `feriado`, `servico_profissional`) usam `ON CONFLICT DO NOTHING`.
- Tabelas sem unicidade natural (`jornada_intervalo`, `bloqueio_agenda`,
  `profissional`, `excecao_jornada`, `excecao_jornada_intervalo`, `afastamento`,
  `estabelecimento`) usam IDs explícitos da faixa reservada com
  `ON CONFLICT DO NOTHING`; a jornada semanal ainda usa `WHERE NOT EXISTS` sobre
  `(profissional_id, dia_semana, hora_inicio, hora_fim)` por não possuir chave
  natural no banco.
- `cliente` e `atendimento` usam IDs explícitos com `ON CONFLICT (id) DO UPDATE`
  para **convergir** os campos de agendamento em bancos que já receberam uma
  versão anterior da seed (sem vínculo de cliente e sem snapshots). A atualização
  atinge somente as colunas de vínculo, snapshot, instantes e cancelamento, sem
  reescrever `status`, `inicio`, serviço, profissional ou duração escolhidos por
  quem estiver usando os dados mock.
- `agendamento_evento` usa IDs explícitos com `ON CONFLICT DO NOTHING`, pois o
  histórico é append-only e não deve ser reescrito.
- `lista_espera`, `lista_espera_evento`, `lista_espera_oferta`,
  `notificacao_interna` e `notificacao_outbox` usam IDs explícitos com
  `ON CONFLICT DO NOTHING`, pois representam fila e histórico; `lista_espera`
  também pode usar `ON CONFLICT (id) DO UPDATE` para status/conteúdo. As
  preferências usam `ON CONFLICT (usuario_id) DO NOTHING`.
- A agenda ampliada complementa os atendimentos existentes usando IDs explícitos
  com `ON CONFLICT (id) DO UPDATE` nos snapshots, como os demais atendimentos.
- Ao final, `setval(pg_get_serial_sequence(...))` reposiciona cada sequência
  para o maior `id` presente, evitando colisões em inserções futuras feitas pela
  aplicação.

## Reset

A opção de reset executa, em uma única instrução, o `TRUNCATE` das tabelas de
domínio com `RESTART IDENTITY CASCADE`, na seguinte relação:

```text
notificacao_outbox, notificacao_interna, notificacao_preferencia,
lista_espera_evento, lista_espera_oferta, lista_espera,
agendamento_evento, agendamento_idempotencia, bloqueio_agenda, evento_seguranca,
token_usuario, atendimento, usuario, excecao_jornada_intervalo, excecao_jornada,
afastamento, feriado, jornada_intervalo, servico_profissional, servico,
profissional, cliente, unidade, estabelecimento
```

O reset **não** toca em `flyway_schema_history` nem em `spring_session` /
`spring_session_attributes`. É uma operação destrutiva para os dados de domínio e
deve ser usada de forma consciente.

## Conteúdo da seed

Todos os IDs abaixo estão na faixa reservada. Datas relativas usam `CURRENT_DATE`
para que a agenda semeada permaneça nos próximos dias.

### Estabelecimentos e filiais (`estabelecimento`, `unidade`)

Dois estabelecimentos, cada um com filiais próprias, para exercitar isolamento
por filial, relatórios e a página pública de cada unidade.

| `estabelecimento.id` | Nome |
| --- | --- |
| 1000 | Estilo Marcado (Mock) |
| 1001 | Studio Bella (Mock) |

| `unidade.id` | Nome | Principal | Estabelecimento | Endereço | Telefone |
| --- | --- | --- | --- | --- | --- |
| 1000 | Unidade Centro | Sim | 1000 | Av. Presidente Vargas, 1200 - Belém/PA | (91) 3222-1000 |
| 1001 | Unidade Batista Campos | Não | 1000 | Rua dos Mundurucus, 2450 - Belém/PA | (91) 3222-2000 |
| 1002 | Unidade Nazaré | Sim | 1001 | Tv. Quintino Bocaiúva, 780 - Belém/PA | (91) 3223-3000 |
| 1003 | Unidade Umarizal | Não | 1001 | Rua Domingos Marreiros, 1500 - Belém/PA | (91) 3223-4000 |

Todas usam `America/Sao_Paulo` e estão ativas. Cada estabelecimento tem
exatamente uma filial principal.

### Profissionais (`profissional`)

| `id` | Nome | Filial | Apresentação |
| --- | --- | --- | --- |
| 1000 | Ana Souza | 1000 | Especialista em cortes e coloração. |
| 1001 | Carlos Lima | 1000 | Barbeiro e especialista em barba. |
| 1002 | Beatriz Rocha | 1001 | Cabeleireira e manicure. |
| 1003 | Diego Mendes | 1001 | Barbeiro e designer de sobrancelha. |
| 1004 | Fernanda Alves | 1002 | Cabeleireira e colorista. |
| 1005 | Rafael Nunes | 1002 | Barbeiro e especialista em barba. |
| 1006 | Patrícia Gomes | 1003 | Manicure e cabeleireira. |
| 1007 | Lucas Barros | 1003 | Barbeiro e terapeuta capilar. |

Cada filial tem dois profissionais. Os quatro novos profissionais têm conta de
acesso própria (ver "Contas de acesso") e jornada cadastrada (ver "Jornada
semanal").

### Serviços (`servico`) e vínculos (`servico_profissional`)

| `id` | Nome | Filial | Duração | Preço | Intervalo | Profissionais |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| 1000 | Corte Masculino | 1000 | 30 min | R$ 45,00 | 10 min | 1000, 1001 |
| 1001 | Corte Feminino | 1000 | 60 min | R$ 80,00 | 10 min | 1000 |
| 1002 | Barba | 1000 | 30 min | R$ 35,00 | 10 min | 1001 |
| 1003 | Coloração | 1000 | 120 min | R$ 180,00 | 15 min | 1000 |
| 1004 | Corte Masculino | 1001 | 30 min | R$ 45,00 | 10 min | 1002, 1003 |
| 1005 | Manicure | 1001 | 45 min | R$ 50,00 | 10 min | 1002 |
| 1006 | Hidratação | 1001 | 40 min | R$ 70,00 | 10 min | 1002, 1003 |
| 1020 | Corte Masculino | 1002 | 30 min | R$ 45,00 | 10 min | 1004, 1005 |
| 1021 | Corte Feminino | 1002 | 60 min | R$ 85,00 | 10 min | 1004 |
| 1022 | Barba | 1002 | 30 min | R$ 35,00 | 10 min | 1005 |
| 1023 | Corte Masculino | 1003 | 30 min | R$ 45,00 | 10 min | 1006, 1007 |
| 1024 | Manicure | 1003 | 45 min | R$ 50,00 | 10 min | 1006 |
| 1025 | Hidratação | 1003 | 40 min | R$ 70,00 | 10 min | 1006, 1007 |

O índice `uk_servico_unidade_nome_normalizado` (migração `V14`) garante que o
nome é único por filial sem diferenciar maiúsculas nem espaços de borda; por
isso há "Corte Masculino" em mais de uma filial, mas nunca duplicado na mesma.

### Clientes (`cliente`)

Os três primeiros clientes correspondem às contas `CLIENTE` e ficam vinculados
por `usuario_id` (definido na seção seguinte). "Pedro Santos" e "Juliana Costa"
são clientes avulsos, sem conta, com telefone de contato, como se tivessem sido
cadastrados pela equipe.

| `id` | Nome | `usuario_id` | Telefone de contato | Origem |
| --- | --- | ---: | --- | --- |
| 1000 | Cliente Demo | 1007 | (91) 98888-0008 | Autenticado |
| 1001 | João Pereira | 1008 | (91) 98888-0009 | Autenticado |
| 1002 | Maria Oliveira | 1009 | (91) 98888-0010 | Autenticado |
| 1003 | Pedro Santos | — | (91) 97777-0003 | Avulso |
| 1004 | Juliana Costa | — | (91) 97777-0004 | Avulso |

O vínculo `cliente.usuario_id` é único e aponta para contas de perfil `CLIENTE`,
conforme a SPEC de agendamento. Clientes avulsos permanecem com `usuario_id`
nulo.

### Contas de acesso (`usuario`)

Todas as contas usam a senha `Estilo@2026`, armazenada como hash BCrypt de custo
12 (compatível com o `BCryptPasswordEncoder` da aplicação), estado `ATIVA` e
domínio fictício `@estilomarcado.dev`.

| `id` | Nome | E-mail | Perfil | Filial | Profissional |
| --- | --- | --- | --- | ---: | ---: |
| 1000 | Administrador Geral | `admin@estilomarcado.dev` | ADMINISTRADOR | 1000 | — |
| 1001 | Recepção Centro | `recepcao@estilomarcado.dev` | RECEPCAO | 1000 | — |
| 1002 | Administradora Batista | `admin.batista@estilomarcado.dev` | ADMINISTRADOR | 1001 | — |
| 1003 | Ana Souza | `ana.souza@estilomarcado.dev` | PROFISSIONAL | 1000 | 1000 |
| 1004 | Carlos Lima | `carlos.lima@estilomarcado.dev` | PROFISSIONAL | 1000 | 1001 |
| 1005 | Beatriz Rocha | `beatriz.rocha@estilomarcado.dev` | PROFISSIONAL | 1001 | 1002 |
| 1006 | Diego Mendes | `diego.mendes@estilomarcado.dev` | PROFISSIONAL | 1001 | 1003 |
| 1007 | Cliente Demo | `cliente@estilomarcado.dev` | CLIENTE | — | — |
| 1008 | João Pereira | `joao.pereira@estilomarcado.dev` | CLIENTE | — | — |
| 1009 | Maria Oliveira | `maria.oliveira@estilomarcado.dev` | CLIENTE | — | — |
| 1030 | Fernanda Alves | `fernanda.alves@estilomarcado.dev` | PROFISSIONAL | 1002 | 1004 |
| 1031 | Rafael Nunes | `rafael.nunes@estilomarcado.dev` | PROFISSIONAL | 1002 | 1005 |
| 1032 | Patrícia Gomes | `patricia.gomes@estilomarcado.dev` | PROFISSIONAL | 1003 | 1006 |
| 1033 | Lucas Barros | `lucas.barros@estilomarcado.dev` | PROFISSIONAL | 1003 | 1007 |
| 1034 | Administração Nazaré | `admin.nazare@estilomarcado.dev` | ADMINISTRADOR | 1002 | — |
| 1035 | Recepção Umarizal | `recepcao.umarizal@estilomarcado.dev` | RECEPCAO | 1003 | — |

Contas de cliente respeitam a regra de vínculo (sem filial e sem profissional);
contas internas têm filial; contas de profissional têm filial e vínculo
profissional único. As filiais do Studio Bella (1002 e 1003) têm administrador e
recepção próprios para exercitar o isolamento por filial.

### Jornada semanal (`jornada_intervalo`)

Dias no padrão ISO (1 = segunda, 7 = domingo). Cada profissional tem dois
intervalos por dia útil, separando manhã e tarde.

| Profissional | Dias | Intervalos |
| --- | --- | --- |
| Ana Souza (1000) | 1–5 | 09:00–12:00 e 13:00–18:00 |
| Carlos Lima (1001) | 2–6 | 10:00–14:00 e 15:00–19:00 |
| Beatriz Rocha (1002) | 1–5 | 08:00–12:00 e 13:00–17:00 |
| Diego Mendes (1003) | 1–5 | 10:00–13:00 e 14:00–20:00 |
| Fernanda Alves (1004) | 1–5 | 09:00–12:00 e 13:00–18:00 |
| Rafael Nunes (1005) | 2–6 | 10:00–14:00 e 15:00–19:00 |
| Patrícia Gomes (1006) | 1–5 | 08:00–12:00 e 13:00–17:00 |
| Lucas Barros (1007) | 1–5 | 10:00–13:00 e 14:00–20:00 |

### Exceções de jornada (`excecao_jornada`, `excecao_jornada_intervalo`)

| `id` | Profissional | Data | Tipo | Intervalo | Motivo |
| --- | --- | --- | --- | --- | --- |
| 1000 | 1000 | `CURRENT_DATE + 14` | JORNADA_ESPECIAL | 10:00–15:00 | Ação especial de beleza |
| 1001 | 1001 | `CURRENT_DATE + 7` | FOLGA | — | Folga programada |
| 1002 | 1002 | `CURRENT_DATE + 10` | FOLGA | — | Compensação de horas |

### Afastamentos (`afastamento`)

| `id` | Profissional | Período | Tipo | Descrição |
| --- | --- | --- | --- | --- |
| 1000 | 1003 | `CURRENT_DATE + 20` a `+25` | FERIAS | Férias programadas |
| 1001 | 1001 | `CURRENT_DATE + 30` a `+31` | LICENCA | Licença médica |

### Feriados (`feriado`)

| `id` | Filial | Data | Nome |
| --- | ---: | --- | --- |
| 1000 | 1000 | 2026-12-25 | Natal |
| 1001 | 1000 | 2027-01-01 | Confraternização Universal |
| 1002 | 1001 | 2026-12-25 | Natal |

### Bloqueios de agenda (`bloqueio_agenda`)

| `id` | Filial | Profissional | Data | Tipo | Janela | Criado por |
| --- | ---: | ---: | --- | --- | --- | ---: |
| 1000 | 1000 | (filial) | `CURRENT_DATE + 6` | Dia inteiro | — | 1000 |
| 1001 | 1000 | 1001 | `CURRENT_DATE + 4` | Parcial | 12:00–13:00 | 1000 |
| 1002 | 1001 | (filial) | `CURRENT_DATE + 9` | Dia inteiro | — | 1002 |

### Atendimentos (`atendimento`)

Estes quatorze atendimentos são o núcleo original, distribuídos nos próximos
três dias, em horários dentro da jornada de cada profissional e apenas com
serviços que ele executa. Os status cobrem `AGENDADO`, `CONFIRMADO` e
`CANCELADO`. A seção "Agenda ampliada" acrescenta histórico e novos dias para os
demais profissionais. Nenhum par de atendimentos ativos do mesmo profissional se
sobrepõe, respeitando a restrição `EXCLUDE` `ex_atendimento_ocupacao`
introduzida pela SPEC de agendamento.

Cada registro carrega os snapshots definidos pela SPEC de agendamento:
`servico_nome` e `preco_acordado` copiados do serviço; `fuso_horario_agendamento`
igual a `America/Sao_Paulo` da filial; `duracao_minutos` e `intervalo_minutos`
copiados do serviço; `criado_em` e `atualizado_em` preenchidos. O atendimento
cancelado também recebe `cancelado_em`, `cancelado_por` e `motivo_cancelamento`.

| `id` | Profissional | Serviço | Cliente | Início | Status | Preço acordado |
| --- | ---: | ---: | ---: | --- | --- | ---: |
| 1000 | 1000 | 1000 | 1000 | `CURRENT_DATE + 1` 09:00 | CONFIRMADO | R$ 45,00 |
| 1001 | 1000 | 1001 | 1001 | `CURRENT_DATE + 1` 11:00 | AGENDADO | R$ 80,00 |
| 1002 | 1000 | 1003 | 1002 | `CURRENT_DATE + 1` 13:30 | AGENDADO | R$ 180,00 |
| 1003 | 1000 | 1000 | 1003 | `CURRENT_DATE + 2` 09:30 | AGENDADO | R$ 45,00 |
| 1004 | 1000 | 1001 | 1004 | `CURRENT_DATE + 2` 15:00 | CANCELADO | R$ 80,00 |
| 1005 | 1001 | 1002 | 1001 | `CURRENT_DATE + 1` 10:30 | CONFIRMADO | R$ 35,00 |
| 1006 | 1001 | 1000 | 1000 | `CURRENT_DATE + 1` 16:00 | AGENDADO | R$ 45,00 |
| 1007 | 1001 | 1002 | 1002 | `CURRENT_DATE + 3` 11:00 | AGENDADO | R$ 35,00 |
| 1008 | 1002 | 1005 | 1003 | `CURRENT_DATE + 1` 08:30 | CONFIRMADO | R$ 50,00 |
| 1009 | 1002 | 1004 | 1004 | `CURRENT_DATE + 1` 13:30 | AGENDADO | R$ 45,00 |
| 1010 | 1002 | 1006 | 1000 | `CURRENT_DATE + 2` 10:00 | AGENDADO | R$ 70,00 |
| 1011 | 1003 | 1004 | 1001 | `CURRENT_DATE + 1` 10:00 | CONFIRMADO | R$ 45,00 |
| 1012 | 1003 | 1006 | 1002 | `CURRENT_DATE + 2` 15:00 | AGENDADO | R$ 70,00 |
| 1013 | 1003 | 1004 | 1003 | `CURRENT_DATE + 3` 17:00 | AGENDADO | R$ 45,00 |

O atendimento `1004` (cancelado) usa `cancelado_por = 1001` (recepção da Unidade
Centro) e motivo `"Cliente solicitou o cancelamento por telefone."`. Os demais
mantêm `cancelado_em`, `cancelado_por` e `motivo_cancelamento` nulos.

### Histórico de eventos (`agendamento_evento`)

A seed gera vinte eventos coerentes com o estado final dos atendimentos: um
`CRIACAO` para cada um dos quatorze atendimentos, um `CONFIRMACAO` para cada
atendimento `CONFIRMADO`, um `CANCELAMENTO` para o atendimento cancelado e um
`REAGENDAMENTO` de exemplo. O autor é a conta autenticada que criou o
atendimento (o próprio cliente, quando o cliente está vinculado) ou a recepção
da filial, quando o cliente é avulso.

| `id` | Atendimento | Autor | Tipo | Estado anterior → novo | `inicio_anterior` |
| --- | ---: | ---: | --- | --- | --- |
| 1000–1013 | 1000–1013 | cliente vinculado ou recepção | CRIACAO | — → AGENDADO | — |
| 1014 | 1000 | 1001 | CONFIRMACAO | AGENDADO → CONFIRMADO | = atual |
| 1015 | 1005 | 1001 | CONFIRMACAO | AGENDADO → CONFIRMADO | = atual |
| 1016 | 1008 | 1002 | CONFIRMACAO | AGENDADO → CONFIRMADO | = atual |
| 1017 | 1011 | 1002 | CONFIRMACAO | AGENDADO → CONFIRMADO | = atual |
| 1018 | 1004 | 1001 | CANCELAMENTO | AGENDADO → CANCELADO | = atual |
| 1019 | 1003 | 1001 | REAGENDAMENTO | AGENDADO → AGENDADO | `CURRENT_DATE + 2` 08:30 |

Para o evento `REAGENDAMENTO`, `inicio_novo` é o horário atual do atendimento
`1003` (`CURRENT_DATE + 2` 09:30) e `inicio_anterior` é o horário anterior. Os
eventos de criação usam `ocorrido_em` anterior aos de confirmação/cancelamento,
de modo que a linha do tempo seja coerente. `autor_id`, `atendimento_id` e
`estado_novo` são sempre preenchidos.

### Agenda ampliada (`atendimento`, IDs 1014–1040)

Aos quatorze atendimentos originais somam-se vinte e sete, cobrindo dias passados
(histórico do painel do cliente e relatórios operacionais) e próximos (agenda
diária/semanal/mensal do profissional). Cada linha copia os snapshots do serviço
e da filial, como os demais. As datas usam `CURRENT_DATE + N`, com `N` negativo
para o histórico.

| ID | Profissional | Serviço | Cliente | Dia | Hora | Status |
| ---: | ---: | ---: | ---: | --- | --- | --- |
| 1014 | 1000 | 1000 | 1003 | `-3` | 09:30 | CONFIRMADO |
| 1015 | 1000 | 1001 | 1001 | `-10` | 13:30 | CONFIRMADO |
| 1016 | 1001 | 1002 | 1002 | `-2` | 10:30 | CONFIRMADO |
| 1017 | 1002 | 1005 | 1004 | `-5` | 08:30 | CONFIRMADO |
| 1018 | 1003 | 1004 | 1000 | `-2` | 10:00 | CONFIRMADO |
| 1019 | 1004 | 1021 | 1000 | `-12` | 14:00 | CONFIRMADO |
| 1020 | 1004 | 1021 | 1000 | `+1` | 09:00 | CONFIRMADO |
| 1021 | 1004 | 1020 | 1001 | `+1` | 10:30 | AGENDADO |
| 1022 | 1004 | 1021 | 1002 | `+1` | 14:00 | AGENDADO |
| 1023 | 1004 | 1020 | 1003 | `+2` | 11:00 | AGENDADO |
| 1024 | 1005 | 1022 | 1003 | `-6` | 15:30 | CONFIRMADO |
| 1025 | 1005 | 1020 | 1000 | `-6` | 16:30 | CONFIRMADO |
| 1026 | 1005 | 1022 | 1001 | `+1` | 10:30 | AGENDADO |
| 1027 | 1005 | 1020 | 1000 | `+1` | 15:00 | CONFIRMADO |
| 1028 | 1005 | 1020 | 1002 | `+2` | 10:00 | AGENDADO |
| 1029 | 1006 | 1025 | 1002 | `-4` | 08:30 | CONFIRMADO |
| 1030 | 1006 | 1024 | 1003 | `-4` | 13:30 | CONFIRMADO |
| 1031 | 1006 | 1024 | 1002 | `+1` | 08:30 | CONFIRMADO |
| 1032 | 1006 | 1023 | 1003 | `+1` | 10:00 | AGENDADO |
| 1033 | 1006 | 1025 | 1000 | `+1` | 13:30 | AGENDADO |
| 1034 | 1006 | 1024 | 1001 | `+2` | 09:00 | AGENDADO |
| 1035 | 1007 | 1025 | 1003 | `-3` | 14:00 | CONFIRMADO |
| 1036 | 1007 | 1023 | 1000 | `-3` | 17:00 | CONFIRMADO |
| 1037 | 1007 | 1023 | 1001 | `+1` | 10:30 | AGENDADO |
| 1038 | 1007 | 1025 | 1000 | `+1` | 14:30 | AGENDADO |
| 1039 | 1007 | 1023 | 1002 | `+2` | 16:00 | AGENDADO |
| 1040 | 1002 | 1005 | 1002 | `+1` | 15:00 | AGENDADO (encaixe) |

O atendimento `1040` é o resultado do encaixe da lista de espera `1002`: nasce
`AGENDADO` com `lista_espera_id = 1002` e é contado como encaixe nos relatórios.
Nenhum par de atendimentos ativos do mesmo profissional se sobrepõe.

Para cada atendimento de 1014 a 1040 a seed gera um evento `CRIACAO`; os
`CONFIRMADO` recebem também um `CONFIRMACAO`. O autor é o usuário do cliente
vinculado (`1007`, `1008` ou `1009`) quando existe, ou um administrador/recepção
da filial para clientes avulsos. O `ocorrido_em` fica próximo do atendimento e
nunca no futuro, de modo que os indicadores diários dos relatórios façam sentido.

### Lista de espera e ofertas (`lista_espera`, `lista_espera_evento`, `lista_espera_oferta`)

Cinco solicitações cobrem todos os estados. A unicidade de solicitação ativa
(`usuario_id`, `unidade_id`, `servico_id`) é respeitada.

| ID | Usuário | Filial | Serviço | Profissional | Janela | Status |
| ---: | ---: | ---: | ---: | ---: | --- | --- |
| 1000 | 1007 | 1000 | 1000 | 1000 | `+2` a `+9`, 09:00–12:00 | ATIVA |
| 1001 | 1008 | 1000 | 1001 | — | `+3` a `+10` | ATIVA |
| 1002 | 1009 | 1001 | 1005 | 1002 | `+1` a `+7` | ATENDIDA |
| 1003 | 1007 | 1000 | 1002 | 1001 | `-5` a `-1` | EXPIRADA |
| 1004 | 1009 | 1001 | 1006 | 1003 | `+4` a `+11` | CANCELADA |

Cada solicitação tem seus eventos em `lista_espera_evento` (`ATIVA`, `ATENDIDA`,
`EXPIRADA`, `CANCELADA`), com autor quando aplicável. As ofertas de encaixe
cobrem os estados `ENVIADA`, `ACEITA`, `EXPIRADA` e `INDISPONIVEL`:

| ID | Solicitação | Profissional | Início | Status |
| ---: | ---: | ---: | --- | --- |
| 1000 | 1000 | 1000 | `+3` 09:00 | ENVIADA |
| 1001 | 1000 | 1000 | `+4` 09:00 | INDISPONIVEL |
| 1002 | 1001 | 1000 | `+4` 11:00 | EXPIRADA |
| 1003 | 1002 | 1002 | `+1` 15:00 | ACEITA |

A oferta `1003`, aceita, corresponde ao atendimento `1040` (`+1` 15:00). A oferta
`1000`, ainda `ENVIADA`, usa `expira_em` futuro e coerente com a preferência para
que o agendador de lista de espera não a invalide de imediato.

### Notificações (`notificacao_preferencia`, `notificacao_interna`, `notificacao_outbox`)

Preferências para as contas de cliente, incluindo casos que desligam lembretes ou
avisos de lista:

| Usuário | `lembretes` | `avisos_lista` |
| ---: | --- | --- |
| 1007 | Ligado | Ligado |
| 1008 | Desligado | Ligado |
| 1009 | Ligado | Desligado |

Notificações internas, algumas já lidas e outras não, cobrindo `CRIACAO`,
`CONFIRMACAO`, `CANCELAMENTO`, `LEMBRETE` e `OFERTA`, com `referencia_tipo`
`AGENDAMENTO` ou `OFERTA` e `dedupe_key` único.

Itens de outbox em estados distintos:

| ID | Usuário | Tipo | Referência | Status |
| ---: | ---: | --- | --- | --- |
| 1000 | 1007 | CONFIRMACAO | Agendamento 1000 | ENVIADO |
| 1001 | 1007 | LEMBRETE | Agendamento 1000 | PENDENTE |
| 1002 | 1008 | CRIACAO | Agendamento 1001 | ENVIADO |
| 1003 | 1009 | OFERTA | Oferta 1003 | ENVIADO |
| 1004 | 1009 | CANCELAMENTO | Agendamento 1004 | CANCELADO |
| 1005 | 1007 | OFERTA | Oferta 1000 | PENDENTE |

Os itens `PENDENTE` usam `enviar_apos` futuro, para que o agendador de
notificações não os processe durante a demonstração; o envio real permanece
responsabilidade da aplicação.

## Invariantes respeitadas

- `usuario`: coerência de perfil e vínculo (cliente sem filial/profissional;
  profissional com ambos; recepção e administrador só com filial) e unicidade de
  e-mail normalizado e de vínculo profissional.
- `unidade`: no máximo uma filial principal por estabelecimento, nome
  normalizado preenchido e unicidade por estabelecimento.
- `servico`: duração positiva, preço não negativo, intervalo não negativo e
  nome único por filial.
- `jornada_intervalo`: dia da semana entre 1 e 7 e `hora_inicio < hora_fim`.
- `excecao_jornada`: tipo válido, uma por profissional/data; `JORNADA_ESPECIAL`
  possui intervalos e `FOLGA` não.
- `afastamento`: `data_inicio <= data_fim` e tipo válido.
- `feriado`: unicidade por filial/data.
- `bloqueio_agenda`: coerência entre `dia_inteiro` e os horários, com `criado_por`
  apontando para uma conta da mesma filial.
- `cliente`: `usuario_id` único, quando presente, apontando para conta de perfil
  `CLIENTE`; `telefone_contato` de até 20 caracteres.
- `atendimento`: duração e intervalo positivos/não negativos, status válido,
  referências existentes, snapshots `servico_nome`, `preco_acordado` e
  `fuso_horario_agendamento` preenchidos, e nenhuma sobreposição entre
  atendimentos `AGENDADO`/`CONFIRMADO` do mesmo profissional (restrição
  `EXCLUDE ex_atendimento_ocupacao`). O atendimento `CANCELADO` não ocupa a
  agenda.
- `agendamento_evento`: `atendimento_id` e `autor_id` existentes, `tipo`
  coerente com a transição, `estado_novo` preenchido e `inicio_novo` igual ao
  horário resultante da ação.
- `lista_espera`: status válido (`ATIVA`, `ATENDIDA`, `CANCELADA`, `EXPIRADA`),
  `data_inicio <= data_fim`, coerência entre `hora_inicio`/`hora_fim` e no
  máximo uma solicitação `ATIVA` por `(usuario_id, unidade_id, servico_id)`.
- `lista_espera_evento` e `lista_espera_oferta`: referências existentes,
  `estado_novo`/`status` válidos e unicidade de
  `(lista_espera_id, profissional_id, inicio)` nas ofertas.
- `atendimento.lista_espera_id`, quando presente, aponta para a solicitação que
  originou o encaixe.
- `notificacao_preferencia`: uma linha por usuário.
- `notificacao_interna` e `notificacao_outbox`: `dedupe_key` único,
  `estado`/`status` válido e referências coerentes com o agendamento ou a
  oferta citada.

## Critérios de aceitação

- Em um banco recém-migrado, a seed insere exatamente 2 estabelecimentos, 4
  filiais, 8 profissionais, 13 serviços, 19 vínculos serviço–profissional, 5
  clientes, 16 contas, 80 intervalos de jornada, 3 exceções, 2 afastamentos, 3
  feriados, 3 bloqueios, 41 atendimentos, 62 eventos de agendamento, 5
  solicitações de lista de espera, 8 eventos de lista de espera, 4 ofertas, 3
  preferências de notificação, 6 notificações internas e 6 itens de outbox.
- A agenda cobre dias passados e futuros; os atendimentos `CONFIRMADO` no passado
  aparecem no histórico do painel do cliente e nos relatórios, e os futuros
  alimentam a agenda em calendário do profissional.
- O atendimento de encaixe `1040` referencia a solicitação `1002` (`ATENDIDA`) e
  aparece como encaixe no relatório operacional.
- A lista de espera cobre os estados `ATIVA`, `ATENDIDA`, `EXPIRADA` e
  `CANCELADA`, e as ofertas cobrem `ENVIADA`, `ACEITA`, `EXPIRADA` e
  `INDISPONIVEL`, sem violar a unicidade de solicitação ativa nem de oferta.
- Os três clientes vinculados apontam para as contas `CLIENTE` corretas e os
  avulsos têm `usuario_id` nulo; nenhum `usuario_id` é reutilizado.
- Todo atendimento tem `servico_nome`, `preco_acordado` e
  `fuso_horario_agendamento` preenchidos e coerentes com o serviço e a filial; o
  único atendimento cancelado tem os campos de cancelamento preenchidos e os
  demais os mantêm nulos.
- O histórico contém um `CRIACAO` por atendimento, os quatro `CONFIRMACAO`, um
  `CANCELAMENTO` e um `REAGENDAMENTO`, sem violar as chaves estrangeiras.
- Nenhum par de atendimentos `AGENDADO`/`CONFIRMADO` do mesmo profissional se
  sobrepõe, de modo que a restrição `ex_atendimento_ocupacao` é satisfeita.
- Executar a seed duas vezes seguidas não altera nenhuma contagem.
- Executar a seed em um banco que já contém dados reais não colide, não
  sobrescreve nem interfere nesses dados.
- A opção de reset esvazia todas as tabelas de domínio e preserva
  `flyway_schema_history`, `spring_session` e `spring_session_attributes`;
  executar a seed em seguida recria o conjunto completo.
- Uma conta mock autentica com sucesso pela API
  (`POST /api/autenticacao/sessoes`) usando a senha `Estilo@2026`, retornando o
  perfil e os vínculos corretos.
- Os executores encerram com mensagem clara quando o Docker não existe ou o
  container não está em execução, e com código de saída diferente de zero quando
  o SQL falha.
- O `README.md` descreve os comandos, a opção de reset e as contas mock.

## Decisões

- A seed fica em `scripts/` (e não em `src/backend/.../db/migration/`) para não
  ser executada automaticamente pelo Flyway e permanecer restrita ao
  desenvolvimento local.
- Optou-se por uma faixa de IDs reservada e um estabelecimento dedicado em vez de
  `TRUNCATE` por padrão, para que a semeadura seja segura em bancos já usados.
- A senha única e pública é aceitável por se tratar de contas fictícias de
  desenvolvimento; nenhuma conta real é criada ou alterada.
- Os scripts dependem apenas do container PostgreSQL em execução, sem exigir o
  backend, o frontend, pgAdmin ou Mailpit.
- Datas relativas (`CURRENT_DATE + N`) mantêm a agenda, as folgas e os bloqueios
  sempre coerentes com o dia da execução.
- A seed não cria tokens, convites nem eventos de auditoria, pois esses fluxos
  pertencem à aplicação e seriam ruído no ambiente de desenvolvimento.
- Os eventos de `agendamento_evento` são populados porque o histórico faz parte
  do que a interface de agendamento exibe; eles são derivados do estado final dos
  atendimentos, não executados pelos serviços da aplicação.
- `cliente` e `atendimento` convergem via `ON CONFLICT (id) DO UPDATE` das
  colunas de agendamento, para que um banco já semeado por uma versão anterior
  receba os snapshots, o vínculo de cliente e os dados de cancelamento sem exigir
  reset nem apagar dados reais.
- O atendimento `CANCELADO` foi mantido de propósito: ele não ocupa a agenda
  (não entra na restrição `EXCLUDE`) e serve para exercitar o histórico e a
  liberação de horário.
- Um segundo estabelecimento com filiais próprias foi adicionado para exercitar
  o isolamento por filial, a página pública de cada unidade e os relatórios sem
  depender de dados reais.
- A agenda inclui dias passados de propósito, pois o painel do cliente e os
  relatórios operacionais dependem de histórico; sem isso, as telas só teriam
  estados vazios ou poucos dias.
- Os estados de lista de espera, ofertas e notificações são representados
  diretamente no banco (sem chamar os serviços) e usam prazos futuros quando
  `PENDENTE`/`ENVIADA`, para que os agendadores não alterem a demonstração.
- Datas do histórico continuam relativas (`CURRENT_DATE - N`) para que a seed
  não envelheça; ao rodar em outro dia, a janela de relatórios acompanha.

## Relação com outras SPECs

- Depende de `2026-09-30-ambiente-de-desenvolvimento.md` para o Docker Compose,
  o container PostgreSQL e as portas locais.
- Pressupõe o schema criado por todas as migrações Flyway até
  `V14__normalizar_unicidade_servico.sql`, incluindo
  `2026-09-30-autenticacao-e-controle-de-acesso.md`,
  `2026-09-30-estabelecimentos-filiais-profissionais-e-permissoes.md`,
  `2026-09-30-catalogo-de-servicos.md`, `2026-09-30-painel-profissional.md`,
  `2026-10-01-jornada-folgas-feriados-e-bloqueios.md`,
  `2026-10-01-agendamento-confirmacao-cancelamento-e-reagendamento.md` e
  `2026-10-01-lista-de-espera-encaixes-notificacoes-historico-e-relatorios-operacionais.md`
  (migração `V13`).
- A parte de agendamento respeita os snapshots, o vínculo `cliente.usuario_id`, o
  histórico `agendamento_evento` e a restrição `EXCLUDE` definidos em
  `2026-10-01-agendamento-confirmacao-cancelamento-e-reagendamento.md`, mas não
  exercita o fluxo de escrita dessa SPEC.
- A lista de espera, as ofertas e as notificações respeitam os estados e as
  invariantes de
  `2026-10-01-lista-de-espera-encaixes-notificacoes-historico-e-relatorios-operacionais.md`,
  sem executar os agendadores.
- Serve de insumo para exercitar `2026-10-01-motor-de-disponibilidade.md`, o
  painel profissional, a agenda em calendário de
  `2026-10-01-agenda-diaria-e-semanal-do-profissional.md`, o painel do cliente de
  `2026-10-01-painel-do-cliente.md`, a agenda operacional e "Meus agendamentos"
  da SPEC de agendamento, além da navegação definida em
  `2026-10-01-navegacao-visual-e-meu-perfil.md`.
- Não altera o contrato HTTP nem regras de negócio: apenas popula dados válidos.

## Rastreabilidade

Toda alteração derivada desta spec deve ser commitada com o trailer:

```text
Spec: SPEC/2026-10-01-seed-de-dados-mock.md
```
