-- V12__create_emergency_contacts_table.sql
-- Create emergency_contacts table for user emergency contacts

CREATE TABLE IF NOT EXISTS emergency_contacts (
    contact_id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    relationship VARCHAR(50),
    is_primary BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Add indexes
CREATE INDEX idx_emergency_contacts_user ON emergency_contacts(user_id);
CREATE INDEX idx_emergency_contacts_primary ON emergency_contacts(is_primary);

-- Add constraints
ALTER TABLE emergency_contacts ADD CONSTRAINT chk_phone_format
    CHECK (phone ~ '^[\+]?[0-9\-\(\)\s]+$');

-- Ensure only one primary contact per user
CREATE UNIQUE INDEX idx_emergency_contacts_user_primary
    ON emergency_contacts(user_id)
    WHERE is_primary = true;