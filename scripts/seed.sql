-- =============================================================================
-- Estilo Marcado - Seed de dados mock (SOMENTE DESENVOLVIMENTO)
-- =============================================================================
-- Popula o banco com estabelecimentos, filiais, profissionais, servicos,
-- contas de acesso, fichas de cliente (vinculadas ou avulsas), jornadas,
-- folgas, feriados, bloqueios, atendimentos (historico e futuros), historico de
-- agendamento, lista de espera, ofertas de encaixe e notificacoes.
--
-- COMO FUNCIONA
--   * Todos os registros usam a faixa de IDs reservada 1000+ e os
--     estabelecimentos "Estilo Marcado" e "Studio Bella". Assim a
--     seed nunca colide com dados reais/ja existentes.
--   * E idempotente: pode rodar varias vezes sem duplicar registros. As colunas
--     de agendamento dos registros mock sao convergidas em bancos que ja
--     receberam versoes anteriores da seed.
--   * Pressupoe o schema ate a migracao V14.
--   * Para comecar do zero, use a opcao de reset do script
--     (scripts/seed.ps1 -Reset ou scripts/seed.sh --reset), que limpa todos os
--     dados de dominio (nao apaga as migracoes do Flyway).
--
-- CONTAS MOCK (todas usam a senha: Estilo@2026)
--   admin@estilomarcado.dev             ADMINISTRADOR  (Unidade Centro)
--   recepcao@estilomarcado.dev          RECEPCAO       (Unidade Centro)
--   admin.batista@estilomarcado.dev     ADMINISTRADOR  (Unidade Batista Campos)
--   admin.nazare@estilomarcado.dev      ADMINISTRADOR  (Unidade Nazare)
--   recepcao.umarizal@estilomarcado.dev RECEPCAO       (Unidade Umarizal)
--   ana.souza@estilomarcado.dev         PROFISSIONAL   (Ana Souza)
--   carlos.lima@estilomarcado.dev       PROFISSIONAL   (Carlos Lima)
--   beatriz.rocha@estilomarcado.dev     PROFISSIONAL   (Beatriz Rocha)
--   diego.mendes@estilomarcado.dev      PROFISSIONAL   (Diego Mendes)
--   fernanda.alves@estilomarcado.dev    PROFISSIONAL   (Fernanda Alves)
--   rafael.nunes@estilomarcado.dev      PROFISSIONAL   (Rafael Nunes)
--   patricia.gomes@estilomarcado.dev    PROFISSIONAL   (Patricia Gomes)
--   lucas.barros@estilomarcado.dev      PROFISSIONAL   (Lucas Barros)
--   cliente@estilomarcado.dev           CLIENTE
--   joao.pereira@estilomarcado.dev      CLIENTE
--   maria.oliveira@estilomarcado.dev    CLIENTE
-- =============================================================================

\encoding UTF8

BEGIN;

-- ----------------------------------------------------------------------------
-- Estabelecimentos e filiais (faixa 1000+)
-- ----------------------------------------------------------------------------
INSERT INTO estabelecimento (id, nome) VALUES
    (1000, 'Estilo Marcado'),
    (1001, 'Studio Bella'),
    (1002, 'Belle Hair')
ON CONFLICT DO NOTHING;

