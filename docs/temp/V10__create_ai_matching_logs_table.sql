-- V10__create_ai_matching_logs_table.sql
-- Create ai_matching_logs table for AI matching algorithm logs

CREATE TABLE IF NOT EXISTS ai_matching_logs (
    log_id SERIAL PRIMARY KEY,
    shared_ride_request_id INTEGER NOT NULL REFERENCES shared_ride_requests(shared_ride_request_id) ON DELETE CASCADE,
    algorithm_version VARCHAR(50),
    request_location TEXT,
    search_radius_km REAL,
    available_drivers_count INTEGER,
    matching_factors TEXT,
    potential_matches TEXT,
    selected_driver_id INTEGER NOT NULL REFERENCES driver_profiles(driver_id),
    matching_score REAL,
    processing_time_ms INTEGER,
    success BOOLEAN,
    failure_reason VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Add indexes
CREATE INDEX idx_ai_matching_logs_request ON ai_matching_logs(shared_ride_request_id);
CREATE INDEX idx_ai_matching_logs_driver ON ai_matching_logs(selected_driver_id);
CREATE INDEX idx_ai_matching_logs_success ON ai_matching_logs(success);
CREATE INDEX idx_ai_matching_logs_created_at ON ai_matching_logs(created_at);

-- Add constraints
ALTER TABLE ai_matching_logs ADD CONSTRAINT chk_search_radius
    CHECK (search_radius_km IS NULL OR search_radius_km > 0);

ALTER TABLE ai_matching_logs ADD CONSTRAINT chk_available_drivers_count
    CHECK (available_drivers_count IS NULL OR available_drivers_count >= 0);

ALTER TABLE ai_matching_logs ADD CONSTRAINT chk_matching_score
    CHECK (matching_score IS NULL OR (matching_score >= 0 AND matching_score <= 1));

ALTER TABLE ai_matching_logs ADD CONSTRAINT chk_processing_time
    CHECK (processing_time_ms IS NULL OR processing_time_ms >= 0);