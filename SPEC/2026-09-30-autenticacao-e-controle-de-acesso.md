# Autenticação e controle de acesso (2026-09-30)

## O que é e por que existe

Este módulo identifica quem utiliza o Estilo Marcado, protege as credenciais e
limita cada operação ao perfil e à unidade corretos. Ele deve permitir o
cadastro de clientes, a entrada e saída de usuários, a recuperação segura de
contas e a aplicação das permissões de cliente, profissional, recepção e
administrador.

As regras de acesso são garantidas pelo backend. Ocultar botões ou páginas no
frontend melhora a experiência, mas não constitui controle de segurança.

## Escopo

- cadastro público de clientes;
- ativação do endereço de e-mail;
- criação e ativação de contas internas;
- login, consulta da sessão atual e logout;
- solicitação e conclusão da recuperação de senha;
- encerramento de sessões após eventos sensíveis;
- autorização por perfil, proprietário do recurso e unidade;
- bloqueio e desativação de contas;
- registro de eventos relevantes de segurança.

## Perfis e vínculo com o domínio

| Perfil | Vínculo | Finalidade |
| --- | --- | --- |
| `CLIENTE` | Não fica restrito a uma unidade | Consulta serviços e administra apenas seus próprios dados e agendamentos. |
| `PROFISSIONAL` | Pertence a uma unidade e referencia exatamente um profissional da mesma unidade | Consulta sua agenda e administra somente a própria disponibilidade nos limites definidos pelas specs correspondentes. |
| `RECEPCAO` | Pertence a uma unidade | Opera clientes e agendamentos da sua unidade, sem alterar configurações administrativas. |
| `ADMINISTRADOR` | Pertence a uma unidade | Administra a unidade, suas contas internas, profissionais, serviços e regras operacionais. |

Uma conta possui um único perfil nesta primeira versão. Contas internas não
podem atuar sobre dados de outra unidade, mesmo quando conhecem o identificador
do recurso. O perfil enviado pelo cliente nunca é aceito como fonte de
autorização; ele é obtido da sessão validada no servidor.

## Estados da conta

| Estado | Significado | Pode iniciar sessão? |
| --- | --- | --- |
| `PENDENTE` | E-mail ainda não foi confirmado ou convite ainda não foi concluído. | Não |
| `ATIVA` | Cadastro válido e liberado para uso. | Sim |
| `BLOQUEADA` | Bloqueio temporário causado por tentativas de login ou ação administrativa. | Não |
| `DESATIVADA` | Conta desabilitada, preservada para manter o histórico. | Não |

Não haverá remoção física de uma conta que esteja referenciada por dados de
negócio. Alterações de estado, perfil, senha ou unidade invalidam todas as
sessões existentes da conta.

## Cadastro e ativação

### Cadastro público de cliente

O formulário solicita nome, e-mail, senha e confirmação da senha. O cadastro:

- normaliza o e-mail removendo espaços nas extremidades e comparando-o sem
  distinção entre letras maiúsculas e minúsculas;
- exige nome entre 2 e 120 caracteres;
- exige e-mail sintaticamente válido com no máximo 254 caracteres;
- exige senha entre 8 e 72 caracteres, com ao menos uma letra e um número;
- rejeita confirmação de senha diferente;
- atribui sempre o perfil `CLIENTE`, independentemente de campos extras
  enviados na requisição;
- armazena somente o hash forte da senha, com salt individual;
- cria a conta como `PENDENTE` e envia um link de ativação de uso único;
- não cria uma segunda conta para o mesmo e-mail normalizado.

Quando o e-mail já pertence a uma conta, o cadastro público devolve a mesma
resposta genérica do cadastro aceito e não revela o estado da conta existente.
O endpoint é limitado por e-mail e por endereço de origem para impedir abuso.

O token de ativação é aleatório, expira em 24 horas, é armazenado apenas na
forma de hash e deixa de valer após o primeiro uso. Uma nova solicitação de
ativação invalida tokens anteriores ainda válidos. Em desenvolvimento, o e-mail
é capturado pelo Mailpit.

