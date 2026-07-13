-- V1: core tables for transactions + response-code reference.
-- Schema is owned by Flyway; Hibernate stays ddl-auto=validate.

-- Reference / ground-truth table for payment response codes (RC).
CREATE TABLE response_codes (
    rc        VARCHAR(2) PRIMARY KEY,
    meaning   TEXT       NOT NULL,
    handling  TEXT       NOT NULL,
    CONSTRAINT response_codes_rc_format CHECK (rc ~ '^[0-9]{2}$')
);

-- Fact table (exact spec fields).
CREATE TABLE transactions (
    transaction_id    VARCHAR(32)   PRIMARY KEY,
    account_number    VARCHAR(32)   NOT NULL,
    amount            NUMERIC(18,0) NOT NULL,   -- VND: 1 đồng is the smallest unit, no decimals
    transaction_date  TIMESTAMPTZ   NOT NULL,
    response_code     VARCHAR(2)    NOT NULL REFERENCES response_codes(rc),
    status            VARCHAR(16)   NOT NULL,
    CONSTRAINT transactions_amount_nonneg CHECK (amount >= 0),
    CONSTRAINT transactions_status_valid  CHECK (status IN ('SUCCESS', 'FAILED'))
);

-- Latest-per-account lookups (Group 1) and per-code filtering.
CREATE INDEX idx_txn_account_date  ON transactions (account_number, transaction_date DESC);
CREATE INDEX idx_txn_response_code ON transactions (response_code);
