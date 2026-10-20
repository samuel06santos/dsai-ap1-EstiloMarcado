# Servidor FastMCP e checkout de pré-reserva (2026-10-17)

## O que é e por que existe

O Estilo Marcado oferecerá suas operações de descoberta e agendamento a
clientes MCP remotos por um servidor FastMCP sobre HTTPS. O usuário poderá
consultar o catálogo anonimamente, autorizar o cliente MCP com sua conta do
Estilo Marcado para operações próprias e, mesmo sem autorizar o cliente MCP,
iniciar uma pré-reserva curta e concluí-la no site. O servidor não criará uma
segunda identidade de cliente nem atribuirá ao modelo acesso à sessão web.

Esta SPEC complementa `2026-09-30-autenticacao-e-controle-de-acesso.md`,
`2026-10-05-firebase-authentication.md`,
`2026-10-01-agendamento-confirmacao-cancelamento-e-reagendamento.md`,
`2026-10-01-protecao-contra-reservas-concorrentes-e-sobreposicao.md` e
`2026-10-01-motor-de-disponibilidade.md`. Ela **altera expressamente** apenas
as afirmações anteriores de que não há pré-reserva temporária nem escolha
automática de profissional. `AGENDADO`, `CONFIRMADO`, `CANCELADO` e suas
transições continuam com o significado já definido.

## Situação encontrada no repositório

- O Angular autentica por e-mail/senha ou Google. Com Firebase habilitado, o
  navegador entrega o ID token a `POST /api/autenticacao/sessoes/firebase` e o
  Spring Boot cria a sessão HTTP `SESSION`. Sem Firebase, há login local por
  e-mail/senha. As APIs de negócio usam essa sessão, não um JWT próprio.
- O PostgreSQL guarda perfil e estado da conta, vínculo `cliente.usuario_id`,
  atendimentos e a tabela de idempotência. O Firebase, quando habilitado,
  prova a credencial; não define permissões de negócio. Login Google não é
  Google Authenticator e não implica TOTP ou MFA.
- A API de criação exige `servicoId`, `profissionalId`, início local e
  `Idempotency-Key`. `AGENDADO` ocupa a agenda desde a criação; a passagem a
  `CONFIRMADO` é ação posterior da recepção/administração.
- O motor oferece horários para todos os profissionais elegíveis quando não
  recebe um filtro de profissional. Escritas bloqueiam profissionais e a
  migração `V12__agendamentos.sql` protege a sobreposição **entre atendimentos**
  ativos com `ex_atendimento_ocupacao`. Essa restrição, sozinha, não protege
  uma pré-reserva armazenada em outra tabela.
- O Compose de produção mantém backend e PostgreSQL na rede interna; o nginx
  do host publica HTTPS e encaminha `/api/` ao frontend, que faz proxy ao
  backend. O novo caminho MCP e os metadados OAuth exigem roteamento próprio.

## Escopo e entregáveis da implementação

- Servidor Python FastMCP remoto, apenas com transporte HTTP compatível com os
  clientes MCP documentados, e adaptador que reutilize regras e transações de
  negócio do Spring Boot.
- Autorização MCP integrada à conta Estilo Marcado, migrações Flyway, emissão e
  verificação de JWT de acesso próprio, curto e exclusivo do MCP.
- Tools públicas, privadas de leitura e de escrita descritas abaixo.
- Tabela própria de pré-reservas, ocupação comum a web e MCP, checkout Angular
  e endpoints Spring Boot protegidos por sessão e CSRF.
- Configuração reproduzível do cliente MCP, Compose de desenvolvimento e de
  produção, nginx, variáveis de ambiente e documentação operacional. O agente
  recebe o servidor por `.mcp.json` versionado e regras de permissão também
  versionadas: `allow` para leitura e `ask` para escrita.
- Testes de segurança, fluxo ponta a ponta, expiração, uso único e disputas
  reais em PostgreSQL.

## Autorização do cliente MCP sob demanda

O site conserva seu login e sua sessão `SESSION`. Para o cliente MCP remoto, o
Estilo Marcado atuará como servidor de autorização: publicará metadados OAuth,
autorizará um cliente público por **código de autorização com PKCE S256** e
emitirá um JWT de acesso com duração máxima de cinco minutos. O fluxo da página
de autorização usa a sessão web já existente; se necessário, conduz ao login
normal por e-mail/senha ou Google e volta ao pedido de autorização. Nenhum
ID token Firebase, cookie ou senha sai desse fluxo para uma tool MCP.

