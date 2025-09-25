-- V16__create_ratings_table.sql
-- Create ratings table for ride ratings and reviews

CREATE TABLE IF NOT EXISTS ratings (
    rating_id SERIAL PRIMARY KEY,
    shared_ride_request_id INTEGER NOT NULL REFERENCES shared_ride_requests(shared_ride_request_id) ON DELETE CASCADE,
    rater_id INTEGER NOT NULL REFERENCES rider_profiles(rider_id) ON DELETE CASCADE,
    target_id INTEGER NOT NULL REFERENCES driver_profiles(driver_id) ON DELETE CASCADE,
    rating_type VARCHAR(20) DEFAULT 'general',
    score INTEGER NOT NULL,
    comment TEXT,
    safety_score INTEGER,
    punctuality_score INTEGER,
    communication_score INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Add indexes
CREATE INDEX idx_ratings_shared_ride_request ON ratings(shared_ride_request_id);
CREATE INDEX idx_ratings_rater ON ratings(rater_id);
CREATE INDEX idx_ratings_target ON ratings(target_id);
CREATE INDEX idx_ratings_score ON ratings(score);
CREATE INDEX idx_ratings_created_at ON ratings(created_at);

-- Add constraints
ALTER TABLE ratings ADD CONSTRAINT chk_rating_type
    CHECK (rating_type IN ('general', 'rider_to_driver', 'driver_to_rider'));

ALTER TABLE ratings ADD CONSTRAINT chk_score_range
    CHECK (score >= 1 AND score <= 5);

ALTER TABLE ratings ADD CONSTRAINT chk_safety_score_range
    CHECK (safety_score IS NULL OR (safety_score >= 1 AND safety_score <= 5));

ALTER TABLE ratings ADD CONSTRAINT chk_punctuality_score_range
    CHECK (punctuality_score IS NULL OR (punctuality_score >= 1 AND punctuality_score <= 5));

ALTER TABLE ratings ADD CONSTRAINT chk_communication_score_range
    CHECK (communication_score IS NULL OR (communication_score >= 1 AND communication_score <= 5));

-- Ensure one rating per ride request per rater-target pair
CREATE UNIQUE INDEX idx_ratings_unique_rating
    ON ratings(shared_ride_request_id, rater_id, target_id);