Após uma ativação válida, a conta passa para `ATIVA`; o usuário ainda precisa
fazer login. Token inválido, expirado ou já utilizado não ativa a conta.

### Contas internas

Não existe cadastro público de profissional, recepção ou administrador. Um
administrador cria essas contas exclusivamente para a própria unidade,
informando nome, e-mail e perfil. No caso de `PROFISSIONAL`, também informa o
profissional da mesma unidade ao qual a conta será vinculada.

A conta interna nasce como `PENDENTE` e recebe um convite de uso único, válido
por 24 horas, para definir sua senha e confirmar o e-mail. Um administrador
pode reenviar o convite, bloquear, desbloquear ou desativar contas da sua
unidade. Somente outro administrador pode criar, alterar ou desativar uma conta
com perfil `ADMINISTRADOR`; o sistema não permite que um administrador remova o
próprio último acesso administrativo da unidade.

O primeiro administrador de cada unidade é provisionado durante a implantação,
sem senha padrão ou credencial fixa no repositório, e conclui o mesmo fluxo de
convite.

## Login, sessão e logout

O login recebe e-mail e senha. Somente uma conta `ATIVA`, com credenciais
válidas, inicia sessão. Falhas por e-mail inexistente, senha incorreta, conta
pendente, bloqueada ou desativada retornam a mesma mensagem pública:
`e-mail ou senha inválidos`. A diferença pode ser registrada internamente, sem
expor senha ou token.

Após cinco tentativas malsucedidas para a mesma conta dentro de 15 minutos,
novas tentativas ficam bloqueadas por 15 minutos. O endpoint também é limitado
por endereço de origem para reduzir ataques distribuídos e deve responder com
`429 Too Many Requests` quando o limite técnico for excedido. Um login válido
zera o contador de falhas.

A autenticação usa sessão gerenciada pelo servidor e um cookie com os atributos
`HttpOnly`, `Secure` em produção e `SameSite=Lax`. O identificador da sessão é
renovado após o login para impedir fixação de sessão. A sessão expira após 30
minutos sem atividade e possui duração absoluta máxima de 12 horas. A primeira
versão não oferece a opção “lembrar de mim”.

Requisições que alteram estado e usam a sessão por cookie devem possuir proteção
contra CSRF. Em produção, credenciais e cookies trafegam apenas por HTTPS, e a
política de CORS aceita somente origens explicitamente configuradas.

O logout invalida a sessão no servidor e remove o cookie, sendo idempotente. A
tela de login não redireciona um usuário com base em um perfil informado pelo
frontend; após autenticar, consulta a sessão atual e usa o perfil retornado pelo
backend.

## Recuperação de conta

Qualquer pessoa pode solicitar a recuperação informando um e-mail. A resposta é
sempre genérica, inclusive quando a conta não existe, não está ativa ou já há
uma solicitação em andamento:

> Se existir uma conta apta para este e-mail, enviaremos as instruções de
> recuperação.

Para uma conta apta, o sistema envia um link com token aleatório de uso único,
válido por 30 minutos. Apenas o hash do token é persistido. Uma nova solicitação
invalida tokens anteriores, e o envio é limitado por conta e por endereço de
origem.

O link permite informar e confirmar uma nova senha sujeita às mesmas regras do
cadastro. A nova senha não pode ser igual à senha atual. Quando a redefinição é
concluída, o token é consumido, todas as sessões da conta são encerradas e o
usuário recebe uma notificação por e-mail. O fluxo não autentica o usuário
automaticamente.

Token inválido, expirado ou consumido produz uma mensagem que não revela dados
da conta e oferece a opção de solicitar um novo link. A equipe administrativa
não pode consultar, definir nem enviar senhas de usuários.

## Matriz de acesso

As permissões abaixo são o mínimo deste módulo. Specs de agenda, agendamentos e
painéis podem restringi-las ainda mais, mas não ampliá-las silenciosamente.

