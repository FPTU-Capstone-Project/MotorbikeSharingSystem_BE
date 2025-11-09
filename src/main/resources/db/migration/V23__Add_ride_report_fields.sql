-- V23__Add_ride_report_fields.sql
-- Extend user_reports table to support ride-specific reports

-- Add ride_id and driver_id columns for ride-specific reports
ALTER TABLE user_reports
    ADD COLUMN IF NOT EXISTS shared_ride_id INTEGER,
    ADD COLUMN IF NOT EXISTS driver_id INTEGER,
    ADD COLUMN IF NOT EXISTS admin_notes TEXT;

-- Add foreign key constraints
ALTER TABLE user_reports
    ADD CONSTRAINT fk_user_reports_shared_ride
        FOREIGN KEY (shared_ride_id) REFERENCES shared_rides (shared_ride_id)
            ON DELETE CASCADE;

ALTER TABLE user_reports
    ADD CONSTRAINT fk_user_reports_driver
        FOREIGN KEY (driver_id) REFERENCES driver_profiles (driver_id)
            ON DELETE SET NULL;

-- Add unique constraint to prevent duplicate reports for the same ride by the same user
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_reports_ride_reporter
    ON user_reports (shared_ride_id, reporter_id)
    WHERE shared_ride_id IS NOT NULL;

-- Add indexes for performance
CREATE INDEX IF NOT EXISTS idx_user_reports_shared_ride_id
    ON user_reports (shared_ride_id)
    WHERE shared_ride_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_user_reports_driver_id
    ON user_reports (driver_id)
    WHERE driver_id IS NOT NULL;

-- Update status constraint to include PENDING and DISMISSED
ALTER TABLE user_reports
    DROP CONSTRAINT IF EXISTS chk_user_reports_status;

ALTER TABLE user_reports
    ADD CONSTRAINT chk_user_reports_status
        CHECK (status IN ('PENDING', 'OPEN', 'IN_PROGRESS', 'RESOLVED', 'DISMISSED'));

-- Update report_type constraint to match spec (SAFETY, BEHAVIOR, PAYMENT, ROUTE, OTHER)
ALTER TABLE user_reports
    DROP CONSTRAINT IF EXISTS chk_user_reports_type;

ALTER TABLE user_reports
    ADD CONSTRAINT chk_user_reports_type
        CHECK (report_type IN ('SAFETY', 'BEHAVIOR', 'PAYMENT', 'ROUTE', 'RIDE_EXPERIENCE', 'TECHNICAL', 'OTHER'));

-- Update notification type constraint to include new report notification types
ALTER TABLE notifications
    DROP CONSTRAINT IF EXISTS chk_notification_type;

ALTER TABLE notifications
    ADD CONSTRAINT chk_notification_type
        CHECK (type IN
               ('RIDE_REQUEST', 'RIDE_CONFIRMED', 'RIDE_STARTED', 'RIDE_COMPLETED', 'PAYMENT', 'PROMOTION', 'SYSTEM',
                'EMERGENCY', 'RIDE_TRACKING_START', 'WALLET_HOLD', 'WALLET_CAPTURE', 'WALLET_RELEASE', 'WALLET_REFUND',
                'BOOKING_REQUEST_CREATED', 'JOIN_RIDE_REQUEST_CREATED', 'RIDE_AUTO_STARTED', 'RIDE_AUTO_COMPLETED',
                'REQUEST_AUTO_STARTED', 'REQUEST_AUTO_COMPLETED', 'REQUEST_STARTED', 'REQUEST_COMPLETED',
                'WALLET_PAYOUT', 'SOS_ALERT', 'SOS_ESCALATED', 'SOS_RESOLVED',
                'USER_REPORT_SUBMITTED', 'USER_REPORT_RESOLVED', 'RIDE_REPORT_SUBMITTED', 
                'RIDE_REPORT_IN_PROGRESS', 'RIDE_REPORT_RESOLVED', 'RIDE_REPORT_DISMISSED'));

