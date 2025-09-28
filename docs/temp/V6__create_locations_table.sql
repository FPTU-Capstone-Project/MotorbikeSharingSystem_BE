-- V6__create_locations_table.sql
-- Create locations table for pickup/dropoff locations

CREATE TABLE IF NOT EXISTS locations (
    location_id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    lat DOUBLE PRECISION NOT NULL,
    lng DOUBLE PRECISION NOT NULL,
    address TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Add indexes for geolocation queries
CREATE INDEX idx_location_lat_lng ON locations(lat, lng);
CREATE INDEX idx_location_name ON locations(name);

-- Add constraints
ALTER TABLE locations ADD CONSTRAINT chk_lat_range
    CHECK (lat >= -90 AND lat <= 90);

ALTER TABLE locations ADD CONSTRAINT chk_lng_range
    CHECK (lng >= -180 AND lng <= 180);