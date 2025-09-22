-- V1__create_users_table.sql
-- Create users table with all necessary columns and constraints

-- Drop table if exists to ensure clean state
DROP TABLE IF EXISTS users CASCADE;

-- Create users table
CREATE TABLE users (
                       user_id SERIAL PRIMARY KEY,
                       email VARCHAR(255) NOT NULL,
                       phone VARCHAR(20) NOT NULL,
                       password_hash VARCHAR(255),
                       full_name VARCHAR(255) NOT NULL,
                       student_id VARCHAR(50),
                       user_type VARCHAR(20) NOT NULL DEFAULT 'student',
                       profile_photo_url VARCHAR(500),
                       is_active BOOLEAN NOT NULL DEFAULT true,
                       email_verified BOOLEAN NOT NULL DEFAULT false,
                       phone_verified BOOLEAN NOT NULL DEFAULT false,
                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       version BIGINT NOT NULL DEFAULT 0
);

-- Add unique constraints
ALTER TABLE users ADD CONSTRAINT uk_users_email UNIQUE (email);
ALTER TABLE users ADD CONSTRAINT uk_users_phone UNIQUE (phone);

-- Add partial unique index for student_id (allows multiple NULLs but unique non-NULLs)
CREATE UNIQUE INDEX uk_users_student_id ON users(student_id) WHERE student_id IS NOT NULL;

-- Add indexes for performance
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_phone ON users(phone);
CREATE INDEX idx_users_user_type ON users(user_type);
CREATE INDEX idx_users_is_active ON users(is_active);

-- Add check constraints
ALTER TABLE users ADD CONSTRAINT chk_user_type
    CHECK (user_type IN ('student', 'admin', 'rider', 'driver'));

-- Create update trigger function for updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
    RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE 'plpgsql';

-- Create trigger for users table
CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();