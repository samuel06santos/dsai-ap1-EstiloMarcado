# Página inicial pública e apresentação do produto (2026-10-05)

## O que é e por que existe

A página inicial pública deve explicar em poucos segundos o que o Estilo Marcado
oferece, inspirar confiança e levar o visitante ao primeiro agendamento. Hoje a
rota `/` mostra uma frase, dois botões e um cartão decorativo; não explica como
funciona, não ajuda a encontrar uma filial e não oferece prova social ou links
úteis no rodapé.

Esta SPEC reformula **somente a experiência pública**. O início do cliente
autenticado continua sendo o painel definido em
`2026-10-01-painel-do-cliente.md`; o início da equipe mantém os destinos por
perfil de `2026-10-01-navegacao-visual-e-meu-perfil.md`.

## Escopo

- Hero com proposta de valor objetiva, visual próprio e ação principal “Encontrar horário”.
- Resumo em três passos: escolher filial e serviço, selecionar horário e confirmar.
- Prévia curta dos benefícios para clientes e para a equipe, sem prometer recursos inexistentes.
- Acesso à descoberta de filiais definida em `2026-10-05-descoberta-de-filiais-e-servicos.md`.
- Carrossel automático de comentários e avaliações **simulados e identificados como demonstração**.
- Rodapé estruturado com navegação útil e crédito do projeto.
- Estados responsivos, acessíveis e de carregamento da página pública.

## Conteúdo e hierarquia

O primeiro bloco comunica o benefício em até uma frase de título e uma frase de
apoio, por exemplo: “Encontre seu horário de beleza sem trocar mensagens.” e
“Escolha o serviço, veja horários reais e agende em poucos passos.” A ação
principal abre `/filiais`; a secundária abre uma âncora “Como funciona”.
“Entrar” e “Criar conta” continuam visíveis no cabeçalho, mas não competem com
a ação principal. A busca de horários públicos não exige criar conta; a conta
é exigida apenas na confirmação da reserva, conforme a SPEC de agendamento.

Abaixo do hero, cartões curtos explicam os três passos, com ícones e textos
diretos. Uma seção de vantagens apresenta somente fatos do produto: horários
atualizados pela disponibilidade, revisão antes de confirmar e controle dos
próprios agendamentos. A página não exibe números de clientes, economia de
tempo, nota média ou dados de filiais que não sejam reais e verificáveis.
Visuais podem sugerir movimento por transições discretas; conteúdo essencial
fica visível sem animação ou interação.

## Carrossel de comentários e avaliações

O carrossel usa um pequeno conjunto local e curado de depoimentos fictícios,
com nome de personagem, comentário curto e nota ilustrativa de 1 a 5 estrelas.
O bloco inteiro traz o rótulo persistente **“Depoimentos simulados para
demonstrar a experiência”**; cada cartão também mostra “Exemplo fictício”.
Não apresenta essas falas como avaliações reais, não inventa fotos de clientes
nem calcula uma média pública a partir delas. O conteúdo é demonstrativo até
existir fluxo real de consentimento e avaliação, fora desta SPEC.

- Há avanço automático em intervalo de 6 segundos, pausa quando o foco está no
  carrossel ou o ponteiro o alcança, e controles “Anterior”, “Próximo” e
  “Pausar/Reproduzir”. Mudança manual reinicia o intervalo.
- Com `prefers-reduced-motion: reduce`, a reprodução automática fica desligada
  e não há transição animada. A leitura do item atual não é interrompida.
- Indicadores mostram a posição (“1 de 4”) e o item ativo; controles são
  operáveis por teclado e toque, com foco visível e alvo de ao menos 44 × 44 px.
- O carrossel não usa `aria-live` para anunciar cada avanço automático; ações
  manuais anunciam o item escolhido sem capturar o foco.

## Rodapé público

O rodapé agrupa links em “Explorar” (`/filiais`, “Como funciona”), “Conta”
(`/entrar`, `/cadastro`, `/recuperar-conta`) e “Projeto” (repositório). Links
levam a destinos existentes; se uma página institucional ainda não existir,
ela não aparece como link vazio. A base exibe o nome do produto e o texto
exato **“Created by Samuel e Renan”**, com o crédito inteiro vinculado a
`https://github.com/samuel06santos/dsai-ap1-EstiloMarcado`. Link externo abre
de modo previsível, com nome acessível e proteção `rel="noopener noreferrer"`
quando abrir em nova aba.

O rodapé aparece na página inicial e nas demais páginas públicas por meio do
layout comum, sem se repetir dentro do componente da home. Em páginas
autenticadas, a navegação própria permanece. Conteúdo de autenticação continua
legível em telas pequenas, sem rodapé sobreposto.

## Linguagem visual e experiência

A paleta vinho, rosado, creme e a tipografia do projeto seguem
`2026-10-01-navegacao-visual-e-meu-perfil.md`. A nova composição usa contraste
entre texto e fundo, hierarquia forte, espaços generosos, cartões com conteúdo
útil e ilustração otimizada. Em 320 px não há rolagem horizontal. Imagem
decorativa tem texto alternativo vazio; imagem informativa tem descrição.

O hero entrega título, explicação e ação principal sem depender do carregamento
de API. A descoberta de filiais pode ter prévia de dados reais quando disponível,
mas falha dessa prévia não bloqueia a navegação. Não há carrossel no topo que
esconda a ação principal.

## Critérios de aceitação

- Visitante entende o propósito da plataforma pelo hero e chega à descoberta
  de filiais em um clique.
- “Como funciona” apresenta os três passos sem exigir login.
- O carrossel avança automaticamente, oferece pausa e navegação manual e mantém
  rótulo visível de simulação em todos os estados.
- Modo de movimento reduzido desativa autoplay; teclado, leitor de tela e toque
  conseguem operar os controles sem perda de foco.
- O rodapé tem grupos de links funcionais e o crédito “Created by Samuel e
  Renan” aponta para o repositório correto.
- A página funciona de 320 px ao desktop, com texto legível, contraste WCAG AA
  e conteúdo principal utilizável mesmo quando dados auxiliares falham.
- Usuário autenticado continua vendo seu painel ou destino por perfil, sem
  depoimentos fictícios misturados a dados pessoais.

## Fora do escopo

- Publicar avaliações reais, gerar depoimentos por usuários ou inferir nota média.
- Marketplace com recomendação personalizada ou patrocínio de filiais.
- Mudar as regras de autorização, disponibilidade ou agendamento.

## Relação com outras SPECs e rastreabilidade

- Usa a linguagem visual de `2026-10-01-navegacao-visual-e-meu-perfil.md`.
- Usa a descoberta definida em `2026-10-05-descoberta-de-filiais-e-servicos.md`.
- Preserva o painel de `2026-10-01-painel-do-cliente.md`.

A rota `/filiais` e sua API pertencem à SPEC de descoberta. Esta implementação
prepara os links da página inicial e do rodapé; a navegação completa até a lista
de filiais será validada quando essa dependência for implementada.
