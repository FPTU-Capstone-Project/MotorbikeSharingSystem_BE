BEGIN;

CREATE INDEX idx_shared_rides_available_browse
ON shared_rides(status, scheduled_time, current_passengers, max_passengers)
WHERE status = 'SCHEDULED' AND current_passengers < max_passengers;

COMMENT ON INDEX idx_shared_rides_available_browse IS 
'Optimizes browse available rides query. Partial index on SCHEDULED rides with seats. Supports time range and seat availability filters.';

CREATE INDEX idx_shared_rides_driver_status_time
ON shared_rides(driver_id, status, scheduled_time DESC);

COMMENT ON INDEX idx_shared_rides_driver_status_time IS 
'Optimizes driver ride listing with status filter and time sort. Supports pagination and historical queries.';

CREATE INDEX idx_ride_requests_rider_status_time
ON shared_ride_requests(rider_id, status, created_at DESC);

COMMENT ON INDEX idx_ride_requests_rider_status_time IS 
'Optimizes rider request history with status filter and time sort. Supports pagination and request tracking.';

CREATE INDEX idx_ride_requests_ride_pending
ON shared_ride_requests(shared_ride_id, status)
WHERE status = 'PENDING';

COMMENT ON INDEX idx_ride_requests_ride_pending IS 
'Optimizes driver pending request queries for notifications. Partial index on PENDING only reduces size and improves performance.';

CREATE INDEX idx_shared_rides_id_passengers
ON shared_rides(shared_ride_id, current_passengers, max_passengers);

COMMENT ON INDEX idx_shared_rides_id_passengers IS 
'Optimizes seat availability checks during accept operations. Supports fast SELECT FOR UPDATE with seat count validation.';

CREATE INDEX idx_ride_requests_expiry_check
ON shared_ride_requests(status, created_at)
WHERE status = 'PENDING';

COMMENT ON INDEX idx_ride_requests_expiry_check IS 
'Optimizes request expiry scheduled job. Partial index on PENDING status for efficient range scan by created_at.';

CREATE INDEX idx_ride_requests_completion
ON shared_ride_requests(shared_ride_id, status)
WHERE status IN ('CONFIRMED', 'ONGOING');

COMMENT ON INDEX idx_ride_requests_completion IS 
'Optimizes ride completion transaction. Finds all active requests for wallet capture. Partial index on active statuses only.';

COMMIT;


