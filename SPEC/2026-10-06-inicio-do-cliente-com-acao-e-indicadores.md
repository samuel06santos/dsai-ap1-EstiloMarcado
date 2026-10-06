# Início do cliente com ação principal e indicadores claros (2026-10-06)

## O que é e por que existe

O início do cliente já reúne próximo horário, histórico e contagens, mas a ação
para agendar fica escondida em estados vazios ou no fim de um atendimento. O
resumo atual apresenta três números dentro de um único bloco, com pouca
separação visual. Esta SPEC torna evidente o próximo passo e permite entender
o uso da conta em uma leitura rápida.

Complementa `2026-10-01-painel-do-cliente.md` e os atalhos contextuais de
`2026-10-05-atalhos-contextuais-e-estados-da-interface.md`. Não muda o ciclo de
vida nem a fonte dos números dos atendimentos.

## Escopo

- Na primeira área visível de “Início” do cliente, ao lado ou logo abaixo da
  saudação, destacar o botão primário “+ Criar agendamento”. O botão aparece
  com ou sem atendimento futuro e não depende de rolar até o histórico.
- A ação abre `/filiais`, onde o cliente escolhe a filial e segue a jornada já
  especificada. Se houver contexto de filial recente, ele pode aparecer como
  atalho secundário separado, sem retirar a liberdade de escolher outra filial.
- A seção “Resumo” apresenta três cards independentes: “Próximos
  atendimentos”, “Realizados” e “Cancelados”, com o número como foco visual.
  Em desktop, os cards ficam na mesma linha, centralizados e com texto
  centralizado; em telas estreitas, reorganizam-se sem rolagem horizontal.
- Os três valores usam exclusivamente `resumo.proximosAtivos`,
  `resumo.realizados` e `resumo.cancelados` de `GET /api/me/painel`. Zero é um
  valor real e deve aparecer como `0`, sem preencher com dados simulados.

## Hierarquia visual e comportamento

O botão principal mantém contraste, tamanho de toque e foco visível. Seu
destino é indicado pelo rótulo e não apenas pelo símbolo `+`; em leitores de
tela, é anunciado como “Criar agendamento”. A saudação, a ação e uma frase
curta cabem no cabeçalho sem criar um bloco que ocupe grande parte da tela.
O próximo atendimento continua destacado com data, filial e ações próprias;
o novo botão não deve parecer que altera ou duplica essa reserva.

Cada indicador é um card de leitura com título, número inteiro e descrição
curta quando necessária. Cores e ícones podem ajudar a distinguir estados,
mas o rótulo textual permanece. Os cards têm altura e espaçamento consistentes,
formam uma linha equilibrada com `justify-content: center` e não sugerem
clique se não houver destino. A seção mantém a ordem geral do painel: próximo
atendimento, próximos horários, resumo e histórico.

Durante o carregamento, o espaço dos indicadores não salta de modo brusco. Se
`GET /api/me/painel` falhar, a tela informa o erro e oferece nova tentativa;
não mostra contagens antigas como se fossem atuais. Um cliente sem reservas
continua vendo o botão e os três cards com `0`.

## Critérios de aceitação e testes

- Cliente com ou sem agendamento encontra “+ Criar agendamento” sem rolagem em
  uma tela desktop comum; a ação abre a descoberta de filiais.
- Os três cards exibem exatamente as contagens recebidas do painel, inclusive
  `0`, e são coerentes com próximo atendimento e histórico após atualização.
- Em desktop os cards aparecem em uma linha centrada, com números e textos
  centralizados; em 320 px não há corte ou rolagem horizontal.
- Carregamento, erro e conta sem atendimento têm estados distintos. O botão
  permanece utilizável quando o painel falha, pois seu destino é independente.
- A ação e os indicadores são compreensíveis com teclado e leitor de tela, com
  foco visível e contraste WCAG AA.

## Fora do escopo

- Recalcular indicadores no frontend, criar novos estados de atendimento ou
  alterar `GET /api/me/painel`.
- Reservar horário com um clique ou pular a escolha e revisão da jornada.

## Relação com outras SPECs e rastreabilidade

- Usa o contrato e a definição de “realizado” de
  `2026-10-01-painel-do-cliente.md`.
- O botão inicia `2026-10-05-descoberta-de-filiais-e-servicos.md` e segue
  `2026-10-05-jornada-guiada-de-agendamento.md`.
