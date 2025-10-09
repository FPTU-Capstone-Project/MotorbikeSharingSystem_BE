ALTER TABLE driver_profiles
    DROP COLUMN IF EXISTS commission_rate;

INSERT INTO wallets (user_id, shadow_balance, pending_balance, total_topped_up, total_spent, is_active, last_synced_at,
                     created_at, updated_at)
SELECT u.user_id,
       300000, -- shadow_balance
       0,      -- pending_balance
       300000, -- total_topped_up
       0,      -- total_spent
       true,   -- is_active
       now(),  -- last_synced_at
       now(),  -- created_at
       now()   -- updated_at
FROM users u
WHERE u.email = 'john.doe@example.com'
  AND NOT EXISTS (SELECT 1
                  FROM wallets w
                  WHERE w.user_id = u.user_id);

WITH transaction_group AS (SELECT '550e8400-e29b-41d4-a716-446655440001'::uuid AS group_id),
     system_txn AS (
         INSERT INTO transactions (
                                   type,
                                   group_id,
                                   direction,
                                   actor_kind,
                                   actor_user_id,
                                   system_wallet,
                                   amount,
                                   currency,
                                   status,
                                   psp_ref,
                                   note,
                                   created_at
             )
             SELECT 'TOPUP',
                    group_id,
                    'IN',
                    'SYSTEM',
                    NULL,
                    'MASTER',
                    300000,
                    'VND',
                    'SUCCESS',
                    'PSP-TEST-300K-001',
                    'PSP Inflow - Test wallet funding',
                    now()
             FROM transaction_group
             RETURNING group_id)
INSERT
INTO transactions (type,
                   group_id,
                   direction,
                   actor_kind,
                   actor_user_id,
                   rider_user_id,
                   amount,
                   currency,
                   status,
                   before_avail,
                   after_avail,
                   before_pending,
                   after_pending,
                   psp_ref,
                   note,
                   created_at)
SELECT 'TOPUP',
       g.group_id,
       'IN',
       'USER',
       2, -- actor_user_id
       1, -- rider_user_id
       300000,
       'VND',
       'SUCCESS',
       0,
       300000,
       0,
       0,
       'PSP-TEST-300K-001',
       'Test wallet funding - 300,000 VND top-up',
       now()
FROM system_txn g;
