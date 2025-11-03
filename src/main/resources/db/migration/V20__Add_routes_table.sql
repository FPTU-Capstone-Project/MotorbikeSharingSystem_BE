CREATE TABLE IF NOT EXISTS routes
(
    route_id         SERIAL PRIMARY KEY,
    name             VARCHAR(255),
    route_type       VARCHAR(20) NOT NULL DEFAULT 'CUSTOM',
    from_location_id INTEGER     NOT NULL REFERENCES locations (location_id),
    to_location_id   INTEGER     NOT NULL REFERENCES locations (location_id),
    default_price    NUMERIC(19, 2),
    polyline         TEXT,
    metadata         TEXT,
    distance_meters  INTEGER,
    duration_seconds INTEGER,
    valid_from       TIMESTAMP,
    valid_until      TIMESTAMP,
    created_at       TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP   NOT NULL DEFAULT NOW()
);

ALTER TABLE routes
    ADD CONSTRAINT chk_routes_route_type CHECK (route_type IN ('TEMPLATE', 'CUSTOM'));

CREATE INDEX IF NOT EXISTS idx_routes_route_type ON routes (route_type);
CREATE INDEX IF NOT EXISTS idx_routes_from_to ON routes (from_location_id, to_location_id);

ALTER TABLE shared_rides
    ADD COLUMN IF NOT EXISTS route_id INTEGER REFERENCES routes (route_id);

CREATE INDEX IF NOT EXISTS idx_shared_rides_route_id ON shared_rides (route_id);

COMMENT ON COLUMN routes.route_type IS 'TEMPLATE = admin curated route, CUSTOM = ride-specific snapshot';
