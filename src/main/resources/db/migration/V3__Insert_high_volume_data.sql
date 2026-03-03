INSERT INTO CONTAS (status, data_fechamento)
SELECT 'ABERTA', EXTRACT(DAY FROM CURRENT_DATE)
FROM generate_series(1, 100000);

INSERT INTO LANCAMENTOS (conta_id, valor, data_lancamento)
SELECT 
    (random() * 99999 + 1)::BIGINT,
    (random() * 500 + 10)::DECIMAL(19, 2),
    CURRENT_TIMESTAMP - (floor(random() * 30) || ' days')::interval
FROM generate_series(1, 200000);
