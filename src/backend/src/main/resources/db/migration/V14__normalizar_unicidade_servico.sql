-- Protege a unicidade do nome mesmo quando requisicoes concorrentes passam
-- pela validacao da aplicacao ao mesmo tempo. Dados legados conflitantes devem
-- ser corrigidos antes desta migracao; nenhum nome e alterado silenciosamente.
ALTER TABLE servico DROP CONSTRAINT uk_servico_unidade_nome;
CREATE UNIQUE INDEX uk_servico_unidade_nome_normalizado
    ON servico (unidade_id, lower(btrim(nome)));
