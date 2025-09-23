-- V13__create_file_uploads_table.sql
-- Create file_uploads table for user document uploads

CREATE TABLE IF NOT EXISTS file_uploads (
    file_id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    file_type VARCHAR(50) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    stored_filename VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_size INTEGER,
    mime_type VARCHAR(100),
    upload_status VARCHAR(30) DEFAULT 'uploaded',
    verification_status VARCHAR(30) DEFAULT 'pending',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Add indexes
CREATE INDEX idx_file_uploads_user ON file_uploads(user_id);
CREATE INDEX idx_file_uploads_type ON file_uploads(file_type);
CREATE INDEX idx_file_uploads_upload_status ON file_uploads(upload_status);
CREATE INDEX idx_file_uploads_verification_status ON file_uploads(verification_status);
CREATE INDEX idx_file_uploads_created_at ON file_uploads(created_at);

-- Add constraints
ALTER TABLE file_uploads ADD CONSTRAINT chk_file_type
    CHECK (file_type IN ('license', 'identity_card', 'passport', 'vehicle_registration', 'insurance', 'profile_photo'));

ALTER TABLE file_uploads ADD CONSTRAINT chk_upload_status
    CHECK (upload_status IN ('uploading', 'uploaded', 'failed', 'deleted'));

ALTER TABLE file_uploads ADD CONSTRAINT chk_verification_status
    CHECK (verification_status IN ('pending', 'verified', 'rejected', 'expired'));

ALTER TABLE file_uploads ADD CONSTRAINT chk_file_size
    CHECK (file_size IS NULL OR file_size > 0);