O formulário de consentimento usa o CSRF vinculado à própria sessão web;
as chamadas Angular existentes continuam usando o cookie `XSRF-TOKEN`. A
página de consentimento usa o visual do Estilo Marcado, carrega uma folha de
estilo própria e informa conta, aplicativo e ações permitidas em linguagem
clara. Sua CSP permite o formulário no próprio site e, para concluir o fluxo,
somente a origem do callback loopback validado para aquele pedido. No nginx,
`form-action` admite `127.0.0.1`, `localhost` e `::1` com porta dinâmica; a
CSP específica da página restringe a porta à URI registrada. Sem essa
permissão, o navegador bloqueia o retorno ao cliente mesmo após o clique em
autorizar. O nginx do frontend deve manter `/oauth/` à frente da regra de
arquivos estáticos, inclusive para servir `/oauth/consent.css` pelo backend.

1. O cliente MCP descobre o recurso protegido e os endpoints de autorização,
   cria `state` e `code_verifier`, e abre a autorização no navegador com
   `code_challenge` S256, `client_id`, `redirect_uri`, escopos e recurso MCP.
2. O backend valida cliente e URI de retorno registrados, sessão web, CSRF e
   conta local `ATIVA` de perfil `CLIENTE`. A página mostra nome do cliente MCP
   e escopos, pede consentimento e emite código curto, opaco e de uso único
   vinculado a usuário, cliente, URI, desafio PKCE e recurso MCP. Recusa,
   expiração, reutilização ou divergência não concedem token.
3. O **cliente MCP**, não a página, recebe o código no retorno e o troca no
   endpoint de token com o `code_verifier`. O endpoint retorna o JWT e um
   refresh token opaco ao cliente MCP, sem precisar de `SESSION` nessa troca.
   O cliente os guarda no mecanismo seguro de credenciais e passa a enviar
   `Authorization: Bearer <JWT>` em **cada** requisição HTTP MCP, inclusive
   depois de reconexão. Antes da expiração, o cliente usa o grant
   `refresh_token` no endpoint OAuth para obter novo JWT sem abrir o login.
   A documentação deve
   mostrar configuração e sequência observável para ao menos um cliente MCP
   remoto real. Abrir a página de login, receber o código ou obter uma sessão
   no navegador **não** prova que o cabeçalho do cliente foi atualizado.

O JWT continua válido por no máximo cinco minutos. O refresh token dura até
trinta dias, é vinculado ao cliente OAuth, usuário, escopos e versão do
consentimento, e gira a cada uso. O banco guarda somente seu hash; reuso,
expiração, revogação, mudança de perfil ou desativação da conta impedem a
renovação. A resposta de erro usa `invalid_grant`. O refresh token nunca é
enviado ao servidor MCP como Bearer nem como argumento de tool. Um Bearer
expirado apresentado em qualquer chamada MCP recebe HTTP 401 com desafio
`WWW-Authenticate` e `invalid_token`, para que o cliente tente renovar. Sem
Bearer, as tools públicas continuam anônimas. Clientes que já tinham apenas
JWT antes desta mudança precisam autorizar uma vez após o novo deploy para
receber o refresh token.

O JWT é assinado com chave assimétrica gerida fora do repositório; a chave
pública é publicada via JWKS com rotação documentada. Exige `iss` exato do
Estilo Marcado, `aud` exata do recurso MCP, `sub` igual ao ID imutável do
usuário local, `iat`, `nbf`, `exp`, `jti`, identificador do cliente e escopos
aprovados. Rejeitar algoritmo inesperado, assinatura inválida, emissor ou
audiência diferentes, token prematuro/expirado, formato malformado e tokens
Firebase ou de outra API. Escopos mínimos: leitura própria e alteração própria;
as tools públicas não exigem escopo. A cada requisição protegida, além das
claims, conferir no PostgreSQL estado `ATIVA` e perfil atual `CLIENTE`.
Mudança de perfil, bloqueio ou desativação deve interromper o uso imediatamente;
revogação de consentimento deve invalidar tokens ainda não expirados por `jti`
ou versão de autorização. Nunca inferir identidade por e-mail, nome, cookie,
argumentos de tool, URL de checkout ou cabeçalho de usuário fornecido pelo
cliente.