INSERT INTO unidade (id, nome, nome_normalizado, estabelecimento_id, principal,
                     endereco, telefone, fuso_horario, ativa) VALUES
    (1000, 'Unidade Centro', 'unidade centro', 1000, TRUE,
     'Av. Presidente Vargas, 1200 - Belem/PA', '(91) 3222-1000', 'America/Sao_Paulo', TRUE),
    (1001, 'Unidade Batista Campos', 'unidade batista campos', 1000, FALSE,
     'Rua dos Mundurucus, 2450 - Belem/PA', '(91) 3222-2000', 'America/Sao_Paulo', TRUE),
    (1002, 'Unidade Nazare', 'unidade nazare', 1001, TRUE,
     'Tv. Quintino Bocaiuva, 780 - Belem/PA', '(91) 3223-3000', 'America/Sao_Paulo', TRUE),
    (1003, 'Unidade Umarizal', 'unidade umarizal', 1001, FALSE,
     'Rua Domingos Marreiros, 1500 - Belem/PA', '(91) 3223-4000', 'America/Sao_Paulo', TRUE),
    (1004, 'Unidade Guama', 'unidade guama', 1000, FALSE,
     'Av. Perimetral, 900 - Belem/PA', '(91) 3224-5000', 'America/Sao_Paulo', TRUE),
    (1005, 'Unidade Marco', 'unidade marco', 1000, FALSE,
     'Av. Alcindo Cacela, 2200 - Belem/PA', '(91) 3224-6000', 'America/Sao_Paulo', TRUE),
    (1006, 'Unidade Icoaraci', 'unidade icoaraci', 1000, FALSE,
     'Rod. Augusto Montenegro, 4300 - Belem/PA', '(91) 3224-7000', 'America/Sao_Paulo', TRUE),
    (1007, 'Unidade Outeiro', 'unidade outeiro', 1000, FALSE,
     'Estrada do Outeiro, 500 - Belem/PA', '(91) 3224-8000', 'America/Sao_Paulo', TRUE),
    (1008, 'Unidade Pedreira', 'unidade pedreira', 1001, FALSE,
     'Av. Pedro Miranda, 1200 - Belem/PA', '(91) 3225-5000', 'America/Sao_Paulo', TRUE),
    (1009, 'Unidade Sacramenta', 'unidade sacramenta', 1001, FALSE,
     'Av. Senador Lemos, 3000 - Belem/PA', '(91) 3225-6000', 'America/Sao_Paulo', TRUE),
    (1010, 'Unidade Canudos', 'unidade canudos', 1001, FALSE,
     'Av. Independencia, 800 - Belem/PA', '(91) 3225-7000', 'America/Sao_Paulo', TRUE),
    (1011, 'Unidade Ananindeua', 'unidade ananindeua', 1002, TRUE,
     'BR-316, km 8, 1500 - Ananindeua/PA', '(91) 3226-5000', 'America/Sao_Paulo', TRUE),
    (1012, 'Unidade Castanhal', 'unidade castanhal', 1002, FALSE,
     'Av. Barao do Rio Branco, 1100 - Castanhal/PA', '(91) 3226-6000', 'America/Sao_Paulo', TRUE),
    (1013, 'Unidade Marituba', 'unidade marituba', 1002, FALSE,
     'Rod. BR-316, km 12, 200 - Marituba/PA', '(91) 3226-7000', 'America/Sao_Paulo', TRUE)
ON CONFLICT DO NOTHING;

-- ----------------------------------------------------------------------------
-- Profissionais
-- ----------------------------------------------------------------------------
INSERT INTO profissional (id, nome, ativo, unidade_id, apresentacao) VALUES
    (1000, 'Ana Souza', TRUE, 1000, 'Especialista em cortes e coloracao.'),
    (1001, 'Carlos Lima', TRUE, 1000, 'Barbeiro e especialista em barba.'),
    (1002, 'Beatriz Rocha', TRUE, 1001, 'Cabeleireira e manicure.'),
    (1003, 'Diego Mendes', TRUE, 1001, 'Barbeiro e designer de sobrancelha.'),
    (1004, 'Fernanda Alves', TRUE, 1002, 'Cabeleireira e colorista.'),
    (1005, 'Rafael Nunes', TRUE, 1002, 'Barbeiro e especialista em barba.'),
    (1006, 'Patricia Gomes', TRUE, 1003, 'Manicure e cabeleireira.'),
    (1007, 'Lucas Barros', TRUE, 1003, 'Barbeiro e terapeuta capilar.'),
    (1008, 'Renata Dias', TRUE, 1004, 'Cabeleireira e escovista.'),
    (1009, 'Bruno Carvalho', TRUE, 1005, 'Barbeiro e especialista em barba.'),
    (1010, 'Camila Fontes', TRUE, 1006, 'Cabeleireira e manicure.'),
    (1011, 'Thiago Moraes', TRUE, 1007, 'Barbeiro e terapeuta capilar.'),
    (1012, 'Larissa Pinto', TRUE, 1008, 'Colorista e cabeleireira.'),
    (1013, 'Gustavo Reis', TRUE, 1009, 'Barbeiro e designer de sobrancelha.'),
    (1014, 'Aline Barbosa', TRUE, 1010, 'Manicure e pedicure.'),
    (1015, 'Marcelo Tavares', TRUE, 1011, 'Barbeiro e especialista em barba.'),
    (1016, 'Sofia Ribeiro', TRUE, 1012, 'Cabeleireira e manicure.'),
    (1017, 'Vitor Hugo', TRUE, 1013, 'Barbeiro e colorista.')
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
    (1006, 1001, 'Hidratacao', 'Hidratacao profunda dos fios.', 40, 70.00, 10, TRUE),
    (1020, 1002, 'Corte Masculino', 'Corte masculino com finalizacao.', 30, 45.00, 10, TRUE),
    (1021, 1002, 'Corte Feminino', 'Corte feminino com lavagem e escova.', 60, 85.00, 10, TRUE),
    (1022, 1002, 'Barba', 'Aparo e modelagem de barba com toalha quente.', 30, 35.00, 10, TRUE),
    (1023, 1003, 'Corte Masculino', 'Corte masculino com finalizacao.', 30, 45.00, 10, TRUE),
    (1024, 1003, 'Manicure', 'Cuidado completo das unhas das maos.', 45, 50.00, 10, TRUE),
    (1025, 1003, 'Hidratacao', 'Hidratacao profunda dos fios.', 40, 70.00, 10, TRUE),
    (1100, 1004, 'Corte Masculino', 'Corte masculino com finalizacao.', 30, 45.00, 10, TRUE),
    (1101, 1004, 'Corte Feminino', 'Corte feminino com lavagem e escova.', 60, 85.00, 10, TRUE),
    (1102, 1005, 'Corte Masculino', 'Corte masculino com finalizacao.', 30, 45.00, 10, TRUE),
    (1103, 1005, 'Barba', 'Aparo e modelagem de barba com toalha quente.', 30, 35.00, 10, TRUE),
    (1104, 1006, 'Corte Feminino', 'Corte feminino com lavagem e escova.', 60, 85.00, 10, TRUE),
    (1105, 1006, 'Manicure', 'Cuidado completo das unhas das maos.', 45, 50.00, 10, TRUE),
    (1106, 1007, 'Corte Masculino', 'Corte masculino com finalizacao.', 30, 45.00, 10, TRUE),
    (1107, 1007, 'Hidratacao', 'Hidratacao profunda dos fios.', 40, 70.00, 10, TRUE),
    (1108, 1008, 'Corte Masculino', 'Corte masculino com finalizacao.', 30, 45.00, 10, TRUE),
    (1109, 1008, 'Coloracao', 'Coloracao completa com tratamento.', 120, 180.00, 15, TRUE),
    (1110, 1009, 'Corte Feminino', 'Corte feminino com lavagem e escova.', 60, 85.00, 10, TRUE),
    (1111, 1009, 'Manicure', 'Cuidado completo das unhas das maos.', 45, 50.00, 10, TRUE),
    (1112, 1010, 'Barba', 'Aparo e modelagem de barba com toalha quente.', 30, 35.00, 10, TRUE),
    (1113, 1010, 'Hidratacao', 'Hidratacao profunda dos fios.', 40, 70.00, 10, TRUE),
    (1114, 1011, 'Corte Masculino', 'Corte masculino com finalizacao.', 30, 45.00, 10, TRUE),
    (1115, 1011, 'Corte Feminino', 'Corte feminino com lavagem e escova.', 60, 85.00, 10, TRUE),
    (1116, 1012, 'Manicure', 'Cuidado completo das unhas das maos.', 45, 50.00, 10, TRUE),
    (1117, 1012, 'Hidratacao', 'Hidratacao profunda dos fios.', 40, 70.00, 10, TRUE),
    (1118, 1013, 'Corte Masculino', 'Corte masculino com finalizacao.', 30, 45.00, 10, TRUE),
    (1119, 1013, 'Coloracao', 'Coloracao completa com tratamento.', 120, 180.00, 15, TRUE)
