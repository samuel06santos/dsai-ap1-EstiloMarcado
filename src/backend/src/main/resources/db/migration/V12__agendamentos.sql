-- Valores legados sao estimados a partir do catalogo atual; os valores originais
-- nao podem ser reconstruidos. Nenhum atendimento e cancelado automaticamente.
ALTER TABLE cliente ADD COLUMN usuario_id BIGINT UNIQUE REFERENCES usuario(id);
ALTER TABLE cliente ADD COLUMN telefone_contato VARCHAR(20);

ALTER TABLE atendimento ADD COLUMN servico_nome VARCHAR(120);
ALTER TABLE atendimento ADD COLUMN preco_acordado NUMERIC(12,2);
ALTER TABLE atendimento ADD COLUMN fuso_horario_agendamento VARCHAR(80);
ALTER TABLE atendimento ADD COLUMN criado_em TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE atendimento ADD COLUMN atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE atendimento ADD COLUMN cancelado_em TIMESTAMPTZ;
ALTER TABLE atendimento ADD COLUMN cancelado_por BIGINT REFERENCES usuario(id);
ALTER TABLE atendimento ADD COLUMN motivo_cancelamento VARCHAR(500);
UPDATE atendimento a SET servico_nome = s.nome, preco_acordado = s.preco,
    fuso_horario_agendamento = u.fuso_horario
FROM servico s JOIN unidade u ON u.id = s.unidade_id WHERE s.id = a.servico_id;
ALTER TABLE atendimento ALTER COLUMN servico_nome SET NOT NULL;
ALTER TABLE atendimento ALTER COLUMN preco_acordado SET NOT NULL;
ALTER TABLE atendimento ALTER COLUMN fuso_horario_agendamento SET NOT NULL;
CREATE INDEX ix_atendimento_cliente_inicio ON atendimento(cliente_id, inicio, id);
CREATE INDEX ix_atendimento_servico ON atendimento(servico_id);
CREATE INDEX ix_atendimento_cancelado_por ON atendimento(cancelado_por) WHERE cancelado_por IS NOT NULL;

CREATE TABLE agendamento_evento (
    id BIGSERIAL PRIMARY KEY,
    atendimento_id BIGINT NOT NULL REFERENCES atendimento(id),
    autor_id BIGINT NOT NULL REFERENCES usuario(id),
    tipo VARCHAR(20) NOT NULL,
    ocorrido_em TIMESTAMPTZ NOT NULL,
    estado_anterior VARCHAR(20),
    estado_novo VARCHAR(20) NOT NULL,
    inicio_anterior TIMESTAMP,
    inicio_novo TIMESTAMP NOT NULL
);
CREATE INDEX ix_agendamento_evento_atendimento ON agendamento_evento(atendimento_id, id);
CREATE INDEX ix_agendamento_evento_autor ON agendamento_evento(autor_id);

CREATE TABLE agendamento_idempotencia (
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    chave UUID NOT NULL,
    unidade_id BIGINT NOT NULL REFERENCES unidade(id),
    pedido_hash VARCHAR(64) NOT NULL,
    atendimento_id BIGINT REFERENCES atendimento(id),
    resposta_json TEXT,
    expira_em TIMESTAMPTZ NOT NULL,
    PRIMARY KEY(usuario_id, chave)
);
CREATE INDEX ix_agendamento_idempotencia_expiracao ON agendamento_idempotencia(expira_em);
CREATE INDEX ix_agendamento_idempotencia_unidade ON agendamento_idempotencia(unidade_id);
CREATE INDEX ix_agendamento_idempotencia_atendimento ON agendamento_idempotencia(atendimento_id);

-- A instalacao falha caso existam reservas legadas ativas sobrepostas.
DO $$
DECLARE conflito RECORD;
BEGIN
    SELECT a.id AS primeiro, b.id AS segundo INTO conflito
    FROM atendimento a JOIN atendimento b
      ON a.profissional_id = b.profissional_id AND a.id < b.id
    WHERE a.status IN ('AGENDADO','CONFIRMADO')
      AND b.status IN ('AGENDADO','CONFIRMADO')
      AND tsrange(a.inicio, a.inicio + (a.duracao_minutos + a.intervalo_minutos) * interval '1 minute', '[)')
          && tsrange(b.inicio, b.inicio + (b.duracao_minutos + b.intervalo_minutos) * interval '1 minute', '[)')
    LIMIT 1;
    IF FOUND THEN
        RAISE EXCEPTION 'atendimentos legados ativos sobrepostos: ids % e %', conflito.primeiro, conflito.segundo;
    END IF;
END $$;
CREATE EXTENSION IF NOT EXISTS btree_gist;
ALTER TABLE atendimento ADD CONSTRAINT ex_atendimento_ocupacao
    EXCLUDE USING gist (
        profissional_id WITH =,
        tsrange(inicio, inicio + (duracao_minutos + intervalo_minutos) * interval '1 minute', '[)') WITH &&
    ) WHERE (status IN ('AGENDADO', 'CONFIRMADO'));
