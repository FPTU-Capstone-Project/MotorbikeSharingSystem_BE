ALTER TABLE transactions
    DROP CONSTRAINT IF EXISTS txn_capture_role_alignment;
ALTER TABLE transactions
    DROP CONSTRAINT IF EXISTS fk_txn_rider_user;
ALTER TABLE transactions
    DROP CONSTRAINT IF EXISTS fk_txn_driver_user;
ALTER TABLE transactions
    DROP CONSTRAINT IF EXISTS txn_booking_required_for_ride;

ALTER TABLE transactions
    DROP COLUMN IF EXISTS rider_user_id;
ALTER TABLE transactions
    DROP COLUMN IF EXISTS driver_user_id;
ALTER TABLE transactions
    DROP COLUMN IF EXISTS booking_id;

ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS shared_ride_id INTEGER REFERENCES shared_rides (shared_ride_id);
ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS shared_ride_request_id INTEGER REFERENCES shared_ride_requests (shared_ride_request_id);

ALTER TABLE transactions
    ADD CONSTRAINT txn_booking_required_for_ride CHECK (
        (type IN ('HOLD_CREATE', 'HOLD_RELEASE') AND shared_ride_request_id IS NOT NULL)
            OR
        (type NOT IN ('HOLD_CREATE', 'HOLD_RELEASE', 'CAPTURE_FARE'))
            OR
        (type = 'CAPTURE_FARE' AND shared_ride_id IS NOT NULL)
        );

ALTER TABLE shared_rides
    DROP CONSTRAINT IF EXISTS chk_max_passengers;
ALTER TABLE shared_rides
    DROP CONSTRAINT IF EXISTS chk_current_passengers;

ALTER TABLE shared_rides
    DROP COLUMN IF EXISTS max_passengers;
ALTER TABLE shared_rides
    DROP COLUMN IF EXISTS current_passengers;