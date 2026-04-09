CREATE TABLE statements (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    account_id        BIGINT       NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    file_name         VARCHAR(255) NOT NULL,
    source            VARCHAR(100),
    upload_date       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    statement_date    DATE,
    status            VARCHAR(30)  NOT NULL DEFAULT 'PROCESSED',
    transaction_count INTEGER      NOT NULL DEFAULT 0,
    total_income      NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    total_expenses    NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    notes             VARCHAR(500)
);

CREATE TABLE financial_goals (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT        NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    rent           NUMERIC(15,2),
    savings_goal   NUMERIC(15,2),
    monthly_budget NUMERIC(15,2),
    notes          VARCHAR(500),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS statement_id BIGINT REFERENCES statements(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS import_hash VARCHAR(128),
    ADD COLUMN IF NOT EXISTS ai_category_label VARCHAR(100),
    ADD COLUMN IF NOT EXISTS import_source VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_transactions_statement_id ON transactions(statement_id);
CREATE INDEX IF NOT EXISTS idx_transactions_import_hash ON transactions(import_hash);
CREATE UNIQUE INDEX IF NOT EXISTS uq_transactions_user_import_hash
    ON transactions(user_id, import_hash)
    WHERE import_hash IS NOT NULL;
