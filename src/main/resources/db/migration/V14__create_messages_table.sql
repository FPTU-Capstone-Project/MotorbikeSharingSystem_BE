-- V14__create_messages_table.sql
-- Create messages table for user messaging

CREATE TABLE IF NOT EXISTS messages (
    message_id SERIAL PRIMARY KEY,
    sender_id INTEGER NOT NULL,
    receiver_id INTEGER NOT NULL,
    shared_ride_request_id INTEGER NOT NULL REFERENCES shared_ride_requests(shared_ride_request_id) ON DELETE CASCADE,
    conversation_id VARCHAR(100),
    message_type VARCHAR(20) DEFAULT 'text',
    content TEXT,
    metadata TEXT,
    is_read BOOLEAN DEFAULT false,
    read_at TIMESTAMP,
    sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Add indexes
CREATE INDEX idx_messages_sender ON messages(sender_id);
CREATE INDEX idx_messages_receiver ON messages(receiver_id);
CREATE INDEX idx_messages_shared_ride_request ON messages(shared_ride_request_id);
CREATE INDEX idx_messages_conversation ON messages(conversation_id);
CREATE INDEX idx_messages_sent_at ON messages(sent_at);
CREATE INDEX idx_messages_is_read ON messages(is_read);

-- Add indexes for conversation queries
CREATE INDEX idx_messages_conversation_participants ON messages(sender_id, receiver_id);

-- Add constraints
ALTER TABLE messages ADD CONSTRAINT chk_message_type
    CHECK (message_type IN ('text', 'image', 'location', 'system', 'notification'));

ALTER TABLE messages ADD CONSTRAINT chk_sender_receiver_different
    CHECK (sender_id != receiver_id);

-- Add foreign key constraints for sender and receiver (they reference users table)
ALTER TABLE messages ADD CONSTRAINT fk_messages_sender
    FOREIGN KEY (sender_id) REFERENCES users(user_id) ON DELETE CASCADE;

ALTER TABLE messages ADD CONSTRAINT fk_messages_receiver
    FOREIGN KEY (receiver_id) REFERENCES users(user_id) ON DELETE CASCADE;