O servidor FastMCP é recurso OAuth: publica metadados de recurso protegido e
um desafio interoperável de autenticação para clientes capazes de autorização
automática. A descoberta e as tools públicas permanecem acessíveis sem
Bearer. A autorização também pode ser iniciada pelo usuário quando uma tool
privada devolver a orientação definida abaixo. Não usar GitHub OAuth, conta
compartilhada, `SESSION` em prompt, credenciais em argumentos de tools nem
tokens Firebase como Bearer MCP.

## Identidade por requisição e fronteira entre serviços

Um único utilitário/middleware FastMCP resolve a identidade **exclusivamente**
do cabeçalho Bearer no contexto HTTP da requisição MCP atual. A ausência do
cabeçalho produz contexto anônimo; Bearer presente mas inválido, expirado ou
malformado encerra a requisição com erro de autenticação, sem degradá-la para
anônima. A identidade não pode persistir entre requisições ou clientes por
variável global, sessão de transporte ou cache sem chave de usuário.

O Spring Boot continua autoridade das regras de negócio. O adaptador FastMCP
passa o Bearer original para endpoints internos próprios do MCP, onde ele é
validado novamente antes de qualquer leitura/escrita privada. Esses endpoints
nunca aceitam `usuarioId`, `clienteId`, perfil ou e-mail declarados pelo
adaptador como prova de identidade. O tráfego interno fica restrito à rede
Compose e não reutiliza a sessão web. Uma chamada pública sem Bearer permanece
pública também no backend. Credencial técnica entre serviços, se necessária,
é separada da conta de usuário e guardada fora do prompt.

## Contrato das tools

Todos os parâmetros são dados de negócio. Nenhuma tool recebe `user_id`,
`clienteId`, e-mail, senha, código de autenticação, cookie ou JWT como
parâmetro. Identificadores de filial, serviço e agendamento não conferem
permissão. Respostas privadas usam `no-store`, paginação/limites e não incluem
dados de outro cliente.

| Tool | Acesso | Entrada e resultado |
| --- | --- | --- |
| `listar_filiais` | Público | Filtros públicos de descoberta e paginação; filiais ativas, IDs e informações públicas. |
| `listar_servicos` | Público | Filial e filtros públicos; serviços ativos, duração, intervalo e preço exibível. |
| `consultar_horarios` | Público | Filial, serviço e data local; horários por profissional ou opção sem profissional, com fuso IANA explícito. Consulta não reserva. |
| `meus_agendamentos` | Cliente com escopo de leitura | Período, estado e paginação; somente agendamentos do `cliente.usuario_id` resolvido do Bearer. |
| `detalhar_agendamento` | Cliente com escopo de leitura | ID de agendamento; detalhe apenas se pertencer ao usuário; fora do escopo equivale a inexistente. |
| `horarios_para_reagendar` | Cliente com escopo de leitura | ID e data; horários elegíveis para o próprio atendimento, preservando serviço e profissional atuais. |
| `minha_lista_espera` | Cliente com escopo de leitura | Paginação; somente registros da própria conta. |
| `criar_agendamento` | Público, com dois desfechos | Filial, serviço e data/hora local; com Bearer válido e confirmação explícita, cria `AGENDADO` próprio; sem Bearer, cria pré-reserva de dez minutos e retorna link HTTPS de checkout. |
| `cancelar_agendamento` | Cliente com escopo de escrita | ID e motivo opcional; após confirmação explícita, cancela apenas atendimento próprio elegível. |
| `reagendar_agendamento` | Cliente com escopo de escrita | ID e novo início local; após confirmação explícita, altera apenas atendimento próprio elegível. |

### Docstrings e escolha das tools pelo agente

Cada tool deve publicar no próprio servidor FastMCP uma docstring/descrição
visível na descoberta MCP, em português, que diga **quando usá-la**, quais
dados de negócio solicitar e qual resultado esperar. A descrição também deve
deixar claro quando a operação é apenas consulta e quando pode alterar a
agenda. Não confiar somente no README ou no nome da função para orientar o
agente; não incluir segredos ou instruções para pedir credenciais na docstring.

