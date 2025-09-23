-- V15__create_notifications_table.sql
-- Create notifications table for user notifications

CREATE TABLE IF NOT EXISTS notifications (
    notif_id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT,
    payload TEXT,
    priority VARCHAR(20) DEFAULT 'normal',
    delivery_method VARCHAR(30) DEFAULT 'push',
    is_read BOOLEAN DEFAULT false,
    read_at TIMESTAMP,
    sent_at TIMESTAMP,
    expires_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Add indexes
CREATE INDEX idx_notifications_user ON notifications(user_id);
CREATE INDEX idx_notifications_type ON notifications(type);
CREATE INDEX idx_notifications_is_read ON notifications(is_read);
CREATE INDEX idx_notifications_created_at ON notifications(created_at);
CREATE INDEX idx_notifications_expires_at ON notifications(expires_at);
CREATE INDEX idx_notifications_priority ON notifications(priority);

-- Add constraints
ALTER TABLE notifications ADD CONSTRAINT chk_notification_type
    CHECK (type IN ('ride_request', 'ride_confirmed', 'ride_started', 'ride_completed', 'payment', 'promotion', 'system', 'emergency'));

ALTER TABLE notifications ADD CONSTRAINT chk_priority
    CHECK (priority IN ('low', 'normal', 'high', 'urgent'));

ALTER TABLE notifications ADD CONSTRAINT chk_delivery_method
    CHECK (delivery_method IN ('push', 'email', 'sms', 'in_app'));