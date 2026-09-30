-- Servico do catalogo. As regras de negocio (duracao positiva, preco nao negativo,
-- intervalo nao negativo e nome unico por unidade) sao protegidas no banco alem da
-- validacao na camada de aplicacao.
CREATE TABLE servico (
    id BIGSERIAL PRIMARY KEY,
    unidade_id BIGINT NOT NULL,
    nome VARCHAR(120) NOT NULL,
    descricao VARCHAR(1000),
    duracao_minutos INTEGER NOT NULL,
    preco NUMERIC(12, 2) NOT NULL DEFAULT 0,
    intervalo_minutos INTEGER,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_servico_unidade
        FOREIGN KEY (unidade_id) REFERENCES unidade (id),
    CONSTRAINT uk_servico_unidade_nome
        UNIQUE (unidade_id, nome),
    CONSTRAINT ck_servico_duracao_positiva
        CHECK (duracao_minutos > 0),
    CONSTRAINT ck_servico_preco_nao_negativo
        CHECK (preco >= 0),
    CONSTRAINT ck_servico_intervalo_nao_negativo
        CHECK (intervalo_minutos IS NULL OR intervalo_minutos >= 0)
);
