# Atalhos contextuais e estados da interface (2026-10-05)

## O que é e por que existe

O sistema já oferece agenda, lista de espera, notificações, relatórios e
cadastros, mas o usuário precisa reconhecer a próxima ação em páginas densas.
Esta SPEC cria uma camada consistente de orientação: ações contextuais nos
estados vazios, filtros legíveis, feedback de salvamento e caminhos de volta.
O objetivo é reduzir passos e dúvidas sem criar permissões ou operações de
negócio paralelas.

Complementa `2026-10-01-navegacao-visual-e-meu-perfil.md` e as SPECs de cada
recurso. Dados e ações continuam autorizados pelo backend.

## Escopo

- Cabeçalho de página padronizado com título, descrição de uma frase,
  contexto de filial/data quando aplicável e ação principal do perfil.
- Atalhos contextuais em agenda da recepção, agenda profissional, lista de
  espera, notificações, Meu perfil e administração da filial.
- Estados vazios distintos para “sem dados”, “sem resultado do filtro” e
  “não foi possível carregar”.
- Filtros com valores legíveis, resumo dos filtros ativos, limpeza em um toque
  e persistência na URL quando houver listagem navegável.
- Confirmação de ações, prevenção de duplo envio, preservação de formulários
  após erro e foco adequado em mensagens.

## Ações por contexto

| Contexto | Orientação e ação disponível |
| --- | --- |
| Cliente sem agendamento | Explicar que ainda não há reserva e oferecer “Encontrar horário”, abrindo `/filiais` ou a última filial pública válida. |
| Cliente com atendimento futuro | Mostrar próximo compromisso real e ações existentes “Ver”, “Reagendar” e “Cancelar” conforme elegibilidade retornada pelo backend. |
| Recepção sem atendimentos no período | Oferecer “Agendar atendimento” e “Alterar período”; manter filial da sessão e data atual. |
| Profissional sem agenda no dia | Indicar que não há atendimentos e oferecer mudança de dia ou “Minha disponibilidade”, sem permitir criação de reserva pelo perfil. |
| Lista de espera vazia | Para cliente, oferecer escolha de filial/serviço; para equipe, explicar que não há solicitações e manter filtros. |
| Notificações vazias | Dizer que não há avisos no momento e mostrar link para preferências quando esse destino existir. |
| Administração sem profissional ou serviço | Oferecer o cadastro autorizado correspondente, com texto que explica por que ele é necessário para abrir horários. |

Atalhos aparecem somente se o destino e a operação estiverem implementados e
permitidos para a sessão. Dados de outra filial nunca são usados para compor
uma sugestão. A página não inventa “próximo horário” ou contagem para preencher
espaço vazio.

## Filtros, formulários e feedback

Listagens com filtros mostram chips textuais (“Filial: Centro”, “Estado:
Confirmado”) e botão “Limpar filtros”. A URL representa período, estado e
seleções que possam ser compartilhadas; recarga ou retorno pelo navegador
restaura a consulta. IDs ficam apenas como valores internos de seletores com
nomes legíveis. Datas mostram o fuso da filial quando influenciam o resultado.

Toda mutação mostra estado “Salvando…” ou equivalente, bloqueia o mesmo envio
enquanto está pendente e confirma sucesso perto da ação. Erro de validação
identifica o campo e mantém o que foi digitado. Erro de rede oferece “Tentar
novamente” quando seguro; operações não idempotentes só repetem com o mesmo
controle de idempotência especificado no recurso. Mensagens importantes usam
região de status acessível sem mover o foco abruptamente; após navegação, o
foco chega ao título da nova página ou ao primeiro controle pertinente.

O menu lateral em mobile mantém destaque da rota atual e fecha após escolher
destino. Botões e links seguem o padrão de hover, foco e alvos de toque de
`2026-10-01-navegacao-visual-e-meu-perfil.md`. Rótulos permanecem visíveis;
ícones não são a única indicação de função. Tabelas extensas oferecem forma
legível em tela estreita, sem ocultar ação essencial atrás de rolagem
horizontal da página inteira.

## Dados e limites

Esta SPEC reaproveita endpoints existentes. Contagens, próximo atendimento e
elegibilidade de ação vêm das respostas dos recursos, não de inferências do
frontend. Onde uma tela ainda não recebe o dado necessário, a ação contextual
fica ausente até o contrato correspondente existir. Persistência de filtro
na URL não substitui validação de escopo: o backend ignora ou rejeita IDs de
outra filial e dados de outro usuário conforme as SPECs de autenticação.

## Critérios de aceitação e testes

- Cada contexto da tabela acima apresenta orientação curta e uma ação válida
  quando o usuário pode executá-la; ações proibidas não aparecem.
- “Sem dados”, “nenhum resultado” e falha de carregamento têm textos e ações
  diferentes, sem afirmar que uma lista está vazia quando a consulta falhou.
- Filtros mantêm nomes legíveis, podem ser limpos e são restaurados por URL
  após recarga ou volta; IDs adulterados não ampliam acesso.
- Formulário mantém valores depois de erro; envio pendente não é duplicado;
  sucesso e falha são anunciados de maneira acessível.
- Títulos, ações e estados funcionam a partir de 320 px, com teclado, foco
  visível e contraste WCAG AA.
- Nenhum atalho altera regras de agendamento, permissões ou filial da sessão.

## Fora do escopo

- Novo sistema de notificações, novos relatórios ou novos estados de atendimento.
- Recomendações personalizadas calculadas a partir de dados sensíveis.
- Reformular os contratos de cada recurso além de campos necessários para
  representar ações e contagens já especificadas.

## Relação com outras SPECs e rastreabilidade

- Aplica a base visual de `2026-10-01-navegacao-visual-e-meu-perfil.md`.
- Usa ações do cliente de `2026-10-01-painel-do-cliente.md`, da equipe de
  `2026-10-01-lista-de-espera-encaixes-notificacoes-historico-e-relatorios-operacionais.md`
  e da agenda de `2026-10-01-agendamento-confirmacao-cancelamento-e-reagendamento.md`.
- O atalho público leva à descoberta de
  `2026-10-05-descoberta-de-filiais-e-servicos.md`.
