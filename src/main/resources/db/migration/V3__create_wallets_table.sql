-- V3__create_wallets_table.sql
-- Create wallets table for user payment management

CREATE TABLE IF NOT EXISTS wallets (
    wallet_id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    psp_account_id VARCHAR(255),
    cached_balance DECIMAL(10, 2) DEFAULT 0,
    pending_balance DECIMAL(10, 2) DEFAULT 0,
    total_topped_up DECIMAL(10, 2) DEFAULT 0,
    total_spent DECIMAL(10, 2) DEFAULT 0,
    last_synced_at TIMESTAMP,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Add unique constraint for user_id (one wallet per user)
ALTER TABLE wallets ADD CONSTRAINT uk_wallet_user UNIQUE (user_id);

-- Add indexes
CREATE INDEX idx_wallet_user ON wallets(user_id);
CREATE INDEX idx_wallet_active ON wallets(is_active);

-- Add check constraints
ALTER TABLE wallets ADD CONSTRAINT chk_balance_positive 
    CHECK (cached_balance >= 0);

ALTER TABLE wallets ADD CONSTRAINT chk_pending_positive 
    CHECK (pending_balance >= 0);

-- Add update trigger for updated_at
CREATE TRIGGER update_wallets_updated_at 
    BEFORE UPDATE ON wallets 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();
