# Navegação visual e Meu perfil (2026-10-01)

## O que é e por que existe

O Estilo Marcado terá uma interface de aplicação com navegação lateral por
perfil, cabeçalho com menu de usuário e páginas com hierarquia visual clara.
O objetivo é substituir a aparência de formulários isolados por uma experiência
coerente de produto, sem alterar a identidade de cores nem ampliar permissões.

Esta SPEC cobre o layout, a navegação, a página **Meu perfil**, um telefone de
contato opcional e o favicon. Complementa as SPECs de autenticação,
estabelecimentos e painel profissional; o backend continua sendo a autoridade
para identidade e acesso.

## Situação atual e limites

- O frontend já possui as rotas `/`, `/conta`, `/administracao/usuarios`,
  `/administracao/estabelecimento` e `/unidades/:id`. A página `/conta` permite
  editar apenas o nome.
- A sessão identifica `CLIENTE`, `PROFISSIONAL`, `RECEPCAO` ou `ADMINISTRADOR`.
  Clientes não pertencem a uma filial. Contas internas pertencem a uma única
  filial, e profissionais têm também seu vínculo profissional.
- O backend ainda não armazena telefone pessoal nem foto de usuário. O telefone
  da filial é um dado diferente e não pode preencher o perfil pessoal.
- Não existe diretório público global de filiais nem fluxo de agendamentos do
  cliente. A agenda profissional existente ainda recebe um ID em cabeçalho;
  esse ID não pode ser usado para autorizar um novo link de navegação.

## Estrutura da interface

Em páginas autenticadas, o layout tem cabeçalho no topo, sidebar à esquerda e
área principal à direita. O cabeçalho exibe a marca, o contexto da página e,
no extremo direito, o botão com avatar padrão e nome do usuário. A sidebar
exibe grupos curtos de destinos permitidos, com ícone, rótulo e destaque da
rota atual. O conteúdo usa título, breve descrição e ações primárias visíveis
antes dos detalhes.

Páginas públicas e de autenticação (início público, cadastro, login, ativação e
recuperação) conservam o cabeçalho da marca e não exibem a sidebar. Após o
login, a navegação usa exclusivamente o perfil devolvido pela sessão: cliente
vai ao início autenticado; profissional vai à agenda quando ela estiver
integrada com segurança; recepção vai à página da própria filial;
administrador vai ao painel da própria filial. A marca sempre leva ao início
apropriado à sessão. Uma rota protegida aberta diretamente mantém sua proteção
e, se preciso, redireciona ao login.

### Sidebar por perfil

| Perfil | Itens ativos nesta entrega | Condições |
| --- | --- | --- |
| `CLIENTE` | Início (`/`), Meu perfil (`/conta`) | “Filial e serviços” (`/unidades/:id`) aparece somente quando há uma filial ativa selecionada por navegação prévia ou link direto. |
| `PROFISSIONAL` | Minha agenda (`/profissional/agenda`), Minha filial (`/minha-filial`), Meu perfil (`/conta`) | A agenda só é liberada após a integração segura descrita abaixo. A filial é sempre a da sessão. |
| `ADMINISTRADOR` | Visão geral e Minha filial (`/administracao/estabelecimento`, seções próprias), Profissionais (seção da própria filial), Equipe (`/administracao/usuarios`), Meu perfil (`/conta`) | “Filiais” e a edição do estabelecimento aparecem somente ao administrador da filial principal. |
| `RECEPCAO` | Minha filial (`/minha-filial`), Meu perfil (`/conta`) | Somente leitura dos dados comuns; operações da recepção dependem de SPEC e rotas próprias. |

“Minha filial” é uma página autenticada de leitura dos dados da filial e do
estabelecimento vinculados à sessão, inclusive quando a filial está inativa.
O administrador usa os links de seu painel para gerenciar **somente** a própria
filial. Para cliente, a filial selecionada é contexto de navegação no frontend,
não vínculo de conta nem permissão. Ao visitar uma filial pública ativa, o
frontend pode guardar o ID da última filial visitada no navegador; antes de
exibir o atalho, valida que ela continua pública. Sem filial selecionada, o
atalho não aparece.

Itens sem tela ou API funcional, como “Meus agendamentos” do cliente e operações
da recepção, não aparecem como links vazios. A sidebar pode ganhar esses itens
quando as respectivas funcionalidades forem implementadas. Nenhum item aponta
para uma página de outro perfil ou para uma filial escolhida por ID arbitrário.

O menu lateral recolhido em telas menores de 768 px abre por botão “Abrir menu”
no cabeçalho, fecha por botão, tecla `Escape`, escolha de destino ou clique fora,
e não deixa conteúdo inacessível atrás dele. Em desktop a sidebar mantém largura
estável, enquanto o conteúdo aproveita o espaço restante. Não haverá rolagem
horizontal a partir de 320 px.

### Integração da agenda profissional

