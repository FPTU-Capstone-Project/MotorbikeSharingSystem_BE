BEGIN;

ALTER TABLE driver_profiles
ADD COLUMN max_detour_minutes INTEGER DEFAULT 8;

COMMENT ON COLUMN driver_profiles.max_detour_minutes IS 
'Maximum detour time in minutes driver accepts for pickups. Used by matching algorithm. Default 8 minutes per BR-26. NULL means use system default.';

ALTER TABLE driver_profiles
ADD CONSTRAINT chk_max_detour_positive
CHECK (max_detour_minutes IS NULL OR max_detour_minutes > 0);

COMMENT ON CONSTRAINT chk_max_detour_positive ON driver_profiles IS 
'Ensures max_detour_minutes is positive when set. NULL allowed to use system default.';

ALTER TABLE driver_profiles
ADD CONSTRAINT chk_max_detour_reasonable
CHECK (max_detour_minutes IS NULL OR max_detour_minutes <= 30);

COMMENT ON CONSTRAINT chk_max_detour_reasonable ON driver_profiles IS 
'Ensures max_detour_minutes is reasonable (<=30 min). Prevents unrealistic values that would degrade matching quality.';

UPDATE driver_profiles
SET max_detour_minutes = 8
WHERE max_detour_minutes IS NULL;

COMMIT;

