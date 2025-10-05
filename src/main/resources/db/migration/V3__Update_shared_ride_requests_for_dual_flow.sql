BEGIN;

ALTER TABLE shared_ride_requests
ADD COLUMN request_kind VARCHAR(20);

COMMENT ON COLUMN shared_ride_requests.request_kind IS 
'Type of ride request: AI_BOOKING (system matches driver) or JOIN_RIDE (rider chooses specific ride)';

ALTER TABLE shared_ride_requests
ALTER COLUMN shared_ride_id DROP NOT NULL;

COMMENT ON COLUMN shared_ride_requests.shared_ride_id IS 
'Reference to shared ride. NULL for AI_BOOKING in PENDING state, set when driver accepts. Always set for JOIN_RIDE.';

UPDATE shared_ride_requests
SET request_kind = 'JOIN_RIDE'
WHERE request_kind IS NULL;

ALTER TABLE shared_ride_requests
ALTER COLUMN request_kind SET NOT NULL;

ALTER TABLE shared_ride_requests
ALTER COLUMN request_kind SET DEFAULT 'JOIN_RIDE';

ALTER TABLE shared_ride_requests
ADD CONSTRAINT chk_request_kind
CHECK (request_kind IN ('AI_BOOKING', 'JOIN_RIDE'));

ALTER TABLE shared_ride_requests
DROP CONSTRAINT IF EXISTS chk_shared_ride_request_status;

ALTER TABLE shared_ride_requests
ADD CONSTRAINT chk_shared_ride_request_status
CHECK (status IN ('PENDING', 'CONFIRMED', 'ONGOING', 'COMPLETED', 'CANCELLED', 'EXPIRED'));

COMMENT ON CONSTRAINT chk_shared_ride_request_status ON shared_ride_requests IS 
'Valid request statuses. EXPIRED added for timeout scenarios to distinguish from explicit CANCELLED.';

ALTER TABLE shared_ride_requests
DROP CONSTRAINT IF EXISTS chk_ai_booking_no_initial_ride;

ALTER TABLE shared_ride_requests
ADD CONSTRAINT chk_request_kind_ride_id_relationship
CHECK (
    -- AI_BOOKING: must start with null shared_ride_id when PENDING
    (request_kind = 'AI_BOOKING' AND status = 'PENDING' AND shared_ride_id IS NULL)
    OR
    -- Any confirmed/ongoing/completed request must have a shared_ride_id
    (status IN ('CONFIRMED', 'ONGOING', 'COMPLETED') AND shared_ride_id IS NOT NULL)
    OR
    -- JOIN_RIDE: must always have shared_ride_id (except terminal states)
    (request_kind = 'JOIN_RIDE' AND status NOT IN ('CANCELLED', 'EXPIRED') AND shared_ride_id IS NOT NULL)
    OR
    -- Terminal states can be null or not null (cancelled before assignment)
    (status IN ('CANCELLED', 'EXPIRED'))
);

COMMENT ON CONSTRAINT chk_request_kind_ride_id_relationship ON shared_ride_requests IS 
'Ensures data integrity: AI_BOOKING starts without ride, gets assigned on accept. JOIN_RIDE always has ride. Terminal states flexible.';

CREATE INDEX idx_ride_requests_matching
ON shared_ride_requests(request_kind, status, pickup_time)
WHERE status = 'PENDING';

COMMENT ON INDEX idx_ride_requests_matching IS 
'Optimizes matching algorithm queries for pending AI_BOOKING requests by pickup time.';

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

