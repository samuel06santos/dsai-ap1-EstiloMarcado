-- =============================================================================
-- Estilo Marcado - Seed de dados mock (SOMENTE DESENVOLVIMENTO)
-- =============================================================================
-- Popula o banco com estabelecimento, filiais, profissionais, servicos,
-- contas de acesso, fichas de cliente (vinculadas ou avulsas), jornadas,
-- folgas, feriados, bloqueios, atendimentos e o historico de agendamento.
--
-- COMO FUNCIONA
--   * Todos os registros usam a faixa de IDs reservada 1000+ e o
--     estabelecimento "Estilo Marcado (Mock)". Assim a seed nunca colide com
--     dados reais/ja existentes do ambiente de desenvolvimento.
--   * E idempotente: pode rodar varias vezes sem duplicar registros. As colunas
--     de agendamento dos registros mock sao convergidas em bancos que ja
--     receberam uma versao anterior da seed.
--   * Pressupoe o schema ate a migracao V12 (agendamentos).
--   * Para comecar do zero, use a opcao de reset do script
--     (scripts/seed.ps1 -Reset ou scripts/seed.sh --reset), que limpa todos os
--     dados de dominio (nao apaga as migracoes do Flyway).
--
-- CONTAS MOCK (todas usam a senha: Estilo@2026)
--   admin@estilomarcado.dev             ADMINISTRADOR  (Unidade Centro)
--   recepcao@estilomarcado.dev          RECEPCAO       (Unidade Centro)
--   admin.batista@estilomarcado.dev     ADMINISTRADOR  (Unidade Batista Campos)
--   ana.souza@estilomarcado.dev         PROFISSIONAL   (Ana Souza)
--   carlos.lima@estilomarcado.dev       PROFISSIONAL   (Carlos Lima)
--   beatriz.rocha@estilomarcado.dev     PROFISSIONAL   (Beatriz Rocha)
--   diego.mendes@estilomarcado.dev      PROFISSIONAL   (Diego Mendes)
--   cliente@estilomarcado.dev           CLIENTE
--   joao.pereira@estilomarcado.dev      CLIENTE
--   maria.oliveira@estilomarcado.dev    CLIENTE
-- =============================================================================

\encoding UTF8

BEGIN;

-- ----------------------------------------------------------------------------
-- Estabelecimento e filiais (faixa 1000+)
-- ----------------------------------------------------------------------------
INSERT INTO estabelecimento (id, nome) VALUES
    (1000, 'Estilo Marcado (Mock)')
ON CONFLICT DO NOTHING;

INSERT INTO unidade (id, nome, nome_normalizado, estabelecimento_id, principal,
                     endereco, telefone, fuso_horario, ativa) VALUES
    (1000, 'Unidade Centro', 'unidade centro', 1000, TRUE,
     'Av. Presidente Vargas, 1200 - Belem/PA', '(91) 3222-1000', 'America/Sao_Paulo', TRUE),
    (1001, 'Unidade Batista Campos', 'unidade batista campos', 1000, FALSE,
     'Rua dos Mundurucus, 2450 - Belem/PA', '(91) 3222-2000', 'America/Sao_Paulo', TRUE)
ON CONFLICT DO NOTHING;

-- ----------------------------------------------------------------------------
-- Profissionais
-- ----------------------------------------------------------------------------
INSERT INTO profissional (id, nome, ativo, unidade_id, apresentacao) VALUES
    (1000, 'Ana Souza', TRUE, 1000, 'Especialista em cortes e coloracao.'),
    (1001, 'Carlos Lima', TRUE, 1000, 'Barbeiro e especialista em barba.'),
    (1002, 'Beatriz Rocha', TRUE, 1001, 'Cabeleireira e manicure.'),
    (1003, 'Diego Mendes', TRUE, 1001, 'Barbeiro e designer de sobrancelha.')
ON CONFLICT DO NOTHING;

