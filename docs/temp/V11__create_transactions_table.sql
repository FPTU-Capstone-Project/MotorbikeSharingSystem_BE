-- V11__create_transactions_table.sql
-- Create transactions table for payment transactions

CREATE TABLE IF NOT EXISTS transactions (
    txn_id SERIAL PRIMARY KEY,
    wallet_id INTEGER NOT NULL REFERENCES wallets(wallet_id) ON DELETE CASCADE,
    shared_ride_request_id INTEGER NOT NULL REFERENCES shared_ride_requests(shared_ride_request_id) ON DELETE CASCADE,
    promotion_id INTEGER REFERENCES promotions(promotion_id),
    type VARCHAR(50) NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    original_amount DECIMAL(19,2),
    discount_amount DECIMAL(19,2),
    processing_fee DECIMAL(19,2),
    psp_txn_id VARCHAR(100),
    status VARCHAR(30) DEFAULT 'pending',
    description TEXT,
    metadata TEXT,
    processed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Add indexes
CREATE INDEX idx_transactions_wallet ON transactions(wallet_id);
CREATE INDEX idx_transactions_shared_ride_request ON transactions(shared_ride_request_id);
CREATE INDEX idx_transactions_promotion ON transactions(promotion_id);
CREATE INDEX idx_transactions_status ON transactions(status);
CREATE INDEX idx_transactions_type ON transactions(type);
CREATE INDEX idx_transactions_created_at ON transactions(created_at);
CREATE INDEX idx_transactions_psp_txn_id ON transactions(psp_txn_id);

-- Add constraints
ALTER TABLE transactions ADD CONSTRAINT chk_transaction_type
    CHECK (type IN ('payment', 'refund', 'credit', 'debit', 'commission'));

ALTER TABLE transactions ADD CONSTRAINT chk_transaction_status
    CHECK (status IN ('pending', 'processing', 'completed', 'failed', 'cancelled', 'refunded'));

ALTER TABLE transactions ADD CONSTRAINT chk_transaction_amount
    CHECK (amount >= 0);

ALTER TABLE transactions ADD CONSTRAINT chk_original_amount
    CHECK (original_amount IS NULL OR original_amount >= 0);

ALTER TABLE transactions ADD CONSTRAINT chk_discount_amount
    CHECK (discount_amount IS NULL OR discount_amount >= 0);

ALTER TABLE transactions ADD CONSTRAINT chk_processing_fee
    CHECK (processing_fee IS NULL OR processing_fee >= 0);