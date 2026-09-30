-- Relacionamento muitos-para-muitos entre servico e profissional: os profissionais
-- habilitados a executar cada servico.
CREATE TABLE servico_profissional (
    servico_id BIGINT NOT NULL,
    profissional_id BIGINT NOT NULL,
    PRIMARY KEY (servico_id, profissional_id),
    CONSTRAINT fk_servico_profissional_servico
        FOREIGN KEY (servico_id) REFERENCES servico (id) ON DELETE CASCADE,
    CONSTRAINT fk_servico_profissional_profissional
        FOREIGN KEY (profissional_id) REFERENCES profissional (id) ON DELETE CASCADE
);
