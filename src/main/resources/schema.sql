-- =============================================================
-- Wallet Service — Database Schema
-- Used by Spring Boot sql.init for both H2 (local) and PostgreSQL (docker).
-- H2 runs in MODE=PostgreSQL so this DDL is compatible with both.
-- =============================================================

-- Drop in FK-safe order
DROP TABLE IF EXISTS transactions;
DROP TABLE IF EXISTS wallets;
DROP TABLE IF EXISTS users;

-- =============================================================
-- USERS
-- =============================================================
CREATE TABLE users (
    user_id    VARCHAR(36)  NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT pk_users PRIMARY KEY (user_id)
);

-- =============================================================
-- WALLETS
-- =============================================================
CREATE TABLE wallets (
    wallet_id           VARCHAR(36)    NOT NULL,
    user_id             VARCHAR(36)    NOT NULL,
    balance             DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    status              VARCHAR(10)    NOT NULL DEFAULT 'ACTIVE',
    currency            VARCHAR(10)    NOT NULL DEFAULT 'EUR',
    total_deposited     DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    total_traded        DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    created_at          TIMESTAMP      NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP      NOT NULL DEFAULT now(),
    last_transaction_at TIMESTAMP,
    description         VARCHAR(255),
    version             BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT pk_wallets     PRIMARY KEY (wallet_id),
    CONSTRAINT fk_wallet_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT uq_wallet_user UNIQUE (user_id),
    CONSTRAINT chk_wallet_status   CHECK (status   IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    CONSTRAINT chk_wallet_currency CHECK (currency IN ('USD', 'EUR', 'GBP')),
    CONSTRAINT chk_balance         CHECK (balance        >= 0),
    CONSTRAINT chk_total_deposited CHECK (total_deposited >= 0),
    CONSTRAINT chk_total_traded    CHECK (total_traded    >= 0)
);

CREATE INDEX idx_wallet_status     ON wallets (status);
CREATE INDEX idx_wallet_created_at ON wallets (created_at);
CREATE INDEX idx_wallet_currency   ON wallets (currency);

-- =============================================================
-- TRANSACTIONS
-- id is a client-generated UUID (VARCHAR) — no sequence needed
-- =============================================================
CREATE TABLE transactions (
    id              VARCHAR(36)    NOT NULL,
    wallet_id       VARCHAR(36)    NOT NULL,
    type            VARCHAR(10)    NOT NULL,
    amount          DECIMAL(19, 2) NOT NULL,
    timestamp       TIMESTAMP      NOT NULL,
    idempotency_key VARCHAR(36),
    balance_after   DECIMAL(19, 2) NOT NULL,

    CONSTRAINT pk_transactions       PRIMARY KEY (id),
    CONSTRAINT fk_transaction_wallet FOREIGN KEY (wallet_id) REFERENCES wallets (wallet_id),
    CONSTRAINT uq_tx_idempotency_key UNIQUE (wallet_id, idempotency_key),
    CONSTRAINT chk_tx_type   CHECK (type   IN ('DEPOSIT', 'TRADE')),
    CONSTRAINT chk_tx_amount CHECK (amount > 0)
);

CREATE INDEX idx_transaction_wallet_timestamp ON transactions (wallet_id, timestamp DESC);
CREATE INDEX idx_transaction_timestamp        ON transactions (timestamp DESC);
