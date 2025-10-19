ALTER TABLE shared_ride_requests
    ADD COLUMN IF NOT EXISTS distance_meters INTEGER NOT NULL DEFAULT 0;