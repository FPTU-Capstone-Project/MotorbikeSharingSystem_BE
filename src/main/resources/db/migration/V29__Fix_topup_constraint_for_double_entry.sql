-- Fix txn_type_combo_valid constraint to allow TOPUP with SYSTEM.MASTER OUT
-- This aligns with double-entry accounting: System.MASTER OUT (Debit) = User IN (Credit)

ALTER TABLE transactions
    DROP CONSTRAINT IF EXISTS txn_type_combo_valid;

-- Updated constraint: Allow TOPUP with SYSTEM.MASTER OUT (for double-entry accounting)
ALTER TABLE transactions
    ADD CONSTRAINT txn_type_combo_valid CHECK (
        CASE type
            WHEN 'TOPUP' THEN
                (
                    -- ✅ FIX: Allow SYSTEM.MASTER OUT (system gives money to user)
                    (actor_kind = 'SYSTEM' AND system_wallet = 'MASTER' AND direction = 'OUT')
                        OR
                    -- ✅ FIX: Allow SYSTEM.MASTER IN (old logic, kept for backward compatibility)
                    (actor_kind = 'SYSTEM' AND system_wallet = 'MASTER' AND direction = 'IN')
                        OR
                    -- User receives money
                    (actor_kind = 'USER' AND direction = 'IN')
                    )
            WHEN 'HOLD_CREATE' THEN (actor_kind = 'USER' AND direction = 'INTERNAL')
            WHEN 'HOLD_RELEASE' THEN (actor_kind = 'USER' AND direction = 'INTERNAL')
            WHEN 'CAPTURE_FARE' THEN (
                (actor_kind = 'USER' AND direction IN ('IN', 'OUT'))
                    OR
                (actor_kind = 'SYSTEM' AND system_wallet = 'COMMISSION' AND direction = 'IN')
                )
            WHEN 'PAYOUT' THEN (
                -- ✅ FIX: Allow PAYOUT (changed from PAYOUT_SUCCESS)
                (actor_kind = 'USER' AND direction = 'OUT')
                    OR
                (actor_kind = 'SYSTEM' AND system_wallet = 'MASTER' AND direction = 'OUT')
                )
            WHEN 'PROMO_CREDIT' THEN (
                (actor_kind = 'SYSTEM' AND system_wallet = 'PROMO' AND direction = 'OUT')
                    OR
                (actor_kind = 'USER' AND direction = 'IN')
                )
            WHEN 'ADJUSTMENT' THEN TRUE
            WHEN 'REFUND' THEN (
                -- ✅ FIX: Allow REFUND with both IN and OUT directions
                (actor_kind = 'USER' AND direction IN ('IN', 'OUT'))
                    OR
                (actor_kind = 'SYSTEM' AND system_wallet IN ('MASTER', 'COMMISSION', 'PROMO') AND direction IN ('IN', 'OUT'))
                )
            ELSE TRUE
            END
        );

