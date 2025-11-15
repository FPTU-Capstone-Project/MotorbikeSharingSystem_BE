-- Add evidence_url column to transactions table for payout evidence storage
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS evidence_url VARCHAR(500);

-- Update status constraint to include PROCESSING status
ALTER TABLE transactions DROP CONSTRAINT IF EXISTS txn_status_allowed;
ALTER TABLE transactions ADD CONSTRAINT txn_status_allowed CHECK (
    status IN ('PENDING', 'PROCESSING', 'SUCCESS', 'FAILED', 'REVERSED')
);






