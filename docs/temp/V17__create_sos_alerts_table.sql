-- V17__create_sos_alerts_table.sql
-- Create sos_alerts table for emergency alerts

CREATE TABLE IF NOT EXISTS sos_alerts (
    sos_id SERIAL PRIMARY KEY,
    share_ride_id INTEGER NOT NULL REFERENCES shared_rides(shared_ride_id) ON DELETE CASCADE,
    triggered_by INTEGER NOT NULL,
    alert_type VARCHAR(30) DEFAULT 'emergency',
    current_lat DOUBLE PRECISION,
    current_lng DOUBLE PRECISION,
    contact_info TEXT,
    description TEXT,
    status VARCHAR(20) DEFAULT 'active',
    acknowledged_by INTEGER,
    acknowledged_at TIMESTAMP,
    resolved_at TIMESTAMP,
    resolution_notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Add indexes
CREATE INDEX idx_sos_alerts_share_ride ON sos_alerts(share_ride_id);
CREATE INDEX idx_sos_alerts_triggered_by ON sos_alerts(triggered_by);
CREATE INDEX idx_sos_alerts_status ON sos_alerts(status);
CREATE INDEX idx_sos_alerts_created_at ON sos_alerts(created_at);
CREATE INDEX idx_sos_alerts_location ON sos_alerts(current_lat, current_lng);

-- Add constraints
ALTER TABLE sos_alerts ADD CONSTRAINT chk_alert_type
    CHECK (alert_type IN ('emergency', 'accident', 'breakdown', 'safety_concern', 'other'));

ALTER TABLE sos_alerts ADD CONSTRAINT chk_sos_status
    CHECK (status IN ('active', 'acknowledged', 'resolved', 'false_alarm'));

ALTER TABLE sos_alerts ADD CONSTRAINT chk_lat_range
    CHECK (current_lat IS NULL OR (current_lat >= -90 AND current_lat <= 90));

ALTER TABLE sos_alerts ADD CONSTRAINT chk_lng_range
    CHECK (current_lng IS NULL OR (current_lng >= -180 AND current_lng <= 180));

-- Add foreign key constraints for triggered_by and acknowledged_by (they reference users table)
ALTER TABLE sos_alerts ADD CONSTRAINT fk_sos_alerts_triggered_by
    FOREIGN KEY (triggered_by) REFERENCES users(user_id) ON DELETE CASCADE;

ALTER TABLE sos_alerts ADD CONSTRAINT fk_sos_alerts_acknowledged_by
    FOREIGN KEY (acknowledged_by) REFERENCES users(user_id) ON DELETE SET NULL;