Antes de ativar “Minha agenda”, a rota `/profissional/agenda` deve consumir a
agenda já especificada para o painel profissional, com o backend obtendo
`profissionalId` **da sessão validada**. O cabeçalho `X-Profissional-Id` enviado
pelo navegador deixa de selecionar identidade; IDs adulterados não permitem
consultar agenda alheia. A rota exige `PROFISSIONAL`, vínculo ativo e filial
ativa; respostas sem sessão ou sem permissão seguem a SPEC de autenticação.
Esta integração não inclui criar, cancelar ou reagendar atendimentos.

## Cabeçalho e menu do avatar

Todos os usuários autenticados veem o mesmo ícone vetorial de avatar padrão,
sem iniciais, foto remota ou upload. O avatar é um botão clicável com nome
acessível (“Abrir menu do usuário”) e abre um menu com **Meu perfil** e **Sair**.
“Meu perfil” abre `/conta`. “Sair” executa o logout existente, limpa o estado
da sessão no frontend e leva à tela pública. Durante a requisição, a ação
mostra estado de progresso e evita cliques duplicados; uma falha preserva a
sessão e mostra mensagem útil.

O menu abre e fecha por clique, `Enter` ou `Espaço`; fecha por `Escape`, clique
fora, mudança de rota ou logout. Ao fechar, o foco retorna ao botão quando
apropriado. As opções têm rótulos visíveis, foco por teclado e alvo de toque de
ao menos 44 × 44 px. O estado aberto é comunicado por `aria-expanded` e o
destino atual da sidebar por `aria-current="page"`.

## Página Meu perfil

A rota `/conta` permanece válida e passa a se apresentar como **Meu perfil**.
Uma seção de identidade mostra o avatar padrão, nome e tag legível do perfil
(`Cliente`, `Profissional`, `Recepção` ou `Administrador`). Uma seção de contato
permite editar nome e um único campo opcional “Telefone / WhatsApp”. E-mail é
mostrado em campo identificado e desabilitado; não é enviado na atualização.
A foto aparece como avatar padrão com indicação discreta de que a troca de foto
ainda não está disponível; não há botão de upload que falhe ou sugira uma
funcionalidade existente.

Para `PROFISSIONAL`, `ADMINISTRADOR` e `RECEPCAO`, uma seção “Meu vínculo”
exibe nome e estado da própria filial, além do nome do estabelecimento. Os
nomes funcionam como links: profissional e recepção abrem a página autenticada
`/minha-filial`, nas respectivas seções; administrador abre as seções da
própria filial e do estabelecimento em
`/administracao/estabelecimento`. Se a filial estiver inativa, a página de
leitura continua disponível e indica o estado sem oferecer sua página pública.
Clientes não veem essa seção.

O botão “Salvar alterações” só fica ativo com dados válidos e modificados.
Durante o envio, apresenta estado de progresso; em sucesso, confirma a
gravação sem apagar o formulário e atualiza imediatamente o nome exibido no
cabeçalho e na sessão local. Em erro, mantém os valores digitados e anuncia o
problema junto ao campo quando houver erro de validação. Recarregar a página
mostra os dados persistidos, inclusive quando o telefone foi removido.

## Dados e contrato HTTP

Uma migração Flyway posterior à versão `V8` acrescenta
`usuario.telefone_contato VARCHAR(20) NULL`, preservando usuários existentes
com valor nulo. O telefone pessoal só é consultado e alterado pelo dono da
conta neste fluxo; não aparece nas listagens públicas, no perfil de outros
usuários nem em logs e eventos de auditoria. Não há exigência de unicidade.

O campo aceita número brasileiro com DDD (10 ou 11 dígitos, com ou sem máscara)
ou formato internacional E.164. O backend remove caracteres de formatação,
normaliza números brasileiros para `+55` e armazena `+` seguido de 8 a 15
dígitos. Valor vazio ou `null` limpa o telefone; valor malformado recebe
`400 Bad Request` com indicação do campo. A interface oferece exemplo de
formato e usa `inputmode="tel"`, sem depender apenas de máscara visual.

| Método e rota | Acesso | Resultado |
| --- | --- | --- |
| `GET /api/usuarios/me` | Usuário autenticado | Mantém os campos atuais e acrescenta `telefoneContato` e, para conta interna, resumos de `filial` (`id`, `nome`, `ativa`) e `estabelecimento` (`id`, `nome`) obtidos do vínculo persistido. |
| `PATCH /api/usuarios/me` | Próprio usuário, com CSRF | Mantém `nome` obrigatório (2 a 120 caracteres) e aceita `telefoneContato` opcional; campo ausente preserva o valor, `null` ou vazio remove. Retorna o perfil atualizado. |
| `DELETE /api/autenticacao/sessao` | Usuário autenticado, com CSRF | Continua sendo a ação “Sair” do menu. |

