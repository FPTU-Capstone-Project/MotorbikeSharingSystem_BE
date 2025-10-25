DELETE FROM pricing_configs WHERE pricing_config_id < 10;

ALTER TABLE pricing_configs
    DROP CONSTRAINT IF EXISTS chk_base_flag_positive;
ALTER TABLE pricing_configs
    DROP CONSTRAINT IF EXISTS chk_per_km_positive;
ALTER TABLE pricing_configs
    DROP CONSTRAINT IF EXISTS chk_per_min_positive;
ALTER TABLE pricing_configs
    DROP CONSTRAINT IF EXISTS chk_min_fare_positive;
ALTER TABLE pricing_configs
    DROP CONSTRAINT IF EXISTS chk_peak_surcharge_positive;
ALTER TABLE pricing_configs
    DROP CONSTRAINT IF EXISTS chk_version_format;
ALTER TABLE pricing_configs
    DROP CONSTRAINT IF EXISTS chk_commission_range;

DROP INDEX IF EXISTS idx_pricing_configs_version;
DROP INDEX IF EXISTS idx_pricing_configs_created_by;

ALTER TABLE pricing_configs
    DROP COLUMN IF EXISTS base_flag_vnd;
ALTER TABLE pricing_configs
    DROP COLUMN IF EXISTS per_km_vnd;
ALTER TABLE pricing_configs
    DROP COLUMN IF EXISTS per_min_vnd;
ALTER TABLE pricing_configs
    DROP COLUMN IF EXISTS min_fare_vnd;
ALTER TABLE pricing_configs
    DROP COLUMN IF EXISTS peak_surcharge_vnd;
ALTER TABLE pricing_configs
    DROP COLUMN IF EXISTS is_active;
ALTER TABLE pricing_configs
    DROP COLUMN IF EXISTS created_by;
ALTER TABLE pricing_configs
    DROP COLUMN IF EXISTS created_at;
ALTER TABLE pricing_configs
    DROP COLUMN IF EXISTS updated_at;
ALTER TABLE pricing_configs
    DROP COLUMN IF EXISTS default_commission;

ALTER TABLE pricing_configs
    ALTER COLUMN version TYPE TIMESTAMP USING version::timestamp;
ALTER TABLE pricing_configs
    ADD COLUMN base_2km_vnd DECIMAL(18, 2) NOT NULL DEFAULT 0;
ALTER TABLE pricing_configs
    ADD COLUMN after_2Km_per_km_vnd DECIMAL(18, 2) NOT NULL DEFAULT 0;
ALTER TABLE pricing_configs
    ADD COLUMN system_commission_rate DECIMAL(5, 4) NOT NULL DEFAULT 0.1000;

ALTER TABLE pricing_configs
    ADD CONSTRAINT chk_base_2km_positive CHECK (base_2km_vnd >= 0);
ALTER TABLE pricing_configs
    ADD CONSTRAINT chk_after_2km_positive CHECK (after_2Km_per_km_vnd >= 0);
ALTER TABLE pricing_configs
    ADD CONSTRAINT chk_commission_range CHECK (system_commission_rate >= 0 AND system_commission_rate <= 1);

-- INSERT INTO pricing_configs (version, base_2km_vnd, after_2Km_per_km_vnd, system_commission_rate, valid_from, valid_until)
-- VALUES ('2025-01-15 00:00:00', 10000.00, 2500.00, 0.1000, '2025-01-15 00:00:00'::timestamp, NULL);


ALTER TABLE shared_ride_requests
    DROP CONSTRAINT IF EXISTS chk_fare_amount;
ALTER TABLE shared_ride_requests
    DROP CONSTRAINT IF EXISTS chk_original_fare;
ALTER TABLE shared_ride_requests
    DROP CONSTRAINT IF EXISTS chk_coverage_time_step;


ALTER TABLE shared_ride_requests
    ADD COLUMN IF NOT EXISTS promotion_id INTEGER REFERENCES promotions(promotion_id);
ALTER TABLE shared_ride_requests
    ADD COLUMN IF NOT EXISTS pricing_config_id INTEGER REFERENCES pricing_configs(pricing_config_id);
ALTER TABLE shared_ride_requests
    ADD COLUMN IF NOT EXISTS subtotal_fare DECIMAL(19, 2) NOT NULL DEFAULT 0;
ALTER TABLE shared_ride_requests
    ADD COLUMN IF NOT EXISTS total_fare DECIMAL(19, 2) NOT NULL DEFAULT 0;


ALTER TABLE shared_ride_requests
    DROP COLUMN IF EXISTS fare_amount;
ALTER TABLE shared_ride_requests
    DROP COLUMN IF EXISTS original_fare;
ALTER TABLE shared_ride_requests
    DROP COLUMN IF EXISTS coverage_time_step;
ALTER TABLE shared_ride_requests
    DROP COLUMN IF EXISTS max_wait_time;

ALTER TABLE shared_ride_requests
    ADD CONSTRAINT chk_subtotal_fare CHECK (subtotal_fare IS NULL OR subtotal_fare >= 0);
ALTER TABLE shared_ride_requests
    ADD CONSTRAINT chk_total_fare CHECK (total_fare IS NULL OR total_fare >= 0);


ALTER TABLE shared_rides
    ADD COLUMN IF NOT EXISTS pricing_config_id INTEGER REFERENCES pricing_configs(pricing_config_id);
ALTER TABLE shared_rides
    ADD COLUMN IF NOT EXISTS driver_earned_amount DECIMAL(19, 2);
ALTER TABLE shared_rides
    ADD COLUMN IF NOT EXISTS actual_duration INTEGER;


ALTER TABLE shared_rides
    DROP COLUMN IF EXISTS base_fare;
ALTER TABLE shared_rides
    DROP COLUMN IF EXISTS per_km_rate;

ALTER TABLE shared_rides
    ADD CONSTRAINT chk_driver_earned_amount_positive CHECK (driver_earned_amount IS NULL OR driver_earned_amount >= 0);
ALTER TABLE shared_rides
    ADD CONSTRAINT chk_actual_duration_positive CHECK (actual_duration IS NULL OR actual_duration > 0);


CREATE INDEX IF NOT EXISTS idx_ride_requests_promotion ON shared_ride_requests(promotion_id);
CREATE INDEX IF NOT EXISTS idx_ride_requests_pricing_config ON shared_ride_requests(pricing_config_id);
CREATE INDEX IF NOT EXISTS idx_shared_rides_pricing_config ON shared_rides(pricing_config_id);


UPDATE shared_ride_requests
SET pricing_config_id = (SELECT pricing_config_id FROM pricing_configs WHERE version = '2025-01-15 00:00:00'::timestamp)
WHERE pricing_config_id IS NULL;

UPDATE shared_rides
SET pricing_config_id = (SELECT pricing_config_id FROM pricing_configs WHERE version = '2025-01-15 00:00:00'::timestamp)
WHERE pricing_config_id IS NULL;


ALTER TABLE shared_ride_requests
    ALTER COLUMN pricing_config_id SET NOT NULL;
ALTER TABLE shared_rides
    ALTER COLUMN pricing_config_id SET NOT NULL;
