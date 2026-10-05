# Firebase Authentication para senha e Google (2026-10-05)

## O que é e por que existe

O Estilo Marcado passará a oferecer entrada por Google e por e-mail/senha no
Firebase Authentication. O login atual usa BCrypt próprio, tokens de ativação
e recuperação locais e Spring Session JDBC. Adicionar somente Google ao lado
dessas credenciais criaria dois donos da identidade e dois ciclos de senha,
verificação de e-mail e recuperação. Esta SPEC define a migração, o vínculo
seguro das contas e a continuidade dos dados e permissões existentes.

Complementa `2026-09-30-autenticacao-e-controle-de-acesso.md`. Onde as duas
SPECs divergirem sobre verificação de senha, ativação e recuperação, esta
substitui essas partes após a migração; estados de conta, matriz de acesso,
sessão de aplicação, limites e autorização no backend continuam válidos.

## Situação atual e alternativas avaliadas

- `usuario.id` é referenciado por cliente, agendamentos, notificações e
  auditoria. `usuario.email_normalizado` é único; contas internas possuem
  perfil, filial e eventualmente vínculo profissional.
- A senha está em `usuario.senha_hash` com BCrypt. O login é verificado no
  backend; após sucesso há cookie `SESSION` HttpOnly, sessão com 30 minutos de
  inatividade e 12 horas de duração absoluta, além de CSRF em escritas.
- Cadastro, convite e recuperação usam tokens locais e envio de e-mail.

| Alternativa | Segurança e identidade | Manutenção e sessões | Decisão |
| --- | --- | --- | --- |
| Manter senha local e acrescentar Google pelo Firebase | Exige conciliar duas provas de posse, verificação de e-mail, bloqueios e recuperação. Coincidência de e-mail pode unir contas indevidamente. | Duas fontes de credencial e regras de revogação diferentes; mais caminhos de suporte e teste. | Rejeitada como arquitetura permanente. |
| Migrar senha e Google para Firebase, manter domínio e sessão da aplicação no backend | Um provedor de credenciais; UID verificado é ligado explicitamente ao `usuario.id`. Backend continua decidindo perfil e escopo. | Migração inicial e integração de ciclo de conta têm custo; depois há um fluxo de login e uma sessão de aplicação. | **Adotada.** |

A migração é viável porque o Firebase Admin SDK aceita importação de hashes
BCrypt. O custo inicial de importação e reconciliação é menor que manter, sem
prazo de término, duas autoridades para a mesma conta. Firebase Auth passa a
ser a autoridade das **credenciais**; PostgreSQL continua a autoridade de
perfil, estado, vínculos e dados de negócio. O e-mail não é chave de identidade
para login ou fusão: o vínculo imutável é `firebase_uid` único.

## Escopo e regras de conta

- Habilitar provedores E-mail/senha e Google no projeto Firebase, com domínios
  autorizados por ambiente e credenciais Admin fora do repositório.
- Migrar clientes e contas internas existentes, sem trocar `usuario.id`, IDs de
  clientes, agendamentos ou histórico.
- Oferecer cadastro e login por e-mail/senha, login Google e vínculo voluntário
  de Google a conta existente.
- Preservar ativação de e-mail para cliente, convite controlado para equipe,
  bloqueio/desativação e recuperação de senha.
- Continuar a usar a sessão de aplicação por cookie após a troca de um ID token
  Firebase por sessão no backend. Chamadas de negócio não recebem ID token.

## Migração e integridade dos dados

Uma migração de banco acrescenta `usuario.firebase_uid` anulável e único.
Para cada conta existente, a importação administrativa usa UID determinístico
derivado de `usuario.id` (por exemplo, `usuario-123`) e, quando presente, o hash
BCrypt existente. Contas convidadas sem senha são criadas sem credencial de
senha até a conclusão do convite.
Contas `ATIVA` cujo e-mail foi verificado pelo fluxo atual entram com
`emailVerified=true`; contas `PENDENTE` entram não verificadas. Contas
`BLOQUEADA` e `DESATIVADA` ficam impedidas de obter sessão de aplicação e são
desabilitadas no Firebase quando aplicável. A importação valida previamente
unicidade de UID e e-mail no Firebase, registra resultado por usuário e pode
ser repetida sem criar contas duplicadas. Colisões exigem tratamento explícito;
nenhum registro local ou remoto é sobrescrito automaticamente.

O corte ocorre somente após conferir quantidade, UIDs, e-mails normalizados,
estado e capacidade de login de amostra de cada perfil. Sessões locais antigas
são invalidadas no corte. Depois, o login por senha local é removido e
`senha_hash`, contadores de tentativa de senha e tokens locais de recuperação
deixam de ser usados; seus dados legados são removidos em migração posterior,
após janela de verificação e backup. Não há fallback silencioso para senha
local. O plano de reversão antes de remover hashes usa backup e configuração
de ambiente, com janela de corte documentada; após remoção, retorno exige fluxo
de redefinição de senha, não reconstrução de hash Firebase.

