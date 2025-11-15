-- V24__Add_priority_and_driver_response_to_reports.sql
-- Add priority level and driver response functionality to user reports

-- Add priority column
ALTER TABLE user_reports
    ADD COLUMN IF NOT EXISTS priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM';

-- Add driver response fields
ALTER TABLE user_reports
    ADD COLUMN IF NOT EXISTS driver_response TEXT,
    ADD COLUMN IF NOT EXISTS driver_responded_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS escalated_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS escalation_reason TEXT;

-- Add constraint for priority
ALTER TABLE user_reports
    DROP CONSTRAINT IF EXISTS chk_user_reports_priority;

ALTER TABLE user_reports
    ADD CONSTRAINT chk_user_reports_priority
        CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'));

-- Add index for priority and escalation
CREATE INDEX IF NOT EXISTS idx_user_reports_priority
    ON user_reports (priority);

CREATE INDEX IF NOT EXISTS idx_user_reports_escalated
    ON user_reports (escalated_at)
    WHERE escalated_at IS NOT NULL;

-- Add index for unresolved reports (for auto-escalation query)
CREATE INDEX IF NOT EXISTS idx_user_reports_unresolved
    ON user_reports (status, created_at)
    WHERE status IN ('PENDING', 'OPEN', 'IN_PROGRESS');

-- Add comments for documentation
COMMENT ON COLUMN user_reports.priority IS 'Priority level: LOW, MEDIUM, HIGH, CRITICAL';
COMMENT ON COLUMN user_reports.driver_response IS 'Driver''s response to the report';
COMMENT ON COLUMN user_reports.driver_responded_at IS 'When driver submitted their response';
COMMENT ON COLUMN user_reports.escalated_at IS 'When report was automatically escalated';
COMMENT ON COLUMN user_reports.escalation_reason IS 'Reason for escalation (e.g., unresolved for X days)';

