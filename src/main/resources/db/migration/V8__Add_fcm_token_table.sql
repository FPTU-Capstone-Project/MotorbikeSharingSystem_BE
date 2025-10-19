CREATE TABLE fcm_tokens
(
    id           SERIAL PRIMARY KEY,
    user_id      INTEGER                               NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    token        VARCHAR(500)                          NOT NULL,
    device_type  VARCHAR(20),
    is_active    BOOLEAN     DEFAULT true,
    created_at   TIMESTAMP   DEFAULT CURRENT_TIMESTAMP NOT NULL,
    last_used_at TIMESTAMP
);

ALTER TABLE fcm_tokens
    ADD CONSTRAINT uk_fcm_tokens_token UNIQUE (token);

CREATE INDEX idx_fcm_tokens_user ON fcm_tokens (user_id);
CREATE INDEX idx_fcm_tokens_active ON fcm_tokens (is_active);
CREATE INDEX idx_fcm_tokens_last_used ON fcm_tokens (last_used_at);

ALTER TABLE fcm_tokens
    ADD CONSTRAINT chk_device_type
        CHECK (device_type IN ('IOS', 'ANDROID', 'WEB') OR device_type IS NULL);
