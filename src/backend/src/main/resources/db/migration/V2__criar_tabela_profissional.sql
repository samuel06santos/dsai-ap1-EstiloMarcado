-- Profissional. Tabela minima necessaria ao catalogo de servicos: um profissional
-- pertence a uma unidade e pode ser habilitado para executar servicos.
CREATE TABLE profissional (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(120) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    unidade_id BIGINT NOT NULL,
    CONSTRAINT fk_profissional_unidade
        FOREIGN KEY (unidade_id) REFERENCES unidade (id)
);
