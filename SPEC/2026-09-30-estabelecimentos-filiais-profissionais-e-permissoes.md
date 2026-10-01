# Estabelecimentos, filiais, profissionais e permissões (2026-09-30)

## O que é e por que existe

Um estabelecimento reúne uma ou mais filiais. Cada filial oferece seus próprios
serviços, possui sua equipe e opera uma agenda isolada. Este módulo permite
identificar a filial correta, manter seus dados e profissionais e aplicar as
permissões administrativas sem misturar informações de filiais diferentes.

No modelo atual, a tabela e as rotas chamadas `unidade` representam uma filial.
Esta SPEC preserva esse nome técnico para não quebrar os vínculos existentes.
`estabelecimento` passa a ser a entidade que agrupa as unidades; não é sinônimo
de `unidade`.

## Escopo e decisões de acesso

- Um estabelecimento possui uma filial principal e pode possuir outras filiais.
- Cada filial pertence a exatamente um estabelecimento. Serviços, profissionais,
  contas internas e futuros agendamentos continuam pertencendo a uma filial.
- Uma conta interna mantém um único perfil e uma única filial, conforme a SPEC
  de autenticação. Não haverá conta com acesso operacional a várias filiais nem
  perfil global nesta versão.
- O administrador da filial principal pode atualizar somente os dados comuns
  do estabelecimento, ver um resumo das filiais (ID, nome e estado) e criar
  outra filial do mesmo estabelecimento. Ao criar a filial, informa
  obrigatoriamente nome e e-mail de seu primeiro administrador; a nova conta
  recebe convite para definir a senha. A operação inteira é atômica: não pode
  restar filial sem administrador convidado.
- A leitura desse resumo, a criação de filial e o convite do seu primeiro
  administrador são as exceções explícitas à regra de administração restrita
  à própria unidade na SPEC de autenticação. Essas exceções não dão ao
  administrador principal acesso posterior a usuários, serviços,
  profissionais ou agendas da nova filial.
- A criação do primeiro estabelecimento, da filial principal e de seu primeiro
  administrador ocorre por provisionamento controlado na implantação, com
  convite e sem senha padrão. Não há cadastro público de estabelecimentos.

As demais regras de sessão, CSRF, estados de conta, auditoria e respostas
`401`/`403` da SPEC de autenticação continuam válidas.

## Dados e regras do estabelecimento

- Possui identificador imutável, nome de exibição obrigatório de 2 a 120
  caracteres, filial principal e datas de criação e atualização.
- O administrador da filial principal pode alterar o nome de exibição. Isso
  não altera os nomes, serviços nem agendas das filiais.
- A filial principal é definida no provisionamento e não pode ser trocada nesta
  versão. A exclusão física de estabelecimentos não é oferecida.
- Os dados administrativos de um estabelecimento só podem ser consultados por
  contas internas de suas filiais. A API pública não oferece busca geral entre
  estabelecimentos, coerentemente com a ausência de marketplace nesta versão.

## Dados e ciclo de vida da filial (`unidade`)

- Possui identificador imutável, `estabelecimento_id`, nome obrigatório de 2 a
  120 caracteres, endereço resumido opcional de até 250 caracteres, telefone
  opcional de até 30 caracteres, fuso horário IANA obrigatório e estado
  `ATIVA` ou `INATIVA`. O fuso inicial é `America/Sao_Paulo` quando não
  informado no provisionamento; nas rotas de criação e edição, o valor enviado
  deve ser validado como identificador IANA.
- O nome da filial é único dentro do estabelecimento após remover espaços nas
  extremidades e comparar sem distinção entre maiúsculas e minúsculas. Filiais
  de estabelecimentos diferentes podem ter o mesmo nome.
- Somente o administrador vinculado à própria filial pode editar seus dados e
  ativá-la ou desativá-la. O administrador da filial principal não ganha esse
  poder sobre outra filial.
- Uma filial nova começa `INATIVA` e só passa a aparecer publicamente depois
  que seu administrador concluir o convite e ativá-la.
- Uma filial inativa permanece no banco e no histórico, mas não aparece na
  consulta pública de filiais e não oferece novos horários ou agendamentos.
  Sua equipe não pode operar recursos da filial enquanto ela estiver inativa;
  o administrador pode consultar e corrigir seus dados para reativá-la.
- A desativação retorna `409 Conflict` se houver agendamentos futuros ativos.
  Esses agendamentos devem ser resolvidos pelo fluxo próprio antes da
  desativação. A filial principal não pode ser desativada enquanto existirem
  outras filiais ativas, pois não há transferência da condição de principal.
- Não existe exclusão física ou transferência de filial entre estabelecimentos
  nesta versão.

## Dados e ciclo de vida do profissional

