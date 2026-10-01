CREATE TABLE lista_espera (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    unidade_id BIGINT NOT NULL REFERENCES unidade(id),
    servico_id BIGINT NOT NULL REFERENCES servico(id),
    profissional_id BIGINT REFERENCES profissional(id),
    data_inicio DATE NOT NULL,
    data_fim DATE NOT NULL,
    hora_inicio TIME,
    hora_fim TIME,
    status VARCHAR(20) NOT NULL DEFAULT 'ATIVA',
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_lista_espera_status CHECK (status IN ('ATIVA','ATENDIDA','CANCELADA','EXPIRADA')),
    CONSTRAINT ck_lista_espera_datas CHECK (data_inicio <= data_fim),
    CONSTRAINT ck_lista_espera_horas CHECK ((hora_inicio IS NULL AND hora_fim IS NULL)
        OR (hora_inicio IS NOT NULL AND hora_fim IS NOT NULL AND hora_inicio < hora_fim))
);
CREATE UNIQUE INDEX uk_lista_espera_ativa ON lista_espera(usuario_id, unidade_id, servico_id)
    WHERE status = 'ATIVA';
CREATE INDEX ix_lista_espera_fila ON lista_espera(unidade_id, servico_id, data_inicio, criado_em, id)
    WHERE status = 'ATIVA';
CREATE INDEX ix_lista_espera_usuario ON lista_espera(usuario_id, criado_em DESC, id DESC);

CREATE TABLE lista_espera_evento (
    id BIGSERIAL PRIMARY KEY,
    lista_espera_id BIGINT NOT NULL REFERENCES lista_espera(id),
    autor_id BIGINT REFERENCES usuario(id),
    estado_anterior VARCHAR(20),
    estado_novo VARCHAR(20) NOT NULL,
    ocorrido_em TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_lista_espera_evento_solicitacao ON lista_espera_evento(lista_espera_id, ocorrido_em, id);

CREATE TABLE lista_espera_oferta (
    id BIGSERIAL PRIMARY KEY,
    lista_espera_id BIGINT NOT NULL REFERENCES lista_espera(id),
    profissional_id BIGINT NOT NULL REFERENCES profissional(id),
    inicio TIMESTAMP NOT NULL,
    fuso_horario VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ENVIADA',
    emitida_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    expira_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_lista_espera_oferta_status CHECK (status IN ('ENVIADA','ACEITA','EXPIRADA','INDISPONIVEL')),
    CONSTRAINT uk_lista_espera_oferta UNIQUE (lista_espera_id, profissional_id, inicio)
);
CREATE INDEX ix_lista_espera_oferta_ativa ON lista_espera_oferta(profissional_id, inicio, expira_em)
    WHERE status = 'ENVIADA';

ALTER TABLE atendimento ADD COLUMN lista_espera_id BIGINT UNIQUE REFERENCES lista_espera(id);
CREATE INDEX ix_agendamento_evento_ocorrido ON agendamento_evento(ocorrido_em, atendimento_id);

CREATE TABLE notificacao_preferencia (
    usuario_id BIGINT PRIMARY KEY REFERENCES usuario(id),
    lembretes BOOLEAN NOT NULL DEFAULT true,
    avisos_lista BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE notificacao_interna (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    tipo VARCHAR(30) NOT NULL,
    referencia_tipo VARCHAR(20) NOT NULL,
    referencia_id BIGINT NOT NULL,
    dedupe_key VARCHAR(150) NOT NULL UNIQUE,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    lido_em TIMESTAMPTZ
);
CREATE INDEX ix_notificacao_usuario ON notificacao_interna(usuario_id, criado_em DESC, id DESC);

CREATE TABLE notificacao_outbox (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    tipo VARCHAR(30) NOT NULL,
    referencia_tipo VARCHAR(20) NOT NULL,
    referencia_id BIGINT NOT NULL,
    dedupe_key VARCHAR(150) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    tentativas INTEGER NOT NULL DEFAULT 0,
    enviar_apos TIMESTAMPTZ NOT NULL DEFAULT now(),
    bloqueado_ate TIMESTAMPTZ,
    enviado_em TIMESTAMPTZ,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_notificacao_outbox_status CHECK
        (status IN ('PENDENTE','PROCESSANDO','ENVIADO','FALHA_FINAL','CANCELADO'))
);
CREATE INDEX ix_notificacao_outbox_pendente ON notificacao_outbox(enviar_apos, id)
    WHERE status IN ('PENDENTE','PROCESSANDO');
