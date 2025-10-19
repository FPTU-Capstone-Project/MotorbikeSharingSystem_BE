CREATE TABLE pricing_configs
(
    pricing_config_id  SERIAL PRIMARY KEY,
    version            VARCHAR(50) UNIQUE NOT NULL,                -- e.g., '2025-10-01'
    base_flag_vnd      BIGINT             NOT NULL DEFAULT 0,      -- base starting amount
    per_km_vnd         BIGINT             NOT NULL DEFAULT 0,      -- price per km
    per_min_vnd        BIGINT             NOT NULL DEFAULT 0,      -- price per minute
    min_fare_vnd       BIGINT             NOT NULL DEFAULT 0,      -- clamp small trips
    peak_surcharge_vnd BIGINT             NOT NULL DEFAULT 0,      -- 0 for MVP if unused
    default_commission DECIMAL(5, 4)      NOT NULL DEFAULT 0.1000, -- 0.10 => 10%
    valid_from         TIMESTAMP          NOT NULL,
    valid_until        TIMESTAMP,                                  -- NULL = open ended
    is_active          BOOLEAN            NOT NULL DEFAULT true,
    created_by         INTEGER,
    created_at         TIMESTAMP          NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP          NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Add indexes for pricing_configs
CREATE INDEX idx_pricing_configs_version ON pricing_configs (version);
CREATE INDEX idx_pricing_configs_active ON pricing_configs (is_active);
CREATE INDEX idx_pricing_configs_valid_dates ON pricing_configs (valid_from, valid_until);
CREATE INDEX idx_pricing_configs_created_by ON pricing_configs (created_by);

-- Add constraints for pricing_configs
ALTER TABLE pricing_configs
    ADD CONSTRAINT chk_base_flag_positive
        CHECK (base_flag_vnd >= 0);
ALTER TABLE pricing_configs
    ADD CONSTRAINT chk_per_km_positive
        CHECK (per_km_vnd >= 0);
ALTER TABLE pricing_configs
    ADD CONSTRAINT chk_per_min_positive
        CHECK (per_min_vnd >= 0);
ALTER TABLE pricing_configs
    ADD CONSTRAINT chk_min_fare_positive
        CHECK (min_fare_vnd >= 0);
ALTER TABLE pricing_configs
    ADD CONSTRAINT chk_peak_surcharge_positive
        CHECK (peak_surcharge_vnd >= 0);
ALTER TABLE pricing_configs
    ADD CONSTRAINT chk_commission_range
        CHECK (default_commission >= 0 AND default_commission <= 1);
ALTER TABLE pricing_configs
    ADD CONSTRAINT chk_valid_date_range
        CHECK (valid_until IS NULL OR valid_until > valid_from);
ALTER TABLE pricing_configs
    ADD CONSTRAINT chk_version_format
        CHECK (version ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$');

INSERT INTO pricing_configs (version,
                             base_flag_vnd,
                             per_km_vnd,
                             per_min_vnd,
                             min_fare_vnd,
                             peak_surcharge_vnd,
                             default_commission,
                             valid_from,
                             valid_until,
                             is_active,
                             created_by)
VALUES ('2025-01-01', -- version (using current date format)
        8000, -- base_flag_vnd
        4000, -- per_km_vnd
        300, -- per_min_vnd
        10000, -- min_fare_vnd
        5000, -- peak_surcharge_vnd
        0.1000, -- default_commission (10%)
        '2000-01-01 00:00:00', -- valid_from
        NULL, -- valid_until (open-ended)
        true, -- is_active
        NULL -- created_by
       );
