BEGIN;

ALTER TABLE shared_ride_requests
    ADD COLUMN request_kind VARCHAR(20);

COMMENT ON COLUMN shared_ride_requests.request_kind IS
    'Type of ride request: BOOKING (system matches driver) or JOIN_RIDE (rider chooses specific ride)';

ALTER TABLE shared_ride_requests
    ALTER COLUMN shared_ride_id DROP NOT NULL;

COMMENT ON COLUMN shared_ride_requests.shared_ride_id IS
    'Reference to shared ride. NULL for BOOKING in PENDING state, set when driver accepts. Always set for JOIN_RIDE.';

UPDATE shared_ride_requests
SET request_kind = 'JOIN_RIDE'
WHERE request_kind IS NULL;

ALTER TABLE shared_ride_requests
    ALTER COLUMN request_kind SET NOT NULL;

ALTER TABLE shared_ride_requests
    ALTER COLUMN request_kind SET DEFAULT 'JOIN_RIDE';

ALTER TABLE shared_ride_requests
    ADD CONSTRAINT chk_request_kind
        CHECK (request_kind IN ('BOOKING', 'JOIN_RIDE'));

ALTER TABLE shared_ride_requests
    DROP CONSTRAINT IF EXISTS chk_shared_ride_request_status;

ALTER TABLE shared_ride_requests
    ADD CONSTRAINT chk_shared_ride_request_status
        CHECK (
            status IN (
                       'PENDING',
                       'BROADCASTING',
                       'CONFIRMED',
                       'ONGOING',
                       'COMPLETED',
                       'CANCELLED',
                       'EXPIRED'
                )
            );

COMMENT ON CONSTRAINT chk_shared_ride_request_status ON shared_ride_requests IS
    'Valid request statuses. BROADCASTING covers the fallback phase before confirmation; EXPIRED represents timeout outcomes.';

ALTER TABLE shared_ride_requests
    DROP CONSTRAINT IF EXISTS chk_request_kind_ride_id_relationship;

ALTER TABLE shared_ride_requests
    ADD CONSTRAINT chk_request_kind_ride_id_relationship
        CHECK (
            -- BOOKING requests start without a ride while matching or broadcasting
            (request_kind = 'BOOKING'
                AND status IN ('PENDING', 'BROADCASTING')
                AND shared_ride_id IS NULL)
                OR
                -- Any request that is confirmed / in-progress / completed must have a ride
            (status IN ('CONFIRMED', 'ONGOING', 'COMPLETED')
                AND shared_ride_id IS NOT NULL)
                OR
                -- JOIN_RIDE requests always tie to a ride unless they were cancelled / expired before acceptance
            (request_kind = 'JOIN_RIDE'
                AND status NOT IN ('CANCELLED', 'EXPIRED')
                AND shared_ride_id IS NOT NULL)
                OR
                -- Terminal states (cancelled/expired) may or may not have a ride reference
            (status IN ('CANCELLED', 'EXPIRED'))
            );

COMMENT ON CONSTRAINT chk_request_kind_ride_id_relationship ON shared_ride_requests IS
    'Ensures integrity: BOOKING stays ride-less through matching/broadcasting, gains one once accepted; JOIN_RIDE is always bound to a ride unless cancelled/expired beforehand.';

DROP INDEX IF EXISTS idx_ride_requests_matching;

CREATE INDEX idx_ride_requests_matching
    ON shared_ride_requests (request_kind, status, pickup_time)
    WHERE status IN ('PENDING', 'BROADCASTING');

COMMENT ON INDEX idx_ride_requests_matching IS
    'Optimizes matching queries for open BOOKING requests (initial matching and broadcast window).';

ALTER TABLE shared_rides
    DROP CONSTRAINT IF EXISTS chk_shared_rides_status;

ALTER TABLE shared_rides
    ADD CONSTRAINT chk_shared_rides_status
        CHECK (status IN ('SCHEDULED', 'ONGOING', 'COMPLETED', 'CANCELLED'));

COMMENT ON CONSTRAINT chk_shared_rides_status ON shared_rides IS
    'Valid ride statuses. SCHEDULED replaces PENDING/ACTIVE for consistency. ONGOING for in-progress, terminal states COMPLETED/CANCELLED.';

UPDATE shared_rides
SET status = 'SCHEDULED'
WHERE status IN ('PENDING', 'ACTIVE');

COMMIT;

