ALTER TABLE shared_rides
    ADD COLUMN driver_approach_polyline TEXT,
    ADD COLUMN driver_approach_distance_meters INTEGER,
    ADD COLUMN driver_approach_duration_seconds INTEGER,
    ADD COLUMN driver_approach_eta TIMESTAMP;

