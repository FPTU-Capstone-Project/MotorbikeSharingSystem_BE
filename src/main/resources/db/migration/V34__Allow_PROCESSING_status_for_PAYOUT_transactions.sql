-- Fix constraint to allow PROCESSING status for PAYOUT transactions
-- This fixes the issue where PAYOUT transactions cannot be set to PROCESSING status
-- when admin marks payout as processing

ALTER TABLE transactions
    DROP CONSTRAINT IF EXISTS txn_status_by_type;

ALTER TABLE transactions
    ADD CONSTRAINT txn_status_by_type CHECK (
        CASE type
            WHEN 'TOPUP' THEN status IN ('PENDING', 'SUCCESS', 'FAILED', 'REVERSED')
            WHEN 'HOLD_CREATE' THEN status IN ('PENDING', 'SUCCESS', 'FAILED', 'REVERSED')
            WHEN 'HOLD_RELEASE' THEN status IN ('PENDING', 'SUCCESS', 'FAILED', 'REVERSED')
            WHEN 'CAPTURE_FARE' THEN status IN ('PENDING', 'SUCCESS', 'FAILED', 'REVERSED')
            WHEN 'PAYOUT' THEN status IN ('PENDING', 'PROCESSING', 'SUCCESS', 'FAILED', 'REVERSED')
            WHEN 'PROMO_CREDIT' THEN status IN ('PENDING', 'SUCCESS', 'FAILED', 'REVERSED')
            WHEN 'ADJUSTMENT' THEN status IN ('PENDING', 'SUCCESS', 'FAILED', 'REVERSED')
            WHEN 'REFUND' THEN status IN ('PENDING', 'SUCCESS', 'FAILED', 'REVERSED')
            ELSE status = 'SUCCESS'
            END
        );