| Tool | Orientação mínima da docstring: quando usar |
| --- | --- |
| `listar_filiais` | Quando o usuário quiser conhecer ou escolher uma filial; começar por ela se ainda não houver filial definida. |
| `listar_servicos` | Quando o usuário quiser conhecer serviços e preços de uma filial ou precisar escolher um serviço antes de buscar horário. |
| `consultar_horarios` | Quando o usuário quiser ver disponibilidade para filial, serviço e data; explicar que os horários são uma prévia e que esta tool não reserva. |
| `meus_agendamentos` | Quando o cliente quiser listar suas próprias reservas futuras ou passadas, com período, estado e paginação; se anônimo, orientar a autorizar o cliente MCP. |
| `detalhar_agendamento` | Quando o cliente pedir os detalhes de um agendamento próprio identificado por ID; não usar para tentar descobrir reservas de terceiros. |
| `horarios_para_reagendar` | Quando o cliente quiser comparar novos horários para um agendamento próprio antes de decidir reagendar; a consulta não muda a reserva. |
| `minha_lista_espera` | Quando o cliente quiser consultar suas inscrições na lista de espera; se anônimo, orientar a autorizar o cliente MCP. |
| `criar_agendamento` | Somente depois de o usuário escolher filial, serviço e data/hora e aprovar a ação; informar que com Bearer cria reserva própria e sem Bearer cria pré-reserva de dez minutos para checkout no site. |
| `cancelar_agendamento` | Somente quando o cliente pedir o cancelamento de reserva própria, revisar o ID/efeito e obter confirmação explícita; não usar para simples consulta. |
| `reagendar_agendamento` | Somente quando o cliente escolher novo horário para reserva própria, revisar o destino e obter confirmação explícita; consultar alternativas antes quando necessário. |

As docstrings das quatro leituras privadas devem indicar o resultado
`AUTENTICACAO_MCP_NECESSARIA` sem sugerir envio de `SESSION`, senha ou JWT em
argumentos. As três docstrings de escrita devem mencionar a confirmação
humana exigida, inclusive na criação anônima da pré-reserva. Testes de
descoberta verificam o texto exposto ao agente, além do comportamento da tool.

Uma chamada privada **sem Bearer** devolve somente resultado estável de
`AUTENTICACAO_MCP_NECESSARIA` com orientação para autorizar o **cliente MCP** e
repetir a chamada; nunca lista vazia que possa ser confundida com ausência de
dados, nem qualquer dado privado. Clientes com OAuth automático devem receber
também o desafio de transporte apropriado para iniciar o fluxo. Bearer
inválido/expirado recebe erro de autenticação e indicação de reautorização;
Bearer válido de perfil ou escopo inadequado recebe acesso negado, sem dados.

Antes de `criar_agendamento`, `cancelar_agendamento` ou
`reagendar_agendamento`, o cliente MCP apresenta ao usuário uma revisão de
filial, serviço, profissional escolhido quando conhecido, data/hora e efeito
da ação e recolhe aprovação explícita pelo mecanismo interativo confiável do
cliente. A aprovação é específica para os dados revisados e de uso único;
mudanças nos dados exigem nova aprovação. Sem suporte a essa confirmação ou
se houver recusa, a tool falha **sem alterar** a agenda. Texto gerado pelo
modelo e um simples argumento booleano de confirmação não substituem a
decisão do usuário. A confirmação no checkout web é adicional para o fluxo
anônimo.

A implementação deve usar o mecanismo nativo do FastMCP adequado à era do
protocolo, sem publicar `confirmacao` como argumento de negócio das tools. Para
clientes modernos, a tool devolve `InputRequiredResult` com uma
`ElicitRequest` estruturada e um `request_state` assinado pelo servidor; a
continuação recebe a resposta em `ctx.input_responses`, valida a intenção e só
então executa a escrita. Para clientes legados, a tool usa `ctx.elicit()` com
limite explícito de 30 segundos; timeout, recusa ou ausência de suporte falham
sem alterar a agenda. O uso de `Resolve`/`Annotated` para esconder esse campo
é proibido nesta integração, pois o parser de `@mcp.tool` do FastMCP o publica
no schema remoto como argumento obrigatório. O campo de aprovação permanece
interno ao protocolo interativo.

As tools de escrita devem tratar a resposta intermediária como resultado da
própria chamada e devolvê-la ao cliente sem chamar o backend. A continuação
deve manter a mesma intenção, Bearer e nonce assinado; o nonce expira em cinco
minutos e o backend aplica uso único quando a operação for cancelamento ou
reagendamento.

O transporte Streamable HTTP do serviço deve permanecer stateful, com resposta
não-JSON (`stateless_http=false` e `json_response=false`), para conservar o
canal necessário aos clientes legados. A configuração do servidor não pode
exigir que cada usuário remoto edite um JSON do projeto. O nonce enviado ao
backend deve estar vinculado à intenção e ao Bearer. No fluxo moderno, o
`request_state` contém a intenção, o hash do Bearer, a expiração e um nonce
UUID, todos protegidos por HMAC; cancelamento e reagendamento extraem esse
nonce e o backend o consome uma única vez. No fluxo legado, o nonce é gerado
para a sessão MCP e continua sujeito às validações do backend.

