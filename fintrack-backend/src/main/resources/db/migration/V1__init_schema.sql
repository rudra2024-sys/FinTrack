-- ============================================================
-- FinTrack Database Schema
-- V1__init_schema.sql
-- ============================================================

-- USERS
CREATE TABLE users (
    id          BIGSERIAL PRIMARY KEY,
    email       VARCHAR(255) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    full_name   VARCHAR(100) NOT NULL,
    currency    VARCHAR(10)  NOT NULL DEFAULT 'INR',
    avatar_url  VARCHAR(500),
    role        VARCHAR(20)  NOT NULL DEFAULT 'ROLE_USER',
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- CATEGORIES
CREATE TABLE categories (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,
    icon        VARCHAR(50),
    color       VARCHAR(7),
    type        VARCHAR(10)  NOT NULL CHECK (type IN ('INCOME', 'EXPENSE', 'BOTH')),
    is_system   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- System default categories (user_id = NULL means global)
INSERT INTO categories (user_id, name, icon, color, type, is_system) VALUES
  (NULL, 'Salary',        'briefcase',     '#22c55e', 'INCOME',  TRUE),
  (NULL, 'Freelance',     'laptop',        '#10b981', 'INCOME',  TRUE),
  (NULL, 'Investments',   'trending-up',   '#06b6d4', 'INCOME',  TRUE),
  (NULL, 'Other Income',  'plus-circle',   '#84cc16', 'INCOME',  TRUE),
  (NULL, 'Food & Dining', 'utensils',      '#f97316', 'EXPENSE', TRUE),
  (NULL, 'Transport',     'car',           '#8b5cf6', 'EXPENSE', TRUE),
  (NULL, 'Shopping',      'shopping-bag',  '#ec4899', 'EXPENSE', TRUE),
  (NULL, 'Utilities',     'zap',           '#f59e0b', 'EXPENSE', TRUE),
  (NULL, 'Healthcare',    'heart',         '#ef4444', 'EXPENSE', TRUE),
  (NULL, 'Entertainment', 'tv',            '#6366f1', 'EXPENSE', TRUE),
  (NULL, 'Rent',          'home',          '#64748b', 'EXPENSE', TRUE),
  (NULL, 'Education',     'book',          '#0ea5e9', 'EXPENSE', TRUE),
  (NULL, 'Travel',        'plane',         '#d946ef', 'EXPENSE', TRUE),
  (NULL, 'Subscriptions', 'repeat',        '#14b8a6', 'EXPENSE', TRUE),
  (NULL, 'Other',         'more-horizontal','#94a3b8', 'BOTH',   TRUE);

-- ACCOUNTS (bank accounts, wallets, etc.)
CREATE TABLE accounts (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name            VARCHAR(100) NOT NULL,
    type            VARCHAR(20)  NOT NULL CHECK (type IN ('CHECKING', 'SAVINGS', 'CREDIT_CARD', 'WALLET', 'INVESTMENT', 'CASH')),
    balance         NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    currency        VARCHAR(10)  NOT NULL DEFAULT 'INR',
    institution     VARCHAR(100),
    account_number  VARCHAR(50),
    color           VARCHAR(7),
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- TRANSACTIONS
CREATE TABLE transactions (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    account_id      BIGINT        NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    category_id     BIGINT        REFERENCES categories(id) ON DELETE SET NULL,
    type            VARCHAR(10)   NOT NULL CHECK (type IN ('INCOME', 'EXPENSE', 'TRANSFER')),
    amount          NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    description     VARCHAR(255),
    merchant        VARCHAR(100),
    notes           TEXT,
    transaction_date DATE         NOT NULL,
    is_recurring    BOOLEAN       NOT NULL DEFAULT FALSE,
    recurring_id    BIGINT,
    tags            VARCHAR(500),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_transactions_user_id        ON transactions(user_id);
CREATE INDEX idx_transactions_date           ON transactions(transaction_date DESC);
CREATE INDEX idx_transactions_category       ON transactions(category_id);
CREATE INDEX idx_transactions_account        ON transactions(account_id);
CREATE INDEX idx_transactions_type           ON transactions(type);

-- RECURRING TRANSACTIONS
CREATE TABLE recurring_transactions (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    account_id          BIGINT        NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    category_id         BIGINT        REFERENCES categories(id) ON DELETE SET NULL,
    type                VARCHAR(10)   NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    amount              NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    description         VARCHAR(255)  NOT NULL,
    frequency           VARCHAR(20)   NOT NULL CHECK (frequency IN ('DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY')),
    start_date          DATE          NOT NULL,
    end_date            DATE,
    next_due_date       DATE          NOT NULL,
    last_processed_date DATE,
    is_active           BOOLEAN       NOT NULL DEFAULT TRUE,
    auto_create         BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- BUDGETS
CREATE TABLE budgets (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id     BIGINT        REFERENCES categories(id) ON DELETE CASCADE,
    name            VARCHAR(100)  NOT NULL,
    amount          NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    period          VARCHAR(20)   NOT NULL CHECK (period IN ('WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY')),
    start_date      DATE          NOT NULL,
    end_date        DATE,
    alert_threshold NUMERIC(5,2)  NOT NULL DEFAULT 80.00,
    is_active       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- SAVINGS GOALS
CREATE TABLE savings_goals (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    account_id          BIGINT        REFERENCES accounts(id) ON DELETE SET NULL,
    name                VARCHAR(100)  NOT NULL,
    description         TEXT,
    target_amount       NUMERIC(15,2) NOT NULL CHECK (target_amount > 0),
    current_amount      NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    monthly_contribution NUMERIC(15,2),
    target_date         DATE,
    icon                VARCHAR(50),
    color               VARCHAR(7),
    status              VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'COMPLETED', 'PAUSED', 'CANCELLED')),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- SAVINGS GOAL CONTRIBUTIONS
CREATE TABLE savings_contributions (
    id          BIGSERIAL PRIMARY KEY,
    goal_id     BIGINT        NOT NULL REFERENCES savings_goals(id) ON DELETE CASCADE,
    amount      NUMERIC(15,2) NOT NULL,
    notes       VARCHAR(255),
    contributed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- REFRESH TOKENS
CREATE TABLE refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token       VARCHAR(512) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
