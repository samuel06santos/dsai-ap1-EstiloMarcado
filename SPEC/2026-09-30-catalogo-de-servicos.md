# Catálogo de serviços  (2026-09-30)

## O quê e por quê

O catálogo de serviços permite à administração cadastrar e manter os serviços
oferecidos em cada unidade. Cada serviço define nome, descrição, duração, preço,
intervalo entre atendimentos e os profissionais habilitados a executá-lo. Ele é
a base do motor de disponibilidade e do agendamento: sem um serviço com duração
e ao menos um profissional habilitado, não é possível calcular horários nem
criar um agendamento.

## Critérios de aceitação

- Todo serviço pertence a uma única unidade.
- Nome e duração são obrigatórios; a duração é um número inteiro de minutos maior que zero.
- O preço é um valor decimal maior ou igual a zero, com até duas casas decimais.
- O intervalo entre atendimentos é opcional, inteiro, em minutos, e não pode ser negativo.
- Um serviço é executado por um ou mais profissionais habilitados da mesma unidade.
- Um serviço sem nenhum profissional habilitado não aparece na consulta de disponibilidade.
- O nome do serviço é único dentro da unidade.
- Serviços podem ser ativados e desativados; um serviço desativado deixa de aparecer para agendamento, mas continua no histórico.
- Nenhum serviço é removido fisicamente quando já existe um agendamento que o referencia.

## Fora do escopo

- Promoções, cupons, descontos e pacotes/combo de serviços.
- Precificação dinâmica e fluxo de pagamento e cobrança.
- Regras de jornada, folgas, feriados e bloqueios de agenda.
- Cálculo de horários disponíveis (motor de disponibilidade).
- Recursos compartilhados, como cadeira, sala ou equipamento.
- Comissões e repartição de valores entre profissionais e unidade.
