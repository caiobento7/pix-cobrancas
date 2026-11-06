CREATE TABLE cobranca (
    id               UUID                     PRIMARY KEY,
    txid             VARCHAR(35)              NOT NULL UNIQUE,
    valor            NUMERIC(12, 2)           NOT NULL CHECK (valor > 0),
    descricao        VARCHAR(140),
    status           VARCHAR(20)              NOT NULL,
    pix_copia_e_cola VARCHAR(512)             NOT NULL,
    criada_em        TIMESTAMP WITH TIME ZONE NOT NULL,
    expira_em        TIMESTAMP WITH TIME ZONE NOT NULL,
    paga_em          TIMESTAMP WITH TIME ZONE,
    end_to_end_id    VARCHAR(32)              UNIQUE,
    valor_pago       NUMERIC(12, 2),
    versao           BIGINT                   NOT NULL
);

-- A conciliação procura cobranças ATIVAS mais antigas que alguns minutos.
CREATE INDEX idx_cobranca_status_criada_em ON cobranca (status, criada_em);

-- Cada Pix recebido fica registrado uma única vez: a chave primária é o endToEndId,
-- que identifica a transação no SPI. É ela que garante a idempotência do webhook.
CREATE TABLE pagamento_recebido (
    end_to_end_id VARCHAR(32)              PRIMARY KEY,
    txid          VARCHAR(35),
    valor         NUMERIC(12, 2)           NOT NULL,
    horario       TIMESTAMP WITH TIME ZONE NOT NULL,
    origem        VARCHAR(20)              NOT NULL,
    resultado     VARCHAR(30)              NOT NULL,
    recebido_em   TIMESTAMP WITH TIME ZONE NOT NULL
);

-- O id da devolução é escolhido por quem pede, como na API Pix do Banco Central:
-- repetir o mesmo pedido devolve o mesmo resultado em vez de devolver o dinheiro duas vezes.
CREATE TABLE devolucao (
    id            VARCHAR(35)              PRIMARY KEY,
    cobranca_id   UUID                     NOT NULL REFERENCES cobranca (id),
    valor         NUMERIC(12, 2)           NOT NULL CHECK (valor > 0),
    motivo        VARCHAR(140),
    status        VARCHAR(20)              NOT NULL,
    solicitada_em TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_devolucao_cobranca ON devolucao (cobranca_id);