`meus_agendamentos` deve aceitar somente datas locais no formato `AAAA-MM-DD`,
com intervalo inclusivo máximo de 31 dias (`ate - de <= 30 dias`), página a
partir de zero e tamanho entre 1 e 100. O adaptador deve rejeitar esses erros
antes de chamar o backend. Respostas HTTP 400 do backend devem preservar a
mensagem de validação para o cliente, em vez de aparecerem como erro genérico
de operação indisponível.

## Criação com Bearer e escolha do profissional

`criar_agendamento` não recebe profissional. Na mesma transação, o backend
considera profissionais ativos e habilitados para o serviço/filial, em ordem
crescente de ID, bloqueia a agenda candidata pelo protocolo atual e escolhe o
primeiro cujo período solicitado ainda seja elegível **após revalidação**.
Se a disputa ou outra regra retirar a elegibilidade, avalia o próximo
candidato; se nenhum servir, retorna `409 HORARIO_INDISPONIVEL`. O profissional
efetivamente escolhido aparece na revisão/resultado; se a confirmação prévia
mostrar apenas a política de escolha, o usuário aprova essa política de forma
clara antes da escrita. O resultado não depende da ordem arbitrária de uma
consulta SQL nem de escolha do modelo.

Com Bearer válido, a criação usa o usuário local como autor e seu vínculo
`cliente` existente ou criado atomicamente, além da idempotência de 24 horas
já definida para criação. O adaptador gera e conserva uma chave de
idempotência por intenção confirmada e a reutiliza em repetição após perda da
resposta; não muda a chave em uma repetição automática. Dados de agendamento,
preço e ocupação são revalidados no Spring Boot antes do commit.

## Pré-reserva anônima e checkout web

Sem Bearer, depois da confirmação explícita no cliente MCP,
`criar_agendamento` grava uma **pré-reserva** separada de `atendimento`, com
prazo exato de dez minutos contado da gravação. Ela não tem `cliente_id`,
`usuario_id`, conta fictícia ou estado `AGENDADO`. Registra filial, serviço,
profissional escolhido, início local, fuso IANA, snapshots de duração e
intervalo, instante de expiração, estado/consumo, identificador de
idempotência e hash de um token aleatório de alta entropia. O token bruto,
opaco, único e de uso único aparece somente no link HTTPS de checkout
retornado ao chamador; não é JWT, não autentica ninguém e não é aceito como
Bearer do MCP. Se uma repetição idempotente precisar devolver o mesmo link,
guardar uma cópia cifrada recuperável apenas até expiração/consumo, além do
hash usado na validação. Devolver o link novamente não permite consumir o
token mais de uma vez.

O link abre uma rota Angular dedicada. Ela mostra dados da pré-reserva, prazo
restante, filial, serviço, profissional, preço/fuso atuais e o aviso de que a
reserva definitiva ainda depende de login e confirmação. Se não houver
sessão, encaminha ao login existente e retorna de forma segura ao checkout.
Após login, apenas uma conta `CLIENTE` `ATIVA` pode confirmar. O cliente
revisa os dados e aciona um `POST` protegido por `SESSION` **e CSRF**; o token
de checkout é prova de posse da pré-reserva, nunca prova de identidade. O
backend, na mesma transação, verifica hash, prazo, uso único, conta e perfil,
revalida serviço, profissional, janela e ocupação, cria/vincula o registro
`cliente` se necessário, converte a ocupação provisória em `atendimento`
`AGENDADO`, consome a pré-reserva e registra auditoria. Nenhum token ou
pré-reserva cria `CONFIRMADO`: esse estado continua reservado à confirmação
posterior pela equipe. Se algo mudou, responder com motivo estável e
alternativas, sem criar atendimento parcial.

O primeiro `POST` válido vence. Repetição pelo mesmo usuário pode devolver o
mesmo resultado de forma idempotente, sem segundo atendimento; uso por outro
usuário, depois do consumo ou após o prazo não deve vincular a pré-reserva a
outra conta nem revelar dados pessoais. A página indica expiração/uso e
oferece nova busca. A leitura dos dados de checkout não expõe identidade; o
token no link não é registrado em logs, analytics ou mensagens de erro. Usar
`Cache-Control: no-store`, `Referrer-Policy: no-referrer`, HTTPS e redireção
restrita à própria aplicação.

## Disponibilidade, expiração e concorrência

