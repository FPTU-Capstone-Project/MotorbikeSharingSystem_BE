-- Migration: Add report chat support to messages table
-- This allows messages to be associated with reports (for admin-user chat about reports)
-- while maintaining backward compatibility with ride request conversations

-- Step 1: Add conversation_type column to distinguish between RIDE_REQUEST and REPORT conversations
ALTER TABLE messages
    ADD COLUMN IF NOT EXISTS conversation_type VARCHAR(20) DEFAULT 'RIDE_REQUEST';

-- Set default value for existing rows
UPDATE messages
SET conversation_type = 'RIDE_REQUEST'
WHERE conversation_type IS NULL OR conversation_type = '';

-- Step 2: Add report_id column (nullable, for report-based conversations)
ALTER TABLE messages
    ADD COLUMN IF NOT EXISTS report_id INTEGER;

-- Step 3: Add foreign key constraint for report_id (with conditional check)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_messages_report'
        AND conrelid = 'messages'::regclass
    ) THEN
        ALTER TABLE messages
            ADD CONSTRAINT fk_messages_report
                FOREIGN KEY (report_id) REFERENCES user_reports (report_id)
                    ON DELETE CASCADE;
    END IF;
END $$;

-- Step 4: Make shared_ride_request_id nullable (to allow report-only conversations)
ALTER TABLE messages
    ALTER COLUMN shared_ride_request_id DROP NOT NULL;

-- Step 5: Add check constraint for conversation_type
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_conversation_type'
        AND conrelid = 'messages'::regclass
    ) THEN
        ALTER TABLE messages
            ADD CONSTRAINT chk_conversation_type
                CHECK (conversation_type IN ('RIDE_REQUEST', 'REPORT'));
    END IF;
END $$;

-- Step 6: Add check constraint for data integrity
-- RIDE_REQUEST must have shared_ride_request_id and no report_id
-- REPORT must have report_id
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_conversation_data_integrity'
        AND conrelid = 'messages'::regclass
    ) THEN
        ALTER TABLE messages
            ADD CONSTRAINT chk_conversation_data_integrity
                CHECK (
                    (conversation_type = 'RIDE_REQUEST' AND shared_ride_request_id IS NOT NULL AND report_id IS NULL)
                    OR
                    (conversation_type = 'REPORT' AND report_id IS NOT NULL)
                );
    END IF;
END $$;

-- Step 7: Add indexes for performance
CREATE INDEX IF NOT EXISTS idx_messages_report ON messages(report_id);
CREATE INDEX IF NOT EXISTS idx_messages_conversation_type ON messages(conversation_type);
CREATE INDEX IF NOT EXISTS idx_messages_report_conversation ON messages(report_id, conversation_type);

