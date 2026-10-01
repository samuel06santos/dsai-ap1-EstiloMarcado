# Catálogo de serviços (2026-09-30; revisada em 2026-10-01)

## O que é e por que existe

Cada filial mantém seu próprio catálogo de atendimentos de salão ou barbearia.
Um serviço informa ao cliente o que será feito, por quanto tempo e por qual
valor; sua duração e seu intervalo posterior alimentam o motor de
disponibilidade. Não há lista fixa de modalidades: corte, barba, coloração e
outros atendimentos são cadastrados conforme a realidade de cada filial.

## Dados e regras

- Todo serviço pertence a exatamente uma filial, vínculo imutável. O
  identificador permanece estável quando o serviço é editado ou desativado.
- Nome obrigatório de 2 a 120 caracteres depois de aparar espaços nas pontas;
  descrição opcional de até 1000 caracteres. Nomes equivalentes após aparar
  espaços e ignorar maiúsculas/minúsculas são únicos na filial, inclusive entre
  serviços desativados; filiais diferentes podem repetir nomes.
- Duração obrigatória em minutos inteiros positivos. Preço obrigatório, em
  reais, maior ou igual a zero e com até duas casas decimais. Intervalo após o
  atendimento é opcional, em minutos inteiros não negativos; ausente equivale
  a zero no cálculo de disponibilidade.
- A administração da própria filial pode criar, editar e ativar/desativar
  serviços. Nome, descrição, duração, preço, intervalo e habilitações podem
  ser corrigidos sem trocar o ID ou a filial. A lista administrativa inclui
  serviços ativos e inativos, mesmo sem profissionais habilitados.
- Um serviço pode ser preparado sem profissional habilitado. `ativo` expressa
  a intenção administrativa de oferecer o serviço; ele só é **agendável** se
  também houver ao menos um profissional ativo e habilitado da mesma filial,
  e a filial estiver ativa. A ausência de profissional não torna o cadastro
  inválido, mas o serviço não aparece no catálogo agendável.
- Apenas profissionais da própria filial podem ser habilitados. Uma associação
  existente com profissional inativado permanece para eventual reativação,
  mas ele não aparece na visão pública nem gera horários.
- A visão pública lista somente serviços agendáveis e mostra apenas
  profissionais ativos. Consulta pública por ID não revela um serviço
  desativado ou uma filial inativa. A consulta administrativa completa requer
  administrador da própria filial; alterar parâmetros ou IDs não amplia acesso.
- Alterações e desativação não apagam agendamentos existentes: eles preservam
  nome, preço e duração/intervalo acordados no momento da reserva, conforme as
  SPECs de agendamento e disponibilidade. Não há exclusão física de serviço
  nesta versão.

## Contrato e interface

- `GET /api/unidades/{unidadeId}/servicos` retorna o catálogo público
  agendável por padrão; `somenteDisponiveis=false` retorna a lista completa
  apenas ao administrador da filial. A ordem é determinística por nome e ID.
- `GET /api/servicos/{id}` retorna dados públicos se o serviço for agendável;
  caso contrário, apenas o administrador da filial pode consultá-lo.
- `POST /api/unidades/{unidadeId}/servicos`, `PUT /api/servicos/{id}` e
  `PATCH /api/servicos/{id}/ativar|desativar` exigem administrador da filial e
  proteção CSRF. Criação retorna `201`; corpo inválido retorna `400`, nome
  equivalente já usado retorna `409`, recurso não encontrado ou oculto
  retorna `404`, falta de sessão `401` e acesso indevido `403`.
- Na página administrativa da filial, mostrar lista com nome, duração, preço,
  intervalo, estado e profissionais habilitados; permitir criar, editar,
  ativar/desativar e selecionar profissionais da filial. Sinalizar claramente
  quando um serviço ativo ainda não pode ser agendado por falta de
  profissional ativo. Formulários exibem validações e retorno de erro/sucesso.
- Na página pública da filial, mostrar nome, descrição, duração e preço dos
  serviços agendáveis antes da escolha de profissional e horário.

## Critérios de aceitação e testes

- Cadastro aceita serviço sem profissional como preparação, mas ele não aparece
  publicamente. Ao associar profissional ativo, passa a aparecer se ativo.
- Inativar o último profissional ou o serviço retira-o da visão pública sem
  apagar dados; reativação restaura a visibilidade quando todas as condições
  voltarem a ser satisfeitas.
- Nomes com espaços nas pontas são armazenados aparados; variações de caixa ou
  espaços não permitem duplicata na mesma filial, inclusive sob concorrência.
- Dados inválidos de nome, duração, preço e intervalo são rejeitados; não se
  associam profissionais de outra filial. A lista administrativa é protegida
  por perfil e filial, e a pública nunca expõe profissionais inativos.
- Testes de integração cobrem persistência, unicidade, permissões, visibilidade
  pública, estado e preservação dos dados históricos. Testes de interface
  cobrem criação/edição, estado vazio, indisponibilidade e mensagens de erro.

## Fora do escopo

- Promoções, descontos, pacotes, pagamento, comissões ou preço por profissional.
- Recursos compartilhados, múltiplos profissionais para um mesmo atendimento
  e serviços em sequência.
- Jornada, cálculo dos horários e regras de conflito, definidos nas SPECs
  `2026-10-01-jornada-folgas-feriados-e-bloqueios.md`,
  `2026-10-01-motor-de-disponibilidade.md` e
  `2026-10-01-protecao-contra-reservas-concorrentes-e-sobreposicao.md`.

## Relação com outras SPECs

- Filial, profissional e escopo administrativo seguem
  `2026-09-30-estabelecimentos-filiais-profissionais-e-permissoes.md`.
- Agendamentos e seus valores históricos seguem
  `2026-10-01-agendamento-confirmacao-cancelamento-e-reagendamento.md`.
