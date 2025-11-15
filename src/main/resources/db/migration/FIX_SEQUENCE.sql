-- Quick fix for user_reports sequence
-- Run this before inserting new data if you get duplicate key errors

DO $$
DECLARE
    max_id INTEGER;
BEGIN
    SELECT COALESCE(MAX(report_id), 0) INTO max_id FROM user_reports;
    EXECUTE format('ALTER SEQUENCE IF EXISTS user_reports_report_id_seq RESTART WITH %s', max_id + 1);
    RAISE NOTICE 'Sequence reset to: %', max_id + 1;
END $$;

