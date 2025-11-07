CREATE TABLE IF NOT EXISTS user_reports
(
    report_id          SERIAL PRIMARY KEY,
    reporter_id        INTEGER     NOT NULL,
    resolver_id        INTEGER,
    report_type        VARCHAR(50) NOT NULL,
    status             VARCHAR(50) NOT NULL,
    description        TEXT        NOT NULL,
    resolution_message TEXT,
    resolved_at        TIMESTAMP,
    created_at         TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE user_reports
    ADD CONSTRAINT fk_user_reports_reporter
        FOREIGN KEY (reporter_id) REFERENCES users (user_id)
            ON DELETE CASCADE;

ALTER TABLE user_reports
    ADD CONSTRAINT fk_user_reports_resolver
        FOREIGN KEY (resolver_id) REFERENCES users (user_id)
            ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_user_reports_status
    ON user_reports (status);

CREATE INDEX IF NOT EXISTS idx_user_reports_type
    ON user_reports (report_type);



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
                'USER_REPORT_SUBMITTED', 'USER_REPORT_RESOLVED'));