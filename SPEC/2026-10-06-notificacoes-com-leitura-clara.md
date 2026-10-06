# Notificações com leitura clara e ação por item (2026-10-06)

## O que é e por que existe

A página de notificações precisa colocar os avisos antes das configurações e
deixar evidente o que é novo. A lista atual mostra códigos de tipo, data e
estado com pouca distinção visual; mesmo quando o cartão recebe aparência de
interação, clicar nele não executa uma ação. Esta SPEC organiza a página e
transforma cada aviso em um caminho compreensível para o contexto correspondente.

Complementa `2026-10-01-lista-de-espera-encaixes-notificacoes-historico-e-relatorios-operacionais.md`
e `2026-10-05-atalhos-contextuais-e-estados-da-interface.md`.

## Escopo e ordem da página

1. **Suas notificações** vem primeiro, logo após o título. Lista avisos do
   destinatário, com os mais recentes acima dos antigos.
2. **Preferências** vem depois da lista, mantendo as opções atuais de lembrete
   e aviso de lista de espera e a confirmação do salvamento.

O link “Ajustar preferências” do estado vazio continua levando à seção
inferior e posiciona o foco no primeiro controle. Falha ao carregar
preferências não apaga notificações já carregadas; falha da lista não faz a
página afirmar que não há avisos. Ações da lista e das preferências mostram
seus próprios estados de carregamento e erro.

## Apresentação e interação dos avisos

Cada item mostra um título em linguagem comum derivado do tipo (por exemplo,
“Agendamento confirmado” ou “Vaga disponível”), data e hora no formato local,
um resumo curto do destino e um estado textual “Não lida” ou “Lida”. Tipos
desconhecidos recebem rótulo genérico legível, sem código técnico em destaque.
Avisos não lidos têm fundo e borda de destaque discretos, indicador de estado
e título com peso maior. Avisos lidos mantêm contraste suficiente, com fundo
neutro. A distinção não depende apenas de cor.

O item inteiro é um alvo interativo de teclado e ponteiro, com cursor `pointer`
e mudança perceptível de cor, borda ou sombra no hover, sem movimento que
desloque a lista. `:focus-visible` mostra contorno claro. Clique ou Enter abre
o contexto correspondente: referência `AGENDAMENTO` leva ao detalhe permitido
do atendimento; `OFERTA` leva à lista de espera do próprio usuário e destaca
a oferta quando o destino suportar isso. Quando a referência não puder ser
aberta, a página informa o motivo e oferece o destino geral seguro, sem link
quebrado. A abertura de item não lido marca-o como lido pela operação
existente; a navegação aguarda o resultado ou informa falha sem perder o item.
O botão explícito “Marcar como lida” permanece como alternativa e não dispara
também o clique do cartão. Item já lido continua abrindo seu contexto.

A contagem de não lidas no cabeçalho é atualizada após a leitura e após
retorno à página. Ler um aviso não altera preferências nem cria notificações
novas. A página não exibe conteúdo privado de outra conta quando a sessão
muda; o servidor continua filtrando pelo destinatário.

## Dados e limites

Reutiliza `GET /api/me/notificacoes`, `PATCH
/api/me/notificacoes/{id}/leitura` e os endpoints atuais de preferências. Os
campos `tipo`, `referenciaTipo`, `referenciaId`, `criadoEm` e `lidoEm` já
permitem título, estado e destino. Caso um resumo específico dependa de dados
que não estejam no aviso, a interface usa texto breve e verdadeiro; não
inventa horário, serviço ou filial. Referências são abertas somente por rotas
e consultas que revalidam autorização no backend.

## Critérios de aceitação e testes

- “Suas notificações” precede “Preferências” em desktop e mobile; o atalho do
  estado vazio chega aos controles de preferências.
- Avisos lidos e não lidos são distinguíveis por texto, contraste e
  apresentação; o hover usa cursor `pointer` e o foco por teclado é visível.
- Clique e Enter no item abrem um destino válido, marcam o aviso não lido uma
  única vez e atualizam a contagem; o botão interno não causa duplo acionamento.
- Erro na leitura não esconde o aviso nem o marca visualmente como lido. Aviso
  de referência removida ou sem acesso não expõe dados e oferece retorno seguro.
- Lista vazia, falha de carga e salvamento de preferências têm mensagens
  distintas. A interface funciona a partir de 320 px e por leitor de tela.

## Fora do escopo

- Criar novos canais de envio, notificações promocionais ou tipos de evento.
- Alterar as regras de geração, entrega e retenção dos avisos existentes.

## Relação com outras SPECs e rastreabilidade

- Mantém leitura e preferências de
  `2026-10-01-lista-de-espera-encaixes-notificacoes-historico-e-relatorios-operacionais.md`.
- Aplica estados de erro e foco de
  `2026-10-05-atalhos-contextuais-e-estados-da-interface.md`.
