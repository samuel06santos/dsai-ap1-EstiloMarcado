CREATE TABLE mcp_refresh_token (
    token_hash CHAR(64) PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    cliente_id VARCHAR(200) NOT NULL REFERENCES mcp_cliente_oauth(id),
    escopos VARCHAR(100) NOT NULL,
    autorizacao_versao INTEGER NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    expira_em TIMESTAMPTZ NOT NULL,
    consumido_em TIMESTAMPTZ,
    CHECK (expira_em > criado_em)
);

CREATE INDEX ix_mcp_refresh_token_expira ON mcp_refresh_token(expira_em);
CREATE INDEX ix_mcp_refresh_token_autorizacao ON mcp_refresh_token(usuario_id, cliente_id);
