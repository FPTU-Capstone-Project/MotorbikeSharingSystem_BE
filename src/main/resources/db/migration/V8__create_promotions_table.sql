-- V8__create_promotions_table.sql
-- Create promotions table for discount codes and promotions

CREATE TABLE IF NOT EXISTS promotions (
    promotion_id SERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    discount_type VARCHAR(20) NOT NULL,
    discount_value DECIMAL(19,2) NOT NULL,
    target_user_type VARCHAR(20),
    min_shared_ride_amount DECIMAL(19,2),
    max_discount DECIMAL(19,2),
    usage_limit INTEGER,
    usage_limit_per_user INTEGER,
    used_count INTEGER DEFAULT 0,
    valid_from TIMESTAMP NOT NULL,
    valid_until TIMESTAMP NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_by INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Add indexes
CREATE INDEX idx_promotions_code ON promotions(code);
CREATE INDEX idx_promotions_active ON promotions(is_active);
CREATE INDEX idx_promotions_valid_dates ON promotions(valid_from, valid_until);
CREATE INDEX idx_promotions_target_user_type ON promotions(target_user_type);

-- Add constraints
ALTER TABLE promotions ADD CONSTRAINT chk_discount_type
    CHECK (discount_type IN ('percentage', 'fixed_amount'));

ALTER TABLE promotions ADD CONSTRAINT chk_discount_value
    CHECK (discount_value > 0);

ALTER TABLE promotions ADD CONSTRAINT chk_target_user_type
    CHECK (target_user_type IN ('rider', 'driver', 'all') OR target_user_type IS NULL);

ALTER TABLE promotions ADD CONSTRAINT chk_valid_dates
    CHECK (valid_until > valid_from);

ALTER TABLE promotions ADD CONSTRAINT chk_usage_limits
    CHECK (usage_limit IS NULL OR usage_limit > 0);

ALTER TABLE promotions ADD CONSTRAINT chk_usage_limit_per_user
    CHECK (usage_limit_per_user IS NULL OR usage_limit_per_user > 0);