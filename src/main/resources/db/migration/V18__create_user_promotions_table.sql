-- V18__create_user_promotions_table.sql
-- Create user_promotions table for tracking promotion usage

CREATE TABLE IF NOT EXISTS user_promotions (
    user_promotion_id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    promotion_id INTEGER NOT NULL REFERENCES promotions(promotion_id) ON DELETE CASCADE,
    shared_ride_request_id INTEGER NOT NULL REFERENCES shared_ride_requests(shared_ride_request_id) ON DELETE CASCADE,
    used_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    discount_applied DECIMAL(10,2)
);

-- Add indexes
CREATE INDEX idx_user_promotions_user ON user_promotions(user_id);
CREATE INDEX idx_user_promotions_promotion ON user_promotions(promotion_id);
CREATE INDEX idx_user_promotions_shared_ride_request ON user_promotions(shared_ride_request_id);
CREATE INDEX idx_user_promotions_used_at ON user_promotions(used_at);

-- Add constraints
ALTER TABLE user_promotions ADD CONSTRAINT chk_discount_applied
    CHECK (discount_applied IS NULL OR discount_applied >= 0);

-- Ensure one promotion usage per ride request
CREATE UNIQUE INDEX idx_user_promotions_unique_usage
    ON user_promotions(shared_ride_request_id, promotion_id);