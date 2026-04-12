ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS transaction_time TIME,
    ADD COLUMN IF NOT EXISTS spending_state VARCHAR(10);

UPDATE transactions
SET spending_state = CASE
    WHEN amount < 500 THEN 'LOW'
    WHEN amount > 2000 THEN 'HIGH'
    ELSE 'NORMAL'
END
WHERE spending_state IS NULL;
