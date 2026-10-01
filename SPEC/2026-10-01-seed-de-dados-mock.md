# Seed de dados mock para desenvolvimento (2026-10-01)

## O que é e por que existe

A seed de dados mock popula o banco de desenvolvimento do Estilo Marcado com um
conjunto pequeno, coerente e realista de filiais, profissionais, serviços,
contas, vínculos entre clientes e contas, jornadas, folgas, feriados, bloqueios,
atendimentos e o histórico de eventos de agendamento. Sem ela, cada desenvolvedor
precisa cadastrar tudo manualmente pela interface antes de conseguir exercitar
catálogo, disponibilidade, painel profissional, agendamento e login.

Esta SPEC define **o que a seed contém**, **quais invariantes ela respeita** e
**como executá-la** depois de subir o ambiente com Docker Compose. Ela é um
recurso de apoio ao desenvolvimento: não faz parte do fluxo de produção e não
substitui as migrações Flyway, que continuam sendo a única autoridade do schema.

A parte de agendamento foi incorporada depois de a SPEC de agendamento e a
migração `V12__agendamentos.sql` existirem: a seed passou a preencher vínculo
`cliente.usuario_id`, snapshots comerciais e de ocupação do atendimento, dados de
cancelamento e as linhas de `agendamento_evento` que a interface de histórico
consome.

## Escopo

- Um arquivo SQL de seed versionado no repositório.
- Um script de execução para Windows/PowerShell e um para Linux/macOS.
- Dados mock de: estabelecimento, filiais (`unidade`), profissionais, serviços,
  vínculos serviço–profissional, contas de acesso (`usuario`), fichas de cliente
  (`cliente`), jornada semanal, exceções de jornada, afastamentos, feriados,
  bloqueios de agenda e atendimentos.
- Parte de agendamento: vínculo de clientes autenticados a contas
  (`cliente.usuario_id`), telefone de contato de clientes avulsos, snapshots
  comerciais e de ocupação do atendimento (`servico_nome`, `preco_acordado`,
  `duracao_minutos`, `intervalo_minutos`, `fuso_horario_agendamento`), instantes
  de criação/atualização e dados de cancelamento (`cancelado_em`,
  `cancelado_por`, `motivo_cancelamento`) e o histórico de eventos
  (`agendamento_evento`) de criação, confirmação, cancelamento e reagendamento.
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
   `1000+` e o estabelecimento `Estilo Marcado (Mock)`. Assim a seed não colide
   com dados reais ou criados manualmente no ambiente de desenvolvimento.
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
- Ao final, `setval(pg_get_serial_sequence(...))` reposiciona cada sequência
  para o maior `id` presente, evitando colisões em inserções futuras feitas pela
  aplicação.

## Reset

A opção de reset executa, em uma única instrução, o `TRUNCATE` das tabelas de
domínio com `RESTART IDENTITY CASCADE`, na seguinte relação:

```text
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

### Estabelecimento e filiais (`estabelecimento`, `unidade`)

| `estabelecimento.id` | Nome |
| --- | --- |
| 1000 | Estilo Marcado (Mock) |

| `unidade.id` | Nome | Principal | Estabelecimento | Endereço | Telefone |
| --- | --- | --- | --- | --- | --- |
| 1000 | Unidade Centro | Sim | 1000 | Av. Presidente Vargas, 1200 - Belém/PA | (91) 3222-1000 |
| 1001 | Unidade Batista Campos | Não | 1000 | Rua dos Mundurucus, 2450 - Belém/PA | (91) 3222-2000 |

Ambas usam `America/Sao_Paulo` e estão ativas.

### Profissionais (`profissional`)

| `id` | Nome | Filial | Apresentação |
| --- | --- | --- | --- |
| 1000 | Ana Souza | 1000 | Especialista em cortes e coloração. |
| 1001 | Carlos Lima | 1000 | Barbeiro e especialista em barba. |
| 1002 | Beatriz Rocha | 1001 | Cabeleireira e manicure. |
| 1003 | Diego Mendes | 1001 | Barbeiro e designer de sobrancelha. |

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

Contas de cliente respeitam a regra de vínculo (sem filial e sem profissional);
contas internas têm filial; contas de profissional têm filial e vínculo
profissional único.

### Jornada semanal (`jornada_intervalo`)

Dias no padrão ISO (1 = segunda, 7 = domingo). Cada profissional tem dois
intervalos por dia útil, separando manhã e tarde.

| Profissional | Dias | Intervalos |
| --- | --- | --- |
| Ana Souza (1000) | 1–5 | 09:00–12:00 e 13:00–18:00 |
| Carlos Lima (1001) | 2–6 | 10:00–14:00 e 15:00–19:00 |
| Beatriz Rocha (1002) | 1–5 | 08:00–12:00 e 13:00–17:00 |
| Diego Mendes (1003) | 1–5 | 10:00–13:00 e 14:00–20:00 |

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

Quatorze atendimentos distribuídos nos próximos três dias, em horários dentro da
jornada de cada profissional e apenas com serviços que ele executa. Os status
cobrem `AGENDADO`, `CONFIRMADO` e `CANCELADO`. Nenhum par de atendimentos ativos
do mesmo profissional se sobrepõe, respeitando a restrição `EXCLUDE`
`ex_atendimento_ocupacao` introduzida pela SPEC de agendamento.

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

## Critérios de aceitação

- Em um banco recém-migrado, a seed insere exatamente 1 estabelecimento, 2
  filiais, 4 profissionais, 7 serviços, 10 vínculos serviço–profissional, 5
  clientes, 10 contas, 40 intervalos de jornada, 3 exceções, 2 afastamentos, 3
  feriados, 3 bloqueios, 14 atendimentos e 20 eventos de agendamento.
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

## Relação com outras SPECs

- Depende de `2026-09-30-ambiente-de-desenvolvimento.md` para o Docker Compose,
  o container PostgreSQL e as portas locais.
- Pressupõe o schema criado por todas as migrações Flyway até
  `V12__agendamentos.sql`, incluindo
  `2026-09-30-autenticacao-e-controle-de-acesso.md`,
  `2026-09-30-estabelecimentos-filiais-profissionais-e-permissoes.md`,
  `2026-09-30-catalogo-de-servicos.md`, `2026-09-30-painel-profissional.md`,
  `2026-10-01-jornada-folgas-feriados-e-bloqueios.md` e
  `2026-10-01-agendamento-confirmacao-cancelamento-e-reagendamento.md`.
- A parte de agendamento respeita os snapshots, o vínculo `cliente.usuario_id`, o
  histórico `agendamento_evento` e a restrição `EXCLUDE` definidos em
  `2026-10-01-agendamento-confirmacao-cancelamento-e-reagendamento.md`, mas não
  exercita o fluxo de escrita dessa SPEC.
- Serve de insumo para exercitar `2026-10-01-motor-de-disponibilidade.md`, o
  painel profissional, a agenda operacional e "Meus agendamentos" da SPEC de
  agendamento, além da navegação definida em
  `2026-10-01-navegacao-visual-e-meu-perfil.md`.
- Não altera o contrato HTTP nem regras de negócio: apenas popula dados válidos.

## Rastreabilidade

Toda alteração derivada desta spec deve ser commitada com o trailer:

```text
Spec: SPEC/2026-10-01-seed-de-dados-mock.md
```
