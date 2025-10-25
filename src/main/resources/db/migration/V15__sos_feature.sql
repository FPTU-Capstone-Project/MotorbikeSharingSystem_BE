-- SOS feature foundational schema changes

-- Allow SOS alerts to exist without an attached ride (e.g. campus incidents)
ALTER TABLE sos_alerts
    ALTER COLUMN share_ride_id DROP NOT NULL;

-- Extend SOS alerts with escalation tracking and lifecycle metadata
ALTER TABLE sos_alerts
    ADD COLUMN IF NOT EXISTS ride_snapshot TEXT,
    ADD COLUMN IF NOT EXISTS last_escalated_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS next_escalation_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS escalation_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS fallback_contact_used BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS auto_call_triggered BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS campus_security_notified BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS ack_deadline TIMESTAMP,
    ADD COLUMN IF NOT EXISTS resolved_by INTEGER;

-- Link resolver to users table
ALTER TABLE sos_alerts DROP CONSTRAINT IF EXISTS fk_sos_alerts_resolved_by;
ALTER TABLE sos_alerts
    ADD CONSTRAINT fk_sos_alerts_resolved_by
        FOREIGN KEY (resolved_by) REFERENCES users (user_id) ON DELETE SET NULL;

-- Refresh status constraint to include the new ESCALATED state
ALTER TABLE sos_alerts
    DROP CONSTRAINT IF EXISTS chk_sos_status;

ALTER TABLE sos_alerts
    ADD CONSTRAINT chk_sos_status
        CHECK (status IN ('ACTIVE', 'ESCALATED', 'ACKNOWLEDGED', 'RESOLVED', 'FALSE_ALARM'));

-- Ensure defaults for alert and status columns
ALTER TABLE sos_alerts
    ALTER COLUMN alert_type SET DEFAULT 'EMERGENCY',
    ALTER COLUMN status SET DEFAULT 'ACTIVE';

-- Index escalation scheduling column for worker efficiency
CREATE INDEX IF NOT EXISTS idx_sos_alerts_next_escalation
    ON sos_alerts (next_escalation_at);

-- Timeline table to capture detailed alert lifecycle events
CREATE TABLE IF NOT EXISTS sos_alert_events
(
    event_id    SERIAL PRIMARY KEY,
    sos_id      INTEGER NOT NULL REFERENCES sos_alerts (sos_id) ON DELETE CASCADE,
    event_type  VARCHAR(40) NOT NULL,
    description TEXT,
    metadata    TEXT,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sos_alert_events_sos
    ON sos_alert_events (sos_id);

-- Emergency contact auditing support
ALTER TABLE emergency_contacts
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP,
    ALTER COLUMN is_primary SET DEFAULT false,
    ALTER COLUMN is_primary SET NOT NULL;

UPDATE emergency_contacts
SET is_primary = COALESCE(is_primary, false),
    updated_at = COALESCE(updated_at, created_at);


ALTER TABLE shared_ride_requests
    ADD COLUMN IF NOT EXISTS duration_seconds INTEGER NOT NULL DEFAULT 0;