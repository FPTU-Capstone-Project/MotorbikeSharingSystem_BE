-- V5__create_vehicles_table.sql
-- Create vehicles table for driver vehicles

CREATE TABLE IF NOT EXISTS vehicles (
    vehicle_id SERIAL PRIMARY KEY,
    driver_id INTEGER NOT NULL REFERENCES driver_profiles(driver_id) ON DELETE CASCADE,
    plate_number VARCHAR(20) NOT NULL,
    model VARCHAR(100) NOT NULL,
    color VARCHAR(50),
    year INTEGER,
    capacity INTEGER DEFAULT 1,
    helmet_count INTEGER DEFAULT 2,
    insurance_expiry TIMESTAMP NOT NULL,
    last_maintenance TIMESTAMP,
    fuel_type VARCHAR(20) DEFAULT 'gasoline',
    status VARCHAR(20) DEFAULT 'pending',
    verified_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Add unique constraint for plate number
ALTER TABLE vehicles ADD CONSTRAINT uk_vehicle_plate UNIQUE (plate_number);

-- Add indexes
CREATE INDEX idx_vehicle_driver ON vehicles(driver_id);
CREATE INDEX idx_vehicle_status ON vehicles(status);

-- Add check constraints
ALTER TABLE vehicles ADD CONSTRAINT chk_vehicle_status 
    CHECK (status IN ('pending', 'active', 'maintenance', 'inactive'));

ALTER TABLE vehicles ADD CONSTRAINT chk_fuel_type 
    CHECK (fuel_type IN ('gasoline', 'electric'));

ALTER TABLE vehicles ADD CONSTRAINT chk_vehicle_year 
    CHECK (year >= 2000 AND year <= EXTRACT(YEAR FROM CURRENT_DATE) + 1);

ALTER TABLE vehicles ADD CONSTRAINT chk_vehicle_capacity 
    CHECK (capacity >= 1 AND capacity <= 2);
