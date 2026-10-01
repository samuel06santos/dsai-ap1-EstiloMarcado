-- Cliente. Tabela minima exigida pelo painel profissional: identifica quem sera
-- atendido. A spec propria de clientes e autenticacao expandira este schema.
CREATE TABLE cliente (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(120) NOT NULL
);
