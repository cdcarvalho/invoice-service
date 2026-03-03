CREATE TABLE CONTAS (
    id BIGSERIAL PRIMARY KEY,
    status VARCHAR(20) NOT NULL,
    data_fechamento INTEGER NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_contas_status_data ON CONTAS (status, data_fechamento);

CREATE TABLE FATURAS (
    id BIGSERIAL PRIMARY KEY,
    conta_id BIGINT NOT NULL,
    mes_referencia VARCHAR(7) NOT NULL,
    valor_total DECIMAL(19, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_faturas_conta FOREIGN KEY (conta_id) REFERENCES CONTAS (id),
    CONSTRAINT uk_fatura_conta_mes UNIQUE (conta_id, mes_referencia)
);

CREATE TABLE LANCAMENTOS (
    id BIGSERIAL PRIMARY KEY,
    conta_id BIGINT NOT NULL,
    valor DECIMAL(19, 2) NOT NULL,
    data_lancamento TIMESTAMP NOT NULL,
    CONSTRAINT fk_lancamentos_conta FOREIGN KEY (conta_id) REFERENCES CONTAS (id)
);
CREATE INDEX idx_lancamentos_conta ON LANCAMENTOS (conta_id);

CREATE TABLE BATCH_PARTITION_CONTROL (
    partition_id INTEGER PRIMARY KEY,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP,
    finished_at TIMESTAMP
);
