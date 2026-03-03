-- Inserir 100.000 contas com data_fechamento igual ao dia atual para testar localmente em poucos segundos
INSERT INTO CONTAS (status, data_fechamento)
SELECT 'ABERTA', EXTRACT(DAY FROM CURRENT_DATE)
FROM generate_series(1, 100000);

-- Inserir 200.000 lançamentos associados aleatoriamente às contas geradas
INSERT INTO LANCAMENTOS (conta_id, valor, data_lancamento)
SELECT 
    (random() * 99999 + 1)::BIGINT, -- Associa a uma das 100k contas criadas
    (random() * 500 + 10)::DECIMAL(19, 2), -- Valor aleatório entre 10 e 510
    CURRENT_TIMESTAMP - (random() * 30 || ' days')::interval -- Data aleatória nos últimos 30 dias
FROM generate_series(1, 200000);
