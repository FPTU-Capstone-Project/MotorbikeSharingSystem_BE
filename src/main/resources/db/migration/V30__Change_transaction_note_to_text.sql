-- ✅ FIX: Change transaction.note from VARCHAR(255) to TEXT
-- PayOS response và các note khác có thể vượt quá 255 ký tự
ALTER TABLE transactions ALTER COLUMN note TYPE TEXT;

