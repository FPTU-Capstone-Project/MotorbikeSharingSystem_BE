-- V2__create_profiles_tables.sql
-- Create profile tables for riders, drivers, and admins

-- Admin profiles table
CREATE TABLE IF NOT EXISTS admin_profiles (
    admin_id INTEGER PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
    department VARCHAR(100),
    permissions TEXT,
    last_login TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Rider profiles table
CREATE TABLE IF NOT EXISTS rider_profiles (
    rider_id INTEGER PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
    emergency_contact VARCHAR(20),
    rating_avg FLOAT DEFAULT 5.0,
    total_rides INTEGER DEFAULT 0,
    total_spent DECIMAL(10, 2) DEFAULT 0,
    status VARCHAR(10) DEFAULT 'ACTIVE',
    preferred_payment_method VARCHAR(20) DEFAULT 'WALLET',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Driver profiles table
CREATE TABLE IF NOT EXISTS driver_profiles (
    driver_id INTEGER PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
    license_number VARCHAR(50) NOT NULL,
    license_verified_at TIMESTAMP,
    status VARCHAR(20) DEFAULT 'PENDING',
    rating_avg FLOAT DEFAULT 5.0,
    total_shared_rides INTEGER DEFAULT 0,
    total_earned DECIMAL(10, 2) DEFAULT 0,
    commission_rate DECIMAL(3, 2) DEFAULT 0.15,
    is_available BOOLEAN DEFAULT false,
    max_passengers INTEGER DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Add unique constraint for license number
ALTER TABLE driver_profiles ADD CONSTRAINT uk_driver_license UNIQUE (license_number);

-- Add indexes
CREATE INDEX idx_driver_status ON driver_profiles(status);
CREATE INDEX idx_driver_available ON driver_profiles(is_available);
CREATE INDEX idx_rider_payment_method ON rider_profiles(preferred_payment_method);

-- Add check constraints
ALTER TABLE rider_profiles ADD CONSTRAINT chk_rider_status
    CHECK (status IN ('ACTIVE', 'SUSPENDED'));
ALTER TABLE driver_profiles ADD CONSTRAINT chk_driver_status 
    CHECK (status IN ('PENDING', 'ACTIVE', 'INACTIVE', 'SUSPENDED'));

ALTER TABLE driver_profiles ADD CONSTRAINT chk_commission_rate 
    CHECK (commission_rate >= 0 AND commission_rate <= 1);

ALTER TABLE driver_profiles ADD CONSTRAINT chk_rating_avg_driver
    CHECK (rating_avg >= 0 AND rating_avg <= 5);

ALTER TABLE rider_profiles ADD CONSTRAINT chk_rating_avg_rider
    CHECK (rating_avg >= 0 AND rating_avg <= 5);

ALTER TABLE rider_profiles ADD CONSTRAINT chk_payment_method
    CHECK (preferred_payment_method IN ('WALLET', 'CREDIT_CARD'));