Novos cadastros e convites são orquestrados pelo backend, que cria a conta no
Firebase e o registro local, com compensação e fila de reconciliação quando
uma das gravações falha. Nenhuma conta remota órfã ganha acesso por si só.
`usuario.id` continua estável e `firebase_uid` nunca é reassociado por e-mail.
Nome e e-mail exibidos no produto vêm do banco local; alterações de e-mail
exigem fluxo próprio de verificação e atualização coordenada em ambos os lados.

## Cadastro, ativação, convite e recuperação

Cadastro público cria apenas `CLIENTE` em estado `PENDENTE`, com e-mail único
normalizado. A senha é enviada ao backend por HTTPS somente para criação no
Firebase; não é gravada, registrada em log nem validada contra hash local.
O link de verificação de e-mail é emitido pelo Firebase e entregue pelo canal
configurado. Após confirmar o link, o cliente volta ao aplicativo, obtém novo
ID token e o backend confirma `emailVerified` antes de ativar a conta local.
Até lá, mesmo um login Firebase válido não cria sessão da aplicação.

Administrador cria conta interna e convite para sua filial sob as restrições
existentes. O convite de uso único continua sendo controlado pelo backend;
ao concluí-lo, a senha é definida no Firebase, o e-mail é confirmado e a conta
local é ativada. O bootstrap do primeiro administrador segue o mesmo princípio.
Recuperação de senha usa link de ação gerado pelo Firebase, enviado pelo canal
da plataforma, e resposta pública genérica, com limites por origem e e-mail.
A página `/redefinir-senha` envia código de ação e nova senha ao backend; ele
confirma o código no Firebase, conclui a troca pela API de Auth, identifica o
UID afetado, revoga sessões de aplicação e tokens de atualização do Firebase,
registra auditoria e envia aviso. A senha não é persistida localmente. Se a
troca remota terminar mas a invalidação local falhar, a rotina de reconciliação
repete a invalidação e a checagem periódica de revogação limita a janela.
Alterações feitas fora desse fluxo por operador exigem rotina administrativa
de invalidação/reconciliação.

## Login Google e associação de contas

O frontend inicia o fluxo Google pelo SDK Firebase e apresenta alternativa
por senha quando popup ou redirecionamento falhar. Após autenticação, obtém ID
token e o envia somente a `POST /api/autenticacao/sessoes/firebase`, com CSRF.
O backend verifica assinatura, emissor, audiência, expiração e revogação pelo
Admin SDK; consulta UID e e-mail verificado; busca `usuario.firebase_uid` e
verifica `ATIVA`, perfil e vínculo. Apenas então cria a sessão da aplicação.
`email`, `perfil` e `unidadeId` enviados pelo navegador são ignorados para
autorização. Token Google recebido diretamente, sem troca por token Firebase,
não é aceito.

Para conta nova Google, o backend cria somente perfil `CLIENTE` após confirmar
e-mail verificado e inexistência de e-mail local. Se já existir conta local com
o mesmo e-mail e outro UID, a entrada é recusada com orientação para entrar na
conta existente e vincular Google autenticado; **não há fusão automática por
e-mail**, inclusive para conta pendente ou interna. O vínculo é iniciado na
página Meu perfil após reautenticação recente no Firebase com um método já
vinculado; usa a função de associação de provedores e confirma que o UID
permaneceu o da conta local. A sessão temporária do SDK é limpa ao concluir
ou cancelar o vínculo.
Falha por credencial já associada a outro UID requer resolução assistida e não
transfere agendamentos. A interface mostra os métodos vinculados e não permite
remover o último método de acesso da conta.

## Sessões, bloqueios e sincronização

Após verificar ID token com `checkRevoked`, o backend cria ou renova o ID da
sessão Spring, com cookie `HttpOnly`, `Secure` em produção e `SameSite=Lax`.
Continuam os limites de 30 minutos de inatividade e 12 horas absolutas,
CSRF, CORS restrito, `GET /api/autenticacao/sessao` e logout idempotente.
O SDK Firebase usa persistência somente durante o ato de login/vínculo; após
troca por sessão, limpa a sessão do SDK para evitar duas sessões persistentes
no navegador. Não se guarda ID token ou refresh token em `localStorage`.

Cada requisição de negócio revalida estado e versão de autorização da conta
local; bloqueio, desativação, mudança de perfil/vínculo e redefinição pelo
produto invalidam imediatamente suas sessões Spring. A aplicação também
revalida periodicamente no Firebase (intervalo máximo de 5 minutos) se o UID
foi desabilitado ou seus tokens foram revogados; falha nessa verificação fecha
a sessão. Para ações administrativas sensíveis, a verificação remota é feita
na própria requisição. Isso explicita o limite de até 5 minutos para uma
revogação feita **fora** do produto, enquanto alterações feitas no produto
têm invalidação local imediata. Indisponibilidade do Firebase impede novos
logins e operações sensíveis que exigem revalidação, sem transformar falha em
acesso liberado.

