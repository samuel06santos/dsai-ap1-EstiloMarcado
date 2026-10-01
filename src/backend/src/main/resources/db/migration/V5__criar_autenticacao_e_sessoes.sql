CREATE TABLE usuario (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(120) NOT NULL,
    email VARCHAR(254) NOT NULL,
    email_normalizado VARCHAR(254) NOT NULL,
    senha_hash VARCHAR(100),
    perfil VARCHAR(30) NOT NULL,
    estado VARCHAR(20) NOT NULL,
    unidade_id BIGINT,
    profissional_id BIGINT,
    tentativas_login INTEGER NOT NULL DEFAULT 0,
    primeira_falha_em TIMESTAMPTZ,
    bloqueado_ate TIMESTAMPTZ,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    senha_alterada_em TIMESTAMPTZ,
    CONSTRAINT uk_usuario_email_normalizado UNIQUE (email_normalizado),
    CONSTRAINT uk_usuario_profissional UNIQUE (profissional_id),
    CONSTRAINT fk_usuario_unidade FOREIGN KEY (unidade_id) REFERENCES unidade (id),
    CONSTRAINT fk_usuario_profissional FOREIGN KEY (profissional_id) REFERENCES profissional (id),
    CONSTRAINT ck_usuario_perfil CHECK (perfil IN ('CLIENTE', 'PROFISSIONAL', 'RECEPCAO', 'ADMINISTRADOR')),
    CONSTRAINT ck_usuario_estado CHECK (estado IN ('PENDENTE', 'ATIVA', 'BLOQUEADA', 'DESATIVADA')),
    CONSTRAINT ck_usuario_vinculo CHECK (
        (perfil = 'CLIENTE' AND unidade_id IS NULL AND profissional_id IS NULL)
        OR (perfil = 'PROFISSIONAL' AND unidade_id IS NOT NULL AND profissional_id IS NOT NULL)
        OR (perfil IN ('RECEPCAO', 'ADMINISTRADOR') AND unidade_id IS NOT NULL AND profissional_id IS NULL)
    )
);

CREATE INDEX ix_usuario_unidade ON usuario (unidade_id);

CREATE TABLE token_usuario (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    finalidade VARCHAR(20) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expira_em TIMESTAMPTZ NOT NULL,
    consumido_em TIMESTAMPTZ,
    CONSTRAINT uk_token_usuario_hash UNIQUE (token_hash),
    CONSTRAINT fk_token_usuario_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT ck_token_usuario_finalidade CHECK (finalidade IN ('ATIVACAO', 'CONVITE', 'RECUPERACAO'))
);

CREATE INDEX ix_token_usuario_ativo
    ON token_usuario (usuario_id, finalidade, expira_em)
    WHERE consumido_em IS NULL;

CREATE TABLE evento_seguranca (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT,
    tipo VARCHAR(50) NOT NULL,
    resultado VARCHAR(20) NOT NULL,
    origem VARCHAR(100),
    detalhes VARCHAR(500),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_evento_seguranca_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id)
);

CREATE INDEX ix_evento_seguranca_usuario_criado
    ON evento_seguranca (usuario_id, criado_em DESC);

-- Schema oficial do Spring Session JDBC para PostgreSQL. O identificador da
-- sessao fica no cookie; seus dados e atributos permanecem no servidor.
CREATE TABLE spring_session (
    primary_id CHAR(36) NOT NULL,
    session_id CHAR(36) NOT NULL,
    creation_time BIGINT NOT NULL,
    last_access_time BIGINT NOT NULL,
    max_inactive_interval INTEGER NOT NULL,
    expiry_time BIGINT NOT NULL,
    principal_name VARCHAR(100),
    CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);

CREATE UNIQUE INDEX spring_session_ix1 ON spring_session (session_id);
CREATE INDEX spring_session_ix2 ON spring_session (expiry_time);
CREATE INDEX spring_session_ix3 ON spring_session (principal_name);

CREATE TABLE spring_session_attributes (
    session_primary_id CHAR(36) NOT NULL,
    attribute_name VARCHAR(200) NOT NULL,
    attribute_bytes BYTEA NOT NULL,
    CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id)
        REFERENCES spring_session (primary_id) ON DELETE CASCADE
);
