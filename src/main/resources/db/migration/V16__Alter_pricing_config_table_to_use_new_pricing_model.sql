BEGIN;
CREATE TABLE fare_tiers
(
    fare_tier_id      SERIAL PRIMARY KEY,
    pricing_config_id INTEGER                             NOT NULL REFERENCES pricing_configs (pricing_config_id) ON DELETE CASCADE,
    tier_level        INTEGER                             NOT NULL,
    description       VARCHAR(255),
    amount            DECIMAL(18, 2)                      NOT NULL,
    min_km            INTEGER                             NOT NULL,
    max_km            INTEGER                             NOT NULL,
    is_active         BOOLEAN   DEFAULT true              NOT NULL,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

    CONSTRAINT chk_min_max_km_range
        CHECK (min_km >= 0 AND max_km > min_km),

    CONSTRAINT chk_amount_positive
        CHECK (amount >= 0),

    CONSTRAINT uk_tier_order_per_config
        UNIQUE (pricing_config_id, tier_level)
);

COMMENT ON TABLE fare_tiers IS 'Stores tiered pricing rules, where each tier defines a fare for a specific distance range (min_km to max_km).';
COMMENT ON COLUMN fare_tiers.amount IS 'The fare amount for this tier. This could be a flat fee or a per-km rate depending on business logic.';
COMMENT ON COLUMN fare_tiers.tier_level IS 'The order in which tiers are evaluated for a given pricing configuration.';
COMMENT ON CONSTRAINT uk_tier_order_per_config ON fare_tiers IS 'Ensures that within one pricing config, the tier order is unique.';

CREATE INDEX idx_fare_tiers_pricing_config_id ON fare_tiers (pricing_config_id);

ALTER TABLE pricing_configs
    DROP CONSTRAINT IF EXISTS chk_base_2km_positive;
ALTER TABLE pricing_configs
    DROP CONSTRAINT IF EXISTS chk_after_2km_positive;

ALTER TABLE pricing_configs
    DROP COLUMN IF EXISTS base_2km_vnd;
ALTER TABLE pricing_configs
    DROP COLUMN IF EXISTS after_2km_per_km_vnd;

COMMENT ON TABLE pricing_configs IS 'Acts as a container for a versioned set of pricing rules (fare tiers). The active version determines the current pricing.';

COMMIT;