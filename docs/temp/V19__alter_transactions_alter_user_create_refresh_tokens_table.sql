DROP INDEX IF EXISTS idx_users_is_active;

ALTER TABLE users
    ADD COLUMN token_version int NOT NULL DEFAULT 0;

ALTER TABLE users
    DROP COLUMN IF EXISTS is_active;

ALTER TABLE users
    ADD COLUMN user_status varchar(20) NOT NULL DEFAULT 'pending';

ALTER TABLE users
    ADD CONSTRAINT user_status_allowed CHECK (
        user_status IN ('pending', 'active', 'suspended', 'rejected', 'deleted')
        );


DROP TABLE IF EXISTS transactions;

CREATE TABLE transactions (
                              txn_id           bigserial PRIMARY KEY,
                              group_id         uuid,
                              type             varchar,
                              direction        varchar,
                              actor_kind       varchar,
                              actor_user_id    int,
                              system_wallet    varchar,
                              amount           decimal(18,2) NOT NULL,
                              currency         char(3) DEFAULT 'VND',
                              booking_id       bigint,
                              rider_user_id    int,
                              driver_user_id   int,
                              psp_ref          varchar,
                              status           varchar,
                              before_avail     decimal(18,2),
                              after_avail      decimal(18,2),
                              before_pending   decimal(18,2),
                              after_pending    decimal(18,2),
                              created_at       timestamp DEFAULT NOW(),
                              note             varchar,
                              CONSTRAINT txn_amount_positive CHECK (amount > 0),
                              CONSTRAINT txn_type_allowed CHECK (
                                  type IN ('TOPUP','HOLD_CREATE','HOLD_RELEASE','CAPTURE_FARE',
                                           'PAYOUT_SUCCESS','PAYOUT_FAILED','PROMO_CREDIT','ADJUSTMENT')
                                  ),
                              CONSTRAINT txn_direction_allowed CHECK (
                                  direction IN ('IN','OUT','INTERNAL')
                                  ),
                              CONSTRAINT txn_actor_kind_allowed CHECK (
                                  actor_kind IN ('USER','SYSTEM','PSP')
                                  ),
                              CONSTRAINT txn_status_allowed CHECK (
                                  status IN ('PENDING','SUCCESS','FAILED','REVERSED')
                                  ),
                              CONSTRAINT txn_system_wallet_allowed CHECK (
                                  system_wallet IS NULL OR system_wallet IN ('MASTER','COMMISSION','PROMO')
                                  ),
                              CONSTRAINT txn_actor_user_presence CHECK (
                                  (actor_kind='USER'   AND actor_user_id IS NOT NULL)
                                      OR (actor_kind<>'USER' AND actor_user_id IS NULL)
                                  ),
                              CONSTRAINT txn_system_wallet_presence CHECK (
                                  (actor_kind='SYSTEM'   AND system_wallet IS NOT NULL)
                                      OR (actor_kind<>'SYSTEM' AND system_wallet IS NULL)
                                  ),
                              CONSTRAINT txn_snapshots_only_for_user CHECK (
                                  (actor_kind='USER')
                                      OR (before_avail IS NULL AND after_avail IS NULL
                                      AND before_pending IS NULL AND after_pending IS NULL)
                                  ),
                              CONSTRAINT txn_booking_required_for_ride CHECK (
                                  (type IN ('HOLD_CREATE','HOLD_RELEASE','CAPTURE_FARE') AND booking_id IS NOT NULL)
                                      OR (type NOT IN ('HOLD_CREATE','HOLD_RELEASE','CAPTURE_FARE'))
                                  ),
                              CONSTRAINT txn_capture_role_alignment CHECK (
                                  type <> 'CAPTURE_FARE' OR (
                                      (actor_kind='USER'   AND direction='OUT' AND rider_user_id  = actor_user_id)
                                          OR (actor_kind='USER'   AND direction='IN'  AND driver_user_id = actor_user_id)
                                          OR (actor_kind='SYSTEM' AND system_wallet='COMMISSION' AND direction='IN')
                                      )
                                  ),
                              CONSTRAINT txn_type_combo_valid CHECK (
                                  CASE type
                                      WHEN 'TOPUP' THEN
                                          (
                                              (actor_kind='SYSTEM' AND system_wallet='MASTER' AND direction='IN')
                                                  OR
                                              (actor_kind='USER'   AND direction='IN')
                                              )
                                      WHEN 'HOLD_CREATE'  THEN (actor_kind='USER'   AND direction='INTERNAL')
                                      WHEN 'HOLD_RELEASE' THEN (actor_kind='USER'   AND direction='INTERNAL')
                                      WHEN 'CAPTURE_FARE' THEN (
                                          (actor_kind='USER'   AND direction IN ('IN','OUT'))
                                              OR
                                          (actor_kind='SYSTEM' AND system_wallet='COMMISSION' AND direction='IN')
                                          )
                                      WHEN 'PAYOUT_SUCCESS' THEN (
                                          (actor_kind='USER'   AND direction='OUT')
                                              OR
                                          (actor_kind='SYSTEM' AND system_wallet='MASTER' AND direction='OUT')
                                          )
                                      WHEN 'PAYOUT_FAILED'  THEN TRUE
                                      WHEN 'PROMO_CREDIT'   THEN (
                                          (actor_kind='SYSTEM' AND system_wallet='PROMO' AND direction='OUT')
                                              OR
                                          (actor_kind='USER'   AND direction='IN')
                                          )
                                      WHEN 'ADJUSTMENT'     THEN TRUE
                                      END
                                  ),
                              CONSTRAINT txn_status_by_type CHECK (
                                  CASE type
                                      WHEN 'TOPUP'          THEN status IN ('PENDING','SUCCESS','FAILED','REVERSED')
                                      WHEN 'PAYOUT_SUCCESS' THEN status = 'SUCCESS'
                                      WHEN 'PAYOUT_FAILED'  THEN status = 'FAILED'
                                      ELSE status = 'SUCCESS'
                                      END
                                  ),
                              CONSTRAINT fk_txn_actor_user   FOREIGN KEY (actor_user_id)  REFERENCES users(user_id) ON DELETE RESTRICT,
                              CONSTRAINT fk_txn_rider_user   FOREIGN KEY (rider_user_id)  REFERENCES users(user_id) ON DELETE RESTRICT,
                              CONSTRAINT fk_txn_driver_user  FOREIGN KEY (driver_user_id) REFERENCES users(user_id) ON DELETE RESTRICT
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


CREATE TABLE IF NOT EXISTS refresh_tokens (
    id serial PRIMARY KEY,
    user_id int NOT NULL REFERENCES users(user_id),
    token varchar NOT NULL UNIQUE,
    expires_at timestamp NOT NULL,
    created_at timestamp DEFAULT now(),
    revoked boolean DEFAULT false
);