ALTER TABLE user_reports
    ADD COLUMN IF NOT EXISTS reporter_chat_started_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS reporter_last_reply_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS reported_chat_started_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS reported_last_reply_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS auto_closed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS auto_closed_reason VARCHAR(100);


