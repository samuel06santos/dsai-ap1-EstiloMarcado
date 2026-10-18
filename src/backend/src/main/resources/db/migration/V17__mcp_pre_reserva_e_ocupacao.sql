CREATE TABLE pre_reserva (
    id BIGSERIAL PRIMARY KEY,
    unidade_id BIGINT NOT NULL REFERENCES unidade(id),
    servico_id BIGINT NOT NULL REFERENCES servico(id),
    profissional_id BIGINT NOT NULL REFERENCES profissional(id),
    inicio TIMESTAMP NOT NULL,
    duracao_minutos INTEGER NOT NULL CHECK (duracao_minutos > 0),
    intervalo_minutos INTEGER NOT NULL CHECK (intervalo_minutos >= 0),
    fuso_horario VARCHAR(80) NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    token_cifrado BYTEA NOT NULL,
    chave_idempotencia UUID NOT NULL UNIQUE,
    pedido_hash CHAR(64) NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'ATIVA'
        CHECK (estado IN ('ATIVA', 'EXPIRADA', 'CONSUMIDA')),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    expira_em TIMESTAMPTZ NOT NULL,
    consumido_em TIMESTAMPTZ,
    consumido_por BIGINT REFERENCES usuario(id),
    atendimento_id BIGINT UNIQUE REFERENCES atendimento(id),
    CHECK (expira_em > criado_em)
);
CREATE INDEX ix_pre_reserva_profissional_prazo ON pre_reserva(profissional_id, expira_em)
    WHERE estado = 'ATIVA';

-- Uma única exclusão protege atendimentos e pré-reservas na mesma agenda.
CREATE TABLE agenda_ocupacao (
    origem_tipo VARCHAR(20) NOT NULL CHECK (origem_tipo IN ('ATENDIMENTO', 'PRE_RESERVA')),
    origem_id BIGINT NOT NULL,
    profissional_id BIGINT NOT NULL REFERENCES profissional(id),
    inicio TIMESTAMP NOT NULL,
    duracao_minutos INTEGER NOT NULL,
    intervalo_minutos INTEGER NOT NULL,
    ativa BOOLEAN NOT NULL,
    PRIMARY KEY (origem_tipo, origem_id)
);

INSERT INTO agenda_ocupacao(origem_tipo, origem_id, profissional_id, inicio,
                            duracao_minutos, intervalo_minutos, ativa)
SELECT 'ATENDIMENTO', id, profissional_id, inicio, duracao_minutos, intervalo_minutos,
       status IN ('AGENDADO','CONFIRMADO') FROM atendimento;

ALTER TABLE agenda_ocupacao ADD CONSTRAINT ex_agenda_ocupacao
    EXCLUDE USING gist (
        profissional_id WITH =,
        tsrange(inicio, inicio + (duracao_minutos + intervalo_minutos) * interval '1 minute', '[)') WITH &&
    ) WHERE (ativa);

CREATE FUNCTION sincronizar_ocupacao_atendimento() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        DELETE FROM agenda_ocupacao WHERE origem_tipo='ATENDIMENTO' AND origem_id=OLD.id;
        RETURN OLD;
    END IF;
    INSERT INTO agenda_ocupacao(origem_tipo, origem_id, profissional_id, inicio,
                                duracao_minutos, intervalo_minutos, ativa)
    VALUES ('ATENDIMENTO', NEW.id, NEW.profissional_id, NEW.inicio,
            NEW.duracao_minutos, NEW.intervalo_minutos,
            NEW.status IN ('AGENDADO','CONFIRMADO'))
    ON CONFLICT (origem_tipo, origem_id) DO UPDATE SET
        profissional_id=EXCLUDED.profissional_id, inicio=EXCLUDED.inicio,
        duracao_minutos=EXCLUDED.duracao_minutos, intervalo_minutos=EXCLUDED.intervalo_minutos,
        ativa=EXCLUDED.ativa;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_atendimento_ocupacao
AFTER INSERT OR UPDATE OF profissional_id, inicio, duracao_minutos, intervalo_minutos, status OR DELETE
ON atendimento FOR EACH ROW EXECUTE FUNCTION sincronizar_ocupacao_atendimento();

CREATE FUNCTION sincronizar_ocupacao_pre_reserva() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        DELETE FROM agenda_ocupacao WHERE origem_tipo='PRE_RESERVA' AND origem_id=OLD.id;
        RETURN OLD;
    END IF;
    INSERT INTO agenda_ocupacao(origem_tipo, origem_id, profissional_id, inicio,
                                duracao_minutos, intervalo_minutos, ativa)
    VALUES ('PRE_RESERVA', NEW.id, NEW.profissional_id, NEW.inicio,
            NEW.duracao_minutos, NEW.intervalo_minutos, NEW.estado='ATIVA')
    ON CONFLICT (origem_tipo, origem_id) DO UPDATE SET
        profissional_id=EXCLUDED.profissional_id, inicio=EXCLUDED.inicio,
        duracao_minutos=EXCLUDED.duracao_minutos, intervalo_minutos=EXCLUDED.intervalo_minutos,
        ativa=EXCLUDED.ativa;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_pre_reserva_ocupacao
AFTER INSERT OR UPDATE OF profissional_id, inicio, duracao_minutos, intervalo_minutos, estado OR DELETE
ON pre_reserva FOR EACH ROW EXECUTE FUNCTION sincronizar_ocupacao_pre_reserva();

ALTER TABLE atendimento DROP CONSTRAINT ex_atendimento_ocupacao;

CREATE TABLE mcp_cliente_oauth (
    id VARCHAR(200) PRIMARY KEY,
    nome VARCHAR(120) NOT NULL,
    redirect_uris TEXT NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE mcp_codigo_oauth (
    codigo_hash CHAR(64) PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    cliente_id VARCHAR(200) NOT NULL REFERENCES mcp_cliente_oauth(id),
    redirect_uri TEXT NOT NULL,
    code_challenge CHAR(43) NOT NULL,
    escopos VARCHAR(100) NOT NULL,
    expira_em TIMESTAMPTZ NOT NULL,
    consumido_em TIMESTAMPTZ
);
CREATE INDEX ix_mcp_codigo_expira ON mcp_codigo_oauth(expira_em);
CREATE TABLE mcp_token_revogado (
    jti UUID PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    expira_em TIMESTAMPTZ NOT NULL
);
CREATE TABLE mcp_autorizacao (
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    cliente_id VARCHAR(200) NOT NULL REFERENCES mcp_cliente_oauth(id),
    versao INTEGER NOT NULL DEFAULT 1,
    revogado_em TIMESTAMPTZ,
    PRIMARY KEY (usuario_id, cliente_id)
);

CREATE TABLE mcp_confirmacao_consumida (
    nonce UUID PRIMARY KEY,
    consumido_em TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
