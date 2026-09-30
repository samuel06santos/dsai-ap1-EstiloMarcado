-- Unidade (estabelecimento). Tabela minima: a spec de estabelecimentos/profissionais
-- expandira este schema quando for escrita.
CREATE TABLE unidade (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(120) NOT NULL
);