- Um profissional possui identificador imutável, `unidade_id`, nome obrigatório
  de 2 a 120 caracteres, apresentação pública opcional de até 500 caracteres,
  estado ativo/inativo e datas de criação e atualização.
- O cadastro do profissional não cria uma conta de acesso. Se precisar entrar
  no sistema, o administrador da mesma filial envia um convite pelo fluxo de
  contas internas e vincula essa conta ao profissional. Um profissional só
  pode estar vinculado a uma conta, e o vínculo deve ser da mesma filial.
- Nome de exibição não é identificador único; dois profissionais da mesma
  filial podem ter o mesmo nome. Nenhum dado de contato pessoal ou credencial
  aparece na consulta pública.
- Somente o administrador da própria filial pode criar, editar, ativar ou
  desativar profissionais. A filial do profissional não pode ser alterada.
- Profissional inativo não aparece nas consultas públicas nem na
  disponibilidade, mesmo que continue associado a serviços. Contas vinculadas
  perdem imediatamente o acesso à agenda profissional, e suas sessões são
  invalidadas; a reativação não restaura uma conta bloqueada ou desativada.
- A desativação retorna `409 Conflict` se houver agendamentos futuros ativos
  para esse profissional. O registro e as associações com serviços permanecem
  para preservar o histórico.

## Matriz de permissões

`Administrador principal` significa uma conta `ADMINISTRADOR` vinculada à
filial principal. A coluna `Administrador da filial` inclui o principal quando
ele age sobre sua própria filial.

| Ação | Público/cliente | Profissional/recepção | Administrador da filial | Administrador principal |
| --- | --- | --- | --- | --- |
| Ver dados públicos de filial ativa e profissionais ativos | Sim | Sim | Sim | Sim |
| Ver dados comuns do próprio estabelecimento | Não | Sim, somente leitura | Sim | Sim |
| Ver resumo de todas as filiais do estabelecimento | Não | Não | Não | Sim |
| Alterar nome do estabelecimento | Não | Não | Não | Sim |
| Criar filial no próprio estabelecimento com primeiro administrador | Não | Não | Não | Sim |
| Consultar e editar filial, inclusive inativa | Não | Não | Sim, somente a própria | Somente a própria |
| Criar e manter profissionais | Não | Não | Sim, somente na própria filial | Somente na própria filial |
| Convidar e administrar contas internas | Não | Não | Sim, somente na própria filial | Somente na própria filial, salvo o convite inicial acima |
| Operar serviços e agendas | Conforme SPECs específicas | Conforme perfil e própria filial | Somente na própria filial | Somente na própria filial |

Um usuário não pode ampliar o escopo alterando IDs, `estabelecimento_id`,
`unidade_id` ou perfil no corpo da requisição. O backend obtém identidade e
escopo da sessão e verifica também a relação entre estabelecimento, filial e
recurso antes de consultar ou alterar dados. Para IDs fora do escopo, pode
responder `404 Not Found` para não revelar sua existência.

## Contrato HTTP inicial

As respostas são JSON e não expõem dados de autenticação. Os endpoints de
escrita usam a proteção CSRF da SPEC de autenticação.

| Método e rota | Acesso | Resultado |
| --- | --- | --- |
| `GET /api/unidades/{unidadeId}/publico` | Público | Dados públicos de filial ativa; `404` se inativa ou inexistente. |
| `GET /api/unidades/{unidadeId}/profissionais` | Público | Lista profissionais ativos da filial ativa. |
| `GET /api/unidades/{unidadeId}/profissionais/{id}` | Público | Profissional ativo da filial ativa; `404` caso contrário. |
| `GET /api/estabelecimentos/{id}` | Conta interna do estabelecimento | Dados comuns; inclui resumo das filiais somente para o administrador principal. |
| `PATCH /api/estabelecimentos/{id}` | Administrador principal | Atualiza nome do estabelecimento. |
| `POST /api/estabelecimentos/{id}/unidades` | Administrador principal | Cria filial e primeiro administrador pendente; `201 Created`. |
| `GET /api/unidades/{unidadeId}` | Administrador da própria filial | Dados internos, inclusive se inativa. |
| `PATCH /api/unidades/{unidadeId}` | Administrador da própria filial | Atualiza dados e estado da filial. |
| `POST /api/unidades/{unidadeId}/profissionais` | Administrador da própria filial | Cria profissional; `201 Created`. |
| `GET /api/unidades/{unidadeId}/profissionais?incluirInativos=true` | Administrador da própria filial | Lista profissionais ativos e inativos. |
| `PATCH /api/unidades/{unidadeId}/profissionais/{id}` | Administrador da própria filial | Atualiza dados ou estado do profissional. |

Na criação de filial, o corpo inclui os dados da filial e `primeiroAdministrador`
com nome e e-mail válidos segundo a SPEC de autenticação. E-mail já utilizado
retorna `409`; não é permitido reutilizar a mesma conta em várias filiais.
O convite usa o prazo e o fluxo já definidos na SPEC de autenticação.

