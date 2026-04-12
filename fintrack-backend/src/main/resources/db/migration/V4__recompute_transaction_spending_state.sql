UPDATE transactions
SET amount = ABS(amount),
    spending_state = CASE
        WHEN ABS(amount) < 500 THEN 'LOW'
        WHEN ABS(amount) > 2000 THEN 'HIGH'
        ELSE 'NORMAL'
    END;