| Recurso ou ação | Público | Cliente | Profissional | Recepção | Administrador |
| --- | ---: | ---: | ---: | ---: | ---: |
| Consultar catálogo público e disponibilidade | Sim | Sim | Sim | Sim | Sim |
| Cadastrar cliente, ativar e recuperar conta | Sim | Sim | Sim | Sim | Sim |
| Consultar ou alterar os próprios dados básicos | Não | Sim | Sim | Sim | Sim |
| Consultar os próprios agendamentos | Não | Sim | Não | Não | Não |
| Consultar a própria agenda profissional | Não | Não | Sim | Não | Sim, na unidade |
| Criar ou alterar agendamento de um cliente | Não | Somente o próprio | Não | Sim, na unidade | Sim, na unidade |
| Consultar clientes e agendas da unidade | Não | Não | Somente o necessário à própria agenda | Sim | Sim |
| Manter catálogo, profissionais e regras da unidade | Não | Não | Não | Não | Sim |
| Criar ou administrar contas internas | Não | Não | Não | Não | Sim, na unidade |
| Alterar perfil, unidade ou vínculo profissional | Não | Não | Não | Não | Sim, na unidade |

Para todo recurso identificado por ID, o backend verifica também propriedade e
unidade. Uma requisição autenticada sem permissão recebe `403 Forbidden`.
Requisição sem sessão válida recebe `401 Unauthorized`. Para evitar revelar a
existência de dados de outra unidade ou de outro usuário, consultas por ID
podem responder `404 Not Found` quando o recurso estiver fora do escopo do
solicitante.

## Contrato HTTP inicial

Todos os endpoints usam JSON, exceto os links abertos no frontend. Os nomes
exatos dos campos podem evoluir sem alterar as regras deste documento.

| Método e rota | Acesso | Resultado esperado |
| --- | --- | --- |
| `POST /api/autenticacao/cadastros` | Público | Aceita o cadastro com resposta genérica e `202 Accepted`. |
| `POST /api/autenticacao/ativacoes` | Público | Consome token e retorna `204 No Content`. |
| `POST /api/autenticacao/ativacoes/reenviar` | Público | Retorna resposta genérica com `202 Accepted`. |
| `POST /api/autenticacao/sessoes` | Público | Autentica, cria cookie e retorna `200 OK` com usuário, perfil e escopo. |
| `GET /api/autenticacao/sessao` | Autenticado | Retorna a identidade e o escopo atuais. |
| `DELETE /api/autenticacao/sessao` | Autenticado | Encerra a sessão e retorna `204 No Content`. |
| `POST /api/autenticacao/recuperacoes` | Público | Aceita a solicitação com resposta genérica e `202 Accepted`. |
| `POST /api/autenticacao/redefinicoes` | Público | Consome token, troca a senha e retorna `204 No Content`. |
| `POST /api/unidades/{unidadeId}/usuarios-internos` | Administrador | Cria convite na própria unidade e retorna `201 Created`. |
| `PATCH /api/unidades/{unidadeId}/usuarios-internos/{id}` | Administrador | Altera perfil, vínculo ou estado conforme as restrições. |

Campos desconhecidos ou inválidos retornam `400 Bad Request`. No cadastro de
conta interna, um e-mail já utilizado retorna `409 Conflict`; no cadastro
público, ele mantém a resposta genérica para não permitir enumeração. Erros
seguem o formato comum da API com status, mensagem, campos e timestamp.
Respostas nunca incluem hash de senha, tokens, contadores de falha ou detalhes
internos de bloqueio.

## Dados e segurança

O modelo deve representar, no mínimo:

- `usuario`: identidade, nome, e-mail original, e-mail normalizado, hash da
  senha, perfil, estado, unidade opcional, profissional opcional, datas de
  criação/alteração e instante da última troca de senha;
- `sessao`: identificador opaco armazenado com segurança, usuário, criação,
  última atividade, expiração e revogação;
- `token_usuario`: hash do token, usuário, finalidade (`ATIVACAO`, `CONVITE` ou
  `RECUPERACAO`), criação, expiração e consumo;
- `evento_seguranca`: usuário quando identificável, tipo do evento, instante,
  resultado e informações técnicas minimizadas para auditoria.