Pré-reservas **não expiradas** ocupam o período semiaberto
`[inicio, inicio + duração + intervalo)` do profissional nas tools MCP, no
motor público, nas rotas web existentes de criação/reagendamento, encaixes e
aceitação de ofertas, e nas alterações administrativas de disponibilidade.
Atendimentos `AGENDADO`/`CONFIRMADO` continuam ocupando; `CANCELADO` não.
O fuso da filial, grade de 15 minutos, horizonte de 60 dias, transições de
fuso, janela completa, bloqueios, pausas, duração e intervalo mantêm as
regras das SPECs anteriores. Snapshots da pré-reserva são conferidos contra
as regras atuais no checkout; alterações posteriores do catálogo não
autorizam silenciosamente uma reserva incompatível.

Todas as escritas que disputam a agenda usam o mesmo bloqueio transacional do
profissional e a mesma ordem fixa de bloqueios. O banco precisa de uma
proteção **comum** de não sobreposição entre atendimento ativo e pré-reserva
vigente; a exclusão atual apenas em `atendimento` não basta. A migração deve
criar uma fonte de ocupação comum ou mecanismo equivalente com garantia no
PostgreSQL, migrar os atendimentos ativos sem perdê-los e preservar
`ex_atendimento_ocupacao` até haver proteção equivalente verificada por
teste. Conflitos viram `409 HORARIO_INDISPONIVEL`, sem dados do vencedor.

A validade da pré-reserva é decidida pelo relógio do banco/instante confiável,
não pelo agendamento de uma limpeza. Consultas ignoram `expira_em <= agora`.
Antes de qualquer escrita conflitante, a transação que detém o bloqueio
torna ocupações expiradas inativas e revalida o horário; assim o espaço pode
ser reservado imediatamente após o prazo mesmo se o limpador não rodar.
Uma limpeza assíncrona remove apenas resíduos, sem participar da correção.
Expiração simultânea à confirmação ou a uma reserva web tem ordem de commit
coerente: no máximo um atendimento ativo ocupa o período, e uma confirmação
após o prazo falha.

A criação anônima também é idempotente por intenção, sem exigir identidade:
chave UUID vinculada ao hash canônico de filial, serviço e início, com prazo
de retenção e limite de taxa. A repetição igual durante a validade devolve a
mesma pré-reserva sem criar outra; chave reutilizada com pedido distinto dá
conflito. O token de checkout bruto não pode reaparecer em logs nem em
resposta após consumo. Limites por origem reduzem esgotamento artificial da
agenda; não substituem a exclusividade transacional. A documentação deve
explicitar que a pré-reserva é temporária e que o checkout pode falhar após
mudança legítima da agenda.

## Contratos, interface e implantação

Os endpoints de autorização fornecem metadados do servidor de autorização,
autorização, token, JWKS e registro/validação de clientes públicos; os
metadados de recurso protegido apontam para eles. Redirecionamentos e
escopos são restritos, com `state`, PKCE obrigatório, códigos de uso único,
consentimento sob sessão web e proteção CSRF. Os endpoints internos de
negócio MCP separam chamadas anônimas das privadas e não aceitam cookie como
autenticação MCP. O `POST` de checkout é web e exige sessão/CSRF.

Entregar exemplo de configuração do cliente com URL HTTPS do transporte,
orientação de autorização e armazenamento local seguro do token; documentar
como inspecionar uma chamada real que contenha `Authorization: Bearer`, sem
imprimir o JWT completo. Documentar expiração/reautorização, escopos,
revogação, respostas anônimas e confirmação humana das escritas.

### Conexão versionada com o agente e permissões

Na implementação, adicionar à raiz do repositório um `.mcp.json` **versionado**
com o servidor `estilo_marcado`, transporte HTTP e URL HTTPS pública do
endpoint MCP. A configuração versionada aponta para produção; o ambiente local
usa uma substituição temporária na configuração de cada cliente, nunca um
Bearer fixo. Não colocar JWT, cookie, segredo de
cliente nem `Authorization` estático nesse arquivo. O cliente MCP deve obter
e guardar seu token pelo fluxo OAuth/PKCE acima; a conexão do arquivo não
autentica o usuário por si só.

