# Jornada guiada de agendamento e retomada (2026-10-05)

## O que é e por que existe

A página da filial já permite escolher serviço, profissional, data e horário,
mas apresenta os controles juntos e a revisão em outra rota. A entrada no
login interrompe a jornada e pode fazer o visitante perder o contexto. Esta
SPEC organiza o fluxo em passos curtos, preserva escolhas e explica claramente
por que um horário pode desaparecer antes da confirmação.

As regras de ocupação, preço acordado, prazo e idempotência permanecem em
`2026-10-01-agendamento-confirmacao-cancelamento-e-reagendamento.md`. A mudança
é de orientação, continuidade e clareza da interface.

## Escopo

- Passos “Serviço”, “Profissional”, “Dia e horário” e “Revisão”, com progresso
  visível e retorno aos passos anteriores sem reiniciar a busca.
- Opção “Qualquer profissional disponível”, mostrando o profissional real em
  cada horário e na revisão; a reserva sempre envia um profissional específico.
- Datas com disponibilidade destacada a partir do motor existente, sem mostrar
  horários de dias passados ou além do horizonte vigente.
- Preservação da escolha ao entrar ou criar conta e retorno à revisão.
- Recuperação de conflito `HORARIO_INDISPONIVEL` com alternativas úteis.
- Comprovante final mais claro, com ações para ver agendamento e voltar à filial.

## Fluxo e regras de apresentação

Cada passo tem título, frase de ajuda e uma única ação principal. Serviço
mostra nome, duração e preço atual. Profissional permite escolha nominal ou
“Qualquer disponível”. Dia e horário mostra primeiro os dias com vagas
consultadas; filtros de manhã/tarde são apenas apresentação dos resultados do
motor, não uma nova regra de disponibilidade. Se não houver horários, mostra
ação para mudar dia, profissional ou serviço. A revisão exibe filial, serviço,
profissional, data, horário local, fuso, duração, preço e estado esperado
`AGENDADO`, com link “Alterar” para cada escolha.

O resumo compacto acompanha a navegação em desktop e aparece antes da ação em
mobile. “Continuar” só fica habilitado após escolha válida; erros aparecem
perto do controle. O botão final informa “Confirmar agendamento” e mostra
progresso sem permitir duplo envio. O horário exibido é uma oferta sujeita a
revalidação, não uma reserva temporária.

## Continuidade após autenticação

O rascunho de escolha contém somente IDs públicos de filial, serviço e
profissional e o instante local escolhido. É mantido na URL de revisão com
parâmetros validados ou em `sessionStorage` por até 30 minutos, sem nome,
e-mail, telefone, cookie ou token. Login e cadastro recebem um destino de
retorno interno e restrito a `/unidades/:id/revisar`; redirecionamentos
externos ou caminhos de outro perfil são recusados. Depois da autenticação,
os dados são reconsultados: filial e serviço devem continuar ativos e o
horário livre. A escolha nunca é enviada automaticamente ao backend como
reserva. Se o cadastro ainda exige ativação de e-mail, a tela explica como
retomar depois; o link de revisão pode ser reaberto, sujeito à mesma validação.

“Qualquer profissional” é resolvido apenas ao selecionar um horário retornado
com `profissionalId`. Se houver mais de uma opção no mesmo horário, a tela
mostra profissional e permite escolha. Nenhum algoritmo atribui profissional
silenciosamente depois que o usuário confirma.

## Falhas e recuperação

Um `409 HORARIO_INDISPONIVEL` conserva filial, serviço e preferência de
profissional, volta ao passo de horário e consulta alternativas próximas.
O aviso diz que a vaga acabou e que nenhuma cobrança ou reserva foi feita.
Se a filial ou o serviço foi desativado, o fluxo oferece voltar à descoberta
ou selecionar outro serviço da mesma filial. Erro de rede preserva as escolhas
e permite tentar novamente com a mesma `Idempotency-Key` até obter resultado
definitivo; não cria uma nova chave para uma repetição do mesmo pedido.

Após sucesso, o comprovante mostra ID, status, horário local e fuso, filial,
serviço, profissional e próximo passo. “Ver meus agendamentos” leva ao item
correto. Nenhuma mensagem sugere confirmação pela equipe antes de ela ocorrer.

## Contratos e dados

Reutiliza `GET /api/unidades/{id}/servicos`, os endpoints públicos de
profissionais e horários, `POST /api/unidades/{id}/agendamentos` e
`GET /api/agendamentos/{id}`. O motor continua sendo a autoridade de horários,
e o `POST` continua exigindo `Idempotency-Key` e revalidando tudo na transação.
Não se introduz reserva provisória nem se persiste um rascunho no servidor.
Parâmetros de URL são contexto de interface e nunca autorizam acesso.

## Critérios de aceitação e testes

- Visitante inicia pela filial ou pela descoberta, entende os quatro passos e
  chega à revisão sem digitar ID ou repetir escolhas.
- Escolha “Qualquer profissional” sempre termina com profissional identificado
  antes do envio; a resposta confirma o mesmo profissional reservado.
- Login e cadastro retornam à revisão interna com dados revalidados; rota
  externa em `retorno` não é seguida.
- Vaga perdida, filial inativa, erro de rede e expiração do rascunho mostram
  recuperação concreta sem apagar escolhas ainda válidas.
- Envio repetido após falha de resposta mantém a mesma chave de idempotência e
  nunca cria dois atendimentos.
- Fluxo funciona por teclado, leitor de tela e toque, com progresso textual,
  foco visível e sem rolagem horizontal em 320 px.
- Comprovante reflete dados persistidos e permite chegar ao atendimento.

## Fora do escopo

- Reservas provisórias, bloqueio temporário de horário e lista de espera nova.
- Pagamento, cupom, pacote ou escolha automática invisível de profissional.
- Alteração das regras de cancelamento e reagendamento.

## Relação com outras SPECs e rastreabilidade

- Reutiliza motor de `2026-10-01-motor-de-disponibilidade.md`.
- Preserva transação, estados e idempotência de
  `2026-10-01-agendamento-confirmacao-cancelamento-e-reagendamento.md`.
- Integra a descoberta de `2026-10-05-descoberta-de-filiais-e-servicos.md` e
  o login de `2026-10-05-firebase-authentication.md`.
