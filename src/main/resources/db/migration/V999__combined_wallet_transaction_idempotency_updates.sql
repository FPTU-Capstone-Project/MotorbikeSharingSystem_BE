-- Combined migration: Wallet versioning, Transaction constraints, and Idempotency/Reconciliation
-- This migration combines V7, V8, and V9 into a single comprehensive update

-- ============================================================================
-- V7: Wallet Versioning and Atomic Updates
-- ============================================================================

-- Add optimistic locking version column to wallets
ALTER TABLE wallets
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- ============================================================================
-- V8: Sync Transaction Enum Constraints
-- ============================================================================

-- Update transaction constraints to match current transaction types
ALTER TABLE transactions
    DROP CONSTRAINT IF EXISTS txn_type_allowed,
    DROP CONSTRAINT IF EXISTS txn_status_by_type,
    DROP CONSTRAINT IF EXISTS transactions_type_check;

-- Updated type allowed constraint
ALTER TABLE transactions
    ADD CONSTRAINT txn_type_allowed CHECK (
        type IN ('TOPUP', 'HOLD_CREATE', 'HOLD_RELEASE', 'CAPTURE_FARE', 'PAYOUT', 'PROMO_CREDIT', 'ADJUSTMENT', 'REFUND')
    );

-- Updated status by type validation for current transaction types
ALTER TABLE transactions
    ADD CONSTRAINT txn_status_by_type CHECK (
        CASE type
            WHEN 'TOPUP' THEN status IN ('PENDING', 'SUCCESS', 'FAILED', 'REVERSED')
            WHEN 'HOLD_CREATE' THEN status IN ('PENDING', 'SUCCESS', 'FAILED', 'REVERSED')
            WHEN 'HOLD_RELEASE' THEN status IN ('PENDING', 'SUCCESS', 'FAILED', 'REVERSED')
            WHEN 'CAPTURE_FARE' THEN status IN ('PENDING', 'SUCCESS', 'FAILED', 'REVERSED')
            WHEN 'PAYOUT' THEN status IN ('PENDING', 'SUCCESS', 'FAILED', 'REVERSED')
            WHEN 'PROMO_CREDIT' THEN status IN ('PENDING', 'SUCCESS', 'FAILED', 'REVERSED')
            WHEN 'ADJUSTMENT' THEN status IN ('PENDING', 'SUCCESS', 'FAILED', 'REVERSED')
            WHEN 'REFUND' THEN status IN ('PENDING', 'SUCCESS', 'FAILED', 'REVERSED')
            ELSE status = 'SUCCESS'
            END
        );

-- ============================================================================
-- V9: Idempotency and Reconciliation Tables
-- ============================================================================