Para agentes que usam o formato de permissões do Claude Code, versionar também
`.claude/settings.json`: `.mcp.json` declara a conexão, enquanto
`permissions.allow` e `permissions.ask` declaram as regras de execução. Não
colocar `allow`/`ask` dentro de `.mcp.json`, onde não seriam aplicados. A
configuração deve nomear **explicitamente** as sete tools de leitura em
`allow` (`listar_filiais`, `listar_servicos`, `consultar_horarios`,
`meus_agendamentos`, `detalhar_agendamento`, `horarios_para_reagendar`,
`minha_lista_espera`) e as três tools de escrita em `ask`
(`criar_agendamento`, `cancelar_agendamento`, `reagendar_agendamento`). A
pré-reserva anônima também é escrita. Não usar curinga que permita tools
futuras automaticamente. Os nomes das regras precisam coincidir com os nomes
MCP publicados, como `mcp__estilo_marcado__listar_filiais`.

`ask` significa pedir aprovação do agente antes da chamada de escrita e
**não substitui** a confirmação específica do usuário exigida pela tool nem
as validações do backend. Se outro agente consumir o `.mcp.json`, documentar
o arquivo/formato de permissões equivalente e verificar que a política efetiva
continue sendo leitura livre e escrita sob pergunta. Alterações futuras nas
tools exigem revisão explícita da lista de permissões.

Compose inicia FastMCP como serviço separado na rede interna, com saúde e
dependências, sem publicar backend, banco ou porta MCP direta na Internet.
Nginx do host encaminha o caminho HTTPS MCP e os metadados OAuth ao serviço
correto, preserva `Authorization` e cabeçalhos necessários ao transporte,
não encaminha `SESSION` ao FastMCP e aplica limites/cabeçalhos seguros. A
configuração Angular mantém a rota de checkout e o retorno ao login; CSP e
política de proxy devem permitir somente as origens necessárias. Chaves de
assinatura, URLs públicas, audiência, origem permitida e limites ficam em
configuração externa, com rotação e checklist de deploy/rollback documentados.

No Compose de desenvolvimento, o transporte MCP e sua descoberta OAuth são
acessados diretamente em `http://127.0.0.1:8000/mcp`, enquanto o emissor OAuth
e o login web ficam em `http://localhost:4200`. O proxy de desenvolvimento
Angular não intermedeia o transporte MCP: o fluxo HTTP do FastMCP requer
resposta contínua e a tentativa de encaminhá-lo por esse proxy falhou no
handshake. Antes de versionar as configurações dos agentes, restaurar a URL
HTTPS de produção em `.codex/config.toml` e `opencode.json`. Em produção,
somente o nginx publica o MCP por HTTPS.

## Critérios de aceitação e testes

- `listar_filiais`, `listar_servicos` e `consultar_horarios` funcionam sem
  Bearer. Uma tool privada sem Bearer devolve apenas
  `AUTENTICACAO_MCP_NECESSARIA`; token inválido, expirado, de audiência ou
  emissor errado falha sem virada para anônimo.
- Login por e-mail/senha e login Google, com Firebase habilitado, conduzem da
  sessão web ao código PKCE e ao JWT do **mesmo** `usuario.id` local. O modo
  de senha local, quando habilitado, também funciona. Nenhum teste presume
  TOTP. Código errado, reuso, troca de `redirect_uri` ou PKCE são recusados;
  o cliente rejeita um retorno cujo `state` não coincide com o pedido.
- Um teste ponta a ponta usa cliente MCP remoto real: antes da autorização
  observa a orientação sem dados; após o callback e troca do código captura
  a requisição HTTP seguinte e comprova `Authorization: Bearer` presente,
  obtém dados próprios; depois da renovação usa o novo Bearer sem novo login.
  Bearer inválido/expirado recebe HTTP 401 com `invalid_token`; refresh
  reutilizado, expirado ou revogado recebe `invalid_grant`. Apenas ver
  a página no navegador não satisfaz este teste.
- O agente carrega o servidor pelo `.mcp.json` versionado. Um teste confere
  que as dez docstrings publicadas descrevem quando usar a tool, que as sete
  leituras estão em `allow` e que as três escritas estão em `ask`, inclusive
  `criar_agendamento` anônimo. Após a autorização de conta necessária, uma
  leitura não pede aprovação de execução; cada escrita pede, e a recusa não
  causa efeito. Nenhum arquivo versionado contém token.
- Um cliente MCP moderno e um cliente legado recebem a revisão interativa das
  três tools de escrita, a chamada não fica aguardando indefinidamente, e a
  resposta aprovada retoma a mesma intenção sem duplicar a escrita. Recusa,
  cancelamento da elicitação ou ausência de suporte falham sem alterar a
  agenda; o teste verifica também que o estado de continuação não aparece
  como argumento de negócio publicado pela tool.