`email`, `perfil`, `estado`, `unidadeId`, `profissionalId` e IDs do
estabelecimento não são editáveis pelo PATCH; tentativa de enviá-los recebe
`400 Bad Request`. Os resumos retornam apenas o vínculo da conta autenticada.
O backend valida esse escopo mesmo que o frontend monte um link incorreto.
A mudança de nome ou telefone não altera perfil, vínculo, e-mail ou senha e não
encerra a sessão. Respostas seguem os códigos `401`, `403` e `400` da SPEC de
autenticação. O avatar padrão é recurso estático do frontend e não acrescenta
campo ou arquivo à API.

## Linguagem visual e componentes

A paleta existente é a base: vinho `#672d3e`, vinho escuro `#4b1d2b`, rosado
`#f1dcd8`, creme `#faf7f5`, tinta `#2d2627` e borda `#e8dedb`. Superfícies
brancas ou creme, variações suaves dessas cores e sombras discretas criam
profundidade. Cores de status só são usadas para comunicar estado e não
substituem texto ou ícone. A tipografia atual (`DM Sans` e `Playfair Display`)
pode ser mantida, com tamanhos, pesos e alturas de linha consistentes.

Painéis deixam de ser uma sequência única de formulários: usam cabeçalhos de
seção, cartões de resumo, listas ou tabelas com ações por item, espaços em
branco e blocos de edição contextuais. Informações de leitura aparecem como
conteúdo, não como campos desabilitados, salvo o e-mail exigido na página de
perfil. Formulários têm agrupamento lógico, rótulos persistentes, ajuda curta,
estados de erro e confirmação. O início autenticado não mostra chamadas para
criar conta; informações como “próximo horário” só aparecem se vierem de dado
real, sem agendamentos fictícios.

Ícones vetoriais locais, de um conjunto consistente, acompanham navegação,
ações, estados e campos onde ajudam a reconhecer a função. Têm o mesmo traço,
tamanho e cor herdada (`currentColor`); não substituem rótulos e ícones apenas
decorativos ficam ocultos de leitores de tela. Não se depende de CDN de ícones.

Todos os links, botões habilitados e itens clicáveis exibem
`cursor: pointer`. Em `hover`, mudam cor e recebem sombra perceptível porém
suave; a opção ativa da sidebar também se distingue sem depender só da cor.
Controles desabilitados mantêm `cursor: not-allowed` e não recebem estilo que
sugira clique. Há `:focus-visible` claro e contraste mínimo WCAG AA para texto
e controles. Transições curtas respeitam `prefers-reduced-motion`.

O favicon será um SVG local da marca, com monograma simples reconhecível em
16 × 16 px, nas cores vinho e creme. O `index.html` o referencia por URL
correta tanto no servidor de desenvolvimento quanto no build de produção;
há fallback `.ico` ou `.png` apenas se a compatibilidade exigir. O título da
aba permanece “Estilo Marcado”. O avatar usa outro SVG local e consistente com
o conjunto de ícones.

## Critérios de aceitação

- Menu e sidebar mostram somente destinos compatíveis com o perfil e vínculo
  da sessão; administrador de filial secundária não vê gestão das demais.
- Links ativos funcionam após navegação direta e recarga. Destinos ainda não
  implementados não são apresentados como ações funcionais.
- O menu do avatar permite abrir Meu perfil e sair por mouse, teclado e toque;
  fecha corretamente e preserva o foco. O layout funciona de 320 px ao desktop.
- Cliente, profissional, recepção e administrador veem o mesmo avatar padrão.
  Não existe troca de foto nesta versão.
- Meu perfil exibe e-mail desabilitado e tag do perfil, salva nome e telefone
  do próprio usuário, permite limpar telefone e mantém os dados após recarga.
- Profissional e administrador conseguem seguir os links do perfil para sua
  própria filial e estabelecimento; filial inativa continua legível no contexto
  interno. Cliente não recebe vínculo que não possui.
- PATCH com campos de identidade ou vínculo adulterados é rejeitado, e acesso
  ao perfil sem sessão recebe `401`; números inválidos recebem `400`.
- A migração preserva as contas anteriores e deixa o telefone inicialmente
  nulo. A resposta pública nunca expõe esse telefone pessoal.
- A agenda profissional da sidebar usa identidade obtida da sessão; um
  cabeçalho ou ID adulterado não permite acessar a agenda de outra pessoa.
- Hover, foco e estado desabilitado são distintos; ícones têm rótulos ou
  descrição acessível; favicon aparece no desenvolvimento e na produção.

## Fora do escopo

- Upload, armazenamento, seleção ou corte de fotos de perfil.
- Alterar e-mail, perfil, senha ou vínculo por esta página.
- Criar fluxo de agendamento do cliente, marketplace ou diretório global de
  estabelecimentos.
- Adicionar novas operações de agenda, serviços ou recepção além de navegação
  e apresentação das funcionalidades já especificadas.
