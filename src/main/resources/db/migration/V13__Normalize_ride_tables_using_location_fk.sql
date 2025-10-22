ALTER TABLE shared_rides
    DROP COLUMN IF EXISTS start_lat;
ALTER TABLE shared_rides
    DROP COLUMN IF EXISTS start_lng;
ALTER TABLE shared_rides
    DROP COLUMN IF EXISTS end_lat;
ALTER TABLE shared_rides
    DROP COLUMN IF EXISTS end_lng;

ALTER TABLE shared_ride_requests
    DROP COLUMN IF EXISTS pickup_lat;
ALTER TABLE shared_ride_requests
    DROP COLUMN IF EXISTS pickup_lng;
ALTER TABLE shared_ride_requests
    DROP COLUMN IF EXISTS dropoff_lat;
ALTER TABLE shared_ride_requests
    DROP COLUMN IF EXISTS dropoff_lng;

AlTER TABLE locations
    ALTER COLUMN name DROP NOT NULL;
AlTER TABLE locations
    ADD COLUMN IF NOT EXISTS is_poi BOOLEAN NOT NULL DEFAULT TRUE;
UPDATE locations
SET address = COALESCE(
        CASE
            WHEN name IS NOT NULL THEN name
            ELSE CONCAT('Location at ', lat, ', ', lng)
            END,
        'Unknown Address'
              )
WHERE address IS NULL;

ALTER TABLE locations
    ALTER COLUMN address SET NOT NULL;