-- Idempotency keys: prevent duplicate processing for PSP callbacks/initiations
CREATE TABLE IF NOT EXISTS idempotency_keys (
    id               SERIAL PRIMARY KEY,
    key_hash         VARCHAR(128) NOT NULL UNIQUE,
    reference        VARCHAR(128),
    request_fingerprint TEXT,
    created_at       TIMESTAMP DEFAULT NOW() NOT NULL,
    expires_at       TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_idem_expires ON idempotency_keys (expires_at);

-- Reconciliation run and results
CREATE TABLE IF NOT EXISTS reconciliation_runs (
    run_id       SERIAL PRIMARY KEY,
    started_at   TIMESTAMP DEFAULT NOW() NOT NULL,
    finished_at  TIMESTAMP,
    status       VARCHAR(20) DEFAULT 'RUNNING',
    notes        TEXT
);

CREATE TABLE IF NOT EXISTS reconciliation_results (
    result_id    SERIAL PRIMARY KEY,
    run_id       INTEGER NOT NULL REFERENCES reconciliation_runs(run_id) ON DELETE CASCADE,
    ref          VARCHAR(128),
    kind         VARCHAR(32), -- MISSING_IN_LEDGER | MISSING_IN_PSP | AMOUNT_MISMATCH | STATUS_MISMATCH
    detail       TEXT,
    created_at   TIMESTAMP DEFAULT NOW() NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_recon_run ON reconciliation_results (run_id);

-- ============================================================================
-- Mock Transaction Data for Testing and Development
-- ============================================================================

-- Insert additional test users for comprehensive transaction testing
INSERT INTO users (email, phone, password_hash, full_name, student_id, user_type, status, email_verified, phone_verified, token_version, created_at, updated_at)
VALUES 
    ('alice.smith@example.com', '0987654322', '$2a$10$BaeiCK1yapOvw.WrcaGb1OqHVOqqSD4TkEAvhHThm.F85BvxYH7ru', 'Alice Smith', 'STU123457', 'USER', 'ACTIVE', true, true, 1, now(), now()),
    ('bob.johnson@example.com', '0987654323', '$2a$10$BaeiCK1yapOvw.WrcaGb1OqHVOqqSD4TkEAvhHThm.F85BvxYH7ru', 'Bob Johnson', 'STU123458', 'USER', 'ACTIVE', true, true, 1, now(), now()),
    ('charlie.brown@example.com', '0987654324', '$2a$10$BaeiCK1yapOvw.WrcaGb1OqHVOqqSD4TkEAvhHThm.F85BvxYH7ru', 'Charlie Brown', 'STU123459', 'USER', 'ACTIVE', true, true, 1, now(), now())
ON CONFLICT (email) DO NOTHING;

-- Create rider profiles for test users
INSERT INTO rider_profiles (rider_id, emergency_contact, preferred_payment_method, created_at)
SELECT u.user_id, '0901234567', 'WALLET', now()
FROM users u
WHERE u.email IN ('alice.smith@example.com', 'bob.johnson@example.com', 'charlie.brown@example.com')
  AND NOT EXISTS (SELECT 1 FROM rider_profiles rp WHERE rp.rider_id = u.user_id);

-- Create driver profiles for test users
INSERT INTO driver_profiles (driver_id, license_number, status, is_available, created_at)
SELECT u.user_id, 'DL' || u.user_id || '789', 'ACTIVE', true, now()
FROM users u
WHERE u.email IN ('alice.smith@example.com', 'bob.johnson@example.com', 'charlie.brown@example.com')
  AND NOT EXISTS (SELECT 1 FROM driver_profiles dp WHERE dp.driver_id = u.user_id);

-- Create wallets for test users
INSERT INTO wallets (user_id, shadow_balance, pending_balance, total_topped_up, total_spent, is_active, last_synced_at, created_at, updated_at)
SELECT u.user_id, 500000, 0, 500000, 0, true, now(), now(), now()
FROM users u
WHERE u.email IN ('alice.smith@example.com', 'bob.johnson@example.com', 'charlie.brown@example.com')
  AND NOT EXISTS (SELECT 1 FROM wallets w WHERE w.user_id = u.user_id);

-- Mock Transaction Data: Various transaction types and scenarios
-- Transaction Group 1: Alice's Top-up
WITH transaction_group AS (SELECT '11111111-1111-1111-1111-111111111111'::uuid AS group_id),
     system_txn AS (
         INSERT INTO transactions (group_id, type, direction, actor_kind, actor_user_id, system_wallet, amount, currency, status, psp_ref, note, created_at)
         SELECT group_id, 'TOPUP', 'IN', 'SYSTEM', NULL, 'MASTER', 200000, 'VND', 'SUCCESS', 'PSP-ALICE-200K-001', 'PSP Inflow - Alice wallet funding', now()
         FROM transaction_group
         RETURNING group_id)
INSERT INTO transactions (group_id, type, direction, actor_kind, actor_user_id, rider_user_id, amount, currency, status, before_avail, after_avail, before_pending, after_pending, psp_ref, note, created_at)
SELECT g.group_id, 'TOPUP', 'IN', 'USER', 
       (SELECT user_id FROM users WHERE email = 'alice.smith@example.com'),
       (SELECT user_id FROM users WHERE email = 'alice.smith@example.com'),
       200000, 'VND', 'SUCCESS', 500000, 700000, 0, 0, 'PSP-ALICE-200K-001', 'Alice wallet top-up - 200,000 VND', now()
FROM system_txn g;

-- Transaction Group 2: Bob's Ride Payment Flow
WITH transaction_group AS (SELECT '22222222-2222-2222-2222-222222222222'::uuid AS group_id),
     hold_create AS (
         INSERT INTO transactions (group_id, type, direction, actor_kind, actor_user_id, rider_user_id, amount, currency, status, before_avail, after_avail, before_pending, after_pending, note, created_at)
         SELECT group_id, 'HOLD_CREATE', 'INTERNAL', 'USER', 
                (SELECT user_id FROM users WHERE email = 'bob.johnson@example.com'),
                (SELECT user_id FROM users WHERE email = 'bob.johnson@example.com'),
                50000, 'VND', 'SUCCESS', 500000, 450000, 0, 50000, 'Hold funds for ride payment', now()
         FROM transaction_group
         RETURNING group_id),
     hold_release AS (
         INSERT INTO transactions (group_id, type, direction, actor_kind, actor_user_id, rider_user_id, amount, currency, status, before_avail, after_avail, before_pending, after_pending, note, created_at)
         SELECT group_id, 'HOLD_RELEASE', 'INTERNAL', 'USER', 
                (SELECT user_id FROM users WHERE email = 'bob.johnson@example.com'),
                (SELECT user_id FROM users WHERE email = 'bob.johnson@example.com'),
                50000, 'VND', 'SUCCESS', 450000, 500000, 50000, 0, 'Release hold - ride cancelled', now()
         FROM transaction_group
         RETURNING group_id)
SELECT 1; -- Placeholder to complete the CTE

-- Transaction Group 3: Charlie's Successful Ride Payment
WITH transaction_group AS (SELECT '33333333-3333-3333-3333-333333333333'::uuid AS group_id),
     hold_create AS (
         INSERT INTO transactions (group_id, type, direction, actor_kind, actor_user_id, rider_user_id, amount, currency, status, before_avail, after_avail, before_pending, after_pending, note, created_at)
         SELECT group_id, 'HOLD_CREATE', 'INTERNAL', 'USER', 
                (SELECT user_id FROM users WHERE email = 'charlie.brown@example.com'),
                (SELECT user_id FROM users WHERE email = 'charlie.brown@example.com'),
                75000, 'VND', 'SUCCESS', 500000, 425000, 0, 75000, 'Hold funds for ride payment', now()
         FROM transaction_group
         RETURNING group_id),
     capture_fare AS (
         INSERT INTO transactions (group_id, type, direction, actor_kind, actor_user_id, rider_user_id, driver_user_id, amount, currency, status, before_avail, after_avail, before_pending, after_pending, note, created_at)
         SELECT group_id, 'CAPTURE_FARE', 'INTERNAL', 'USER', 
                (SELECT user_id FROM users WHERE email = 'charlie.brown@example.com'),
                (SELECT user_id FROM users WHERE email = 'charlie.brown@example.com'),
                (SELECT user_id FROM users WHERE email = 'alice.smith@example.com'),
                75000, 'VND', 'SUCCESS', 425000, 425000, 75000, 0, 'Capture fare for completed ride', now()
         FROM transaction_group
         RETURNING group_id),
     driver_payout AS (
         INSERT INTO transactions (group_id, type, direction, actor_kind, actor_user_id, driver_user_id, amount, currency, status, before_avail, after_avail, before_pending, after_pending, note, created_at)
         SELECT group_id, 'PAYOUT', 'OUT', 'USER', 
                (SELECT user_id FROM users WHERE email = 'alice.smith@example.com'),
                (SELECT user_id FROM users WHERE email = 'alice.smith@example.com'),
                63750, 'VND', 'SUCCESS', 700000, 636250, 0, 0, 'Driver payout (85% of fare)', now()
         FROM transaction_group
         RETURNING group_id),
     system_commission AS (
         INSERT INTO transactions (group_id, type, direction, actor_kind, actor_user_id, system_wallet, amount, currency, status, note, created_at)
         SELECT group_id, 'PAYOUT', 'IN', 'SYSTEM', NULL, 'COMMISSION', 11250, 'VND', 'SUCCESS', 'System commission (15% of fare)', now()
         FROM transaction_group
         RETURNING group_id)
SELECT 1; -- Placeholder to complete the CTE

-- Transaction Group 4: Promo Credit
WITH transaction_group AS (SELECT '44444444-4444-4444-4444-444444444444'::uuid AS group_id),
     system_txn AS (
         INSERT INTO transactions (group_id, type, direction, actor_kind, actor_user_id, system_wallet, amount, currency, status, note, created_at)
         SELECT group_id, 'PROMO_CREDIT', 'OUT', 'SYSTEM', NULL, 'PROMO', 10000, 'VND', 'SUCCESS', 'Promo credit deduction', now()
         FROM transaction_group
         RETURNING group_id)
INSERT INTO transactions (group_id, type, direction, actor_kind, actor_user_id, rider_user_id, amount, currency, status, before_avail, after_avail, before_pending, after_pending, note, created_at)
SELECT g.group_id, 'PROMO_CREDIT', 'IN', 'USER', 
       (SELECT user_id FROM users WHERE email = 'alice.smith@example.com'),
       (SELECT user_id FROM users WHERE email = 'alice.smith@example.com'),
       10000, 'VND', 'SUCCESS', 636250, 646250, 0, 0, 'Promo credit applied - Welcome bonus', now()
FROM system_txn g;

-- Transaction Group 5: Failed Transaction
WITH transaction_group AS (SELECT '55555555-5555-5555-5555-555555555555'::uuid AS group_id),
     system_txn AS (
         INSERT INTO transactions (group_id, type, direction, actor_kind, actor_user_id, system_wallet, amount, currency, status, psp_ref, note, created_at)
         SELECT group_id, 'TOPUP', 'IN', 'SYSTEM', NULL, 'MASTER', 100000, 'VND', 'FAILED', 'PSP-FAILED-100K-001', 'PSP Inflow - Failed transaction', now()
         FROM transaction_group
         RETURNING group_id)
INSERT INTO transactions (group_id, type, direction, actor_kind, actor_user_id, rider_user_id, amount, currency, status, before_avail, after_avail, before_pending, after_pending, psp_ref, note, created_at)
SELECT g.group_id, 'TOPUP', 'IN', 'USER', 
       (SELECT user_id FROM users WHERE email = 'bob.johnson@example.com'),
       (SELECT user_id FROM users WHERE email = 'bob.johnson@example.com'),
       100000, 'VND', 'FAILED', 500000, 500000, 0, 0, 'PSP-FAILED-100K-001', 'Failed top-up attempt - Payment declined', now()
FROM system_txn g;

-- Transaction Group 6: Adjustment Transaction
WITH transaction_group AS (SELECT '66666666-6666-6666-6666-666666666666'::uuid AS group_id),
     system_txn AS (
         INSERT INTO transactions (group_id, type, direction, actor_kind, actor_user_id, system_wallet, amount, currency, status, note, created_at)
         SELECT group_id, 'ADJUSTMENT', 'OUT', 'SYSTEM', NULL, 'MASTER', 5000, 'VND', 'SUCCESS', 'Adjustment deduction', now()
         FROM transaction_group
         RETURNING group_id)
INSERT INTO transactions (group_id, type, direction, actor_kind, actor_user_id, rider_user_id, amount, currency, status, before_avail, after_avail, before_pending, after_pending, note, created_at)
SELECT g.group_id, 'ADJUSTMENT', 'IN', 'USER', 
       (SELECT user_id FROM users WHERE email = 'charlie.brown@example.com'),
       (SELECT user_id FROM users WHERE email = 'charlie.brown@example.com'),
       5000, 'VND', 'SUCCESS', 500000, 505000, 0, 0, 'Manual adjustment - Customer service credit', now()
FROM system_txn g;

-- Transaction Group 7: Refund Transaction
WITH transaction_group AS (SELECT '77777777-7777-7777-7777-777777777777'::uuid AS group_id),
     system_txn AS (
         INSERT INTO transactions (group_id, type, direction, actor_kind, actor_user_id, system_wallet, amount, currency, status, note, created_at)
         SELECT group_id, 'REFUND', 'OUT', 'SYSTEM', NULL, 'MASTER', 25000, 'VND', 'SUCCESS', 'Refund processing', now()
         FROM transaction_group
         RETURNING group_id)
INSERT INTO transactions (group_id, type, direction, actor_kind, actor_user_id, rider_user_id, amount, currency, status, before_avail, after_avail, before_pending, after_pending, note, created_at)
SELECT g.group_id, 'REFUND', 'IN', 'USER', 
       (SELECT user_id FROM users WHERE email = 'alice.smith@example.com'),
       (SELECT user_id FROM users WHERE email = 'alice.smith@example.com'),
       25000, 'VND', 'SUCCESS', 646250, 671250, 0, 0, 'Refund for cancelled ride - Partial refund', now()
FROM system_txn g;