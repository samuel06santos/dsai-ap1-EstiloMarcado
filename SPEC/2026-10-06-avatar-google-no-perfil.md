# Foto do Google no avatar da conta (2026-10-06)

## O que é e por que existe

O cabeçalho usa sempre `/avatar.svg`, mesmo quando a conta está vinculada ao
Google e possui foto. Mostrar essa imagem ajuda o usuário a reconhecer qual
conta está aberta, especialmente quando alterna entre acesso por Google e por
e-mail/senha. A foto é um detalhe de apresentação: não define identidade,
perfil, autorização ou estado da sessão.

Esta SPEC amplia `2026-10-05-firebase-authentication.md` e substitui apenas a
regra de avatar sempre padrão de
`2026-10-01-navegacao-visual-e-meu-perfil.md`.

## Escopo

- O avatar no cabeçalho e em Meu perfil mostra a foto associada ao provedor
  Google quando ele está vinculado à conta autenticada e oferece uma imagem
  válida. Isso vale também após entrar por e-mail/senha na mesma conta.
- Contas sem vínculo Google, sem foto, com foto indisponível ou com falha no
  carregamento continuam a mostrar o avatar local atual, sem imagem quebrada.
- A imagem acompanha o mesmo usuário após recarga da página e nova sessão da
  aplicação. Atualizações feitas no Google são refletidas em novo login ou em
  nova leitura autorizada do perfil, sem exigir atualização instantânea.
- A troca de conta, o logout e o desvínculo do Google retiram imediatamente a
  foto anterior da interface. A foto de outro usuário não deve aparecer durante
  o carregamento da sessão.

## Fonte dos dados e contrato

O Firebase Authentication permanece a fonte do vínculo e da foto do provedor
Google. O backend consulta os dados verificados do UID ligado a `usuario.id` e
expõe para a própria conta um campo opcional `fotoPerfilUrl` em
`GET /api/autenticacao/sessao` e `GET /api/usuarios/me`. As respostas de login
da aplicação devolvem o mesmo campo. `null` significa usar `/avatar.svg`.
O campo não é recebido em PATCH de perfil, cadastro ou login como prova de
identidade. A aplicação não usa o ID token no navegador para alimentar o avatar
depois que a sessão Firebase temporária é limpa.

Somente a foto do provedor `google.com` vinculado ao UID da conta pode preencher
`fotoPerfilUrl`. O backend aceita apenas URL HTTPS de origem de imagem
confiável do provedor, sem credenciais embutidas nem esquemas como `data:` ou
`javascript:`. A política de imagens do navegador permite apenas a origem
necessária, preservando as demais restrições de segurança. Falha temporária ao
consultar a foto não bloqueia a sessão; retorna `null` ou o último valor válido
da mesma conta. Cache, se usado, é associado a `usuario.id` e invalidado em
desvínculo, bloqueio ou troca de usuário.

O nome e o e-mail do usuário continuam vindo da sessão local. O texto
alternativo da foto não repete o nome já visível ao lado do avatar; o botão do
menu conserva o rótulo acessível “Abrir menu do usuário”.

## Experiência e estados

A foto ocupa o círculo de 36 px existente, preserva proporção com corte
centralizado e nunca deforma a imagem. O cabeçalho não muda de altura quando a
foto chega. Durante o carregamento da sessão, o avatar padrão pode ser mostrado
sem exibir dados da sessão anterior. Erro de rede da imagem troca para o SVG
local sem aviso intrusivo. Meu perfil usa a mesma imagem e o mesmo fallback,
sem prometer edição ou upload de foto.

## Critérios de aceitação e testes

- Conta com Google vinculado e foto válida mostra a foto após login por Google,
  por senha e após recarga; conta sem vínculo ou foto mostra `/avatar.svg`.
- Logout e entrada em outra conta não exibem a foto da conta anterior, nem por
  um instante durante a troca de sessão.
- URL inválida, resposta remota indisponível e falha de carregamento da imagem
  mantêm o avatar padrão, sem quebrar o menu ou o layout.
- A API nunca aceita `fotoPerfilUrl` enviado pelo cliente para alterar vínculo,
  sessão ou perfil. A foto exibida pertence ao UID associado ao usuário local.
- O avatar e seu botão funcionam em 320 px, com teclado, foco visível e leitor
  de tela, sem texto alternativo redundante.

## Fora do escopo

- Upload, corte, escolha manual, armazenamento de arquivos de avatar ou
  sincronização contínua da foto com o Google.
- Uso de foto para reconhecimento, autorização ou associação automática de
  contas pelo e-mail.

## Relação com outras SPECs e rastreabilidade

- Segue vínculo por UID, sessão da aplicação e regras de provedores de
  `2026-10-05-firebase-authentication.md`.
- Mantém menu, cabeçalho e linguagem visual de
  `2026-10-01-navegacao-visual-e-meu-perfil.md`.
