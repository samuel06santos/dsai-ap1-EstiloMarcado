-- Jornada semanal, folgas/jornadas especiais, feriados, afastamentos e bloqueios
-- de agenda. Ver SPEC/2026-10-01-jornada-folgas-feriados-e-bloqueios.md.
-- As invariantes sao protegidas no banco alem da validacao na aplicacao.

CREATE TABLE jornada_intervalo (
    id BIGSERIAL PRIMARY KEY,
    profissional_id BIGINT NOT NULL,
    dia_semana SMALLINT NOT NULL,
    hora_inicio TIME NOT NULL,
    hora_fim TIME NOT NULL,
    CONSTRAINT fk_jornada_intervalo_profissional
        FOREIGN KEY (profissional_id) REFERENCES profissional (id) ON DELETE CASCADE,
    CONSTRAINT ck_jornada_intervalo_dia_semana
        CHECK (dia_semana BETWEEN 1 AND 7),
    CONSTRAINT ck_jornada_intervalo_horario
        CHECK (hora_inicio < hora_fim)
);

CREATE INDEX ix_jornada_intervalo_profissional_dia
    ON jornada_intervalo (profissional_id, dia_semana);

CREATE TABLE excecao_jornada (
    id BIGSERIAL PRIMARY KEY,
    profissional_id BIGINT NOT NULL,
    data DATE NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    motivo VARCHAR(500),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_excecao_jornada_profissional
        FOREIGN KEY (profissional_id) REFERENCES profissional (id) ON DELETE CASCADE,
    CONSTRAINT uk_excecao_jornada_profissional_data
        UNIQUE (profissional_id, data),
    CONSTRAINT ck_excecao_jornada_tipo
        CHECK (tipo IN ('FOLGA', 'JORNADA_ESPECIAL'))
);

CREATE TABLE excecao_jornada_intervalo (
    id BIGSERIAL PRIMARY KEY,
    excecao_id BIGINT NOT NULL,
    hora_inicio TIME NOT NULL,
    hora_fim TIME NOT NULL,
    CONSTRAINT fk_excecao_intervalo_excecao
        FOREIGN KEY (excecao_id) REFERENCES excecao_jornada (id) ON DELETE CASCADE,
    CONSTRAINT ck_excecao_intervalo_horario
        CHECK (hora_inicio < hora_fim)
);

CREATE INDEX ix_excecao_intervalo_excecao ON excecao_jornada_intervalo (excecao_id);

CREATE TABLE afastamento (
    id BIGSERIAL PRIMARY KEY,
    profissional_id BIGINT NOT NULL,
    data_inicio DATE NOT NULL,
    data_fim DATE NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    descricao VARCHAR(500),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_afastamento_profissional
        FOREIGN KEY (profissional_id) REFERENCES profissional (id) ON DELETE CASCADE,
    CONSTRAINT ck_afastamento_periodo
        CHECK (data_inicio <= data_fim),
    CONSTRAINT ck_afastamento_tipo
        CHECK (tipo IN ('FERIAS', 'LICENCA', 'AFASTAMENTO', 'OUTRO'))
);

CREATE INDEX ix_afastamento_profissional_periodo
    ON afastamento (profissional_id, data_inicio, data_fim);

CREATE TABLE feriado (
    id BIGSERIAL PRIMARY KEY,
    unidade_id BIGINT NOT NULL,
    data DATE NOT NULL,
    nome VARCHAR(120) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_feriado_unidade
        FOREIGN KEY (unidade_id) REFERENCES unidade (id) ON DELETE CASCADE,
    CONSTRAINT uk_feriado_unidade_data
        UNIQUE (unidade_id, data)
);

CREATE TABLE bloqueio_agenda (
    id BIGSERIAL PRIMARY KEY,
    unidade_id BIGINT NOT NULL,
    profissional_id BIGINT,
    data DATE NOT NULL,
    dia_inteiro BOOLEAN NOT NULL DEFAULT FALSE,
    hora_inicio TIME,
    hora_fim TIME,
    motivo VARCHAR(500),
    criado_por BIGINT NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bloqueio_unidade
        FOREIGN KEY (unidade_id) REFERENCES unidade (id) ON DELETE CASCADE,
    CONSTRAINT fk_bloqueio_profissional
        FOREIGN KEY (profissional_id) REFERENCES profissional (id) ON DELETE CASCADE,
    CONSTRAINT fk_bloqueio_usuario
        FOREIGN KEY (criado_por) REFERENCES usuario (id),
    CONSTRAINT ck_bloqueio_horario
        CHECK (
            (dia_inteiro = TRUE AND hora_inicio IS NULL AND hora_fim IS NULL)
            OR (dia_inteiro = FALSE AND hora_inicio IS NOT NULL
                AND hora_fim IS NOT NULL AND hora_inicio < hora_fim)
        )
);

CREATE INDEX ix_bloqueio_unidade_data ON bloqueio_agenda (unidade_id, data);
CREATE INDEX ix_bloqueio_profissional_data ON bloqueio_agenda (profissional_id, data);
