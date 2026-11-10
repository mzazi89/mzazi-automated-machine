-- ---------------------------------------------------------------------------
-- MZAZI TECH AUTOMATED MACHINE — PostgreSQL schema (Neon compatible)
-- Run with: npm run migrate
-- ---------------------------------------------------------------------------

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Administrators -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS admins (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         TEXT NOT NULL UNIQUE,
    name          TEXT NOT NULL,
    role          TEXT NOT NULL DEFAULT 'operator'
                  CHECK (role IN ('super_admin', 'admin', 'operator')),
    password_hash TEXT NOT NULL,
    disabled      BOOLEAN NOT NULL DEFAULT FALSE,
    last_login_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Registered Android terminals ------------------------------------------------
CREATE TABLE IF NOT EXISTS devices (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    label        TEXT NOT NULL,
    api_key_hash TEXT NOT NULL,
    enabled      BOOLEAN NOT NULL DEFAULT TRUE,
    last_seen_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- USSD workflows (operator verified) ------------------------------------------
CREATE TABLE IF NOT EXISTS ussd_workflows (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    network         TEXT NOT NULL CHECK (network IN ('SAFARICOM', 'AIRTEL', 'TELKOM')),
    name            TEXT NOT NULL,
    steps           JSONB NOT NULL DEFAULT '[]'::jsonb,
    success_pattern TEXT,
    failure_pattern TEXT,
    timeout_ms      INTEGER NOT NULL DEFAULT 45000 CHECK (timeout_ms BETWEEN 5000 AND 180000),
    enabled         BOOLEAN NOT NULL DEFAULT FALSE,
    verified_by     TEXT,
    version         INTEGER NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- A workflow cannot be enabled before someone takes responsibility for the codes.
    CONSTRAINT workflow_requires_verifier CHECK (NOT enabled OR verified_by IS NOT NULL),
    CONSTRAINT workflow_requires_steps CHECK (NOT enabled OR jsonb_array_length(steps) > 0)
);

CREATE INDEX IF NOT EXISTS idx_workflows_network ON ussd_workflows (network, enabled);

-- Bundle catalogue ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bundles (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    network             TEXT NOT NULL CHECK (network IN ('SAFARICOM', 'AIRTEL', 'TELKOM')),
    name                TEXT NOT NULL,
    data_size_mb        INTEGER NOT NULL CHECK (data_size_mb > 0),
    selling_price_cents BIGINT NOT NULL CHECK (selling_price_cents > 0),
    cost_price_cents    BIGINT NOT NULL DEFAULT 0 CHECK (cost_price_cents >= 0),
    validity_days       INTEGER NOT NULL DEFAULT 30 CHECK (validity_days > 0),
    workflow_id         UUID REFERENCES ussd_workflows (id) ON DELETE SET NULL,
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order          INTEGER NOT NULL DEFAULT 0,
    deleted_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_bundles_network ON bundles (network, enabled) WHERE deleted_at IS NULL;

-- Transactions ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS transactions (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference            TEXT NOT NULL UNIQUE,
    customer_msisdn      TEXT NOT NULL,
    network              TEXT NOT NULL CHECK (network IN ('SAFARICOM', 'AIRTEL', 'TELKOM')),
    bundle_id            UUID REFERENCES bundles (id) ON DELETE SET NULL,
    bundle_name          TEXT NOT NULL,
    bundle_size_mb       INTEGER NOT NULL,
    amount_cents         BIGINT NOT NULL CHECK (amount_cents > 0),
    payment_status       TEXT NOT NULL DEFAULT 'PENDING'
                         CHECK (payment_status IN ('PENDING', 'PAID', 'FAILED', 'CANCELLED', 'REVERSED', 'REFUNDED')),
    purchase_status      TEXT NOT NULL DEFAULT 'QUEUED'
                         CHECK (purchase_status IN ('QUEUED', 'RUNNING', 'AWAITING_MANUAL_CONFIRMATION', 'SUCCESS', 'FAILED', 'CANCELLED')),
    payment_reference    TEXT,
    mpesa_receipt        TEXT,
    checkout_request_id  TEXT,
    failure_reason       TEXT,
    sim_slot             INTEGER,
    manual_confirmation_required BOOLEAN NOT NULL DEFAULT FALSE,
    idempotency_key      TEXT NOT NULL UNIQUE,
    test_mode            BOOLEAN NOT NULL DEFAULT FALSE,
    device_id            UUID REFERENCES devices (id) ON DELETE SET NULL,
    paid_at              TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_transactions_created ON transactions (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_msisdn ON transactions (customer_msisdn);
CREATE INDEX IF NOT EXISTS idx_transactions_payment ON transactions (payment_status);
CREATE INDEX IF NOT EXISTS idx_transactions_purchase ON transactions (purchase_status);

-- M-Pesa STK requests ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS payments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id      UUID NOT NULL REFERENCES transactions (id) ON DELETE CASCADE,
    provider            TEXT NOT NULL DEFAULT 'MPESA',
    checkout_request_id TEXT UNIQUE,
    merchant_request_id TEXT,
    amount_cents        BIGINT NOT NULL CHECK (amount_cents > 0),
    phone               TEXT NOT NULL,
    status              TEXT NOT NULL DEFAULT 'INITIATED'
                        CHECK (status IN ('INITIATED', 'PENDING', 'SUCCESS', 'FAILED', 'CANCELLED', 'TIMEOUT', 'REVERSED')),
    result_code         INTEGER,
    result_desc         TEXT,
    mpesa_receipt       TEXT,
    raw_request         JSONB,
    raw_callback        JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_payments_transaction ON payments (transaction_id);
CREATE INDEX IF NOT EXISTS idx_payments_status ON payments (status);

-- USSD delivery jobs -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS ussd_jobs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id  UUID NOT NULL UNIQUE REFERENCES transactions (id) ON DELETE CASCADE,
    network         TEXT NOT NULL CHECK (network IN ('SAFARICOM', 'AIRTEL', 'TELKOM')),
    workflow_id     UUID REFERENCES ussd_workflows (id) ON DELETE SET NULL,
    status          TEXT NOT NULL DEFAULT 'QUEUED'
                    CHECK (status IN ('QUEUED', 'CLAIMED', 'RUNNING', 'AWAITING_MANUAL_CONFIRMATION', 'SUCCESS', 'FAILED', 'CANCELLED', 'DEAD_LETTER')),
    attempt_count   INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    max_attempts    INTEGER NOT NULL DEFAULT 3 CHECK (max_attempts BETWEEN 1 AND 10),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    claimed_by      UUID REFERENCES devices (id) ON DELETE SET NULL,
    lease_expires_at TIMESTAMPTZ,
    last_error      TEXT,
    sim_slot        INTEGER NOT NULL DEFAULT 0,
    steps_log       JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ussd_jobs_pending ON ussd_jobs (status, next_attempt_at);

-- Audit trail -------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_logs (
    id         BIGSERIAL PRIMARY KEY,
    actor_type TEXT NOT NULL CHECK (actor_type IN ('admin', 'device', 'system', 'mpesa')),
    actor_id   TEXT,
    action     TEXT NOT NULL,
    target     TEXT,
    detail     JSONB,
    ip         TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_audit_created ON audit_logs (created_at DESC);

-- updated_at maintenance --------------------------------------------------------
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE
    target TEXT;
BEGIN
    FOREACH target IN ARRAY ARRAY['admins', 'devices', 'ussd_workflows', 'bundles', 'transactions', 'payments', 'ussd_jobs']
    LOOP
        EXECUTE format('DROP TRIGGER IF EXISTS trg_%1$s_updated ON %1$s', target);
        EXECUTE format(
            'CREATE TRIGGER trg_%1$s_updated BEFORE UPDATE ON %1$s FOR EACH ROW EXECUTE FUNCTION set_updated_at()',
            target
        );
    END LOOP;
END;
$$;

-- Database-level guarantee: no delivery job without verified payment -------------
CREATE OR REPLACE FUNCTION assert_transaction_paid() RETURNS TRIGGER AS $$
DECLARE
    paid BOOLEAN;
BEGIN
    SELECT (payment_status = 'PAID') INTO paid
    FROM transactions
    WHERE id = NEW.transaction_id;

    IF paid IS DISTINCT FROM TRUE THEN
        RAISE EXCEPTION 'USSD job refused: transaction % is not PAID', NEW.transaction_id
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_ussd_jobs_require_paid ON ussd_jobs;
CREATE TRIGGER trg_ussd_jobs_require_paid
    BEFORE INSERT ON ussd_jobs
    FOR EACH ROW EXECUTE FUNCTION assert_transaction_paid();
