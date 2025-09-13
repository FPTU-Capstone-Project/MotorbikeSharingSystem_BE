-- V4__create_verifications_table.sql
-- Create verifications table for document verification tracking

CREATE TABLE IF NOT EXISTS verifications (
    verification_id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL,
    status VARCHAR(20) DEFAULT 'pending',
    document_url VARCHAR(500),
    document_type VARCHAR(20),
    rejection_reason TEXT,
    verified_by INTEGER REFERENCES admin_profiles(admin_id),
    verified_at TIMESTAMP,
    expires_at TIMESTAMP,
    metadata TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Add indexes
CREATE INDEX idx_verification_user_type_status ON verifications(user_id, type, status);
CREATE INDEX idx_verification_status_created ON verifications(status, created_at);
CREATE INDEX idx_verification_user ON verifications(user_id);
CREATE INDEX idx_verification_type ON verifications(type);
CREATE INDEX idx_verification_status ON verifications(status);

-- Add check constraints
ALTER TABLE verifications ADD CONSTRAINT chk_verification_type 
    CHECK (type IN ('student_id', 'driver_license', 'background_check', 'vehicle_registration'));

ALTER TABLE verifications ADD CONSTRAINT chk_verification_status 
    CHECK (status IN ('pending', 'approved', 'rejected', 'expired'));

ALTER TABLE verifications ADD CONSTRAINT chk_document_type 
    CHECK (document_type IN ('image', 'pdf') OR document_type IS NULL);
