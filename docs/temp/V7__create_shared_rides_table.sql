-- V7__create_shared_rides_table.sql
-- Create shared_rides table for shared ride offerings

CREATE TABLE IF NOT EXISTS shared_rides (
    shared_ride_id SERIAL PRIMARY KEY,
    driver_id INTEGER NOT NULL REFERENCES driver_profiles(driver_id) ON DELETE CASCADE,
    vehicle_id INTEGER NOT NULL REFERENCES vehicles(vehicle_id) ON DELETE CASCADE,
    start_location_id INTEGER NOT NULL REFERENCES locations(location_id),
    end_location_id INTEGER NOT NULL REFERENCES locations(location_id),
    status VARCHAR(50) DEFAULT 'PENDING',
    max_passengers INTEGER DEFAULT 1,
    current_passengers INTEGER DEFAULT 0,
    base_fare DECIMAL(10,2),
    per_km_rate DECIMAL(10,2),
    estimated_duration INTEGER,
    estimated_distance REAL,
    actual_duration INTEGER,
    actual_distance REAL,
    scheduled_time TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Add indexes
CREATE INDEX idx_shared_rides_driver ON shared_rides(driver_id);
CREATE INDEX idx_shared_rides_vehicle ON shared_rides(vehicle_id);
CREATE INDEX idx_shared_rides_status ON shared_rides(status);
CREATE INDEX idx_shared_rides_scheduled_time ON shared_rides(scheduled_time);
CREATE INDEX idx_shared_rides_start_location ON shared_rides(start_location_id);
CREATE INDEX idx_shared_rides_end_location ON shared_rides(end_location_id);

-- Add constraints
ALTER TABLE shared_rides ADD CONSTRAINT chk_shared_rides_status
    CHECK (status IN ('SCHEDULED', 'ACTIVE', 'COMPLETED', 'CANCELLED', 'PENDING'));

ALTER TABLE shared_rides ADD CONSTRAINT chk_max_passengers
    CHECK (max_passengers >= 1 AND max_passengers <= 3);

ALTER TABLE shared_rides ADD CONSTRAINT chk_current_passengers
    CHECK (current_passengers >= 0 AND current_passengers <= max_passengers);

ALTER TABLE shared_rides ADD CONSTRAINT chk_base_fare
    CHECK (base_fare >= 0);

ALTER TABLE shared_rides ADD CONSTRAINT chk_per_km_rate
    CHECK (per_km_rate >= 0);