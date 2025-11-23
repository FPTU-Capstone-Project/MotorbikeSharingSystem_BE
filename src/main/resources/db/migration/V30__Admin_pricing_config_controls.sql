BEGIN;

-- Add lifecycle metadata for pricing configs to support draft/scheduled/active flows
ALTER TABLE pricing_configs
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'DRAFT';

ALTER TABLE pricing_configs
    ADD COLUMN IF NOT EXISTS created_by INTEGER REFERENCES users (user_id);

ALTER TABLE pricing_configs
    ADD COLUMN IF NOT EXISTS updated_by INTEGER REFERENCES users (user_id);

ALTER TABLE pricing_configs
    ADD COLUMN IF NOT EXISTS change_reason VARCHAR(255);

ALTER TABLE pricing_configs
    ADD COLUMN IF NOT EXISTS notice_sent_at TIMESTAMP;

ALTER TABLE pricing_configs
    ALTER COLUMN valid_from DROP NOT NULL;

DO $$
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_pricing_config_status') THEN
            ALTER TABLE pricing_configs
                ADD CONSTRAINT chk_pricing_config_status
                    CHECK (status IN ('DRAFT', 'SCHEDULED', 'ACTIVE', 'ARCHIVED'));
        END IF;
    END $$;

DO $$
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_pricing_config_valid_range') THEN
            ALTER TABLE pricing_configs
                ADD CONSTRAINT chk_pricing_config_valid_range
                    CHECK (valid_until IS NULL OR valid_from IS NULL OR valid_until > valid_from);
        END IF;
    END $$;

CREATE INDEX IF NOT EXISTS idx_pricing_configs_status_valid_from
    ON pricing_configs (status, valid_from);

UPDATE pricing_configs
SET status = CASE
                 WHEN valid_until IS NULL THEN 'ACTIVE'
                 ELSE 'ARCHIVED'
    END;

DO $$
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_fare_tiers_max_km_limit') THEN
            ALTER TABLE fare_tiers
                ADD CONSTRAINT chk_fare_tiers_max_km_limit CHECK (max_km <= 25);
        END IF;
    END $$;

COMMIT;
