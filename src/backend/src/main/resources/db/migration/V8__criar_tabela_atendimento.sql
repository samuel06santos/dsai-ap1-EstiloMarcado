-- Atendimento agendado para um profissional. Tabela minima exigida pelo painel
-- profissional; a spec de agendamentos definira as regras completas (conflitos,
-- reagendamento e demais transicoes de status).
CREATE TABLE atendimento (
    id BIGSERIAL PRIMARY KEY,
    profissional_id BIGINT NOT NULL,
    servico_id BIGINT NOT NULL,
    cliente_id BIGINT NOT NULL,
    inicio TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT fk_atendimento_profissional
        FOREIGN KEY (profissional_id) REFERENCES profissional (id),
    CONSTRAINT fk_atendimento_servico
        FOREIGN KEY (servico_id) REFERENCES servico (id),
    CONSTRAINT fk_atendimento_cliente
        FOREIGN KEY (cliente_id) REFERENCES cliente (id),
    CONSTRAINT ck_atendimento_status
        CHECK (status IN ('AGENDADO', 'CONFIRMADO', 'CANCELADO'))
);