-- ----------------------------------------------------------------------------
-- Servicos do catalogo
-- ----------------------------------------------------------------------------
INSERT INTO servico (id, unidade_id, nome, descricao, duracao_minutos, preco,
                     intervalo_minutos, ativo) VALUES
    (1000, 1000, 'Corte Masculino', 'Corte na maquina e tesoura com finalizacao.', 30, 45.00, 10, TRUE),
    (1001, 1000, 'Corte Feminino', 'Corte feminino com lavagem e escova.', 60, 80.00, 10, TRUE),
    (1002, 1000, 'Barba', 'Aparo e modelagem de barba com toalha quente.', 30, 35.00, 10, TRUE),
    (1003, 1000, 'Coloracao', 'Coloracao completa com tratamento.', 120, 180.00, 15, TRUE),
    (1004, 1001, 'Corte Masculino', 'Corte masculino com finalizacao.', 30, 45.00, 10, TRUE),
    (1005, 1001, 'Manicure', 'Cuidado completo das unhas das maos.', 45, 50.00, 10, TRUE),
    (1006, 1001, 'Hidratacao', 'Hidratacao profunda dos fios.', 40, 70.00, 10, TRUE)
ON CONFLICT DO NOTHING;

INSERT INTO servico_profissional (servico_id, profissional_id) VALUES
    (1000, 1000), (1000, 1001), (1001, 1000), (1002, 1001), (1003, 1000),
    (1004, 1002), (1004, 1003), (1005, 1002), (1006, 1002), (1006, 1003)
ON CONFLICT DO NOTHING;

