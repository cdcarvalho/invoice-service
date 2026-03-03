-- Contas adicionais para testar variação de lançamentos
INSERT INTO CONTAS (status, data_fechamento)
SELECT 'ABERTA', EXTRACT(DAY FROM CURRENT_DATE)
FROM generate_series(1, 100);

-- Lançamentos variados: cada uma dessas 100 contas terá entre 1 e 60 lançamentos
INSERT INTO LANCAMENTOS (conta_id, valor, data_lancamento)
SELECT 
    c.id,
    (random() * 500 + 10)::DECIMAL(19, 2),
    CURRENT_TIMESTAMP - (random() * 30 || ' days')::interval
FROM (
    SELECT id, (random() * 60 + 1)::INT AS qtd_lancamentos 
    FROM CONTAS 
    ORDER BY id DESC LIMIT 100
) c
CROSS JOIN generate_series(1, c.qtd_lancamentos);
