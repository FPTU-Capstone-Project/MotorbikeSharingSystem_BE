-- V9__create_shared_ride_requests_table.sql
-- Create shared_ride_requests table for ride requests

CREATE TABLE IF NOT EXISTS shared_ride_requests (
    shared_ride_request_id SERIAL PRIMARY KEY,
    share_ride_id INTEGER NOT NULL REFERENCES shared_rides(shared_ride_id) ON DELETE CASCADE,
    rider_id INTEGER NOT NULL REFERENCES rider_profiles(rider_id) ON DELETE CASCADE,
    pickup_location_id INTEGER REFERENCES locations(location_id),
    dropoff_location_id INTEGER REFERENCES locations(location_id),
    status VARCHAR(50) DEFAULT 'pending',
    fare_amount DECIMAL(19,2) NOT NULL,
    original_fare DECIMAL(19,2),
    discount_amount DECIMAL(19,2),
    pickup_time TIMESTAMP NOT NULL,
    max_wait_time INTEGER,
    special_requests TEXT,
    estimated_pickup_time TIMESTAMP,
    actual_pickup_time TIMESTAMP,
    estimated_dropoff_time TIMESTAMP,
    actual_dropoff_time TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Add indexes
CREATE INDEX idx_shared_ride_requests_share_ride ON shared_ride_requests(share_ride_id);
CREATE INDEX idx_shared_ride_requests_rider ON shared_ride_requests(rider_id);
CREATE INDEX idx_shared_ride_requests_status ON shared_ride_requests(status);
CREATE INDEX idx_shared_ride_requests_pickup_time ON shared_ride_requests(pickup_time);
CREATE INDEX idx_shared_ride_requests_pickup_location ON shared_ride_requests(pickup_location_id);
CREATE INDEX idx_shared_ride_requests_dropoff_location ON shared_ride_requests(dropoff_location_id);

-- Add constraints
ALTER TABLE shared_ride_requests ADD CONSTRAINT chk_shared_ride_request_status
    CHECK (status IN ('pending', 'confirmed', 'in_progress', 'completed', 'cancelled'));

ALTER TABLE shared_ride_requests ADD CONSTRAINT chk_fare_amount
    CHECK (fare_amount >= 0);

ALTER TABLE shared_ride_requests ADD CONSTRAINT chk_original_fare
    CHECK (original_fare IS NULL OR original_fare >= 0);

ALTER TABLE shared_ride_requests ADD CONSTRAINT chk_discount_amount
    CHECK (discount_amount IS NULL OR discount_amount >= 0);

ALTER TABLE shared_ride_requests ADD CONSTRAINT chk_max_wait_time
    CHECK (max_wait_time IS NULL OR max_wait_time > 0);