Campos inválidos, fuso desconhecido ou tentativa de editar vínculos imutáveis
retornam `400 Bad Request`; conflito de nome na mesma organização, e-mail já
usado ou desativação com agendamentos futuros ativos retorna `409 Conflict`.
Requisições sem sessão quando ela é exigida retornam `401 Unauthorized`; falta
de permissão retorna `403 Forbidden`, admitido `404` para recurso fora do
escopo. A consulta pública não revela uma filial inativa.

## Interface e fluxos

- O administrador principal vê os dados do estabelecimento, as filiais e a
  ação de criar filial com convite para o primeiro administrador.
- Cada administrador vê um formulário de dados da própria filial e a lista de
  seus profissionais, inclusive inativos, com ações de criar, editar e
  ativar/desativar.
- Visitantes e clientes veem somente informações da filial ativa selecionada,
  profissionais ativos e serviços disponíveis; não existe diretório global de
  estabelecimentos.
- Mensagens de conflito explicam qual dado precisa ser corrigido sem expor
  contas ou recursos de outra filial. Desativações com agendamentos futuros
  orientam a resolver os atendimentos antes de repetir a operação.
- A interface oculta ações sem permissão, mas o backend aplica todas as regras
  independentemente do frontend.

## Migração e integridade

- Uma migração Flyway cria `estabelecimento` e acrescenta a chave de
  estabelecimento, fuso, estado e demais campos a `unidade`, preservando os IDs
  e vínculos existentes de serviços, profissionais e usuários.
- Cada unidade anterior à migração passa a ser filial principal de um
  estabelecimento próprio. A migração não agrupa unidades por nome nem move
  contas; qualquer agrupamento posterior exige procedimento específico.
- Chaves estrangeiras, restrições de obrigatoriedade e índice de unicidade do
  nome normalizado por estabelecimento protegem as invariantes no banco. A
  aplicação valida os mesmos limites e executa criação de filial e primeiro
  administrador em uma transação.
- Provisionamento por `.env` continua idempotente e passa a criar ou localizar
  o estabelecimento correspondente à filial principal. Não armazena senha
  administrativa em variável de ambiente, banco em texto puro ou repositório.
- Eventos de criação e alteração de estabelecimento, filial e profissional
  registram autor, recurso e instante, sem dados sensíveis ou tokens.

## Critérios de aceitação

- A migração mantém IDs, serviços, profissionais e contas já existentes e
  associa cada unidade antiga a um estabelecimento próprio.
- O provisionamento inicial cria estabelecimento, filial principal e primeiro
  administrador convidado; repetidas inicializações não duplicam registros.
- Um administrador principal cria filial e convite inicial no mesmo fluxo.
  Falha de validação ou persistência não deixa filial órfã.
- O administrador convidado só entra após concluir o convite e então administra
  sua própria filial. O criador não passa a administrar os dados dessa filial.
- Filiais de um estabelecimento podem ter o mesmo nome que filiais de outro,
  mas não nomes equivalentes dentro do mesmo estabelecimento.
- Profissional não pode ser vinculado a uma conta de outra filial nem a duas
  contas; inativos não aparecem publicamente nem geram disponibilidade.
- Filial inativa não aparece publicamente nem aceita novos agendamentos.
  Desativações com agendamentos futuros ativos são impedidas.
- Testes de integração cobrem acesso permitido e negado por perfil, filial e
  estabelecimento, inclusive IDs adulterados, `401`, `403`/`404` e a exceção
  controlada para criar filial e convidar seu primeiro administrador.
- Testes de migração cobrem banco legado e invariantes de unicidade e vínculo.

## Fora do escopo

- Marketplace, busca global e cadastro público de estabelecimentos.
- Uma conta operar várias filiais, transferência de profissional ou filial e
  troca da filial principal.
- Endereço geocodificado, mapa, fotos e upload de mídia.
- Jornadas, folgas, feriados, cálculo de disponibilidade e agendamentos, que
  terão regras próprias nas respectivas SPECs.
- Financeiro, contratos, faturamento e administração global da plataforma.

## Relação com outras SPECs

- Complementa `2026-09-30-autenticacao-e-controle-de-acesso.md` e substitui
  somente sua restrição de escopo no momento de criar uma nova filial com seu
  primeiro administrador, conforme descrito acima.
- Mantém as regras de serviço e habilitação por profissional da SPEC
  `2026-09-30-catalogo-de-servicos.md`; associação e disponibilidade continuam
  limitadas à mesma filial.
- Jornadas, disponibilidade e agendamentos devem aplicar o estado da filial e
  do profissional sem ampliar as permissões aqui definidas.