ON CONFLICT DO NOTHING;

INSERT INTO servico_profissional (servico_id, profissional_id) VALUES
    (1000, 1000), (1000, 1001), (1001, 1000), (1002, 1001), (1003, 1000),
    (1004, 1002), (1004, 1003), (1005, 1002), (1006, 1002), (1006, 1003),
    (1020, 1004), (1020, 1005), (1021, 1004), (1022, 1005),
    (1023, 1006), (1023, 1007), (1024, 1006), (1025, 1006), (1025, 1007),
    (1100, 1008), (1101, 1008), (1102, 1009), (1103, 1009),
    (1104, 1010), (1105, 1010), (1106, 1011), (1107, 1011),
    (1108, 1012), (1109, 1012), (1110, 1013), (1111, 1013),
    (1112, 1014), (1113, 1014), (1114, 1015), (1115, 1015),
    (1116, 1016), (1117, 1016), (1118, 1017), (1119, 1017)
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
     'CLIENTE', 'ATIVA', NULL, NULL, CURRENT_TIMESTAMP),
    (1030, 'Fernanda Alves', 'fernanda.alves@estilomarcado.dev', 'fernanda.alves@estilomarcado.dev',
     '$2b$12$R94GRi/MWe4xPxCE4KKIGeo6mESFCnCcRNtrdL7t6P8MP6xDMN.bO', '(91) 98888-0011',
     'PROFISSIONAL', 'ATIVA', 1002, 1004, CURRENT_TIMESTAMP),
    (1031, 'Rafael Nunes', 'rafael.nunes@estilomarcado.dev', 'rafael.nunes@estilomarcado.dev',
     '$2b$12$R94GRi/MWe4xPxCE4KKIGeo6mESFCnCcRNtrdL7t6P8MP6xDMN.bO', '(91) 98888-0012',
     'PROFISSIONAL', 'ATIVA', 1002, 1005, CURRENT_TIMESTAMP),
    (1032, 'Patricia Gomes', 'patricia.gomes@estilomarcado.dev', 'patricia.gomes@estilomarcado.dev',
     '$2b$12$R94GRi/MWe4xPxCE4KKIGeo6mESFCnCcRNtrdL7t6P8MP6xDMN.bO', '(91) 98888-0013',
     'PROFISSIONAL', 'ATIVA', 1003, 1006, CURRENT_TIMESTAMP),
    (1033, 'Lucas Barros', 'lucas.barros@estilomarcado.dev', 'lucas.barros@estilomarcado.dev',
     '$2b$12$R94GRi/MWe4xPxCE4KKIGeo6mESFCnCcRNtrdL7t6P8MP6xDMN.bO', '(91) 98888-0014',
     'PROFISSIONAL', 'ATIVA', 1003, 1007, CURRENT_TIMESTAMP),
    (1034, 'Administracao Nazare', 'admin.nazare@estilomarcado.dev', 'admin.nazare@estilomarcado.dev',
     '$2b$12$R94GRi/MWe4xPxCE4KKIGeo6mESFCnCcRNtrdL7t6P8MP6xDMN.bO', '(91) 98888-0015',
     'ADMINISTRADOR', 'ATIVA', 1002, NULL, CURRENT_TIMESTAMP),
    (1035, 'Recepcao Umarizal', 'recepcao.umarizal@estilomarcado.dev', 'recepcao.umarizal@estilomarcado.dev',
     '$2b$12$R94GRi/MWe4xPxCE4KKIGeo6mESFCnCcRNtrdL7t6P8MP6xDMN.bO', '(91) 98888-0016',
     'RECEPCAO', 'ATIVA', 1003, NULL, CURRENT_TIMESTAMP)
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
    (1003, 5, TIME '10:00', TIME '13:00'), (1003, 5, TIME '14:00', TIME '20:00'),
    -- Fernanda Alves: segunda a sexta, 09:00-12:00 e 13:00-18:00
    (1004, 1, TIME '09:00', TIME '12:00'), (1004, 1, TIME '13:00', TIME '18:00'),
    (1004, 2, TIME '09:00', TIME '12:00'), (1004, 2, TIME '13:00', TIME '18:00'),
    (1004, 3, TIME '09:00', TIME '12:00'), (1004, 3, TIME '13:00', TIME '18:00'),
    (1004, 4, TIME '09:00', TIME '12:00'), (1004, 4, TIME '13:00', TIME '18:00'),
    (1004, 5, TIME '09:00', TIME '12:00'), (1004, 5, TIME '13:00', TIME '18:00'),
    -- Rafael Nunes: terca a sabado, 10:00-14:00 e 15:00-19:00
    (1005, 2, TIME '10:00', TIME '14:00'), (1005, 2, TIME '15:00', TIME '19:00'),
    (1005, 3, TIME '10:00', TIME '14:00'), (1005, 3, TIME '15:00', TIME '19:00'),
    (1005, 4, TIME '10:00', TIME '14:00'), (1005, 4, TIME '15:00', TIME '19:00'),
    (1005, 5, TIME '10:00', TIME '14:00'), (1005, 5, TIME '15:00', TIME '19:00'),
    (1005, 6, TIME '10:00', TIME '14:00'), (1005, 6, TIME '15:00', TIME '19:00'),
    -- Patricia Gomes: segunda a sexta, 08:00-12:00 e 13:00-17:00
    (1006, 1, TIME '08:00', TIME '12:00'), (1006, 1, TIME '13:00', TIME '17:00'),
    (1006, 2, TIME '08:00', TIME '12:00'), (1006, 2, TIME '13:00', TIME '17:00'),
    (1006, 3, TIME '08:00', TIME '12:00'), (1006, 3, TIME '13:00', TIME '17:00'),
    (1006, 4, TIME '08:00', TIME '12:00'), (1006, 4, TIME '13:00', TIME '17:00'),
    (1006, 5, TIME '08:00', TIME '12:00'), (1006, 5, TIME '13:00', TIME '17:00'),
    -- Lucas Barros: segunda a sexta, 10:00-13:00 e 14:00-20:00
    (1007, 1, TIME '10:00', TIME '13:00'), (1007, 1, TIME '14:00', TIME '20:00'),
    (1007, 2, TIME '10:00', TIME '13:00'), (1007, 2, TIME '14:00', TIME '20:00'),
    (1007, 3, TIME '10:00', TIME '13:00'), (1007, 3, TIME '14:00', TIME '20:00'),
    (1007, 4, TIME '10:00', TIME '13:00'), (1007, 4, TIME '14:00', TIME '20:00'),
    (1007, 5, TIME '10:00', TIME '13:00'), (1007, 5, TIME '14:00', TIME '20:00')
) AS v(profissional_id, dia_semana, hora_inicio, hora_fim)
WHERE NOT EXISTS (
    SELECT 1 FROM jornada_intervalo j
    WHERE j.profissional_id = v.profissional_id
      AND j.dia_semana = v.dia_semana
      AND j.hora_inicio = v.hora_inicio
      AND j.hora_fim = v.hora_fim
);