-- ----------------------------------------------------------------------------
-- Contas de acesso (inseridas antes das fichas de cliente por causa de
-- cliente.usuario_id)
-- Hash BCrypt (custo 12) da senha "Estilo@2026".
-- ----------------------------------------------------------------------------
INSERT INTO usuario (id, nome, email, email_normalizado, senha_hash, telefone_contato,
                     perfil, estado, unidade_id, profissional_id, senha_alterada_em) VALUES
    (1000, 'Administrador Geral', 'admin@estilomarcado.dev', 'admin@estilomarcado.dev',
     '$2b$12$R94GRi/MWe4xPxCE4KKIGeo6mESFCnCcRNtrdL7t6P8MP6xDMN.bO', '(91) 98888-0001',
     'ADMINISTRADOR', 'ATIVA', 1000, NULL, CURRENT_TIMESTAMP),
    (1001, 'Recepcao Centro', 'recepcao@estilomarcado.dev', 'recepcao@estilomarcado.dev',
     '$2b$12$R94GRi/MWe4xPxCE4KKIGeo6mESFCnCcRNtrdL7t6P8MP6xDMN.bO', '(91) 98888-0002',
     'RECEPCAO', 'ATIVA', 1000, NULL, CURRENT_TIMESTAMP),
    (1002, 'Administradora Batista', 'admin.batista@estilomarcado.dev', 'admin.batista@estilomarcado.dev',
     '$2b$12$R94GRi/MWe4xPxCE4KKIGeo6mESFCnCcRNtrdL7t6P8MP6xDMN.bO', '(91) 98888-0003',
     'ADMINISTRADOR', 'ATIVA', 1001, NULL, CURRENT_TIMESTAMP),
    (1003, 'Ana Souza', 'ana.souza@estilomarcado.dev', 'ana.souza@estilomarcado.dev',
     '$2b$12$R94GRi/MWe4xPxCE4KKIGeo6mESFCnCcRNtrdL7t6P8MP6xDMN.bO', '(91) 98888-0004',
     'PROFISSIONAL', 'ATIVA', 1000, 1000, CURRENT_TIMESTAMP),
    (1004, 'Carlos Lima', 'carlos.lima@estilomarcado.dev', 'carlos.lima@estilomarcado.dev',
     '$2b$12$R94GRi/MWe4xPxCE4KKIGeo6mESFCnCcRNtrdL7t6P8MP6xDMN.bO', '(91) 98888-0005',
     'PROFISSIONAL', 'ATIVA', 1000, 1001, CURRENT_TIMESTAMP),
    (1005, 'Beatriz Rocha', 'beatriz.rocha@estilomarcado.dev', 'beatriz.rocha@estilomarcado.dev',
     '$2b$12$R94GRi/MWe4xPxCE4KKIGeo6mESFCnCcRNtrdL7t6P8MP6xDMN.bO', '(91) 98888-0006',
     'PROFISSIONAL', 'ATIVA', 1001, 1002, CURRENT_TIMESTAMP),
    (1006, 'Diego Mendes', 'diego.mendes@estilomarcado.dev', 'diego.mendes@estilomarcado.dev',
     '$2b$12$R94GRi/MWe4xPxCE4KKIGeo6mESFCnCcRNtrdL7t6P8MP6xDMN.bO', '(91) 98888-0007',
     'PROFISSIONAL', 'ATIVA', 1001, 1003, CURRENT_TIMESTAMP),
    (1007, 'Cliente Demo', 'cliente@estilomarcado.dev', 'cliente@estilomarcado.dev',
     '$2b$12$R94GRi/MWe4xPxCE4KKIGeo6mESFCnCcRNtrdL7t6P8MP6xDMN.bO', '(91) 98888-0008',
     'CLIENTE', 'ATIVA', NULL, NULL, CURRENT_TIMESTAMP),
    (1008, 'Joao Pereira', 'joao.pereira@estilomarcado.dev', 'joao.pereira@estilomarcado.dev',
     '$2b$12$R94GRi/MWe4xPxCE4KKIGeo6mESFCnCcRNtrdL7t6P8MP6xDMN.bO', '(91) 98888-0009',
     'CLIENTE', 'ATIVA', NULL, NULL, CURRENT_TIMESTAMP),
    (1009, 'Maria Oliveira', 'maria.oliveira@estilomarcado.dev', 'maria.oliveira@estilomarcado.dev',
     '$2b$12$R94GRi/MWe4xPxCE4KKIGeo6mESFCnCcRNtrdL7t6P8MP6xDMN.bO', '(91) 98888-0010',
     'CLIENTE', 'ATIVA', NULL, NULL, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;

-- ----------------------------------------------------------------------------
-- Clientes: fichas vinculadas a contas CLIENTE e clientes avulsos.
-- ON CONFLICT (id) DO UPDATE converge o vinculo em bancos ja semeados antes da
-- parte de agendamento existir.
-- ----------------------------------------------------------------------------
INSERT INTO cliente (id, nome, usuario_id, telefone_contato) VALUES
    (1000, 'Cliente Demo', 1007, '(91) 98888-0008'),
    (1001, 'Joao Pereira', 1008, '(91) 98888-0009'),
    (1002, 'Maria Oliveira', 1009, '(91) 98888-0010'),
    (1003, 'Pedro Santos', NULL, '(91) 97777-0003'),
    (1004, 'Juliana Costa', NULL, '(91) 97777-0004')
ON CONFLICT (id) DO UPDATE SET
    usuario_id = EXCLUDED.usuario_id,
    telefone_contato = EXCLUDED.telefone_contato;

-- ----------------------------------------------------------------------------
-- Jornada semanal (dia_semana ISO: 1=segunda ... 7=domingo)
-- ----------------------------------------------------------------------------
INSERT INTO jornada_intervalo (profissional_id, dia_semana, hora_inicio, hora_fim)
SELECT v.profissional_id, v.dia_semana, v.hora_inicio, v.hora_fim
FROM (VALUES
    -- Ana Souza: segunda a sexta, 09:00-12:00 e 13:00-18:00
    (1000, 1, TIME '09:00', TIME '12:00'), (1000, 1, TIME '13:00', TIME '18:00'),
    (1000, 2, TIME '09:00', TIME '12:00'), (1000, 2, TIME '13:00', TIME '18:00'),
    (1000, 3, TIME '09:00', TIME '12:00'), (1000, 3, TIME '13:00', TIME '18:00'),
    (1000, 4, TIME '09:00', TIME '12:00'), (1000, 4, TIME '13:00', TIME '18:00'),
    (1000, 5, TIME '09:00', TIME '12:00'), (1000, 5, TIME '13:00', TIME '18:00'),
    -- Carlos Lima: terca a sabado, 10:00-14:00 e 15:00-19:00
    (1001, 2, TIME '10:00', TIME '14:00'), (1001, 2, TIME '15:00', TIME '19:00'),
    (1001, 3, TIME '10:00', TIME '14:00'), (1001, 3, TIME '15:00', TIME '19:00'),
    (1001, 4, TIME '10:00', TIME '14:00'), (1001, 4, TIME '15:00', TIME '19:00'),
    (1001, 5, TIME '10:00', TIME '14:00'), (1001, 5, TIME '15:00', TIME '19:00'),
    (1001, 6, TIME '10:00', TIME '14:00'), (1001, 6, TIME '15:00', TIME '19:00'),
    -- Beatriz Rocha: segunda a sexta, 08:00-12:00 e 13:00-17:00
    (1002, 1, TIME '08:00', TIME '12:00'), (1002, 1, TIME '13:00', TIME '17:00'),
    (1002, 2, TIME '08:00', TIME '12:00'), (1002, 2, TIME '13:00', TIME '17:00'),
    (1002, 3, TIME '08:00', TIME '12:00'), (1002, 3, TIME '13:00', TIME '17:00'),
    (1002, 4, TIME '08:00', TIME '12:00'), (1002, 4, TIME '13:00', TIME '17:00'),
    (1002, 5, TIME '08:00', TIME '12:00'), (1002, 5, TIME '13:00', TIME '17:00'),
    -- Diego Mendes: segunda a sexta, 10:00-13:00 e 14:00-20:00
    (1003, 1, TIME '10:00', TIME '13:00'), (1003, 1, TIME '14:00', TIME '20:00'),
    (1003, 2, TIME '10:00', TIME '13:00'), (1003, 2, TIME '14:00', TIME '20:00'),
    (1003, 3, TIME '10:00', TIME '13:00'), (1003, 3, TIME '14:00', TIME '20:00'),
    (1003, 4, TIME '10:00', TIME '13:00'), (1003, 4, TIME '14:00', TIME '20:00'),
    (1003, 5, TIME '10:00', TIME '13:00'), (1003, 5, TIME '14:00', TIME '20:00')
) AS v(profissional_id, dia_semana, hora_inicio, hora_fim)
WHERE NOT EXISTS (
    SELECT 1 FROM jornada_intervalo j
    WHERE j.profissional_id = v.profissional_id
      AND j.dia_semana = v.dia_semana
      AND j.hora_inicio = v.hora_inicio
      AND j.hora_fim = v.hora_fim
);

-- ----------------------------------------------------------------------------
-- Excecoes de jornada (folgas e jornadas especiais)
-- ----------------------------------------------------------------------------
INSERT INTO excecao_jornada (id, profissional_id, data, tipo, motivo) VALUES
    (1000, 1000, CURRENT_DATE + 14, 'JORNADA_ESPECIAL', 'Acao especial de beleza'),
    (1001, 1001, CURRENT_DATE + 7, 'FOLGA', 'Folga programada'),
    (1002, 1002, CURRENT_DATE + 10, 'FOLGA', 'Compensacao de horas')
ON CONFLICT DO NOTHING;

INSERT INTO excecao_jornada_intervalo (id, excecao_id, hora_inicio, hora_fim) VALUES
    (1000, 1000, TIME '10:00', TIME '15:00')
ON CONFLICT DO NOTHING;

-- ----------------------------------------------------------------------------
-- Afastamentos
-- ----------------------------------------------------------------------------
INSERT INTO afastamento (id, profissional_id, data_inicio, data_fim, tipo, descricao) VALUES
    (1000, 1003, CURRENT_DATE + 20, CURRENT_DATE + 25, 'FERIAS', 'Ferias programadas'),
    (1001, 1001, CURRENT_DATE + 30, CURRENT_DATE + 31, 'LICENCA', 'Licenca medica')
ON CONFLICT DO NOTHING;

-- ----------------------------------------------------------------------------
-- Feriados
-- ----------------------------------------------------------------------------
INSERT INTO feriado (id, unidade_id, data, nome) VALUES
    (1000, 1000, DATE '2026-12-25', 'Natal'),
    (1001, 1000, DATE '2027-01-01', 'Confraternizacao Universal'),
    (1002, 1001, DATE '2026-12-25', 'Natal')
ON CONFLICT DO NOTHING;

-- ----------------------------------------------------------------------------
-- Bloqueios de agenda
-- ----------------------------------------------------------------------------
INSERT INTO bloqueio_agenda (id, unidade_id, profissional_id, data, dia_inteiro,
                             hora_inicio, hora_fim, motivo, criado_por) VALUES
    (1000, 1000, NULL, CURRENT_DATE + 6, TRUE, NULL, NULL, 'Manutencao eletrica', 1000),
    (1001, 1000, 1001, CURRENT_DATE + 4, FALSE, TIME '12:00', TIME '13:00', 'Intervalo estendido', 1000),
    (1002, 1001, NULL, CURRENT_DATE + 9, TRUE, NULL, NULL, 'Treinamento da equipe', 1002)
ON CONFLICT DO NOTHING;

-- ----------------------------------------------------------------------------
-- Atendimentos (agenda dos proximos dias)
--
-- Snapshots comerciais e de ocupacao conforme a SPEC de agendamento. A coluna
-- status/inicio nao e reescrita em reexecucoes (apenas os metadados convergem),
-- e nenhum par de atendimentos ativos do mesmo profissional se sobrepoe, o que
-- satisfaz a restricao EXCLUDE ex_atendimento_ocupacao.
-- ----------------------------------------------------------------------------
INSERT INTO atendimento (id, profissional_id, servico_id, cliente_id, inicio, status,
                         duracao_minutos, intervalo_minutos, servico_nome, preco_acordado,
                         fuso_horario_agendamento, criado_em, atualizado_em,
                         cancelado_em, cancelado_por, motivo_cancelamento) VALUES
    -- Ana Souza (Unidade Centro)
    (1000, 1000, 1000, 1000, (CURRENT_DATE + 1) + TIME '09:00', 'CONFIRMADO',
     30, 10, 'Corte Masculino', 45.00, 'America/Sao_Paulo',
     now() - INTERVAL '10 days', now() - INTERVAL '1 day', NULL, NULL, NULL),
    (1001, 1000, 1001, 1001, (CURRENT_DATE + 1) + TIME '11:00', 'AGENDADO',
     60, 10, 'Corte Feminino', 80.00, 'America/Sao_Paulo',
     now() - INTERVAL '10 days', now() - INTERVAL '1 day', NULL, NULL, NULL),
    (1002, 1000, 1003, 1002, (CURRENT_DATE + 1) + TIME '13:30', 'AGENDADO',
     120, 15, 'Coloracao', 180.00, 'America/Sao_Paulo',
     now() - INTERVAL '10 days', now() - INTERVAL '1 day', NULL, NULL, NULL),
    (1003, 1000, 1000, 1003, (CURRENT_DATE + 2) + TIME '09:30', 'AGENDADO',
     30, 10, 'Corte Masculino', 45.00, 'America/Sao_Paulo',
     now() - INTERVAL '10 days', now() - INTERVAL '1 day', NULL, NULL, NULL),
    (1004, 1000, 1001, 1004, (CURRENT_DATE + 2) + TIME '15:00', 'CANCELADO',
     60, 10, 'Corte Feminino', 80.00, 'America/Sao_Paulo',
     now() - INTERVAL '10 days', now() - INTERVAL '2 days',
     now() - INTERVAL '2 days', 1001, 'Cliente solicitou o cancelamento por telefone.'),
    -- Carlos Lima (Unidade Centro)
    (1005, 1001, 1002, 1001, (CURRENT_DATE + 1) + TIME '10:30', 'CONFIRMADO',
     30, 10, 'Barba', 35.00, 'America/Sao_Paulo',
     now() - INTERVAL '10 days', now() - INTERVAL '1 day', NULL, NULL, NULL),
    (1006, 1001, 1000, 1000, (CURRENT_DATE + 1) + TIME '16:00', 'AGENDADO',
     30, 10, 'Corte Masculino', 45.00, 'America/Sao_Paulo',
     now() - INTERVAL '10 days', now() - INTERVAL '1 day', NULL, NULL, NULL),
    (1007, 1001, 1002, 1002, (CURRENT_DATE + 3) + TIME '11:00', 'AGENDADO',
     30, 10, 'Barba', 35.00, 'America/Sao_Paulo',
     now() - INTERVAL '10 days', now() - INTERVAL '1 day', NULL, NULL, NULL),
    -- Beatriz Rocha (Unidade Batista Campos)
    (1008, 1002, 1005, 1003, (CURRENT_DATE + 1) + TIME '08:30', 'CONFIRMADO',
     45, 10, 'Manicure', 50.00, 'America/Sao_Paulo',
     now() - INTERVAL '10 days', now() - INTERVAL '1 day', NULL, NULL, NULL),
    (1009, 1002, 1004, 1004, (CURRENT_DATE + 1) + TIME '13:30', 'AGENDADO',
     30, 10, 'Corte Masculino', 45.00, 'America/Sao_Paulo',
     now() - INTERVAL '10 days', now() - INTERVAL '1 day', NULL, NULL, NULL),
    (1010, 1002, 1006, 1000, (CURRENT_DATE + 2) + TIME '10:00', 'AGENDADO',
     40, 10, 'Hidratacao', 70.00, 'America/Sao_Paulo',
     now() - INTERVAL '10 days', now() - INTERVAL '1 day', NULL, NULL, NULL),
    -- Diego Mendes (Unidade Batista Campos)
    (1011, 1003, 1004, 1001, (CURRENT_DATE + 1) + TIME '10:00', 'CONFIRMADO',
     30, 10, 'Corte Masculino', 45.00, 'America/Sao_Paulo',
     now() - INTERVAL '10 days', now() - INTERVAL '1 day', NULL, NULL, NULL),
    (1012, 1003, 1006, 1002, (CURRENT_DATE + 2) + TIME '15:00', 'AGENDADO',
     40, 10, 'Hidratacao', 70.00, 'America/Sao_Paulo',
     now() - INTERVAL '10 days', now() - INTERVAL '1 day', NULL, NULL, NULL),
    (1013, 1003, 1004, 1003, (CURRENT_DATE + 3) + TIME '17:00', 'AGENDADO',
     30, 10, 'Corte Masculino', 45.00, 'America/Sao_Paulo',
     now() - INTERVAL '10 days', now() - INTERVAL '1 day', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET
    servico_nome = EXCLUDED.servico_nome,
    preco_acordado = EXCLUDED.preco_acordado,
    fuso_horario_agendamento = EXCLUDED.fuso_horario_agendamento,
    criado_em = EXCLUDED.criado_em,
    atualizado_em = EXCLUDED.atualizado_em,
    cancelado_em = EXCLUDED.cancelado_em,
    cancelado_por = EXCLUDED.cancelado_por,
    motivo_cancelamento = EXCLUDED.motivo_cancelamento;

-- ----------------------------------------------------------------------------
-- Historico de agendamento (append-only; nao e reescrito em reexecucoes)
--   CRIACAO        -> um evento por atendimento
--   CONFIRMACAO    -> atendimentos 1000, 1005, 1008 e 1011
--   CANCELAMENTO   -> atendimento 1004
--   REAGENDAMENTO  -> atendimento 1003 (exemplo de historico)
-- ----------------------------------------------------------------------------
INSERT INTO agendamento_evento (id, atendimento_id, autor_id, tipo, ocorrido_em,
                                estado_anterior, estado_novo, inicio_anterior, inicio_novo) VALUES
    -- Criacao (autor: cliente vinculado ou recepcao da filial)
    (1000, 1000, 1007, 'CRIACAO', now() - INTERVAL '10 days', NULL, 'AGENDADO',
     NULL, (CURRENT_DATE + 1) + TIME '09:00'),
    (1001, 1001, 1008, 'CRIACAO', now() - INTERVAL '10 days', NULL, 'AGENDADO',
     NULL, (CURRENT_DATE + 1) + TIME '11:00'),
    (1002, 1002, 1009, 'CRIACAO', now() - INTERVAL '10 days', NULL, 'AGENDADO',
     NULL, (CURRENT_DATE + 1) + TIME '13:30'),
    (1003, 1003, 1001, 'CRIACAO', now() - INTERVAL '10 days', NULL, 'AGENDADO',
     NULL, (CURRENT_DATE + 2) + TIME '09:30'),
    (1004, 1004, 1001, 'CRIACAO', now() - INTERVAL '10 days', NULL, 'AGENDADO',
     NULL, (CURRENT_DATE + 2) + TIME '15:00'),
    (1005, 1005, 1008, 'CRIACAO', now() - INTERVAL '10 days', NULL, 'AGENDADO',
     NULL, (CURRENT_DATE + 1) + TIME '10:30'),
    (1006, 1006, 1007, 'CRIACAO', now() - INTERVAL '10 days', NULL, 'AGENDADO',
     NULL, (CURRENT_DATE + 1) + TIME '16:00'),
    (1007, 1007, 1009, 'CRIACAO', now() - INTERVAL '10 days', NULL, 'AGENDADO',
     NULL, (CURRENT_DATE + 3) + TIME '11:00'),
    (1008, 1008, 1001, 'CRIACAO', now() - INTERVAL '10 days', NULL, 'AGENDADO',
     NULL, (CURRENT_DATE + 1) + TIME '08:30'),
    (1009, 1009, 1001, 'CRIACAO', now() - INTERVAL '10 days', NULL, 'AGENDADO',
     NULL, (CURRENT_DATE + 1) + TIME '13:30'),
    (1010, 1010, 1007, 'CRIACAO', now() - INTERVAL '10 days', NULL, 'AGENDADO',
     NULL, (CURRENT_DATE + 2) + TIME '10:00'),
    (1011, 1011, 1001, 'CRIACAO', now() - INTERVAL '10 days', NULL, 'AGENDADO',
     NULL, (CURRENT_DATE + 1) + TIME '10:00'),
    (1012, 1012, 1009, 'CRIACAO', now() - INTERVAL '10 days', NULL, 'AGENDADO',
     NULL, (CURRENT_DATE + 2) + TIME '15:00'),
    (1013, 1013, 1001, 'CRIACAO', now() - INTERVAL '10 days', NULL, 'AGENDADO',
     NULL, (CURRENT_DATE + 3) + TIME '17:00'),
    -- Confirmacoes
    (1014, 1000, 1001, 'CONFIRMACAO', now() - INTERVAL '9 days', 'AGENDADO', 'CONFIRMADO',
     (CURRENT_DATE + 1) + TIME '09:00', (CURRENT_DATE + 1) + TIME '09:00'),
    (1015, 1005, 1001, 'CONFIRMACAO', now() - INTERVAL '9 days', 'AGENDADO', 'CONFIRMADO',
     (CURRENT_DATE + 1) + TIME '10:30', (CURRENT_DATE + 1) + TIME '10:30'),
    (1016, 1008, 1002, 'CONFIRMACAO', now() - INTERVAL '9 days', 'AGENDADO', 'CONFIRMADO',
     (CURRENT_DATE + 1) + TIME '08:30', (CURRENT_DATE + 1) + TIME '08:30'),
    (1017, 1011, 1002, 'CONFIRMACAO', now() - INTERVAL '9 days', 'AGENDADO', 'CONFIRMADO',
     (CURRENT_DATE + 1) + TIME '10:00', (CURRENT_DATE + 1) + TIME '10:00'),
    -- Cancelamento
    (1018, 1004, 1001, 'CANCELAMENTO', now() - INTERVAL '2 days', 'AGENDADO', 'CANCELADO',
     (CURRENT_DATE + 2) + TIME '15:00', (CURRENT_DATE + 2) + TIME '15:00'),
    -- Reagendamento (estado permanece AGENDADO; muda apenas o inicio)
    (1019, 1003, 1001, 'REAGENDAMENTO', now() - INTERVAL '1 day', 'AGENDADO', 'AGENDADO',
     (CURRENT_DATE + 2) + TIME '08:30', (CURRENT_DATE + 2) + TIME '09:30')
ON CONFLICT DO NOTHING;

-- ----------------------------------------------------------------------------
-- Reposiciona as sequencias apos insercao com ids explicitos
-- ----------------------------------------------------------------------------
SELECT setval(pg_get_serial_sequence('estabelecimento', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 1) FROM estabelecimento), 1));
SELECT setval(pg_get_serial_sequence('unidade', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 1) FROM unidade), 1));
SELECT setval(pg_get_serial_sequence('profissional', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 1) FROM profissional), 1));
SELECT setval(pg_get_serial_sequence('servico', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 1) FROM servico), 1));
SELECT setval(pg_get_serial_sequence('usuario', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 1) FROM usuario), 1));
SELECT setval(pg_get_serial_sequence('cliente', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 1) FROM cliente), 1));
SELECT setval(pg_get_serial_sequence('jornada_intervalo', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 1) FROM jornada_intervalo), 1));
SELECT setval(pg_get_serial_sequence('excecao_jornada', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 1) FROM excecao_jornada), 1));
SELECT setval(pg_get_serial_sequence('excecao_jornada_intervalo', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 1) FROM excecao_jornada_intervalo), 1));
SELECT setval(pg_get_serial_sequence('afastamento', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 1) FROM afastamento), 1));
SELECT setval(pg_get_serial_sequence('feriado', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 1) FROM feriado), 1));
SELECT setval(pg_get_serial_sequence('bloqueio_agenda', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 1) FROM bloqueio_agenda), 1));
SELECT setval(pg_get_serial_sequence('atendimento', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 1) FROM atendimento), 1));
SELECT setval(pg_get_serial_sequence('agendamento_evento', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 1) FROM agendamento_evento), 1));

COMMIT;

-- Resumo do que foi semeado.
SELECT 'estabelecimento' AS tabela, COUNT(*) AS registros FROM estabelecimento
UNION ALL SELECT 'unidade', COUNT(*) FROM unidade
UNION ALL SELECT 'profissional', COUNT(*) FROM profissional
UNION ALL SELECT 'servico', COUNT(*) FROM servico
UNION ALL SELECT 'cliente', COUNT(*) FROM cliente
UNION ALL SELECT 'usuario', COUNT(*) FROM usuario
UNION ALL SELECT 'jornada_intervalo', COUNT(*) FROM jornada_intervalo
UNION ALL SELECT 'excecao_jornada', COUNT(*) FROM excecao_jornada
UNION ALL SELECT 'afastamento', COUNT(*) FROM afastamento
UNION ALL SELECT 'feriado', COUNT(*) FROM feriado
UNION ALL SELECT 'bloqueio_agenda', COUNT(*) FROM bloqueio_agenda
UNION ALL SELECT 'atendimento', COUNT(*) FROM atendimento
UNION ALL SELECT 'agendamento_evento', COUNT(*) FROM agendamento_evento
ORDER BY tabela;
