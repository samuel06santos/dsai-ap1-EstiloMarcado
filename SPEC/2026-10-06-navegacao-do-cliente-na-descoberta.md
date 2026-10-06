# Navegação do cliente durante a descoberta de filiais (2026-10-06)

## O que é e por que existe

O menu do cliente oferece “Explorar filiais”, mas a rota `/filiais` é tratada
como página pública mesmo com sessão ativa. Ao abri-la, o layout autenticado e
a sidebar desaparecem, interrompendo a navegação entre as áreas da conta.
Além disso, “Filial e serviços” surge no menu a partir da última filial
visitada e ocupa espaço para um caminho que já pode ser alcançado pela
descoberta. Esta SPEC simplifica o menu e mantém o contexto de navegação.

Complementa `2026-10-05-descoberta-de-filiais-e-servicos.md` e altera apenas a
lista de destinos do cliente prevista em
`2026-10-01-painel-do-cliente.md`.

## Escopo

- Remover “Filial e serviços” somente da sidebar do perfil `CLIENTE`. A rota
  pública `/unidades/:id`, seus links diretos, o histórico e a jornada de
  agendamento continuam disponíveis.
- “Explorar filiais” continua no menu do cliente e abre `/filiais` dentro do
  layout autenticado quando há sessão `CLIENTE` válida. A sidebar permanece
  visível no desktop e marca esse item como rota ativa.
- Visitantes sem sessão continuam vendo a listagem `/filiais` no layout
  público, com a navegação e o rodapé públicos. A busca e os resultados da
  página são os mesmos nos dois contextos.
- Após abrir uma filial, voltar para `/filiais` preserva busca, tags de
  serviço e página pelos parâmetros de URL já definidos. O cliente consegue
  usar o menu para ir a “Início”, “Meus agendamentos” e “Notificações” sem
  passar por uma tela de login.

## Comportamento responsivo e de sessão

No desktop, a sidebar faz parte do layout e não deve sumir ao trocar entre
“Início” e “Explorar filiais”, inclusive em acesso direto e após recarga de
`/filiais`. Em mobile, ela continua sendo um painel aberto pelo botão de menu;
pode fechar após escolher a rota e deve poder ser aberta novamente. O estado
ativo da rota é perceptível sem depender só da cor.

Se a sessão expirar enquanto o cliente está em `/filiais`, a listagem pode
continuar pública, mas o menu e os dados de conta deixam de aparecer. O
carregamento inicial da sessão não deve mostrar brevemente uma sidebar de
outro usuário. Ao retornar do login, o cliente recupera a navegação
autenticada e os filtros públicos da URL.

Esta mudança é de composição visual e rotas. `/filiais` continua pública e
`/unidades/:id` continua sujeita às regras existentes de filial ativa,
serviços e disponibilidade. O contexto de filial recente pode continuar a
ajudar no agendamento, mas não cria um item de menu permanente.

## Critérios de aceitação e testes

- Cliente autenticado usa “Explorar filiais” e permanece com sidebar visível
  no desktop; o item fica ativo e os demais destinos funcionam.
- Acesso direto e recarga de `/filiais` com sessão válida reproduzem o mesmo
  layout autenticado; visitante sem sessão vê o layout público.
- “Filial e serviços” não aparece na sidebar do cliente, mesmo após visitar
  uma filial; `/unidades/:id` e links de agendamento seguem funcionais.
- Busca, filtro por tags e paginação persistem ao ir e voltar da filial.
- Em 320 px, o menu pode ser aberto e fechado por toque e teclado, sem cobrir
  permanentemente a lista de filiais ou prender o foco.

## Fora do escopo

- Retirar a página de filial, alterar o diretório público ou restringir
  `/filiais` a usuários autenticados.
- Modificar o menu e as permissões de profissional, recepção e administrador.

## Relação com outras SPECs e rastreabilidade

- Preserva busca e URL de `2026-10-05-descoberta-de-filiais-e-servicos.md`.
- Ajusta a sidebar de `2026-10-01-navegacao-visual-e-meu-perfil.md` e
  `2026-10-01-painel-do-cliente.md` apenas para o perfil cliente.