## Contrato HTTP e interface

| Método e rota | Acesso | Resultado |
| --- | --- | --- |
| `POST /api/autenticacao/cadastros` | Público | Cria cliente pendente no Firebase e localmente; resposta genérica `202`. |
| `POST /api/autenticacao/sessoes/firebase` | Público, com CSRF | Troca ID token verificado por sessão da aplicação; `200` com `SessaoResponse`. |
| `GET /api/autenticacao/sessao` | Sessão válida | Mantém o contrato atual com perfil e escopo locais. |
| `DELETE /api/autenticacao/sessao` | Sessão válida | Invalida cookie e contexto local; `204`. |
| `POST /api/autenticacao/recuperacoes` | Público | Envia link de recuperação Firebase com resposta genérica `202`. |
| `POST /api/autenticacao/redefinicoes` | Público, com CSRF | Confirma código e nova senha no Firebase, revoga sessões; `204`. |
| `POST /api/autenticacao/contas/google` | Sessão recente, com CSRF | Confirma vínculo Google ao mesmo UID; nunca troca `usuario.id`. |

As rotas de ativação, conclusão de recuperação e convite existentes são
adaptadas para concluir ações Firebase; não aceitam tokens legados após o corte.
Falhas de credencial, conta pendente ou bloqueada mantêm mensagem pública
genérica. A tela `/entrar` apresenta as duas opções com peso visual equilibrado,
estado de progresso, erro compreensível e retorno seguro à reserva em andamento.
Cadastro e recuperação informam o próximo passo. Perfil mostra provedores
vinculados. Botões têm rótulo e não dependem de ícone do Google como único nome.

## Segurança e operação

- Chaves Admin, projeto e domínios permitidos são separados por ambiente; a
  chave de serviço nunca vai ao frontend ou ao Git. Configuração pública do
  SDK não concede privilégios; regras e autorização continuam no backend.
- Cadastro e troca de token têm limites por origem; o provedor aplica seus
  próprios limites de senha. Auditoria registra método e resultado, nunca
  senha, ID token, refresh token, cookie ou URL de ação completa.
- Jobs de reconciliação identificam contas remotas órfãs, UID ausente, e-mail
  divergente e estados incompatíveis, sem corrigi-los por associação automática.
- Antes do corte, testar a experiência de redirecionamento em navegadores que
  restringem armazenamento de terceiros; popup e redirecionamento devem ter
  comportamento de recuperação claro.

## Critérios de aceitação e testes

- Usuários antigos de cada perfil entram com a mesma senha após importação
  BCrypt e preservam `usuario.id`, agendamentos, notificações e permissões.
- Cliente novo só ganha sessão depois da verificação de e-mail; Google com
  e-mail verificado cria apenas cliente, nunca perfil interno.
- Login por senha e Google do mesmo UID retorna o mesmo usuário local após
  vínculo explícito. E-mail coincidente com UID diferente não une contas.
- Token expirado, revogado, de outro projeto ou adulterado não cria sessão;
  conta bloqueada ou desativada também não.
- Logout, redefinição de senha e mudança de perfil encerram as sessões locais
  cabíveis; o cookie conserva limites de tempo e proteção CSRF.
- Falha entre Firebase e banco é detectável e reconciliável sem usuário local
  duplicado. Migração repetida não duplica UID nem e-mail.
- Testes de integração cobrem troca de token, autorização por perfil/filial,
  vínculo, contas pendentes, colisão de e-mail e revogação. Testes de interface
  cobrem ambos os métodos, retorno à reserva, cancelamento do popup e erro.

## Fora do escopo

- Usar Firestore ou Firebase Security Rules como fonte de dados de negócio.
- Cadastro público de profissional, recepção ou administrador.
- Fusão automática de contas ou importação automática de clientes avulsos.
- Login anônimo, telefone, MFA e alteração de e-mail sem fluxo verificado.

## Relação com outras SPECs e referências técnicas

- Preserva matriz de acesso e limites de `2026-09-30-autenticacao-e-controle-de-acesso.md`.
- Preserva perfil e navegação de `2026-10-01-navegacao-visual-e-meu-perfil.md`.
- Base técnica: [importação de usuários e BCrypt](https://firebase.google.com/docs/auth/admin/import-users),
  [verificação de ID tokens](https://firebase.google.com/docs/auth/admin/verify-id-tokens),
  [vínculo de provedores](https://firebase.google.com/docs/auth/web/account-linking),
  [gerenciamento de sessões](https://firebase.google.com/docs/auth/admin/manage-sessions),
  [links de ação por e-mail](https://firebase.google.com/docs/auth/admin/email-action-links),
  [confirmação de redefinição](https://firebase.google.com/docs/reference/rest/auth)
  e [login Google](https://firebase.google.com/docs/auth/web/google-signin).