-- Jornada padrao dos profissionais das novas filiais (1008-1017):
-- segunda a sexta, 09:00-12:00 e 13:00-18:00.
INSERT INTO jornada_intervalo (profissional_id, dia_semana, hora_inicio, hora_fim)
SELECT p.id, j.dia_semana, j.hora_inicio, j.hora_fim
FROM (VALUES (1008), (1009), (1010), (1011), (1012),
             (1013), (1014), (1015), (1016), (1017)) AS p(id)
CROSS JOIN (VALUES
    (1, TIME '09:00', TIME '12:00'), (1, TIME '13:00', TIME '18:00'),
    (2, TIME '09:00', TIME '12:00'), (2, TIME '13:00', TIME '18:00'),
    (3, TIME '09:00', TIME '12:00'), (3, TIME '13:00', TIME '18:00'),
    (4, TIME '09:00', TIME '12:00'), (4, TIME '13:00', TIME '18:00'),
    (5, TIME '09:00', TIME '12:00'), (5, TIME '13:00', TIME '18:00')
) AS j(dia_semana, hora_inicio, hora_fim)
WHERE NOT EXISTS (
    SELECT 1 FROM jornada_intervalo j2
    WHERE j2.profissional_id = p.id
      AND j2.dia_semana = j.dia_semana
      AND j2.hora_inicio = j.hora_inicio
      AND j2.hora_fim = j.hora_fim
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
-- Lista de espera e ofertas de encaixe
--   ATIVA / ATENDIDA / EXPIRADA / CANCELADA
-- A ficha de encaixe (atendimento 1040) referencia a solicitacao 1002.
-- ----------------------------------------------------------------------------
INSERT INTO lista_espera (id, usuario_id, unidade_id, servico_id, profissional_id,
                          data_inicio, data_fim, hora_inicio, hora_fim, status,
                          criado_em, atualizado_em) VALUES
    (1000, 1007, 1000, 1000, 1000, CURRENT_DATE + 2, CURRENT_DATE + 9, TIME '09:00', TIME '12:00',
     'ATIVA', now() - INTERVAL '3 days', now() - INTERVAL '3 days'),
    (1001, 1008, 1000, 1001, NULL, CURRENT_DATE + 3, CURRENT_DATE + 10, NULL, NULL,
     'ATIVA', now() - INTERVAL '2 days', now() - INTERVAL '2 days'),
    (1002, 1009, 1001, 1005, 1002, CURRENT_DATE + 1, CURRENT_DATE + 7, NULL, NULL,
     'ATENDIDA', now() - INTERVAL '4 days', now() - INTERVAL '1 day'),
    (1003, 1007, 1000, 1002, 1001, CURRENT_DATE - 5, CURRENT_DATE - 1, NULL, NULL,
     'EXPIRADA', now() - INTERVAL '20 days', now() - INTERVAL '1 day'),
    (1004, 1009, 1001, 1006, 1003, CURRENT_DATE + 4, CURRENT_DATE + 11, NULL, NULL,
     'CANCELADA', now() - INTERVAL '6 days', now() - INTERVAL '5 days')
ON CONFLICT DO NOTHING;

-- ----------------------------------------------------------------------------
-- Atendimentos (nucleo original, ids 1000-1013)
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
-- Agenda ampliada (ids 1014-1040): historico (dia_offset negativo) e proximos
-- dias, incluindo o encaixe 1040 (lista_espera_id = 1002). Os snapshots vem do
-- servico e da filial via join.
-- ----------------------------------------------------------------------------
INSERT INTO atendimento (id, profissional_id, servico_id, cliente_id, inicio, status,
                         duracao_minutos, intervalo_minutos, servico_nome, preco_acordado,
                         fuso_horario_agendamento, criado_em, atualizado_em,
                         cancelado_em, cancelado_por, motivo_cancelamento, lista_espera_id)
SELECT v.id, v.profissional_id, v.servico_id, v.cliente_id,
       (CURRENT_DATE + v.dia_offset) + v.hora, v.status,
       s.duracao_minutos, COALESCE(s.intervalo_minutos, 0), s.nome, s.preco, u.fuso_horario,
       now() - INTERVAL '20 days', now() - INTERVAL '2 days',
       NULL, NULL, NULL, v.lista_espera_id
FROM (VALUES
    -- Historico (realizados)
    (1014, 1000, 1000, 1003, -3,  TIME '09:30', 'CONFIRMADO', NULL),
    (1015, 1000, 1001, 1001, -10, TIME '13:30', 'CONFIRMADO', NULL),
    (1016, 1001, 1002, 1002, -2,  TIME '10:30', 'CONFIRMADO', NULL),
    (1017, 1002, 1005, 1004, -5,  TIME '08:30', 'CONFIRMADO', NULL),
    (1018, 1003, 1004, 1000, -2,  TIME '10:00', 'CONFIRMADO', NULL),
    (1019, 1004, 1021, 1000, -12, TIME '14:00', 'CONFIRMADO', NULL),
    -- Fernanda Alves (Unidade Nazare)
    (1020, 1004, 1021, 1000, 1,   TIME '09:00', 'CONFIRMADO', NULL),
    (1021, 1004, 1020, 1001, 1,   TIME '10:30', 'AGENDADO',   NULL),
    (1022, 1004, 1021, 1002, 1,   TIME '14:00', 'AGENDADO',   NULL),
    (1023, 1004, 1020, 1003, 2,   TIME '11:00', 'AGENDADO',   NULL),
    -- Rafael Nunes (Unidade Nazare)
    (1024, 1005, 1022, 1003, -6,  TIME '15:30', 'CONFIRMADO', NULL),
    (1025, 1005, 1020, 1000, -6,  TIME '16:30', 'CONFIRMADO', NULL),
    (1026, 1005, 1022, 1001, 1,   TIME '10:30', 'AGENDADO',   NULL),
    (1027, 1005, 1020, 1000, 1,   TIME '15:00', 'CONFIRMADO', NULL),
    (1028, 1005, 1020, 1002, 2,   TIME '10:00', 'AGENDADO',   NULL),
    -- Patricia Gomes (Unidade Umarizal)
    (1029, 1006, 1025, 1002, -4,  TIME '08:30', 'CONFIRMADO', NULL),
    (1030, 1006, 1024, 1003, -4,  TIME '13:30', 'CONFIRMADO', NULL),
    (1031, 1006, 1024, 1002, 1,   TIME '08:30', 'CONFIRMADO', NULL),
    (1032, 1006, 1023, 1003, 1,   TIME '10:00', 'AGENDADO',   NULL),
    (1033, 1006, 1025, 1000, 1,   TIME '13:30', 'AGENDADO',   NULL),
    (1034, 1006, 1024, 1001, 2,   TIME '09:00', 'AGENDADO',   NULL),
    -- Lucas Barros (Unidade Umarizal)
    (1035, 1007, 1025, 1003, -3,  TIME '14:00', 'CONFIRMADO', NULL),
    (1036, 1007, 1023, 1000, -3,  TIME '17:00', 'CONFIRMADO', NULL),
    (1037, 1007, 1023, 1001, 1,   TIME '10:30', 'AGENDADO',   NULL),
    (1038, 1007, 1025, 1000, 1,   TIME '14:30', 'AGENDADO',   NULL),
    (1039, 1007, 1023, 1002, 2,   TIME '16:00', 'AGENDADO',   NULL),
    -- Encaixe da lista de espera 1002 (Beatriz)
    (1040, 1002, 1005, 1002, 1,   TIME '15:00', 'AGENDADO',   1002)
) AS v(id, profissional_id, servico_id, cliente_id, dia_offset, hora, status, lista_espera_id)
JOIN servico s ON s.id = v.servico_id
JOIN unidade u ON u.id = s.unidade_id
ON CONFLICT (id) DO UPDATE SET
    servico_nome = EXCLUDED.servico_nome,
    preco_acordado = EXCLUDED.preco_acordado,
    fuso_horario_agendamento = EXCLUDED.fuso_horario_agendamento,
    criado_em = EXCLUDED.criado_em,
    atualizado_em = EXCLUDED.atualizado_em,
    cancelado_em = EXCLUDED.cancelado_em,
    cancelado_por = EXCLUDED.cancelado_por,
    motivo_cancelamento = EXCLUDED.motivo_cancelamento,
    lista_espera_id = EXCLUDED.lista_espera_id;

-- ----------------------------------------------------------------------------
-- Eventos da lista de espera
-- ----------------------------------------------------------------------------
INSERT INTO lista_espera_evento (id, lista_espera_id, autor_id, estado_anterior, estado_novo, ocorrido_em) VALUES
    (1000, 1000, 1007, NULL, 'ATIVA', now() - INTERVAL '3 days'),
    (1001, 1001, 1008, NULL, 'ATIVA', now() - INTERVAL '2 days'),
    (1002, 1002, 1009, NULL, 'ATIVA', now() - INTERVAL '4 days'),
    (1003, 1002, 1009, 'ATIVA', 'ATENDIDA', now() - INTERVAL '1 day'),
    (1004, 1003, 1007, NULL, 'ATIVA', now() - INTERVAL '20 days'),
    (1005, 1003, NULL, 'ATIVA', 'EXPIRADA', now() - INTERVAL '1 day'),
    (1006, 1004, 1009, NULL, 'ATIVA', now() - INTERVAL '6 days'),
    (1007, 1004, 1009, 'ATIVA', 'CANCELADA', now() - INTERVAL '5 days')
ON CONFLICT DO NOTHING;

INSERT INTO lista_espera_oferta (id, lista_espera_id, profissional_id, inicio, fuso_horario,
                                 status, emitida_em, expira_em) VALUES
    (1000, 1000, 1000, (CURRENT_DATE + 3) + TIME '09:00', 'America/Sao_Paulo', 'ENVIADA',
     now() - INTERVAL '1 hour', now() + INTERVAL '15 minutes'),
    (1001, 1000, 1000, (CURRENT_DATE + 4) + TIME '09:00', 'America/Sao_Paulo', 'INDISPONIVEL',
     now() - INTERVAL '2 days', now() - INTERVAL '2 days'),
    (1002, 1001, 1000, (CURRENT_DATE + 4) + TIME '11:00', 'America/Sao_Paulo', 'EXPIRADA',
     now() - INTERVAL '2 days', now() - INTERVAL '2 days'),
    (1003, 1002, 1002, (CURRENT_DATE + 1) + TIME '15:00', 'America/Sao_Paulo', 'ACEITA',
     now() - INTERVAL '2 days', now() - INTERVAL '1 day')
ON CONFLICT DO NOTHING;

-- ----------------------------------------------------------------------------
-- Historico de agendamento (append-only; nao e reescrito em reexecucoes)
--   CRIACAO        -> um evento por atendimento
--   CONFIRMACAO    -> atendimentos confirmados
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

-- Eventos de criacao da agenda ampliada (id = atendimento + 986).
INSERT INTO agendamento_evento (id, atendimento_id, autor_id, tipo, ocorrido_em,
                                estado_anterior, estado_novo, inicio_anterior, inicio_novo)
SELECT a.id + 986, a.id,
       CASE a.cliente_id
           WHEN 1000 THEN 1007
           WHEN 1001 THEN 1008
           WHEN 1002 THEN 1009
           ELSE (SELECT u.id FROM usuario u
                 WHERE u.perfil IN ('RECEPCAO', 'ADMINISTRADOR')
                   AND u.unidade_id = p.unidade_id
                 ORDER BY u.id LIMIT 1)
       END,
       'CRIACAO',
       LEAST(now() - INTERVAL '1 hour',
             (a.inicio AT TIME ZONE a.fuso_horario_agendamento) - INTERVAL '3 days'),
       NULL, 'AGENDADO', NULL, a.inicio
FROM atendimento a
JOIN profissional p ON p.id = a.profissional_id
WHERE a.id BETWEEN 1014 AND 1040
  AND NOT EXISTS (
      SELECT 1 FROM agendamento_evento e
      WHERE e.atendimento_id = a.id AND e.tipo = 'CRIACAO'
  );

-- Eventos de confirmacao da agenda ampliada (id = atendimento + 1986).
INSERT INTO agendamento_evento (id, atendimento_id, autor_id, tipo, ocorrido_em,
                                estado_anterior, estado_novo, inicio_anterior, inicio_novo)
SELECT a.id + 1986, a.id,
       (SELECT u.id FROM usuario u
        WHERE u.perfil IN ('RECEPCAO', 'ADMINISTRADOR')
          AND u.unidade_id = p.unidade_id
        ORDER BY u.id LIMIT 1),
       'CONFIRMACAO',
       LEAST(now() - INTERVAL '1 hour',
             (a.inicio AT TIME ZONE a.fuso_horario_agendamento) - INTERVAL '2 days'),
       'AGENDADO', 'CONFIRMADO', a.inicio, a.inicio
FROM atendimento a
JOIN profissional p ON p.id = a.profissional_id
WHERE a.id BETWEEN 1014 AND 1040
  AND a.status = 'CONFIRMADO'
  AND NOT EXISTS (
      SELECT 1 FROM agendamento_evento e
      WHERE e.atendimento_id = a.id AND e.tipo = 'CONFIRMACAO'
  );

-- ----------------------------------------------------------------------------
-- Notificacoes: preferencias, internas e outbox.
-- Itens PENDENTE usam enviar_apos futuro para nao serem processados no demo.
-- ----------------------------------------------------------------------------
INSERT INTO notificacao_preferencia (usuario_id, lembretes, avisos_lista) VALUES
    (1007, TRUE, TRUE),
    (1008, FALSE, TRUE),
    (1009, TRUE, FALSE)
ON CONFLICT DO NOTHING;

INSERT INTO notificacao_interna (id, usuario_id, tipo, referencia_tipo, referencia_id,
                                 dedupe_key, criado_em, lido_em) VALUES
    (1000, 1007, 'CONFIRMACAO', 'AGENDAMENTO', 1000, 'seed:confirmacao:1000:1007:interna',
     now() - INTERVAL '9 days', now() - INTERVAL '8 days'),
    (1001, 1007, 'LEMBRETE', 'AGENDAMENTO', 1000, 'seed:lembrete:1000:1007:interna',
     now() - INTERVAL '1 day', NULL),
    (1002, 1008, 'CRIACAO', 'AGENDAMENTO', 1001, 'seed:criacao:1001:1008:interna',
     now() - INTERVAL '10 days', NULL),
    (1003, 1009, 'OFERTA', 'OFERTA', 1003, 'seed:oferta:1003:1009:interna',
     now() - INTERVAL '2 days', NULL),
    (1004, 1009, 'CANCELAMENTO', 'AGENDAMENTO', 1004, 'seed:cancelamento:1004:1009:interna',
     now() - INTERVAL '2 days', now() - INTERVAL '2 days'),
    (1005, 1007, 'OFERTA', 'OFERTA', 1000, 'seed:oferta:1000:1007:interna',
     now() - INTERVAL '3 hours', NULL)
ON CONFLICT DO NOTHING;

INSERT INTO notificacao_outbox (id, usuario_id, tipo, referencia_tipo, referencia_id,
                                dedupe_key, status, tentativas, enviar_apos, enviado_em, criado_em) VALUES
    (1000, 1007, 'CONFIRMACAO', 'AGENDAMENTO', 1000, 'seed:confirmacao:1000:1007:email',
     'ENVIADO', 1, now() - INTERVAL '9 days', now() - INTERVAL '9 days', now() - INTERVAL '9 days'),
    (1001, 1007, 'LEMBRETE', 'AGENDAMENTO', 1000, 'seed:lembrete:1000:1007:email',
     'PENDENTE', 0, now() + INTERVAL '1 day', NULL, now() - INTERVAL '1 day'),
    (1002, 1008, 'CRIACAO', 'AGENDAMENTO', 1001, 'seed:criacao:1001:1008:email',
     'ENVIADO', 1, now() - INTERVAL '10 days', now() - INTERVAL '10 days', now() - INTERVAL '10 days'),
    (1003, 1009, 'OFERTA', 'OFERTA', 1003, 'seed:oferta:1003:1009:email',
     'ENVIADO', 1, now() - INTERVAL '2 days', now() - INTERVAL '2 days', now() - INTERVAL '2 days'),
    (1004, 1009, 'CANCELAMENTO', 'AGENDAMENTO', 1004, 'seed:cancelamento:1004:1009:email',
     'CANCELADO', 0, now() - INTERVAL '2 days', NULL, now() - INTERVAL '2 days'),
    (1005, 1007, 'OFERTA', 'OFERTA', 1000, 'seed:oferta:1000:1007:email',
     'PENDENTE', 0, now() + INTERVAL '1 day', NULL, now() - INTERVAL '3 hours')
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
SELECT setval(pg_get_serial_sequence('lista_espera', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 1) FROM lista_espera), 1));
SELECT setval(pg_get_serial_sequence('lista_espera_evento', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 1) FROM lista_espera_evento), 1));
SELECT setval(pg_get_serial_sequence('lista_espera_oferta', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 1) FROM lista_espera_oferta), 1));
SELECT setval(pg_get_serial_sequence('atendimento', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 1) FROM atendimento), 1));
SELECT setval(pg_get_serial_sequence('agendamento_evento', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 1) FROM agendamento_evento), 1));
SELECT setval(pg_get_serial_sequence('notificacao_interna', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 1) FROM notificacao_interna), 1));
SELECT setval(pg_get_serial_sequence('notificacao_outbox', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 1) FROM notificacao_outbox), 1));

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
UNION ALL SELECT 'lista_espera', COUNT(*) FROM lista_espera
UNION ALL SELECT 'lista_espera_evento', COUNT(*) FROM lista_espera_evento
UNION ALL SELECT 'lista_espera_oferta', COUNT(*) FROM lista_espera_oferta
UNION ALL SELECT 'atendimento', COUNT(*) FROM atendimento
UNION ALL SELECT 'agendamento_evento', COUNT(*) FROM agendamento_evento
UNION ALL SELECT 'notificacao_preferencia', COUNT(*) FROM notificacao_preferencia
UNION ALL SELECT 'notificacao_interna', COUNT(*) FROM notificacao_interna
UNION ALL SELECT 'notificacao_outbox', COUNT(*) FROM notificacao_outbox
ORDER BY tabela;