O e-mail normalizado é único no banco. Uma conta interna exige `unidade_id`; o
perfil profissional exige também `profissional_id` único e pertencente à mesma
unidade. Senhas são processadas por algoritmo de hash adaptativo aprovado pela
biblioteca de segurança adotada, com fator de custo configurável e nunca são
criptografadas de forma reversível.

Logs e eventos de auditoria não contêm senha, cookie, token completo ou outros
segredos. Devem ser registrados, no mínimo: login bem-sucedido ou malsucedido,
logout, ativação, pedido e conclusão de recuperação, bloqueio/desbloqueio,
desativação e alteração de perfil ou vínculo. Somente administradores da mesma
unidade podem acessar eventos administrativos; tentativas de login permanecem
restritas à operação técnica.

## Critérios de aceitação

### Cadastro e ativação

- Um visitante cadastra uma conta de cliente válida e recebe o e-mail de
  ativação no Mailpit em desenvolvimento.
- O usuário não consegue autenticar antes de ativar o e-mail.
- O link ativa a conta uma única vez e deixa de funcionar após 24 horas.
- E-mails que diferem apenas por maiúsculas, minúsculas ou espaços não originam
  contas duplicadas.
- A resposta do cadastro público não permite distinguir um e-mail novo de um
  já cadastrado.
- Nenhum campo da requisição pública permite criar um perfil privilegiado.
- Uma conta interna somente é criada por administrador da mesma unidade e só
  fica ativa depois de concluir o convite.
- Um profissional não pode ser vinculado a duas contas nem a uma conta de outra
  unidade.

### Login e sessão

- Credenciais válidas de uma conta ativa criam uma sessão e permitem consultar
  a identidade atual.
- E-mail inexistente, senha incorreta e conta indisponível apresentam a mesma
  mensagem pública.
- A sexta tentativa inválida dentro da janela definida é bloqueada, e uma conta
  volta a aceitar tentativas após o prazo.
- O cookie de sessão não fica acessível a JavaScript e possui os atributos
  definidos para o ambiente.
- Logout, troca de senha, desativação e alteração de acesso invalidam as
  sessões correspondentes.
- Uma requisição mutável sem proteção CSRF válida é rejeitada.

### Recuperação

- A solicitação de recuperação retorna a mesma resposta para e-mail existente
  e inexistente.
- Um token válido permite definir uma nova senha uma única vez dentro de 30
  minutos.
- Um segundo pedido invalida o token anterior.
- Após a troca, a senha antiga e todas as sessões anteriores deixam de valer.
- Senhas e tokens não aparecem nas respostas, no banco em texto puro nem nos
  logs da aplicação.

### Controle de acesso

- Cada perfil acessa apenas as ações previstas na matriz.
- Cliente não consulta nem altera dados privados de outro cliente.
- Profissional acessa somente a agenda e o vínculo profissional autorizados.
- Recepção não altera catálogo, permissões ou configuração administrativa.
- Administrador não acessa nem altera recursos de outra unidade.
- Alterar IDs, perfil ou unidade manualmente na requisição não contorna as
  verificações do backend.
- Testes de integração cobrem respostas `401`, `403` e o isolamento por
  proprietário e unidade para todos os endpoints protegidos.

## Fora do escopo

- login social, OAuth externo, SSO e integração com diretórios corporativos;
- autenticação multifator e chaves de acesso (passkeys);
- uma conta com múltiplos perfis ou vinculada a várias unidades;
- edição ou troca de e-mail após a ativação;
- autoexclusão e anonimização completa de dados pessoais;
- painel global de superadministrador;
- políticas avançadas de detecção de fraude e dispositivos confiáveis.

## Dependências e relação com outras specs

- A spec de ambiente fornece PostgreSQL e Mailpit para persistência e teste dos
  e-mails.
- A spec de visão geral define os quatro perfis adotados neste documento.
- As specs de profissionais, disponibilidade, agendamentos e painéis devem
  reutilizar a identidade, o perfil e a unidade resolvidos por este módulo.
- A implementação deverá adicionar as dependências de segurança, persistência
  de sessão e envio de e-mail necessárias ao backend, além das telas e guardas
  de rota correspondentes no frontend.
