CREATE TABLE estabelecimento (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(120) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Preserva os IDs das unidades existentes e evita inferir agrupamentos por nome.
INSERT INTO estabelecimento (id, nome)
SELECT id, nome FROM unidade;
SELECT setval(pg_get_serial_sequence('estabelecimento', 'id'),
              GREATEST(COALESCE((SELECT MAX(id) FROM estabelecimento), 0), 1),
              EXISTS (SELECT 1 FROM estabelecimento));

ALTER TABLE unidade
    ADD COLUMN estabelecimento_id BIGINT,
    ADD COLUMN principal BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN nome_normalizado VARCHAR(120),
    ADD COLUMN endereco VARCHAR(250),
    ADD COLUMN telefone VARCHAR(30),
    ADD COLUMN fuso_horario VARCHAR(80) NOT NULL DEFAULT 'America/Sao_Paulo',
    ADD COLUMN ativa BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN atualizado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE unidade
SET estabelecimento_id = id,
    principal = TRUE,
    nome_normalizado = lower(trim(nome));

ALTER TABLE unidade
    ALTER COLUMN estabelecimento_id SET NOT NULL,
    ALTER COLUMN nome_normalizado SET NOT NULL,
    ADD CONSTRAINT fk_unidade_estabelecimento FOREIGN KEY (estabelecimento_id)
        REFERENCES estabelecimento(id),
    ADD CONSTRAINT uk_unidade_nome_estabelecimento
        UNIQUE (estabelecimento_id, nome_normalizado);

CREATE UNIQUE INDEX uk_unidade_principal_estabelecimento
    ON unidade(estabelecimento_id) WHERE principal = TRUE;

ALTER TABLE profissional
    ADD COLUMN apresentacao VARCHAR(500),
    ADD COLUMN criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN atualizado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
