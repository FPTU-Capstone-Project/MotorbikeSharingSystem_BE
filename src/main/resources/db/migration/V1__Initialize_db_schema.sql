-- V1__Initialize_db_schema.sql
-- Consolidated database schema initialization
-- Contains all CREATE statements from V1-V18 with uppercased enum values and defaults

-- Create helper function for updating timestamps
CREATE OR REPLACE FUNCTION update_updated_at_column()
    RETURNS TRIGGER AS
$$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create users table
CREATE TABLE users
(
    user_id           SERIAL PRIMARY KEY,
    email             VARCHAR(255) NOT NULL,
    phone             VARCHAR(20)  NOT NULL,
    password_hash     VARCHAR(255),
    full_name         VARCHAR(255) NOT NULL,
    student_id        VARCHAR(50),
    user_type         VARCHAR(20)  NOT NULL DEFAULT 'USER',
    profile_photo_url VARCHAR(500),
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    email_verified    BOOLEAN      NOT NULL DEFAULT false,
    phone_verified    BOOLEAN      NOT NULL DEFAULT false,
    token_version     BIGINT       NOT NULL DEFAULT 1,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Add unique constraints for users
ALTER TABLE users
    ADD CONSTRAINT uk_users_email UNIQUE (email);
ALTER TABLE users
    ADD CONSTRAINT uk_users_phone UNIQUE (phone);

-- Add partial unique index for student_id (allows multiple NULLs but unique non-NULLs)
CREATE UNIQUE INDEX uk_users_student_id ON users (student_id) WHERE student_id IS NOT NULL;

-- Add indexes for performance on users
CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_users_phone ON users (phone);
CREATE INDEX idx_users_user_type ON users (user_type);
CREATE INDEX idx_users_status ON users (status);

-- Add check constraints for users
ALTER TABLE users
    ADD CONSTRAINT chk_status
        CHECK (status IN ('EMAIL_VERIFYING', 'PENDING', 'ACTIVE', 'SUSPENDED', 'REJECTED', 'DELETED'));
ALTER TABLE users
    ADD CONSTRAINT chk_user_type
        CHECK (user_type IN ('USER', 'ADMIN'));

INSERT INTO users (email, phone, password_hash, full_name, user_type, status, email_verified, phone_verified)
VALUES ('admin@mssus.com',
        '0900000001',
        '$2a$10$BaeiCK1yapOvw.WrcaGb1OqHVOqqSD4TkEAvhHThm.F85BvxYH7ru', -- password: Password1!
        'System Administrator',
        'ADMIN',
        'ACTIVE',
        true,
        true)
ON CONFLICT (email) DO NOTHING;

-- Admin profiles table
CREATE TABLE admin_profiles
(
    admin_id    INTEGER PRIMARY KEY REFERENCES users (user_id) ON DELETE CASCADE,
    department  VARCHAR(100),
    permissions TEXT,
    last_login  TIMESTAMP,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Rider profiles table
CREATE TABLE rider_profiles
(
    rider_id                 INTEGER PRIMARY KEY REFERENCES users (user_id) ON DELETE CASCADE,
    emergency_contact        VARCHAR(20),
    total_rides              INTEGER        DEFAULT 0,
    total_spent              DECIMAL(10, 2) DEFAULT 0,
    status                   VARCHAR(10)    DEFAULT 'ACTIVE',
    preferred_payment_method VARCHAR(20)    DEFAULT 'WALLET',
    created_at               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Driver profiles table
CREATE TABLE driver_profiles
(
    driver_id           INTEGER PRIMARY KEY REFERENCES users (user_id) ON DELETE CASCADE,
    license_number      VARCHAR(50)                              NOT NULL,
    license_verified_at TIMESTAMP,
    status              VARCHAR(20)    DEFAULT 'PENDING',
    rating_avg          FLOAT          DEFAULT 5.0,
    total_shared_rides  INTEGER        DEFAULT 0,
    total_earned        DECIMAL(10, 2) DEFAULT 0,
    commission_rate     DECIMAL(3, 2)  DEFAULT 0.15,
    is_available        BOOLEAN        DEFAULT false,
    max_passengers      INTEGER        DEFAULT 1,
    created_at          TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Add unique constraint for license number
ALTER TABLE driver_profiles
    ADD CONSTRAINT uk_driver_license UNIQUE (license_number);

-- Add indexes for profiles
CREATE INDEX idx_driver_status ON driver_profiles (status);
CREATE INDEX idx_driver_available ON driver_profiles (is_available);
CREATE INDEX idx_rider_payment_method ON rider_profiles (preferred_payment_method);

-- Add check constraints for profiles
ALTER TABLE rider_profiles
    ADD CONSTRAINT chk_rider_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED'));
ALTER TABLE driver_profiles
    ADD CONSTRAINT chk_driver_status
        CHECK (status IN ('PENDING', 'ACTIVE', 'INACTIVE', 'SUSPENDED'));
ALTER TABLE driver_profiles
    ADD CONSTRAINT chk_commission_rate
        CHECK (commission_rate >= 0 AND commission_rate <= 1);
ALTER TABLE driver_profiles
    ADD CONSTRAINT chk_rating_avg_driver
        CHECK (rating_avg >= 0 AND rating_avg <= 5);
ALTER TABLE rider_profiles
    ADD CONSTRAINT chk_payment_method
        CHECK (preferred_payment_method IN ('WALLET', 'CREDIT_CARD'));

-- Insert ordinary user
INSERT INTO users (email, phone, password_hash, full_name, student_id, user_type, status, email_verified,
                   phone_verified)
VALUES ('john.doe@example.com',
        '0987654321',
        '$2a$10$BaeiCK1yapOvw.WrcaGb1OqHVOqqSD4TkEAvhHThm.F85BvxYH7ru',
        'John Doe',
        'STU123456',
        'USER',
        'ACTIVE',
        true,
        true)
ON CONFLICT (email) DO NOTHING;

-- Insert rider profile for the user
INSERT INTO rider_profiles (rider_id, emergency_contact, preferred_payment_method)
SELECT user_id, '0901234567', 'WALLET'
FROM users
WHERE email = 'john.doe@example.com';

-- Insert driver profile for the user
INSERT INTO driver_profiles (driver_id, license_number, status, is_available)
SELECT user_id, 'DL123456789', 'ACTIVE', true
FROM users
WHERE email = 'john.doe@example.com';

-- Create wallets table
CREATE TABLE wallets
(
    wallet_id       SERIAL PRIMARY KEY,
    user_id         INTEGER                                  NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    psp_account_id  VARCHAR(255),
    cached_balance  DECIMAL(10, 2) DEFAULT 0,
    pending_balance DECIMAL(10, 2) DEFAULT 0,
    total_topped_up DECIMAL(10, 2) DEFAULT 0,
    total_spent     DECIMAL(10, 2) DEFAULT 0,
    last_synced_at  TIMESTAMP,
    is_active       BOOLEAN        DEFAULT true,
    created_at      TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Add unique constraint for user_id (one wallet per user)
ALTER TABLE wallets
    ADD CONSTRAINT uk_wallet_user UNIQUE (user_id);

-- Add indexes for wallets
CREATE INDEX idx_wallet_user ON wallets (user_id);
CREATE INDEX idx_wallet_active ON wallets (is_active);

-- Add check constraints for wallets
ALTER TABLE wallets
    ADD CONSTRAINT chk_balance_positive
        CHECK (cached_balance >= 0);
ALTER TABLE wallets
    ADD CONSTRAINT chk_pending_positive
        CHECK (pending_balance >= 0);

-- Add update trigger for updated_at on wallets
CREATE TRIGGER update_wallets_updated_at
    BEFORE UPDATE
    ON wallets
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

-- Create verifications table
CREATE TABLE verifications
(
    verification_id  SERIAL PRIMARY KEY,
    user_id          INTEGER                               NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    type             VARCHAR(50)                           NOT NULL,
    status           VARCHAR(20) DEFAULT 'PENDING',
    document_url     VARCHAR(500),
    document_type    VARCHAR(20),
    rejection_reason TEXT,
    verified_by      INTEGER REFERENCES admin_profiles (admin_id),
    verified_at      TIMESTAMP,
    expires_at       TIMESTAMP,
    metadata         TEXT,
    created_at       TIMESTAMP   DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Add indexes for verifications
CREATE INDEX idx_verification_user_type_status ON verifications (user_id, type, status);
CREATE INDEX idx_verification_status_created ON verifications (status, created_at);
CREATE INDEX idx_verification_user ON verifications (user_id);
CREATE INDEX idx_verification_type ON verifications (type);
CREATE INDEX idx_verification_status ON verifications (status);

-- Add check constraints for verifications
ALTER TABLE verifications
    ADD CONSTRAINT chk_verification_type
        CHECK (type IN
               ('STUDENT_ID', 'DRIVER_LICENSE', 'BACKGROUND_CHECK', 'VEHICLE_REGISTRATION', 'DRIVER_DOCUMENTS'));
ALTER TABLE verifications
    ADD CONSTRAINT chk_verification_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'EXPIRED'));
ALTER TABLE verifications
    ADD CONSTRAINT chk_document_type
        CHECK (document_type IN ('IMAGE', 'PDF') OR document_type IS NULL);

-- Create vehicles table
CREATE TABLE vehicles
(
    vehicle_id       SERIAL PRIMARY KEY,
    driver_id        INTEGER                               NOT NULL REFERENCES driver_profiles (driver_id) ON DELETE CASCADE,
    plate_number     VARCHAR(20)                           NOT NULL,
    model            VARCHAR(100)                          NOT NULL,
    color            VARCHAR(50),
    year             INTEGER,
    capacity         INTEGER     DEFAULT 1,
    helmet_count     INTEGER     DEFAULT 2,
    insurance_expiry TIMESTAMP                             NOT NULL,
    last_maintenance TIMESTAMP,
    fuel_type        VARCHAR(20) DEFAULT 'GASOLINE',
    status           VARCHAR(20) DEFAULT 'PENDING',
    verified_at      TIMESTAMP,
    created_at       TIMESTAMP   DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Add unique constraint for plate number
ALTER TABLE vehicles
    ADD CONSTRAINT uk_vehicle_plate UNIQUE (plate_number);

-- Add indexes for vehicles
CREATE INDEX idx_vehicle_driver ON vehicles (driver_id);
CREATE INDEX idx_vehicle_status ON vehicles (status);

-- Add check constraints for vehicles
ALTER TABLE vehicles
    ADD CONSTRAINT chk_vehicle_status
        CHECK (status IN ('PENDING', 'ACTIVE', 'MAINTENANCE', 'INACTIVE'));
ALTER TABLE vehicles
    ADD CONSTRAINT chk_fuel_type
        CHECK (fuel_type IN ('GASOLINE', 'ELECTRIC'));
ALTER TABLE vehicles
    ADD CONSTRAINT chk_vehicle_year
        CHECK (year >= 2000 AND year <= EXTRACT(YEAR FROM CURRENT_DATE) + 1);
ALTER TABLE vehicles
    ADD CONSTRAINT chk_vehicle_capacity
        CHECK (capacity >= 1 AND capacity <= 2);

INSERT INTO vehicles (driver_id,
                      plate_number,
                      model,
                      color,
                      year,
                      capacity,
                      helmet_count,
                      insurance_expiry,
                      last_maintenance,
                      fuel_type,
                      status,
                      verified_at)
SELECT dp.driver_id,
       '29A-12345',
       'Honda Wave Alpha',
       'Black',
       2022,
       1,
       2,
       '2025-01-01 00:00:00',
       '2024-06-01 00:00:00',
       'GASOLINE',
       'ACTIVE',
       '2024-06-10 12:00:00'
FROM driver_profiles dp
         JOIN users u ON dp.driver_id = u.user_id
WHERE u.email = 'john.doe@example.com';


-- Create locations table
CREATE TABLE locations
(
    location_id SERIAL PRIMARY KEY,
    name        VARCHAR(255)     NOT NULL,
    lat         DOUBLE PRECISION NOT NULL,
    lng         DOUBLE PRECISION NOT NULL,
    address     TEXT,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Add indexes for geolocation queries
CREATE INDEX idx_location_lat_lng ON locations (lat, lng);
CREATE INDEX idx_location_name ON locations (name);

-- Add constraints for locations
ALTER TABLE locations
    ADD CONSTRAINT chk_lat_range
        CHECK (lat >= -90 AND lat <= 90);
ALTER TABLE locations
    ADD CONSTRAINT chk_lng_range
        CHECK (lng >= -180 AND lng <= 180);

INSERT INTO locations (name, lat, lng, address)
VALUES ('Tòa S2.02 Vinhomes Grand Park', 10.8386317, 106.8318038, NULL),
       ('FPT University - HCMC Campus', 10.841480, 106.809844, NULL),
       ('Tòa S6.02 Vinhomes Grand Park', 10.8426113, 106.8374642, NULL),
       ('Sảnh C6-C5, Ký túc xá Khu B ĐHQG TP.HCM', 10.8833471, 106.7795158, NULL);


-- Create shared_rides table
CREATE TABLE shared_rides
(
    shared_ride_id     SERIAL PRIMARY KEY,
    driver_id          INTEGER                               NOT NULL REFERENCES driver_profiles (driver_id) ON DELETE CASCADE,
    vehicle_id         INTEGER                               NOT NULL REFERENCES vehicles (vehicle_id) ON DELETE CASCADE,
    start_location_id  INTEGER                               NOT NULL REFERENCES locations (location_id),
    end_location_id    INTEGER                               NOT NULL REFERENCES locations (location_id),
    status             VARCHAR(50) DEFAULT 'PENDING',
    max_passengers     INTEGER     DEFAULT 1,
    current_passengers INTEGER     DEFAULT 0,
    base_fare          DECIMAL(10, 2),
    per_km_rate        DECIMAL(10, 2),
    estimated_duration INTEGER,
    estimated_distance REAL,
    actual_distance    REAL,
    scheduled_time     TIMESTAMP,
    started_at         TIMESTAMP,
    completed_at       TIMESTAMP,
    created_at         TIMESTAMP   DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Add indexes for shared_rides
CREATE INDEX idx_shared_rides_driver ON shared_rides (driver_id);
CREATE INDEX idx_shared_rides_vehicle ON shared_rides (vehicle_id);
CREATE INDEX idx_shared_rides_status ON shared_rides (status);
CREATE INDEX idx_shared_rides_scheduled_time ON shared_rides (scheduled_time);
CREATE INDEX idx_shared_rides_start_location ON shared_rides (start_location_id);
CREATE INDEX idx_shared_rides_end_location ON shared_rides (end_location_id);

-- Add constraints for shared_rides
ALTER TABLE shared_rides
    ADD CONSTRAINT chk_shared_rides_status
        CHECK (status IN ('SCHEDULED', 'ACTIVE', 'COMPLETED', 'CANCELLED', 'PENDING'));
ALTER TABLE shared_rides
    ADD CONSTRAINT chk_max_passengers
        CHECK (max_passengers >= 1 AND max_passengers <= 3);
ALTER TABLE shared_rides
    ADD CONSTRAINT chk_current_passengers
        CHECK (current_passengers >= 0 AND current_passengers <= max_passengers);
ALTER TABLE shared_rides
    ADD CONSTRAINT chk_base_fare
        CHECK (base_fare >= 0);
ALTER TABLE shared_rides
    ADD CONSTRAINT chk_per_km_rate
        CHECK (per_km_rate >= 0);

-- Create promotions table
CREATE TABLE promotions
(
    promotion_id           SERIAL PRIMARY KEY,
    code                   VARCHAR(50)    NOT NULL UNIQUE,
    title                  VARCHAR(255)   NOT NULL,
    description            TEXT,
    discount_type          VARCHAR(20)    NOT NULL,
    discount_value         DECIMAL(19, 2) NOT NULL,
    target_user_type       VARCHAR(20),
    min_shared_ride_amount DECIMAL(19, 2),
    max_discount           DECIMAL(19, 2),
    usage_limit            INTEGER,
    usage_limit_per_user   INTEGER,
    used_count             INTEGER                 DEFAULT 0,
    valid_from             TIMESTAMP      NOT NULL,
    valid_until            TIMESTAMP      NOT NULL,
    is_active              BOOLEAN        NOT NULL DEFAULT true,
    created_by             INTEGER,
    created_at             TIMESTAMP               DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Add indexes for promotions
CREATE INDEX idx_promotions_code ON promotions (code);
CREATE INDEX idx_promotions_active ON promotions (is_active);
CREATE INDEX idx_promotions_valid_dates ON promotions (valid_from, valid_until);
CREATE INDEX idx_promotions_target_user_type ON promotions (target_user_type);

-- Add constraints for promotions
ALTER TABLE promotions
    ADD CONSTRAINT chk_discount_type
        CHECK (discount_type IN ('PERCENTAGE', 'FIXED_AMOUNT'));
ALTER TABLE promotions
    ADD CONSTRAINT chk_discount_value
        CHECK (discount_value > 0);
ALTER TABLE promotions
    ADD CONSTRAINT chk_target_user_type
        CHECK (target_user_type IN ('RIDER', 'DRIVER', 'ALL') OR target_user_type IS NULL);
ALTER TABLE promotions
    ADD CONSTRAINT chk_valid_dates
        CHECK (valid_until > valid_from);
ALTER TABLE promotions
    ADD CONSTRAINT chk_usage_limits
        CHECK (usage_limit IS NULL OR usage_limit > 0);
ALTER TABLE promotions
    ADD CONSTRAINT chk_usage_limit_per_user
        CHECK (usage_limit_per_user IS NULL OR usage_limit_per_user > 0);

-- Create shared_ride_requests table
CREATE TABLE shared_ride_requests
(
    shared_ride_request_id SERIAL PRIMARY KEY,
    shared_ride_id         INTEGER                               NOT NULL REFERENCES shared_rides (shared_ride_id) ON DELETE CASCADE,
    rider_id               INTEGER                               NOT NULL REFERENCES rider_profiles (rider_id) ON DELETE CASCADE,
    pickup_location_id     INTEGER REFERENCES locations (location_id),
    dropoff_location_id    INTEGER REFERENCES locations (location_id),
    status                 VARCHAR(50) DEFAULT 'PENDING',
    fare_amount            DECIMAL(19, 2)                        NOT NULL,
    original_fare          DECIMAL(19, 2),
    discount_amount        DECIMAL(19, 2),
    pickup_time            TIMESTAMP                             NOT NULL,
    max_wait_time          INTEGER,
    special_requests       TEXT,
    estimated_pickup_time  TIMESTAMP,
    actual_pickup_time     TIMESTAMP,
    estimated_dropoff_time TIMESTAMP,
    actual_dropoff_time    TIMESTAMP,
    created_at             TIMESTAMP   DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Add indexes for shared_ride_requests
CREATE INDEX idx_shared_ride_requests_share_ride ON shared_ride_requests (shared_ride_id);
CREATE INDEX idx_shared_ride_requests_rider ON shared_ride_requests (rider_id);
CREATE INDEX idx_shared_ride_requests_status ON shared_ride_requests (status);
CREATE INDEX idx_shared_ride_requests_pickup_time ON shared_ride_requests (pickup_time);
CREATE INDEX idx_shared_ride_requests_pickup_location ON shared_ride_requests (pickup_location_id);
CREATE INDEX idx_shared_ride_requests_dropoff_location ON shared_ride_requests (dropoff_location_id);

-- Add constraints for shared_ride_requests
ALTER TABLE shared_ride_requests
    ADD CONSTRAINT chk_shared_ride_request_status
        CHECK (status IN ('PENDING', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'));
ALTER TABLE shared_ride_requests
    ADD CONSTRAINT chk_fare_amount
        CHECK (fare_amount >= 0);
ALTER TABLE shared_ride_requests
    ADD CONSTRAINT chk_original_fare
        CHECK (original_fare IS NULL OR original_fare >= 0);
ALTER TABLE shared_ride_requests
    ADD CONSTRAINT chk_discount_amount
        CHECK (discount_amount IS NULL OR discount_amount >= 0);
ALTER TABLE shared_ride_requests
    ADD CONSTRAINT chk_max_wait_time
        CHECK (max_wait_time IS NULL OR max_wait_time > 0);

-- Create ai_matching_logs table
CREATE TABLE ai_matching_logs
(
    log_id                  SERIAL PRIMARY KEY,
    shared_ride_request_id  INTEGER                             NOT NULL REFERENCES shared_ride_requests (shared_ride_request_id) ON DELETE CASCADE,
    algorithm_version       VARCHAR(50),
    request_location        TEXT,
    search_radius_km        REAL,
    available_drivers_count INTEGER,
    matching_factors        TEXT,
    potential_matches       TEXT,
    selected_driver_id      INTEGER                             NOT NULL REFERENCES driver_profiles (driver_id),
    matching_score          REAL,
    processing_time_ms      INTEGER,
    success                 BOOLEAN,
    failure_reason          VARCHAR(500),
    created_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Add indexes for ai_matching_logs
CREATE INDEX idx_ai_matching_logs_request ON ai_matching_logs (shared_ride_request_id);
CREATE INDEX idx_ai_matching_logs_driver ON ai_matching_logs (selected_driver_id);
CREATE INDEX idx_ai_matching_logs_success ON ai_matching_logs (success);
CREATE INDEX idx_ai_matching_logs_created_at ON ai_matching_logs (created_at);

-- Add constraints for ai_matching_logs
ALTER TABLE ai_matching_logs
    ADD CONSTRAINT chk_search_radius
        CHECK (search_radius_km IS NULL OR search_radius_km > 0);
ALTER TABLE ai_matching_logs
    ADD CONSTRAINT chk_available_drivers_count
        CHECK (available_drivers_count IS NULL OR available_drivers_count >= 0);
ALTER TABLE ai_matching_logs
    ADD CONSTRAINT chk_matching_score
        CHECK (matching_score IS NULL OR (matching_score >= 0 AND matching_score <= 1));
ALTER TABLE ai_matching_logs
    ADD CONSTRAINT chk_processing_time
        CHECK (processing_time_ms IS NULL OR processing_time_ms >= 0);

-- Create transactions table
CREATE TABLE transactions
(
    txn_id         bigserial PRIMARY KEY,
    group_id       uuid,
    type           varchar,
    direction      varchar,
    actor_kind     varchar,
    actor_user_id  int,
    system_wallet  varchar,
    amount         decimal(18, 2) NOT NULL,
    currency       char(3)   DEFAULT 'VND',
    booking_id     bigint,
    rider_user_id  int,
    driver_user_id int,
    psp_ref        varchar,
    status         varchar,
    before_avail   decimal(18, 2),
    after_avail    decimal(18, 2),
    before_pending decimal(18, 2),
    after_pending  decimal(18, 2),
    created_at     timestamp DEFAULT NOW(),
    note           varchar,
    CONSTRAINT txn_amount_positive CHECK (amount > 0),
    CONSTRAINT txn_type_allowed CHECK (
        type IN ('TOPUP', 'HOLD_CREATE', 'HOLD_RELEASE', 'CAPTURE_FARE',
                 'PAYOUT_SUCCESS', 'PAYOUT_FAILED', 'PROMO_CREDIT', 'ADJUSTMENT')
        ),
    CONSTRAINT txn_direction_allowed CHECK (
        direction IN ('IN', 'OUT', 'INTERNAL')
        ),
    CONSTRAINT txn_actor_kind_allowed CHECK (
        actor_kind IN ('USER', 'SYSTEM', 'PSP')
        ),
    CONSTRAINT txn_status_allowed CHECK (
        status IN ('PENDING', 'SUCCESS', 'FAILED', 'REVERSED')
        ),
    CONSTRAINT txn_system_wallet_allowed CHECK (
        system_wallet IS NULL OR system_wallet IN ('MASTER', 'COMMISSION', 'PROMO')
        ),
    CONSTRAINT txn_actor_user_presence CHECK (
        (actor_kind = 'USER' AND actor_user_id IS NOT NULL)
            OR (actor_kind <> 'USER' AND actor_user_id IS NULL)
        ),
    CONSTRAINT txn_system_wallet_presence CHECK (
        (actor_kind = 'SYSTEM' AND system_wallet IS NOT NULL)
            OR (actor_kind <> 'SYSTEM' AND system_wallet IS NULL)
        ),
    CONSTRAINT txn_snapshots_only_for_user CHECK (
        (actor_kind = 'USER')
            OR (before_avail IS NULL AND after_avail IS NULL
            AND before_pending IS NULL AND after_pending IS NULL)
        ),
    CONSTRAINT txn_booking_required_for_ride CHECK (
        (type IN ('HOLD_CREATE', 'HOLD_RELEASE', 'CAPTURE_FARE') AND booking_id IS NOT NULL)
            OR (type NOT IN ('HOLD_CREATE', 'HOLD_RELEASE', 'CAPTURE_FARE'))
        ),
    CONSTRAINT txn_capture_role_alignment CHECK (
        type <> 'CAPTURE_FARE' OR (
            (actor_kind = 'USER' AND direction = 'OUT' AND rider_user_id = actor_user_id)
                OR (actor_kind = 'USER' AND direction = 'IN' AND driver_user_id = actor_user_id)
                OR (actor_kind = 'SYSTEM' AND system_wallet = 'COMMISSION' AND direction = 'IN')
            )
        ),
    CONSTRAINT txn_type_combo_valid CHECK (
        CASE type
            WHEN 'TOPUP' THEN
                (
                    (actor_kind = 'SYSTEM' AND system_wallet = 'MASTER' AND direction = 'IN')
                        OR
                    (actor_kind = 'USER' AND direction = 'IN')
                    )
            WHEN 'HOLD_CREATE' THEN (actor_kind = 'USER' AND direction = 'INTERNAL')
            WHEN 'HOLD_RELEASE' THEN (actor_kind = 'USER' AND direction = 'INTERNAL')
            WHEN 'CAPTURE_FARE' THEN (
                (actor_kind = 'USER' AND direction IN ('IN', 'OUT'))
                    OR
                (actor_kind = 'SYSTEM' AND system_wallet = 'COMMISSION' AND direction = 'IN')
                )
            WHEN 'PAYOUT_SUCCESS' THEN (
                (actor_kind = 'USER' AND direction = 'OUT')
                    OR
                (actor_kind = 'SYSTEM' AND system_wallet = 'MASTER' AND direction = 'OUT')
                )
            WHEN 'PAYOUT_FAILED' THEN TRUE
            WHEN 'PROMO_CREDIT' THEN (
                (actor_kind = 'SYSTEM' AND system_wallet = 'PROMO' AND direction = 'OUT')
                    OR
                (actor_kind = 'USER' AND direction = 'IN')
                )
            WHEN 'ADJUSTMENT' THEN TRUE
            END
        ),
    CONSTRAINT txn_status_by_type CHECK (
        CASE type
            WHEN 'TOPUP' THEN status IN ('PENDING', 'SUCCESS', 'FAILED', 'REVERSED')
            WHEN 'PAYOUT_SUCCESS' THEN status = 'SUCCESS'
            WHEN 'PAYOUT_FAILED' THEN status = 'FAILED'
            ELSE status = 'SUCCESS'
            END
        ),
    CONSTRAINT fk_txn_actor_user FOREIGN KEY (actor_user_id) REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_txn_rider_user FOREIGN KEY (rider_user_id) REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_txn_driver_user FOREIGN KEY (driver_user_id) REFERENCES users (user_id) ON DELETE RESTRICT
);

CREATE INDEX idx_txn_user
    ON transactions (actor_kind, actor_user_id, status, created_at);

CREATE INDEX idx_txn_type
    ON transactions (type, status, created_at);

CREATE INDEX idx_txn_system
    ON transactions (actor_kind, system_wallet, status, created_at);

CREATE INDEX idx_txn_booking
    ON transactions (booking_id, created_at);

CREATE INDEX idx_txn_group
    ON transactions (group_id);

CREATE INDEX idx_txn_driver_income
    ON transactions (driver_user_id, type, status, created_at);

CREATE INDEX idx_txn_rider_spend
    ON transactions (rider_user_id, type, status, created_at);


CREATE TABLE IF NOT EXISTS refresh_tokens
(
    id         serial PRIMARY KEY,
    user_id    int       NOT NULL REFERENCES users (user_id),
    token      varchar   NOT NULL UNIQUE,
    expires_at timestamp NOT NULL,
    created_at timestamp DEFAULT now(),
    revoked    boolean   DEFAULT false
);

-- Create emergency_contacts table
CREATE TABLE emergency_contacts
(
    contact_id   SERIAL PRIMARY KEY,
    user_id      INTEGER      NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    name         VARCHAR(255) NOT NULL,
    phone        VARCHAR(20)  NOT NULL,
    relationship VARCHAR(50),
    is_primary   BOOLEAN   DEFAULT false,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Add indexes for emergency_contacts
CREATE INDEX idx_emergency_contacts_user ON emergency_contacts (user_id);
CREATE INDEX idx_emergency_contacts_primary ON emergency_contacts (is_primary);

-- Add constraints for emergency_contacts
ALTER TABLE emergency_contacts
    ADD CONSTRAINT chk_phone_format
        CHECK (phone ~ '^[\+]?[0-9\-\(\)\s]+$');

-- Ensure only one primary contact per user
CREATE UNIQUE INDEX idx_emergency_contacts_user_primary
    ON emergency_contacts (user_id)
    WHERE is_primary = true;

-- Create file_uploads table
CREATE TABLE file_uploads
(
    file_id             SERIAL PRIMARY KEY,
    user_id             INTEGER      NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    file_type           VARCHAR(50)  NOT NULL,
    original_filename   VARCHAR(255) NOT NULL,
    stored_filename     VARCHAR(255) NOT NULL,
    file_path           VARCHAR(500) NOT NULL,
    file_size           INTEGER,
    mime_type           VARCHAR(100),
    upload_status       VARCHAR(30) DEFAULT 'UPLOADED',
    verification_status VARCHAR(30) DEFAULT 'PENDING',
    created_at          TIMESTAMP   DEFAULT CURRENT_TIMESTAMP
);

-- Add indexes for file_uploads
CREATE INDEX idx_file_uploads_user ON file_uploads (user_id);
CREATE INDEX idx_file_uploads_type ON file_uploads (file_type);
CREATE INDEX idx_file_uploads_upload_status ON file_uploads (upload_status);
CREATE INDEX idx_file_uploads_verification_status ON file_uploads (verification_status);
CREATE INDEX idx_file_uploads_created_at ON file_uploads (created_at);

-- Add constraints for file_uploads
ALTER TABLE file_uploads
    ADD CONSTRAINT chk_file_type
        CHECK (file_type IN
               ('LICENSE', 'IDENTITY_CARD', 'PASSPORT', 'VEHICLE_REGISTRATION', 'INSURANCE', 'PROFILE_PHOTO'));
ALTER TABLE file_uploads
    ADD CONSTRAINT chk_upload_status
        CHECK (upload_status IN ('UPLOADING', 'UPLOADED', 'FAILED', 'DELETED'));
ALTER TABLE file_uploads
    ADD CONSTRAINT chk_verification_status
        CHECK (verification_status IN ('PENDING', 'VERIFIED', 'REJECTED', 'EXPIRED'));
ALTER TABLE file_uploads
    ADD CONSTRAINT chk_file_size
        CHECK (file_size IS NULL OR file_size > 0);

-- Create messages table
CREATE TABLE messages
(
    message_id             SERIAL PRIMARY KEY,
    sender_id              INTEGER                               NOT NULL,
    receiver_id            INTEGER                               NOT NULL,
    shared_ride_request_id INTEGER                               NOT NULL REFERENCES shared_ride_requests (shared_ride_request_id) ON DELETE CASCADE,
    conversation_id        VARCHAR(100),
    message_type           VARCHAR(20) DEFAULT 'TEXT',
    content                TEXT,
    metadata               TEXT,
    is_read                BOOLEAN     DEFAULT false,
    read_at                TIMESTAMP,
    sent_at                TIMESTAMP   DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Add indexes for messages
CREATE INDEX idx_messages_sender ON messages (sender_id);
CREATE INDEX idx_messages_receiver ON messages (receiver_id);
CREATE INDEX idx_messages_shared_ride_request ON messages (shared_ride_request_id);
CREATE INDEX idx_messages_conversation ON messages (conversation_id);
CREATE INDEX idx_messages_sent_at ON messages (sent_at);
CREATE INDEX idx_messages_is_read ON messages (is_read);
CREATE INDEX idx_messages_conversation_participants ON messages (sender_id, receiver_id);

-- Add constraints for messages
ALTER TABLE messages
    ADD CONSTRAINT chk_message_type
        CHECK (message_type IN ('TEXT', 'IMAGE', 'LOCATION', 'SYSTEM', 'NOTIFICATION'));
ALTER TABLE messages
    ADD CONSTRAINT chk_sender_receiver_different
        CHECK (sender_id != receiver_id);

-- Add foreign key constraints for sender and receiver
ALTER TABLE messages
    ADD CONSTRAINT fk_messages_sender
        FOREIGN KEY (sender_id) REFERENCES users (user_id) ON DELETE CASCADE;
ALTER TABLE messages
    ADD CONSTRAINT fk_messages_receiver
        FOREIGN KEY (receiver_id) REFERENCES users (user_id) ON DELETE CASCADE;

-- Create notifications table
CREATE TABLE notifications
(
    notif_id        SERIAL PRIMARY KEY,
    user_id         INTEGER      NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    type            VARCHAR(50)  NOT NULL,
    title           VARCHAR(255) NOT NULL,
    message         TEXT,
    payload         TEXT,
    priority        VARCHAR(20) DEFAULT 'NORMAL',
    delivery_method VARCHAR(30) DEFAULT 'PUSH',
    is_read         BOOLEAN     DEFAULT false,
    read_at         TIMESTAMP,
    sent_at         TIMESTAMP,
    expires_at      TIMESTAMP,
    created_at      TIMESTAMP   DEFAULT CURRENT_TIMESTAMP
);

-- Add indexes for notifications
CREATE INDEX idx_notifications_user ON notifications (user_id);
CREATE INDEX idx_notifications_type ON notifications (type);
CREATE INDEX idx_notifications_is_read ON notifications (is_read);
CREATE INDEX idx_notifications_created_at ON notifications (created_at);
CREATE INDEX idx_notifications_expires_at ON notifications (expires_at);
CREATE INDEX idx_notifications_priority ON notifications (priority);

-- Add constraints for notifications
ALTER TABLE notifications
    ADD CONSTRAINT chk_notification_type
        CHECK (type IN
               ('RIDE_REQUEST', 'RIDE_CONFIRMED', 'RIDE_STARTED', 'RIDE_COMPLETED', 'PAYMENT', 'PROMOTION', 'SYSTEM',
                'EMERGENCY'));
ALTER TABLE notifications
    ADD CONSTRAINT chk_priority
        CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT'));
ALTER TABLE notifications
    ADD CONSTRAINT chk_delivery_method
        CHECK (delivery_method IN ('PUSH', 'EMAIL', 'SMS', 'IN_APP'));

-- Create ratings table
CREATE TABLE ratings
(
    rating_id              SERIAL PRIMARY KEY,
    shared_ride_request_id INTEGER                               NOT NULL REFERENCES shared_ride_requests (shared_ride_request_id) ON DELETE CASCADE,
    rater_id               INTEGER                               NOT NULL REFERENCES rider_profiles (rider_id) ON DELETE CASCADE,
    target_id              INTEGER                               NOT NULL REFERENCES driver_profiles (driver_id) ON DELETE CASCADE,
    rating_type            VARCHAR(20) DEFAULT 'GENERAL',
    score                  INTEGER                               NOT NULL,
    comment                TEXT,
    safety_score           INTEGER,
    punctuality_score      INTEGER,
    communication_score    INTEGER,
    created_at             TIMESTAMP   DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Add indexes for ratings
CREATE INDEX idx_ratings_shared_ride_request ON ratings (shared_ride_request_id);
CREATE INDEX idx_ratings_rater ON ratings (rater_id);
CREATE INDEX idx_ratings_target ON ratings (target_id);
CREATE INDEX idx_ratings_score ON ratings (score);
CREATE INDEX idx_ratings_created_at ON ratings (created_at);

-- Add constraints for ratings
ALTER TABLE ratings
    ADD CONSTRAINT chk_rating_type
        CHECK (rating_type IN ('GENERAL', 'RIDER_TO_DRIVER', 'DRIVER_TO_RIDER'));
ALTER TABLE ratings
    ADD CONSTRAINT chk_score_range
        CHECK (score >= 1 AND score <= 5);
ALTER TABLE ratings
    ADD CONSTRAINT chk_safety_score_range
        CHECK (safety_score IS NULL OR (safety_score >= 1 AND safety_score <= 5));
ALTER TABLE ratings
    ADD CONSTRAINT chk_punctuality_score_range
        CHECK (punctuality_score IS NULL OR (punctuality_score >= 1 AND punctuality_score <= 5));
ALTER TABLE ratings
    ADD CONSTRAINT chk_communication_score_range
        CHECK (communication_score IS NULL OR (communication_score >= 1 AND communication_score <= 5));

-- Ensure one rating per ride request per rater-target pair
CREATE UNIQUE INDEX idx_ratings_unique_rating
    ON ratings (shared_ride_request_id, rater_id, target_id);

-- Create sos_alerts table
CREATE TABLE sos_alerts
(
    sos_id           SERIAL PRIMARY KEY,
    share_ride_id    INTEGER                               NOT NULL REFERENCES shared_rides (shared_ride_id) ON DELETE CASCADE,
    triggered_by     INTEGER                               NOT NULL,
    alert_type       VARCHAR(30) DEFAULT 'EMERGENCY',
    current_lat      DOUBLE PRECISION,
    current_lng      DOUBLE PRECISION,
    contact_info     TEXT,
    description      TEXT,
    status           VARCHAR(20) DEFAULT 'ACTIVE',
    acknowledged_by  INTEGER,
    acknowledged_at  TIMESTAMP,
    resolved_at      TIMESTAMP,
    resolution_notes TEXT,
    created_at       TIMESTAMP   DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Add indexes for sos_alerts
CREATE INDEX idx_sos_alerts_share_ride ON sos_alerts (share_ride_id);
CREATE INDEX idx_sos_alerts_triggered_by ON sos_alerts (triggered_by);
CREATE INDEX idx_sos_alerts_status ON sos_alerts (status);
CREATE INDEX idx_sos_alerts_created_at ON sos_alerts (created_at);
CREATE INDEX idx_sos_alerts_location ON sos_alerts (current_lat, current_lng);

-- Add constraints for sos_alerts
ALTER TABLE sos_alerts
    ADD CONSTRAINT chk_alert_type
        CHECK (alert_type IN ('EMERGENCY', 'ACCIDENT', 'BREAKDOWN', 'SAFETY_CONCERN', 'OTHER'));
ALTER TABLE sos_alerts
    ADD CONSTRAINT chk_sos_status
        CHECK (status IN ('ACTIVE', 'ACKNOWLEDGED', 'RESOLVED', 'FALSE_ALARM'));
ALTER TABLE sos_alerts
    ADD CONSTRAINT chk_lat_range
        CHECK (current_lat IS NULL OR (current_lat >= -90 AND current_lat <= 90));
ALTER TABLE sos_alerts
    ADD CONSTRAINT chk_lng_range
        CHECK (current_lng IS NULL OR (current_lng >= -180 AND current_lng <= 180));

-- Add foreign key constraints for triggered_by and acknowledged_by
ALTER TABLE sos_alerts
    ADD CONSTRAINT fk_sos_alerts_triggered_by
        FOREIGN KEY (triggered_by) REFERENCES users (user_id) ON DELETE CASCADE;
ALTER TABLE sos_alerts
    ADD CONSTRAINT fk_sos_alerts_acknowledged_by
        FOREIGN KEY (acknowledged_by) REFERENCES users (user_id) ON DELETE SET NULL;

-- Create user_promotions table
CREATE TABLE user_promotions
(
    user_promotion_id      SERIAL PRIMARY KEY,
    user_id                INTEGER NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    promotion_id           INTEGER NOT NULL REFERENCES promotions (promotion_id) ON DELETE CASCADE,
    shared_ride_request_id INTEGER NOT NULL REFERENCES shared_ride_requests (shared_ride_request_id) ON DELETE CASCADE,
    used_at                TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    discount_applied       DECIMAL(10, 2)
);

-- Add indexes for user_promotions
CREATE INDEX idx_user_promotions_user ON user_promotions (user_id);
CREATE INDEX idx_user_promotions_promotion ON user_promotions (promotion_id);
CREATE INDEX idx_user_promotions_shared_ride_request ON user_promotions (shared_ride_request_id);
CREATE INDEX idx_user_promotions_used_at ON user_promotions (used_at);

-- Add constraints for user_promotions
ALTER TABLE user_promotions
    ADD CONSTRAINT chk_discount_applied
        CHECK (discount_applied IS NULL OR discount_applied >= 0);

-- Ensure one promotion usage per ride request
CREATE UNIQUE INDEX idx_user_promotions_unique_usage
    ON user_promotions (shared_ride_request_id, promotion_id);
