-- Add wallet_id column to transactions table for SSOT architecture
-- This allows transactions to be directly linked to wallets for balance calculation

-- Add wallet_id column (nullable for system transactions)
ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS wallet_id INTEGER REFERENCES wallets(wallet_id) ON DELETE SET NULL;

-- Add index for wallet_id + status for efficient balance queries
CREATE INDEX IF NOT EXISTS idx_txn_wallet_status ON transactions(wallet_id, status, created_at)
WHERE wallet_id IS NOT NULL;

-- Add idempotency_key column if not exists (for duplicate prevention)
ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(255);

-- Add unique constraint on idempotency_key
CREATE UNIQUE INDEX IF NOT EXISTS idx_txn_idempotency_key ON transactions(idempotency_key)
WHERE idempotency_key IS NOT NULL;

-- Update existing transactions to set wallet_id based on actor_user_id
-- Only for USER transactions
UPDATE transactions t
SET wallet_id = w.wallet_id
FROM wallets w
WHERE t.actor_user_id = w.user_id
  AND t.actor_kind = 'USER'
  AND t.wallet_id IS NULL;

