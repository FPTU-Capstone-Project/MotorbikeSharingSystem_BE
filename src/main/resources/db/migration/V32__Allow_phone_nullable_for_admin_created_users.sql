-- Allow phone to be nullable so admin-created accounts can omit it
ALTER TABLE users
    ALTER COLUMN phone DROP NOT NULL;