- `meus_agendamentos` rejeita antes da chamada interna datas com horário/fuso,
  intervalos invertidos ou maiores que 31 dias, página negativa e tamanho fora
  de 1..100. Quando o backend devolve HTTP 400, o cliente recebe a mensagem
  de validação específica, e não o erro genérico de operação indisponível.
- Dois clientes com Bearers distintos não veem, cancelam nem reagendam dados
  um do outro, mesmo conhecendo IDs. Perfil diferente, conta bloqueada ou
  desativada e consentimento revogado perdem acesso. Parâmetros extras de
  identidade são rejeitados. Escopos de leitura não autorizam escrita.
- Cada escrita exige aprovação explícita e específica do usuário. Recusa ou
  cliente sem mecanismo de aprovação não altera reservas; repetição da mesma
  intenção confirmada não duplica atendimento ou pré-reserva.
- Criação autenticada escolhe determinística e transacionalmente um
  profissional elegível, retorna `AGENDADO` próprio e respeita fuso, jornada,
  bloqueios, duração, intervalo e idempotência existentes.
- Criação anônima retorna só pré-reserva de dez minutos e link de checkout;
  não cria cliente fictício nem `AGENDADO`/`CONFIRMADO`. Checkout exige
  `SESSION` e CSRF, revalida, cria `AGENDADO` do cliente logado exatamente
  uma vez, e rejeita token ausente, alterado, vencido ou já consumido.
- Consultas públicas, MCP e web ocultam horários bloqueados por pré-reserva
  válida; no instante de expiração o horário volta a ser reservável mesmo sem
  limpeza. Serviços com intervalo além da meia-noite seguem os mesmos
  limites semiabertos.
- Testes de integração em PostgreSQL real, com conexões/transações
  independentes e início sincronizado, disputam: duas pré-reservas; pré-reserva
  versus criação/reagendamento web; pré-reserva versus encaixe/oferta;
  confirmação de checkout versus expiração e reserva web; dois `POST` de
  checkout; mudança administrativa de disponibilidade versus pré-reserva.
  Em cada caso, verificar o estado persistido e que no máximo uma ocupação
  válida vence, sem eventos ou chaves de sucesso órfãos.
- Testes de frontend cobrem login com retorno ao checkout, revisão, CSRF,
  expiração, uso único e erro de horário perdido. Testes de implantação
  validam Compose/nginx, roteamento HTTPS, metadados OAuth, passagem do
  cabeçalho e ausência de cookies no serviço MCP.

## Relação com outras SPECs e referências

- Preserva a autoridade de perfil, estado e sessão web de
  `2026-09-30-autenticacao-e-controle-de-acesso.md` e o vínculo Firebase/Google
  de `2026-10-05-firebase-authentication.md`.
- Estende a criação e a idempotência de
  `2026-10-01-agendamento-confirmacao-cancelamento-e-reagendamento.md` e a
  exclusividade de
  `2026-10-01-protecao-contra-reservas-concorrentes-e-sobreposicao.md`.
- Reutiliza a composição de horários de
  `2026-10-01-motor-de-disponibilidade.md` e afeta encaixes de
  `2026-10-01-lista-de-espera-encaixes-notificacoes-historico-e-relatorios-operacionais.md`.
- A implementação deve seguir a [autorização MCP](https://modelcontextprotocol.io/specification/2026-07-28/basic/authorization),
  a [verificação de tokens FastMCP](https://gofastmcp.com/servers/auth/token-verification)
  e a [autenticação OAuth do cliente FastMCP](https://gofastmcp.com/clients/auth/oauth)
  nas versões fixadas no projeto.
- Para o agente Claude Code, seguir a [configuração MCP de projeto](https://code.claude.com/docs/en/mcp)
  e as [regras de permissão `allow`/`ask`](https://code.claude.com/docs/en/permissions).

## Fora do escopo

- GitHub OAuth, Google Authenticator/TOTP presumido, conta compartilhada,
  pagamento e autorização de perfis internos para operar como clientes MCP.
- Credenciais, cookies ou tokens passados ao modelo como argumentos de tools.
- Alterar a confirmação operacional `AGENDADO` → `CONFIRMADO` ou permitir que
  pré-reserva vencida seja renovada sem nova disputa de disponibilidade.

Toda implementação derivada desta SPEC deve usar o trailer:

```text
Spec: SPEC/2026-10-17-servidor-fastmcp-e-checkout-de-pre-reserva.md
```
