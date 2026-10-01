-- Preserva a duracao e a pausa praticadas quando o atendimento foi marcado.
-- Para dados legados, o catalogo atual e a unica fonte recuperavel.
ALTER TABLE atendimento ADD COLUMN duracao_minutos INTEGER;
ALTER TABLE atendimento ADD COLUMN intervalo_minutos INTEGER;

UPDATE atendimento a
SET duracao_minutos = s.duracao_minutos,
    intervalo_minutos = COALESCE(s.intervalo_minutos, 0)
FROM servico s
WHERE s.id = a.servico_id;

ALTER TABLE atendimento ALTER COLUMN duracao_minutos SET NOT NULL;
ALTER TABLE atendimento ALTER COLUMN intervalo_minutos SET NOT NULL;
ALTER TABLE atendimento ADD CONSTRAINT ck_atendimento_duracao_positiva
    CHECK (duracao_minutos > 0);
ALTER TABLE atendimento ADD CONSTRAINT ck_atendimento_intervalo_nao_negativo
    CHECK (intervalo_minutos >= 0);

CREATE INDEX ix_atendimento_profissional_inicio_ativo
    ON atendimento (profissional_id, inicio)
    WHERE status IN ('AGENDADO', 'CONFIRMADO');
