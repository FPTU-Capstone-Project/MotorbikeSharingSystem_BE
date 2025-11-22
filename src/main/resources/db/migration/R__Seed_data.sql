-- R__Seed_data.sql
-- Mock data for Motorbike Sharing System
-- This data is completely different from V1_Initialize data

-- Truncate tables in reverse order of dependencies
TRUNCATE TABLE verifications CASCADE;

TRUNCATE TABLE emergency_contacts CASCADE;

TRUNCATE TABLE user_reports CASCADE;

TRUNCATE TABLE shared_rides CASCADE;

TRUNCATE TABLE vehicles CASCADE;

TRUNCATE TABLE promotions CASCADE;

TRUNCATE TABLE locations CASCADE;

TRUNCATE TABLE wallets CASCADE;

TRUNCATE TABLE driver_profiles CASCADE;

TRUNCATE TABLE rider_profiles CASCADE;

TRUNCATE TABLE users CASCADE;

-- Reset sequences to start from 1
ALTER SEQUENCE users_user_id_seq RESTART WITH 1;

ALTER SEQUENCE locations_location_id_seq RESTART WITH 1;

ALTER SEQUENCE vehicles_vehicle_id_seq RESTART WITH 1;

ALTER SEQUENCE verifications_verification_id_seq RESTART WITH 1;

ALTER SEQUENCE shared_rides_shared_ride_id_seq RESTART WITH 1;

ALTER SEQUENCE emergency_contacts_contact_id_seq RESTART WITH 1;

ALTER SEQUENCE promotions_promotion_id_seq RESTART WITH 1;

ALTER SEQUENCE user_reports_report_id_seq RESTART WITH 1;

ALTER TABLE driver_profiles
    ADD COLUMN IF NOT EXISTS suspended_at TIMESTAMP;

ALTER TABLE driver_profiles
    ADD COLUMN IF NOT EXISTS activated_at TIMESTAMP;

ALTER TABLE rider_profiles
    ADD COLUMN IF NOT EXISTS suspended_at TIMESTAMP;

ALTER TABLE rider_profiles
    ADD COLUMN IF NOT EXISTS activated_at TIMESTAMP;

ALTER TABLE wallets
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- =====================================================
-- 0. ADMIN USER (Must be first!)
-- =====================================================
-- Password for admin: "Password1!" (hashed with BCrypt)
INSERT INTO users (email,
                   phone,
                   password_hash,
                   full_name,
                   student_id,
                   user_type,
                   profile_photo_url,
                   email_verified,
                   phone_verified,
                   token_version,
                   status,
                   created_at,
                   updated_at)
VALUES ('admin@mssus.com',
        '0901234567',
        '$2a$10$BaeiCK1yapOvw.WrcaGb1OqHVOqqSD4TkEAvhHThm.F85BvxYH7ru',
        'System Administrator',
        NULL,
        'ADMIN',
        'https://i.pravatar.cc/150?img=1',
        true,
        true,
        1,
        'ACTIVE',
        NOW(),
        NOW());

-- =====================================================
-- 1. USERS (Different from V1_Initialize)
-- =====================================================
-- Password for all users: "Password1!" (hashed with BCrypt)
INSERT INTO users (email,
                   phone,
                   password_hash,
                   full_name,
                   student_id,
                   user_type,
                   profile_photo_url,
                   email_verified,
                   phone_verified,
                   token_version,
                   status,
                   created_at,
                   updated_at)
VALUES
    -- Student Users (will be riders)
    ('nguyen.van.a@student.hcmut.edu.vn',
     '0909111222',
     '$2a$10$BaeiCK1yapOvw.WrcaGb1OqHVOqqSD4TkEAvhHThm.F85BvxYH7ru',
     'Nguyen Van A',
     '2110001',
     'USER',
     'https://i.pravatar.cc/150?img=11',
     true,
     true,
     1,
     'ACTIVE',
     NOW(),
     NOW()),
    ('tran.thi.b@student.hcmut.edu.vn',
     '0909222333',
     '$2a$10$BaeiCK1yapOvw.WrcaGb1OqHVOqqSD4TkEAvhHThm.F85BvxYH7ru',
     'Tran Thi B',
     '2110002',
     'USER',
     'https://i.pravatar.cc/150?img=12',
     true,
     true,
     1,
     'ACTIVE',
     NOW(),
     NOW()),
    ('le.van.c@student.hcmut.edu.vn',
     '0909333444',
     '$2a$10$BaeiCK1yapOvw.WrcaGb1OqHVOqqSD4TkEAvhHThm.F85BvxYH7ru',
     'Le Van C',
     '2110003',
     'USER',
     'https://i.pravatar.cc/150?img=13',
     true,
     true,
     1,
     'ACTIVE',
     NOW(),
     NOW()),
    ('pham.thi.d@student.hcmut.edu.vn',
     '0909444555',
     '$2a$10$BaeiCK1yapOvw.WrcaGb1OqHVOqqSD4TkEAvhHThm.F85BvxYH7ru',
     'Pham Thi D',
     '2110004',
     'USER',
     'https://i.pravatar.cc/150?img=14',
     true,
     true,
     1,
     'ACTIVE',
     NOW(),
     NOW()),
    ('hoang.van.e@student.hcmut.edu.vn',
     '0909555666',
     '$2a$10$BaeiCK1yapOvw.WrcaGb1OqHVOqqSD4TkEAvhHThm.F85BvxYH7ru',
     'Hoang Van E',
     '2110005',
     'USER',
     'https://i.pravatar.cc/150?img=15',
     true,
     true,
     1,
     'ACTIVE',
     NOW(),
     NOW()),

-- Driver Users
    ('vo.van.f@student.hcmut.edu.vn',
     '0909666777',
     '$2a$10$BaeiCK1yapOvw.WrcaGb1OqHVOqqSD4TkEAvhHThm.F85BvxYH7ru',
     'Vo Van F',
     '2110006',
     'USER',
     'https://i.pravatar.cc/150?img=16',
     true,
     true,
     1,
     'ACTIVE',
     NOW(),
     NOW()),
    ('dang.thi.g@student.hcmut.edu.vn',
     '0909777888',
     '$2a$10$BaeiCK1yapOvw.WrcaGb1OqHVOqqSD4TkEAvhHThm.F85BvxYH7ru',
     'Dang Thi G',
     '2110007',
     'USER',
     'https://i.pravatar.cc/150?img=17',
     true,
     true,
     1,
     'ACTIVE',
     NOW(),
     NOW()),
    ('do.van.h@student.hcmut.edu.vn',
     '0909888999',
     '$2a$10$BaeiCK1yapOvw.WrcaGb1OqHVOqqSD4TkEAvhHThm.F85BvxYH7ru',
     'Do Van H',
     '2110008',
     'USER',
     'https://i.pravatar.cc/150?img=18',
     true,
     true,
     1,
     'ACTIVE',
     NOW(),
     NOW()),
    ('bui.thi.i@student.hcmut.edu.vn',
     '0909999000',
     '$2a$10$BaeiCK1yapOvw.WrcaGb1OqHVOqqSD4TkEAvhHThm.F85BvxYH7ru',
     'Bui Thi I',
     '2110009',
     'USER',
     'https://i.pravatar.cc/150?img=19',
     true,
     true,
     1,
     'ACTIVE',
     NOW(),
     NOW()),

-- Pending/Email Verifying Users
    ('truong.van.k@student.hcmut.edu.vn',
     '0909000111',
     '$2a$10$BaeiCK1yapOvw.WrcaGb1OqHVOqqSD4TkEAvhHThm.F85BvxYH7ru',
     'Truong Van K',
     '2110010',
     'USER',
     NULL,
     false,
     false,
     1,
     'EMAIL_VERIFYING',
     NOW(),
     NOW()),
    ('ngo.thi.l@student.hcmut.edu.vn',
     '0909111333',
     '$2a$10$BaeiCK1yapOvw.WrcaGb1OqHVOqqSD4TkEAvhHThm.F85BvxYH7ru',
     'Ngo Thi L',
     '2110011',
     'USER',
     NULL,
     false,
     false,
     1,
     'PENDING',
     NOW(),
     NOW());

-- =====================================================
-- 2. RIDER PROFILES
-- =====================================================
INSERT INTO rider_profiles (rider_id,
                            total_rides,
                            total_spent,
                            status,
                            preferred_payment_method,
                            created_at)
SELECT user_id, 25, 825000.00, 'ACTIVE', 'WALLET', NOW()
FROM users
WHERE email = 'nguyen.van.a@student.hcmut.edu.vn'
UNION ALL
SELECT user_id, 12, 432000.00, 'ACTIVE', 'WALLET', NOW()
FROM users
WHERE email = 'tran.thi.b@student.hcmut.edu.vn'
UNION ALL
SELECT user_id, 35, 1120000.00, 'ACTIVE', 'WALLET', NOW()
FROM users
WHERE email = 'le.van.c@student.hcmut.edu.vn'
UNION ALL
SELECT user_id, 8, 256000.00, 'ACTIVE', 'WALLET', NOW()
FROM users
WHERE email = 'pham.thi.d@student.hcmut.edu.vn'
UNION ALL
SELECT user_id, 18, 612000.00, 'ACTIVE', 'WALLET', NOW()
FROM users
WHERE email = 'hoang.van.e@student.hcmut.edu.vn'
UNION ALL
SELECT user_id, 42, 1386000.00, 'ACTIVE', 'WALLET', NOW()
FROM users
WHERE email = 'vo.van.f@student.hcmut.edu.vn'
UNION ALL
SELECT user_id, 28, 896000.00, 'ACTIVE', 'WALLET', NOW()
FROM users
WHERE email = 'dang.thi.g@student.hcmut.edu.vn';

-- =====================================================
-- 3. DRIVER PROFILES
-- =====================================================
INSERT INTO driver_profiles (driver_id,
                             license_number,
                             license_verified_at,
                             status,
                             rating_avg,
                             total_shared_rides,
                             total_earned,
                             is_available,
                             max_passengers,
                             created_at)
SELECT user_id,
       'B2-987654321',
       NOW() - INTERVAL '90 days',
       'ACTIVE',
       4.85,
       68,
       2244000.00,
       true,
       2,
       NOW()
FROM users
WHERE email = 'vo.van.f@student.hcmut.edu.vn'
UNION ALL
SELECT user_id,
       'B2-876543210',
       NOW() - INTERVAL '75 days',
       'ACTIVE',
       4.92,
       55,
       1815000.00,
       true,
       1,
       NOW()
FROM users
WHERE email = 'dang.thi.g@student.hcmut.edu.vn'
UNION ALL
SELECT user_id,
       'B2-765432109',
       NOW() - INTERVAL '60 days',
       'ACTIVE',
       4.78,
       72,
       2376000.00,
       false,
       2,
       NOW()
FROM users
WHERE email = 'do.van.h@student.hcmut.edu.vn'
UNION ALL
SELECT user_id,
       'B2-654321098',
       NOW() - INTERVAL '100 days',
       'ACTIVE',
       4.95,
       85,
       2805000.00,
       true,
       1,
       NOW()
FROM users
WHERE email = 'bui.thi.i@student.hcmut.edu.vn';

-- =====================================================
-- 5. LOCATIONS
-- =====================================================
INSERT INTO locations (name,
                       lat,
                       lng,
                       address,
                       created_at)
VALUES ('BK Campus - CS1',
        10.772015,
        106.657248,
        'CS1 Building, 268 Ly Thuong Kiet, Ward 14, District 10, HCMC',
        NOW()),
       ('BK Campus - CS2',
        10.772890,
        106.659180,
        'CS2 Building, 268 Ly Thuong Kiet, Ward 14, District 10, HCMC',
        NOW()),
       ('BK Campus - Main Library',
        10.773215,
        106.658456,
        'Main Library, 268 Ly Thuong Kiet, Ward 14, District 10, HCMC',
        NOW()),
       ('District 1 - Nguyen Hue',
        10.774410,
        106.702538,
        'Nguyen Hue Boulevard, District 1, HCMC',
        NOW()),
       ('District 1 - Opera House',
        10.776934,
        106.703098,
        'Saigon Opera House, District 1, HCMC',
        NOW()),
       ('District 3 - Turtle Lake',
        10.786230,
        106.690810,
        'Turtle Lake, District 3, HCMC',
        NOW()),
       ('Binh Thanh - Vincom',
        10.800760,
        106.716000,
        'Vincom Center, Binh Thanh District, HCMC',
        NOW()),
       ('Phu Nhuan - Coffee Street',
        10.799510,
        106.677890,
        'Phan Xich Long, Phu Nhuan District, HCMC',
        NOW()),
       ('Thu Duc - Mega Market',
        10.850120,
        106.762340,
        'Mega Market Thu Duc, Thu Duc City, HCMC',
        NOW()),
       ('Go Vap - Emart',
        10.839456,
        106.677123,
        'Emart Go Vap, Go Vap District, HCMC',
        NOW());

-- =====================================================
-- 6. VEHICLES
-- =====================================================
INSERT INTO vehicles (driver_id,
                      plate_number,
                      model,
                      color,
                      year,
                      capacity,
                      insurance_expiry,
                      last_maintenance,
                      fuel_type,
                      status,
                      verified_at,
                      created_at)
SELECT dp.driver_id,
       '59-X1 98765',
       'Honda Future',
       'Black',
       2021,
       2,
       NOW() + INTERVAL '220 days',
       NOW() - INTERVAL '12 days',
       'GASOLINE',
       'ACTIVE',
       NOW() - INTERVAL '85 days',
       NOW()
FROM driver_profiles dp
         JOIN users u ON dp.driver_id = u.user_id
WHERE u.email = 'vo.van.f@student.hcmut.edu.vn'
UNION ALL
SELECT dp.driver_id,
       '59-Y2 87654',
       'Yamaha Jupiter',
       'Blue',
       2022,
       2,
       NOW() + INTERVAL '240 days',
       NOW() - INTERVAL '8 days',
       'GASOLINE',
       'ACTIVE',
       NOW() - INTERVAL '85 days',
       NOW()
FROM driver_profiles dp
         JOIN users u ON dp.driver_id = u.user_id
WHERE u.email = 'vo.van.f@student.hcmut.edu.vn'
UNION ALL
SELECT dp.driver_id,
       '59-Z3 76543',
       'Honda Vision',
       'Red',
       2023,
       2,
       NOW() + INTERVAL '280 days',
       NOW() - INTERVAL '5 days',
       'GASOLINE',
       'ACTIVE',
       NOW() - INTERVAL '70 days',
       NOW()
FROM driver_profiles dp
         JOIN users u ON dp.driver_id = u.user_id
WHERE u.email = 'dang.thi.g@student.hcmut.edu.vn'
UNION ALL
SELECT dp.driver_id,
       '59-A4 65432',
       'Yamaha Grande',
       'White',
       2022,
       2,
       NOW() + INTERVAL '190 days',
       NOW() - INTERVAL '18 days',
       'GASOLINE',
       'ACTIVE',
       NOW() - INTERVAL '55 days',
       NOW()
FROM driver_profiles dp
         JOIN users u ON dp.driver_id = u.user_id
WHERE u.email = 'do.van.h@student.hcmut.edu.vn'
UNION ALL
SELECT dp.driver_id,
       '59-B5 54321',
       'Honda PCX',
       'Gray',
       2020,
       2,
       NOW() + INTERVAL '160 days',
       NOW() - INTERVAL '25 days',
       'GASOLINE',
       'MAINTENANCE',
       NOW() - INTERVAL '55 days',
       NOW()
FROM driver_profiles dp
         JOIN users u ON dp.driver_id = u.user_id
WHERE u.email = 'do.van.h@student.hcmut.edu.vn'
UNION ALL
SELECT dp.driver_id,
       '59-C6 43210',
       'Yamaha NVX',
       'Orange',
       2024,
       2,
       NOW() + INTERVAL '330 days',
       NOW() - INTERVAL '2 days',
       'GASOLINE',
       'ACTIVE',
       NOW() - INTERVAL '95 days',
       NOW()
FROM driver_profiles dp
         JOIN users u ON dp.driver_id = u.user_id
WHERE u.email = 'bui.thi.i@student.hcmut.edu.vn';

-- =====================================================
-- 7. VERIFICATIONS
-- =====================================================
-- Student ID Verifications - Approved
INSERT INTO verifications (user_id,
                           type,
                           status,
                           document_url,
                           document_type,
                           verified_by,
                           verified_at,
                           expires_at,
                           metadata,
                           created_at)
SELECT u1.user_id,
       'STUDENT_ID',
       'APPROVED',
       'https://cdn.mssus.com/verify/student_2110001.jpg',
       'IMAGE',
       (SELECT user_id
        FROM users
        WHERE email = 'admin@mssus.com'),
       NOW() - INTERVAL '85 days',
       NOW() + INTERVAL '1095 days',
       '{"student_id":"2110001","faculty":"Computer Science"}',
       NOW() - INTERVAL '86 days'
FROM users u1
WHERE u1.email = 'nguyen.van.a@student.hcmut.edu.vn'
UNION ALL
SELECT u1.user_id,
       'STUDENT_ID',
       'APPROVED',
       'https://cdn.mssus.com/verify/student_2110002.jpg',
       'IMAGE',
       (SELECT user_id
        FROM users
        WHERE email = 'admin@mssus.com'),
       NOW() - INTERVAL '80 days',
       NOW() + INTERVAL '1095 days',
       '{"student_id":"2110002","faculty":"Electrical Engineering"}',
       NOW() - INTERVAL '81 days'
FROM users u1
WHERE u1.email = 'tran.thi.b@student.hcmut.edu.vn'
UNION ALL
SELECT u1.user_id,
       'STUDENT_ID',
       'APPROVED',
       'https://cdn.mssus.com/verify/student_2110003.jpg',
       'IMAGE',
       (SELECT user_id
        FROM users
        WHERE email = 'admin@mssus.com'),
       NOW() - INTERVAL '75 days',
       NOW() + INTERVAL '1095 days',
       '{"student_id":"2110003","faculty":"Mechanical Engineering"}',
       NOW() - INTERVAL '76 days'
FROM users u1
WHERE u1.email = 'le.van.c@student.hcmut.edu.vn'
UNION ALL
SELECT u1.user_id,
       'STUDENT_ID',
       'APPROVED',
       'https://cdn.mssus.com/verify/student_2110004.pdf',
       'PDF',
       (SELECT user_id
        FROM users
        WHERE email = 'admin@mssus.com'),
       NOW() - INTERVAL '70 days',
       NOW() + INTERVAL '1095 days',
       '{"student_id":"2110004","faculty":"Civil Engineering"}',
       NOW() - INTERVAL '71 days'
FROM users u1
WHERE u1.email = 'pham.thi.d@student.hcmut.edu.vn'
UNION ALL
SELECT u1.user_id,
       'STUDENT_ID',
       'APPROVED',
       'https://cdn.mssus.com/verify/student_2110005.jpg',
       'IMAGE',
       (SELECT user_id
        FROM users
        WHERE email = 'admin@mssus.com'),
       NOW() - INTERVAL '65 days',
       NOW() + INTERVAL '1095 days',
       '{"student_id":"2110005","faculty":"Chemical Engineering"}',
       NOW() - INTERVAL '66 days'
FROM users u1
WHERE u1.email = 'hoang.van.e@student.hcmut.edu.vn';

-- Driver License Verifications - Approved
INSERT INTO verifications (user_id,
                           type,
                           status,
                           document_url,
                           document_type,
                           verified_by,
                           verified_at,
                           expires_at,
                           metadata,
                           created_at)
SELECT u1.user_id,
       'DRIVER_LICENSE',
       'APPROVED',
       'https://cdn.mssus.com/verify/license_B2987654321.jpg',
       'IMAGE',
       (SELECT user_id
        FROM users
        WHERE email = 'admin@mssus.com'),
       NOW() - INTERVAL '90 days',
       NOW() + INTERVAL '1095 days',
       '{"license_number":"B2-987654321","issue_date":"2018-05-15"}',
       NOW() - INTERVAL '92 days'
FROM users u1
WHERE u1.email = 'vo.van.f@student.hcmut.edu.vn'
UNION ALL
SELECT u1.user_id,
       'DRIVER_LICENSE',
       'APPROVED',
       'https://cdn.mssus.com/verify/license_B2876543210.jpg',
       'IMAGE',
       (SELECT user_id
        FROM users
        WHERE email = 'admin@mssus.com'),
       NOW() - INTERVAL '75 days',
       NOW() + INTERVAL '1095 days',
       '{"license_number":"B2-876543210","issue_date":"2019-08-20"}',
       NOW() - INTERVAL '77 days'
FROM users u1
WHERE u1.email = 'dang.thi.g@student.hcmut.edu.vn'
UNION ALL
SELECT u1.user_id,
       'DRIVER_LICENSE',
       'APPROVED',
       'https://cdn.mssus.com/verify/license_B2765432109.pdf',
       'PDF',
       (SELECT user_id
        FROM users
        WHERE email = 'admin@mssus.com'),
       NOW() - INTERVAL '60 days',
       NOW() + INTERVAL '1095 days',
       '{"license_number":"B2-765432109","issue_date":"2020-03-10"}',
       NOW() - INTERVAL '62 days'
FROM users u1
WHERE u1.email = 'do.van.h@student.hcmut.edu.vn'
UNION ALL
SELECT u1.user_id,
       'DRIVER_LICENSE',
       'APPROVED',
       'https://cdn.mssus.com/verify/license_B2654321098.jpg',
       'IMAGE',
       (SELECT user_id
        FROM users
        WHERE email = 'admin@mssus.com'),
       NOW() - INTERVAL '100 days',
       NOW() + INTERVAL '1095 days',
       '{"license_number":"B2-654321098","issue_date":"2017-11-25"}',
       NOW() - INTERVAL '102 days'
FROM users u1
WHERE u1.email = 'bui.thi.i@student.hcmut.edu.vn';

-- Vehicle Registration Verifications
INSERT INTO verifications (user_id,
                           type,
                           status,
                           document_url,
                           document_type,
                           verified_by,
                           verified_at,
                           expires_at,
                           metadata,
                           created_at)
SELECT u1.user_id,
       'VEHICLE_REGISTRATION',
       'APPROVED',
       'https://cdn.mssus.com/verify/vehicle_59X198765.pdf',
       'PDF',
       (SELECT user_id
        FROM users
        WHERE email = 'admin@mssus.com'),
       NOW() - INTERVAL '85 days',
       NOW() + INTERVAL '365 days',
       '{"plate":"59-X1 98765","model":"Honda Future"}',
       NOW() - INTERVAL '86 days'
FROM users u1
WHERE u1.email = 'vo.van.f@student.hcmut.edu.vn'
UNION ALL
SELECT u1.user_id,
       'VEHICLE_REGISTRATION',
       'APPROVED',
       'https://cdn.mssus.com/verify/vehicle_59Z376543.pdf',
       'PDF',
       (SELECT user_id
        FROM users
        WHERE email = 'admin@mssus.com'),
       NOW() - INTERVAL '70 days',
       NOW() + INTERVAL '365 days',
       '{"plate":"59-Z3 76543","model":"Honda Vision"}',
       NOW() - INTERVAL '71 days'
FROM users u1
WHERE u1.email = 'dang.thi.g@student.hcmut.edu.vn'
UNION ALL
SELECT u1.user_id,
       'VEHICLE_REGISTRATION',
       'APPROVED',
       'https://cdn.mssus.com/verify/vehicle_59C643210.pdf',
       'PDF',
       (SELECT user_id
        FROM users
        WHERE email = 'admin@mssus.com'),
       NOW() - INTERVAL '95 days',
       NOW() + INTERVAL '365 days',
       '{"plate":"59-C6 43210","model":"Yamaha NVX"}',
       NOW() - INTERVAL '96 days'
FROM users u1
WHERE u1.email = 'bui.thi.i@student.hcmut.edu.vn';

-- Pending Verifications
INSERT INTO verifications (user_id,
                           type,
                           status,
                           document_url,
                           document_type,
                           verified_by,
                           verified_at,
                           expires_at,
                           metadata,
                           created_at)
SELECT u1.user_id,
       'STUDENT_ID',
       'PENDING',
       'https://cdn.mssus.com/verify/student_2110010_pending.jpg',
       'IMAGE',
       NULL::INTEGER,
       NULL::TIMESTAMP,
       NULL::TIMESTAMP,
       '{"student_id":"2110010","faculty":"Architecture"}',
       NOW() - INTERVAL '2 days'
FROM users u1
WHERE u1.email = 'truong.van.k@student.hcmut.edu.vn'
UNION ALL
SELECT u1.user_id,
       'STUDENT_ID',
       'PENDING',
       'https://cdn.mssus.com/verify/student_2110011_pending.jpg',
       'IMAGE',
       NULL::INTEGER,
       NULL::TIMESTAMP,
       NULL::TIMESTAMP,
       '{"student_id":"2110011","faculty":"Environment"}',
       NOW() - INTERVAL '1 day'
FROM users u1
WHERE u1.email = 'ngo.thi.l@student.hcmut.edu.vn';

-- Rejected Verification (with reason)
INSERT INTO verifications (user_id,
                           type,
                           status,
                           document_url,
                           document_type,
                           rejection_reason,
                           verified_by,
                           verified_at,
                           expires_at,
                           metadata,
                           created_at)
SELECT u1.user_id,
       'DRIVER_LICENSE',
       'REJECTED',
       'https://cdn.mssus.com/verify/license_rejected_hoang.jpg',
       'IMAGE',
       'Photo is unclear, please submit a high-quality scan of your driver license',
       (SELECT user_id
        FROM users
        WHERE email = 'admin@mssus.com'),
       NOW() - INTERVAL '10 days',
       NULL::TIMESTAMP,
       NULL,
       NOW() - INTERVAL '11 days'
FROM users u1
WHERE u1.email = 'hoang.van.e@student.hcmut.edu.vn';

-- =====================================================
-- 10. PROMOTIONS
-- =====================================================
INSERT INTO promotions (code,
                        title,
                        description,
                        discount_type,
                        discount_rate,
                        target_user_type,
                        min_shared_ride_amount,
                        max_discount,
                        usage_limit,
                        usage_limit_per_user,
                        used_count,
                        valid_from,
                        valid_until,
                        is_active,
                        created_at)
VALUES ('HCMUT2024',
        'HCMUT Student Discount',
        'Special discount for HCMUT students',
        'PERCENTAGE',
        25.00,
        'RIDER',
        30000.00,
        60000.00,
        1500,
        3,
        287,
        NOW() - INTERVAL '60 days',
        NOW() + INTERVAL '305 days',
        true,
        NOW() - INTERVAL '60 days'),
       ('NEWDRIVER',
        'New Driver Bonus',
        'Welcome bonus for new drivers',
        'FIXED_AMOUNT',
        100000.00,
        'DRIVER',
        0.00,
        100000.00,
        200,
        1,
        45,
        NOW() - INTERVAL '45 days',
        NOW() + INTERVAL '320 days',
        true,
        NOW() - INTERVAL '45 days'),
       ('WEEKEND50',
        'Weekend Special',
        '50k off for weekend rides',
        'FIXED_AMOUNT',
        50000.00,
        'ALL',
        80000.00,
        50000.00,
        3000,
        5,
        678,
        NOW() - INTERVAL '30 days',
        NOW() + INTERVAL '335 days',
        true,
        NOW() - INTERVAL '30 days'),
       ('MORNING20',
        'Morning Commute',
        '20% off morning rides (6-9 AM)',
        'PERCENTAGE',
        20.00,
        'RIDER',
        25000.00,
        40000.00,
        5000,
        10,
        1234,
        NOW() - INTERVAL '90 days',
        NOW() + INTERVAL '275 days',
        true,
        NOW() - INTERVAL '90 days'),
       ('LONGRIDE',
        'Long Distance Reward',
        '15% off rides over 15km',
        'PERCENTAGE',
        15.00,
        'ALL',
        100000.00,
        80000.00,
        2000,
        5,
        456,
        NOW() - INTERVAL '75 days',
        NOW() + INTERVAL '290 days',
        true,
        NOW() - INTERVAL '75 days');

-- =====================================================
-- SEED DATA COMPLETED
-- =====================================================

-- Admin user
INSERT INTO users (email,
                   phone,
                   password_hash,
                   full_name,
                   user_type,
                   status,
                   email_verified,
                   phone_verified,
                   created_at,
                   updated_at,
                   token_version)
VALUES ('admin@mssus.com',
        '0900000001',
        '$2a$10$BaeiCK1yapOvw.WrcaGb1OqHVOqqSD4TkEAvhHThm.F85BvxYH7ru', -- password: Password1!
        'System Administrator',
        'ADMIN',
        'ACTIVE',
        true,
        true,
        NOW(),
        NOW(),
        1)
ON CONFLICT (email) DO NOTHING;

-- Ordinary user (John Doe)
INSERT INTO users (email,
                   phone,
                   password_hash,
                   full_name,
                   student_id,
                   user_type,
                   status,
                   email_verified,
                   created_at,
                   updated_at,
                   phone_verified,
                   token_version)
VALUES ('john.doe@example.com',
        '0987654321',
        '$2a$10$BaeiCK1yapOvw.WrcaGb1OqHVOqqSD4TkEAvhHThm.F85BvxYH7ru',
        'John Doe',
        'STU123456',
        'USER',
        'ACTIVE',
        true,
        NOW(),
        NOW(),
        true,
        1)
ON CONFLICT (email) DO NOTHING;

-- Rider profile for John Doe
INSERT INTO rider_profiles (rider_id,
                            preferred_payment_method,
                            created_at)
SELECT user_id, 'WALLET', NOW()
FROM users
WHERE email = 'john.doe@example.com';

-- Driver profile for John Doe
INSERT INTO driver_profiles (driver_id,
                             license_number,
                             status,
                             is_available,
                             created_at)
SELECT user_id, 'DL123456789', 'ACTIVE', true, NOW()
FROM users
WHERE email = 'john.doe@example.com';

-- Driver 1
INSERT INTO users (email,
                   phone,
                   password_hash,
                   full_name,
                   student_id,
                   user_type,
                   status,
                   email_verified,
                   phone_verified,
                   created_at,
                   updated_at,
                   token_version)
VALUES ('driver1@example.com',
        '0987652321',
        '$2a$10$BaeiCK1yapOvw.WrcaGb1OqHVOqqSD4TkEAvhHThm.F85BvxYH7ru',
        'Driver One',
        'SE111111',
        'USER',
        'ACTIVE',
        true,
        true,
        NOW(),
        NOW(),
        1)
ON CONFLICT (email) DO NOTHING;

-- Rider profile for Driver 1
INSERT INTO rider_profiles (rider_id,
                            preferred_payment_method,
                            created_at)
SELECT user_id, 'WALLET', NOW()
FROM users
WHERE email = 'driver1@example.com';

-- Driver profile for Driver 1
INSERT INTO driver_profiles (driver_id,
                             license_number,
                             status,
                             is_available,
                             created_at)
SELECT user_id, 'DL123236789', 'ACTIVE', true, NOW()
FROM users
WHERE email = 'driver1@example.com';

INSERT INTO users (email,
                   phone,
                   password_hash,
                   full_name,
                   student_id,
                   user_type,
                   status,
                   email_verified,
                   phone_verified,
                   created_at,
                   updated_at,
                   token_version)
VALUES ('driver2@example.com',
        '0957652321',
        '$2a$10$BaeiCK1yapOvw.WrcaGb1OqHVOqqSD4TkEAvhHThm.F85BvxYH7ru',
        'Driver Two',
        'SE111113',
        'USER',
        'ACTIVE',
        true,
        true,
        NOW(),
        NOW(),
        1)
ON CONFLICT (email) DO NOTHING;

INSERT INTO driver_profiles (driver_id,
                             license_number,
                             status,
                             is_available,
                             created_at)
SELECT user_id, 'DL123266789', 'ACTIVE', true, NOW()
FROM users
WHERE email = 'driver2@example.com';

-- Locations
INSERT INTO locations (name, lat, lng, address)
VALUES ('Tòa S2.02 Vinhomes Grand Park',
        10.8386317,
        106.8318038,
        'N/A'),
       ('FPT University - HCMC Campus',
        10.841480,
        106.809844,
        'N/A'),
       ('Tòa S6.02 Vinhomes Grand Park',
        10.8426113,
        106.8374642,
        'N/A'),
       ('Sảnh C6-C5, Ký túc xá Khu B ĐHQG TP.HCM',
        10.8833471,
        106.7795158,
        'N/A');

-- Vehicle for John Doe
INSERT INTO vehicles (driver_id,
                      plate_number,
                      model,
                      color,
                      year,
                      capacity,
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
       '2025-01-01 00:00:00',
       '2024-06-01 00:00:00',
       'GASOLINE',
       'ACTIVE',
       '2024-06-10 12:00:00'
FROM driver_profiles dp
         JOIN users u ON dp.driver_id = u.user_id
WHERE u.email = 'john.doe@example.com';

-- Vehicle for Driver 1
INSERT INTO vehicles (driver_id,
                      plate_number,
                      model,
                      color,
                      year,
                      capacity,
                      insurance_expiry,
                      last_maintenance,
                      fuel_type,
                      status,
                      verified_at)
SELECT dp.driver_id,
       '29A-12344',
       'Honda Wave Alpha',
       'Black',
       2022,
       1,
       '2025-01-01 00:00:00',
       '2024-06-01 00:00:00',
       'GASOLINE',
       'ACTIVE',
       '2024-06-10 12:00:00'
FROM driver_profiles dp
         JOIN users u ON dp.driver_id = u.user_id
WHERE u.email = 'driver1@example.com';

INSERT INTO vehicles (driver_id,
                      plate_number,
                      model,
                      color,
                      year,
                      capacity,
                      insurance_expiry,
                      last_maintenance,
                      fuel_type,
                      status,
                      verified_at)
SELECT dp.driver_id,
       '29A-12334',
       'Honda Wave Alpha',
       'Black',
       2022,
       1,
       '2025-01-01 00:00:00',
       '2024-06-01 00:00:00',
       'GASOLINE',
       'ACTIVE',
       '2024-06-10 12:00:00'
FROM driver_profiles dp
         JOIN users u ON dp.driver_id = u.user_id
WHERE u.email = 'driver2@example.com';

INSERT INTO wallets (user_id,
                     shadow_balance,
                     pending_balance,
                     total_topped_up,
                     total_spent,
                     is_active,
                     last_synced_at,
                     created_at,
                     updated_at,
                     version)
SELECT u.user_id,
       300000, -- shadow_balance
       0,      -- pending_balance
       300000, -- total_topped_up
       0,      -- total_spent
       true,   -- is_active
       now(),  -- last_synced_at
       now(),  -- created_at
       now(),  -- updated_at
       0       -- version
FROM users u
WHERE u.email = 'john.doe@example.com'
  AND NOT EXISTS (SELECT 1
                  FROM wallets w
                  WHERE w.user_id = u.user_id);

WITH transaction_group AS (SELECT '550e8400-e29b-41d4-a716-446655440001'::uuid AS group_id),
     system_txn AS (
         INSERT INTO
             transactions (
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
             RETURNING
                 group_id)
INSERT
INTO transactions (type,
                   group_id,
                   direction,
                   actor_kind,
                   actor_user_id,
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
       (SELECT user_id
        FROM users
        WHERE email = 'john.doe@example.com'), -- actor_user_id
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

INSERT INTO wallets (user_id,
                     shadow_balance,
                     pending_balance,
                     total_topped_up,
                     total_spent,
                     is_active,
                     last_synced_at,
                     created_at,
                     updated_at,
                     version)
SELECT u.user_id,
       300000, -- shadow_balance
       0,      -- pending_balance
       300000, -- total_topped_up
       0,      -- total_spent
       true,   -- is_active
       now(),  -- last_synced_at
       now(),  -- created_at
       now(),  -- updated_at
       0       -- version
FROM users u
WHERE u.email = 'driver1@example.com'
  AND NOT EXISTS (SELECT 1
                  FROM wallets w
                  WHERE w.user_id = u.user_id);

WITH transaction_group AS (SELECT '550e8400-e29b-41d4-a716-446655440002'::uuid AS group_id),
     system_txn AS (
         INSERT INTO
             transactions (
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
             RETURNING
                 group_id)
INSERT
INTO transactions (type,
                   group_id,
                   direction,
                   actor_kind,
                   actor_user_id,
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
       (SELECT user_id
        FROM users
        WHERE email = 'driver1@example.com'), -- actor_user_id
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

INSERT INTO wallets (user_id,
                     shadow_balance,
                     pending_balance,
                     total_topped_up,
                     total_spent,
                     is_active,
                     last_synced_at,
                     created_at,
                     updated_at,
                     version)
SELECT u.user_id,
       300000, -- shadow_balance
       0,      -- pending_balance
       300000, -- total_topped_up
       0,      -- total_spent
       true,   -- is_active
       now(),  -- last_synced_at
       now(),  -- created_at
       now(),  -- updated_at
       0       -- version
FROM users u
WHERE u.email = 'driver2@example.com'
  AND NOT EXISTS (SELECT 1
                  FROM wallets w
                  WHERE w.user_id = u.user_id);

WITH transaction_group AS (SELECT '550e8400-e29b-41d4-a716-446655440003'::uuid AS group_id),
     system_txn AS (
         INSERT INTO
             transactions (
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
             RETURNING
                 group_id)
INSERT
INTO transactions (type,
                   group_id,
                   direction,
                   actor_kind,
                   actor_user_id,
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
       (SELECT user_id
        FROM users
        WHERE email = 'driver2@example.com'), -- actor_user_id
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

INSERT INTO locations (name, lat, lng, address)
VALUES ('Nhà Văn Hóa Sinh Viên',
        10.8753395,
        106.8000331,
        'N/A');

INSERT INTO pricing_configs (version,
                             system_commission_rate,
                             valid_from,
                             valid_until)
VALUES ('2025-01-15 00:00:00',
        0.1000,
        '2025-01-15 00:00:00'::timestamp,
        NULL);

DO
$$
    DECLARE
        v_pricing_config_id INTEGER;
    BEGIN
        -- Find the pricing_config_id for the version we want to populate.
        SELECT pricing_config_id
        INTO v_pricing_config_id
        FROM pricing_configs
        WHERE version = '2025-01-15 00:00:00'::timestamp
        LIMIT 1;

        -- Only insert if the pricing config exists.
        IF v_pricing_config_id IS NOT NULL THEN
            -- Tier 1: Base fare for the first 2km.
            INSERT INTO fare_tiers (pricing_config_id, tier_level, description, amount, min_km, max_km)
            VALUES (v_pricing_config_id, 1, 'Base fare for first 5km', 10000.00, 0, 5);

            -- Tier 2: Price per km for distances beyond 2km.
            INSERT INTO fare_tiers (pricing_config_id, tier_level, description, amount, min_km, max_km)
            VALUES (v_pricing_config_id, 2, 'Fixed fare for above 5km', 15000.00, 5, 999);
        END IF;
    END;
$$;

-- ============================================================================
-- Mock Transaction Data for Testing and Development
-- ============================================================================

-- -- Insert additional test users for comprehensive transaction testing
-- INSERT INTO users (email, phone, password_hash, full_name, student_id, user_type, status, email_verified,
--                    phone_verified, token_version, created_at, updated_at)
-- VALUES ('alice.smith@example.com', '0987654322', '$2a$10$BaeiCK1yapOvw.WrcaGb1OqHVOqqSD4TkEAvhHThm.F85BvxYH7ru',
--         'Alice Smith', 'STU123457', 'USER', 'ACTIVE', true, true, 1, now(), now()),
--        ('bob.johnson@example.com', '0987654323', '$2a$10$BaeiCK1yapOvw.WrcaGb1OqHVOqqSD4TkEAvhHThm.F85BvxYH7ru',
--         'Bob Johnson', 'STU123458', 'USER', 'ACTIVE', true, true, 1, now(), now()),
--        ('charlie.brown@example.com', '0987654324', '$2a$10$BaeiCK1yapOvw.WrcaGb1OqHVOqqSD4TkEAvhHThm.F85BvxYH7ru',
--         'Charlie Brown', 'STU123459', 'USER', 'ACTIVE', true, true, 1, now(), now())
-- ON CONFLICT (email) DO NOTHING;

-- -- Create rider profiles for test users
-- INSERT INTO rider_profiles (rider_id, emergency_contact, preferred_payment_method, created_at)
-- SELECT u.user_id, '0901234567', 'WALLET', now()
-- FROM users u
-- WHERE u.email IN ('alice.smith@example.com', 'bob.johnson@example.com', 'charlie.brown@example.com')
--   AND NOT EXISTS (SELECT 1 FROM rider_profiles rp WHERE rp.rider_id = u.user_id);

-- -- Create driver profiles for test users
-- INSERT INTO driver_profiles (driver_id, license_number, status, is_available, created_at)
-- SELECT u.user_id, 'DL' || u.user_id || '789', 'ACTIVE', true, now()
-- FROM users u
-- WHERE u.email IN ('alice.smith@example.com', 'bob.johnson@example.com', 'charlie.brown@example.com')
--   AND NOT EXISTS (SELECT 1 FROM driver_profiles dp WHERE dp.driver_id = u.user_id);

-- -- Create wallets for test users
-- INSERT INTO wallets (user_id, shadow_balance, pending_balance, total_topped_up, total_spent, is_active, last_synced_at,
--                      created_at, updated_at, version)
-- SELECT u.user_id,
--        500000,
--        0,
--        500000,
--        0,
--        true,
--        now(),
--        now(),
--        now(),
--          0
-- FROM users u
-- WHERE u.email IN ('alice.smith@example.com', 'bob.johnson@example.com', 'charlie.brown@example.com')
--   AND NOT EXISTS (SELECT 1 FROM wallets w WHERE w.user_id = u.user_id);

-- -- Mock Transaction Data: Various transaction types and scenarios
-- -- Transaction Group 1: Alice's Top-up
-- WITH transaction_group AS (SELECT '11111111-1111-1111-1111-111111111111'::uuid AS group_id),
--      system_txn AS (
--          INSERT INTO transactions (group_id, type, direction, actor_kind, actor_user_id, system_wallet, amount,
--                                    currency, status, psp_ref, note, created_at)
--              SELECT group_id,
--                     'TOPUP',
--                     'IN',
--                     'SYSTEM',
--                     NULL,
--                     'MASTER',
--                     200000,
--                     'VND',
--                     'SUCCESS',
--                     'PSP-ALICE-200K-001',
--                     'PSP Inflow - Alice wallet funding',
--                     now()
--              FROM transaction_group
--              RETURNING group_id)
-- INSERT
-- INTO transactions (group_id, type, direction, actor_kind, actor_user_id, rider_user_id, amount, currency, status,
--                    before_avail, after_avail, before_pending, after_pending, psp_ref, note, created_at)
-- SELECT g.group_id,
--        'TOPUP',
--        'IN',
--        'USER',
--        (SELECT user_id FROM users WHERE email = 'alice.smith@example.com'),
--        (SELECT user_id FROM users WHERE email = 'alice.smith@example.com'),
--        200000,
--        'VND',
--        'SUCCESS',
--        500000,
--        700000,
--        0,
--        0,
--        'PSP-ALICE-200K-001',
--        'Alice wallet top-up - 200,000 VND',
--        now()
-- FROM system_txn g;

-- -- Transaction Group 2: Bob's Ride Payment Flow
-- WITH transaction_group AS (SELECT '22222222-2222-2222-2222-222222222222'::uuid AS group_id),
--      hold_create AS (
--          INSERT INTO transactions (group_id, type, direction, actor_kind, actor_user_id, rider_user_id, amount,
--                                    currency, status, before_avail, after_avail, before_pending, after_pending, note,
--                                    created_at)
--              SELECT group_id,
--                     'HOLD_CREATE',
--                     'INTERNAL',
--                     'USER',
--                     (SELECT user_id FROM users WHERE email = 'bob.johnson@example.com'),
--                     (SELECT user_id FROM users WHERE email = 'bob.johnson@example.com'),
--                     50000,
--                     'VND',
--                     'SUCCESS',
--                     500000,
--                     450000,
--                     0,
--                     50000,
--                     'Hold funds for ride payment',
--                     now()
--              FROM transaction_group
--              RETURNING group_id),
--      hold_release AS (
--          INSERT INTO transactions (group_id, type, direction, actor_kind, actor_user_id, rider_user_id, amount,
--                                    currency, status, before_avail, after_avail, before_pending, after_pending, note,
--                                    created_at)
--              SELECT group_id,
--                     'HOLD_RELEASE',
--                     'INTERNAL',
--                     'USER',
--                     (SELECT user_id FROM users WHERE email = 'bob.johnson@example.com'),
--                     (SELECT user_id FROM users WHERE email = 'bob.johnson@example.com'),
--                     50000,
--                     'VND',
--                     'SUCCESS',
--                     450000,
--                     500000,
--                     50000,
--                     0,
--                     'Release hold - ride cancelled',
--                     now()
--              FROM transaction_group
--              RETURNING group_id)
-- SELECT 1;
-- -- Placeholder to complete the CTE

-- -- Transaction Group 3: Charlie's Successful Ride Payment
-- WITH transaction_group AS (SELECT '33333333-3333-3333-3333-333333333333'::uuid AS group_id),
--      hold_create AS (
--          INSERT INTO transactions (group_id, type, direction, actor_kind, actor_user_id, rider_user_id, amount,
--                                    currency, status, before_avail, after_avail, before_pending, after_pending, note,
--                                    created_at)
--              SELECT group_id,
--                     'HOLD_CREATE',
--                     'INTERNAL',
--                     'USER',
--                     (SELECT user_id FROM users WHERE email = 'charlie.brown@example.com'),
--                     (SELECT user_id FROM users WHERE email = 'charlie.brown@example.com'),
--                     75000,
--                     'VND',
--                     'SUCCESS',
--                     500000,
--                     425000,
--                     0,
--                     75000,
--                     'Hold funds for ride payment',
--                     now()
--              FROM transaction_group
--              RETURNING group_id),
--      capture_fare AS (
--          INSERT INTO transactions (group_id, type, direction, actor_kind, actor_user_id, rider_user_id, driver_user_id,
--                                    amount, currency, status, before_avail, after_avail, before_pending, after_pending,
--                                    note, created_at)
--              SELECT group_id,
--                     'CAPTURE_FARE',
--                     'INTERNAL',
--                     'USER',
--                     (SELECT user_id FROM users WHERE email = 'charlie.brown@example.com'),
--                     (SELECT user_id FROM users WHERE email = 'charlie.brown@example.com'),
--                     (SELECT user_id FROM users WHERE email = 'alice.smith@example.com'),
--                     75000,
--                     'VND',
--                     'SUCCESS',
--                     425000,
--                     425000,
--                     75000,
--                     0,
--                     'Capture fare for completed ride',
--                     now()
--              FROM transaction_group
--              RETURNING group_id),
--      driver_payout AS (
--          INSERT INTO transactions (group_id, type, direction, actor_kind, actor_user_id, driver_user_id, amount,
--                                    currency, status, before_avail, after_avail, before_pending, after_pending, note,
--                                    created_at)
--              SELECT group_id,
--                     'PAYOUT',
--                     'OUT',
--                     'USER',
--                     (SELECT user_id FROM users WHERE email = 'alice.smith@example.com'),
--                     (SELECT user_id FROM users WHERE email = 'alice.smith@example.com'),
--                     63750,
--                     'VND',
--                     'SUCCESS',
--                     700000,
--                     636250,
--                     0,
--                     0,
--                     'Driver payout (85% of fare)',
--                     now()
--              FROM transaction_group
--              RETURNING group_id),
--      system_commission AS (
--          INSERT INTO transactions (group_id, type, direction, actor_kind, actor_user_id, system_wallet, amount,
--                                    currency, status, note, created_at)
--              SELECT group_id,
--                     'PAYOUT',
--                     'IN',
--                     'SYSTEM',
--                     NULL,
--                     'COMMISSION',
--                     11250,
--                     'VND',
--                     'SUCCESS',
--                     'System commission (15% of fare)',
--                     now()
--              FROM transaction_group
--              RETURNING group_id)
-- SELECT 1;
-- -- Placeholder to complete the CTE

-- -- Transaction Group 4: Promo Credit
-- WITH transaction_group AS (SELECT '44444444-4444-4444-4444-444444444444'::uuid AS group_id),
--      system_txn AS (
--          INSERT INTO transactions (group_id, type, direction, actor_kind, actor_user_id, system_wallet, amount,
--                                    currency, status, note, created_at)
--              SELECT group_id,
--                     'PROMO_CREDIT',
--                     'OUT',
--                     'SYSTEM',
--                     NULL,
--                     'PROMO',
--                     10000,
--                     'VND',
--                     'SUCCESS',
--                     'Promo credit deduction',
--                     now()
--              FROM transaction_group
--              RETURNING group_id)
-- INSERT
-- INTO transactions (group_id, type, direction, actor_kind, actor_user_id, rider_user_id, amount, currency, status,
--                    before_avail, after_avail, before_pending, after_pending, note, created_at)
-- SELECT g.group_id,
--        'PROMO_CREDIT',
--        'IN',
--        'USER',
--        (SELECT user_id FROM users WHERE email = 'alice.smith@example.com'),
--        (SELECT user_id FROM users WHERE email = 'alice.smith@example.com'),
--        10000,
--        'VND',
--        'SUCCESS',
--        636250,
--        646250,
--        0,
--        0,
--        'Promo credit applied - Welcome bonus',
--        now()
-- FROM system_txn g;

-- -- Transaction Group 5: Failed Transaction
-- WITH transaction_group AS (SELECT '55555555-5555-5555-5555-555555555555'::uuid AS group_id),
--      system_txn AS (
--          INSERT INTO transactions (group_id, type, direction, actor_kind, actor_user_id, system_wallet, amount,
--                                    currency, status, psp_ref, note, created_at)
--              SELECT group_id,
--                     'TOPUP',
--                     'IN',
--                     'SYSTEM',
--                     NULL,
--                     'MASTER',
--                     100000,
--                     'VND',
--                     'FAILED',
--                     'PSP-FAILED-100K-001',
--                     'PSP Inflow - Failed transaction',
--                     now()
--              FROM transaction_group
--              RETURNING group_id)
-- INSERT
-- INTO transactions (group_id, type, direction, actor_kind, actor_user_id, rider_user_id, amount, currency, status,
--                    before_avail, after_avail, before_pending, after_pending, psp_ref, note, created_at)
-- SELECT g.group_id,
--        'TOPUP',
--        'IN',
--        'USER',
--        (SELECT user_id FROM users WHERE email = 'bob.johnson@example.com'),
--        (SELECT user_id FROM users WHERE email = 'bob.johnson@example.com'),
--        100000,
--        'VND',
--        'FAILED',
--        500000,
--        500000,
--        0,
--        0,
--        'PSP-FAILED-100K-001',
--        'Failed top-up attempt - Payment declined',
--        now()
-- FROM system_txn g;

-- -- Transaction Group 6: Adjustment Transaction
-- WITH transaction_group AS (SELECT '66666666-6666-6666-6666-666666666666'::uuid AS group_id),
--      system_txn AS (
--          INSERT INTO transactions (group_id, type, direction, actor_kind, actor_user_id, system_wallet, amount,
--                                    currency, status, note, created_at)
--              SELECT group_id,
--                     'ADJUSTMENT',
--                     'OUT',
--                     'SYSTEM',
--                     NULL,
--                     'MASTER',
--                     5000,
--                     'VND',
--                     'SUCCESS',
--                     'Adjustment deduction',
--                     now()
--              FROM transaction_group
--              RETURNING group_id)
-- INSERT
-- INTO transactions (group_id, type, direction, actor_kind, actor_user_id, rider_user_id, amount, currency, status,
--                    before_avail, after_avail, before_pending, after_pending, note, created_at)
-- SELECT g.group_id,
--        'ADJUSTMENT',
--        'IN',
--        'USER',
--        (SELECT user_id FROM users WHERE email = 'charlie.brown@example.com'),
--        (SELECT user_id FROM users WHERE email = 'charlie.brown@example.com'),
--        5000,
--        'VND',
--        'SUCCESS',
--        500000,
--        505000,
--        0,
--        0,
--        'Manual adjustment - Customer service credit',
--        now()
-- FROM system_txn g;

-- -- Transaction Group 7: Refund Transaction
-- WITH transaction_group AS (SELECT '77777777-7777-7777-7777-777777777777'::uuid AS group_id),
--      system_txn AS (
--          INSERT INTO transactions (group_id, type, direction, actor_kind, actor_user_id, system_wallet, amount,
--                                    currency, status, note, created_at)
--              SELECT group_id,
--                     'REFUND',
--                     'OUT',
--                     'SYSTEM',
--                     NULL,
--                     'MASTER',
--                     25000,
--                     'VND',
--                     'SUCCESS',
--                     'Refund processing',
--                     now()
--              FROM transaction_group
--              RETURNING group_id)
-- INSERT
-- INTO transactions (group_id, type, direction, actor_kind, actor_user_id, rider_user_id, amount, currency, status,
--                    before_avail, after_avail, before_pending, after_pending, note, created_at)
-- SELECT g.group_id,
--        'REFUND',
--        'IN',
--        'USER',
--        (SELECT user_id FROM users WHERE email = 'alice.smith@example.com'),
--        (SELECT user_id FROM users WHERE email = 'alice.smith@example.com'),
--        25000,
--        'VND',
--        'SUCCESS',
--        646250,
--        671250,
--        0,
--        0,
--        'Refund for cancelled ride - Partial refund',
--        now()
-- FROM system_txn g;

-- =====================================================
-- 12. ROUTES (Template catalogue)
-- =====================================================
INSERT INTO routes (name,
                    route_type,
                    from_location_id,
                    to_location_id,
                    default_price,
                    polyline,
                    distance_meters,
                    duration_seconds,
                    metadata,
                    valid_from,
                    created_at,
                    updated_at)
SELECT 'S2.02 Vinhomes Grand Park to FPT University HCMC',
       'TEMPLATE',
       (SELECT location_id
        FROM locations
        WHERE name = 'Tòa S2.02 Vinhomes Grand Park'
        LIMIT 1),
       (SELECT location_id
        FROM locations
        WHERE name = 'FPT University - HCMC Campus'
        LIMIT 1),
       10000.00,
       'g}caAoq`kS^xAuD~@xArF_@J_@DuAX}AXqE|@iHvAtAfBn@x@~@hApB`CHNJRLZHj@BXAf@Gh@Cd@MhBIbBSbEAJCb@GxBAf@ErAAf@GXEXg@|@a@p@KPYd@[h@aA`BkAlBo@bAW`@c@h@UTCBcCdBoBvAj@`Aj@~@j@~@l@~@f@x@LJRVVVgCfEjA~AtCcD|CrCLLj@h@nDbDj@f@xBrBlBfBJPLPPd@FZAH?F?F@D_@p@_@j@wGdH',
       3598,
       880,
       '{"notes":"Core campus corridor"}',
       NOW(),
       NOW(),
       NOW()
WHERE NOT EXISTS (SELECT 1
                  FROM routes
                  WHERE name = 'S2.02 Vinhomes Grand Park to FPT University HCMC');

INSERT INTO routes (name,
                    route_type,
                    from_location_id,
                    to_location_id,
                    default_price,
                    polyline,
                    distance_meters,
                    duration_seconds,
                    metadata,
                    valid_from,
                    created_at,
                    updated_at)
SELECT 'FPT University HCMC to S2.02 Vinhomes Grand Park',
       'TEMPLATE',
       (SELECT location_id
        FROM locations
        WHERE name = 'FPT University - HCMC Campus'
        LIMIT 1),
       (SELECT location_id
        FROM locations
        WHERE name = 'Tòa S2.02 Vinhomes Grand Park'
        LIMIT 1),
       10000.00,
       'g}caAoq`kS^xAuD~@xArF_@J_@DuAX}AXqE|@iHvAtAfBn@x@~@hApB`CHNJRLZHj@BXAf@Gh@Cd@MhBIbBSbEAJCb@GxBAf@ErAAf@GXEXg@|@a@p@KPYd@[h@aA`BkAlBo@bAW`@c@h@UTCBcCdBoBvAj@`Aj@~@j@~@l@~@f@x@LJRVVVgCfEjA~AtCcD|CrCLLj@h@nDbDj@f@xBrBlBfBJPLPPd@FZAH?F?F@D_@p@_@j@wGdH',
       3598,
       880,
       '{"notes":"Core campus corridor"}',
       NOW(),
       NOW(),
       NOW()
WHERE NOT EXISTS (SELECT 1
                  FROM routes
                  WHERE name = 'FPT University HCMC to S2.02 Vinhomes Grand Park');

-- =====================================================
-- 13. USER REPORTS
-- =====================================================

-- Seed data for user_reports table
-- This script inserts sample reports with various statuses, priorities, and scenarios
--
-- IMPORTANT NOTES:
-- 1. This script uses subqueries to get user_id and driver_id from existing users and driver_profiles
-- 2. For ride-specific reports, it attempts to use shared_ride_id from the shared_rides table
--    - If shared_rides table has data, it will use actual ride IDs
--    - If shared_rides table is empty, shared_ride_id will be NULL (which is allowed)
-- 3. Make sure users and driver_profiles tables have seed data before running this script
-- 4. The script will work even if shared_rides table is empty (reports will just have NULL shared_ride_id)

-- Reset sequence to continue from existing data (if any)
-- This prevents duplicate key errors when inserting new data
DO
$$
    DECLARE
        max_id INTEGER;
    BEGIN
        SELECT COALESCE(MAX(report_id), 0) INTO max_id FROM user_reports;
        EXECUTE format('ALTER SEQUENCE IF EXISTS user_reports_report_id_seq RESTART WITH %s', max_id + 1);
    END
$$;

-- =====================================================
-- 1. GENERAL USER REPORTS (No ride-specific)
-- =====================================================

-- PENDING general report (HIGH priority)
INSERT INTO user_reports (reporter_id,
                          report_type,
                          status,
                          priority,
                          description,
                          created_at,
                          updated_at)
SELECT user_id,
       'TECHNICAL',
       'OPEN',
       'HIGH',
       'Ứng dụng bị crash khi tôi cố gắng xem lịch sử chuyến đi. Đã thử khởi động lại nhưng vẫn lỗi.',
       NOW() - INTERVAL '2 days',
       NOW() - INTERVAL '2 days'
FROM users
WHERE email = 'nguyen.van.a@student.hcmut.edu.vn'
LIMIT 1;

-- IN_PROGRESS general report (MEDIUM priority)
INSERT INTO user_reports (reporter_id,
                          report_type,
                          status,
                          priority,
                          description,
                          admin_notes,
                          created_at,
                          updated_at)
SELECT user_id,
       'TECHNICAL',
       'IN_PROGRESS',
       'MEDIUM',
       'Không thể cập nhật thông tin cá nhân. Form bị lỗi validation.',
       'Đang kiểm tra vấn đề với form validation. Sẽ sửa trong bản cập nhật tiếp theo.',
       NOW() - INTERVAL '5 days',
       NOW() - INTERVAL '1 day'
FROM users
WHERE email = 'tran.thi.b@student.hcmut.edu.vn'
LIMIT 1;

-- RESOLVED general report (LOW priority)
INSERT INTO user_reports (reporter_id,
                          resolver_id,
                          report_type,
                          status,
                          priority,
                          description,
                          resolution_message,
                          resolved_at,
                          created_at,
                          updated_at)
SELECT (SELECT user_id
        FROM users
        WHERE email = 'le.van.c@student.hcmut.edu.vn'
        LIMIT 1),
       (SELECT user_id
        FROM users
        WHERE email = 'admin@mssus.com'
        LIMIT 1),
       'OTHER',
       'RESOLVED',
       'LOW',
       'Đề xuất thêm tính năng lọc chuyến đi theo ngày tháng.',
       'Cảm ơn bạn đã đề xuất! Tính năng này đã được thêm vào trong phiên bản 2.1. Bạn có thể sử dụng ngay bây giờ.',
       NOW() - INTERVAL '3 days',
       NOW() - INTERVAL '10 days',
       NOW() - INTERVAL '3 days'
LIMIT 1;

-- DISMISSED general report (MEDIUM priority)
INSERT INTO user_reports (reporter_id,
                          resolver_id,
                          report_type,
                          status,
                          priority,
                          description,
                          admin_notes,
                          resolution_message,
                          resolved_at,
                          created_at,
                          updated_at)
SELECT (SELECT user_id
        FROM users
        WHERE email = 'pham.thi.d@student.hcmut.edu.vn'
        LIMIT 1),
       (SELECT user_id
        FROM users
        WHERE email = 'admin@mssus.com'
        LIMIT 1),
       'OTHER',
       'DISMISSED',
       'MEDIUM',
       'Yêu cầu hoàn tiền cho chuyến đi đã hủy.',
       'Chuyến đi đã được hoàn tiền tự động theo chính sách. Không cần xử lý thêm.',
       'Chuyến đi của bạn đã được hoàn tiền tự động vào ví. Vui lòng kiểm tra lại.',
       NOW() - INTERVAL '1 day',
       NOW() - INTERVAL '7 days',
       NOW() - INTERVAL '1 day'
LIMIT 1;

-- =====================================================
-- 2. RIDE-SPECIFIC REPORTS (With shared_ride_id and driver_id)
-- =====================================================

-- PENDING ride report (CRITICAL priority - Safety issue)
-- Note: If shared_rides table has data, use actual ride IDs. Otherwise, shared_ride_id will be NULL
INSERT INTO user_reports (reporter_id,
                          shared_ride_id,
                          driver_id,
                          report_type,
                          status,
                          priority,
                          description,
                          created_at,
                          updated_at)
SELECT (SELECT user_id
        FROM users
        WHERE email = 'nguyen.van.a@student.hcmut.edu.vn'
        LIMIT 1),
       (SELECT shared_ride_id
        FROM shared_rides
        ORDER BY shared_ride_id
        LIMIT 1 OFFSET 0), -- Use first ride if exists
       (SELECT dp.driver_id
        FROM driver_profiles dp
                 JOIN users u ON dp.driver_id = u.user_id
        WHERE u.email = 'vo.van.f@student.hcmut.edu.vn'
        LIMIT 1),
       'SAFETY',
       'PENDING',
       'CRITICAL',
       'Tài xế lái xe rất nguy hiểm, vượt đèn đỏ và không tuân thủ luật giao thông. Tôi cảm thấy không an toàn trong suốt chuyến đi.',
       NOW() - INTERVAL '1 day',
       NOW() - INTERVAL '1 day'
LIMIT 1;

-- PENDING ride report (HIGH priority - Behavior issue)
INSERT INTO user_reports (reporter_id,
                          shared_ride_id,
                          driver_id,
                          report_type,
                          status,
                          priority,
                          description,
                          created_at,
                          updated_at)
SELECT (SELECT user_id
        FROM users
        WHERE email = 'tran.thi.b@student.hcmut.edu.vn'
        LIMIT 1),
       (SELECT shared_ride_id
        FROM shared_rides
        ORDER BY shared_ride_id
        LIMIT 1 OFFSET 1), -- Use second ride if exists
       (SELECT dp.driver_id
        FROM driver_profiles dp
                 JOIN users u ON dp.driver_id = u.user_id
        WHERE u.email = 'dang.thi.g@student.hcmut.edu.vn'
        LIMIT 1),
       'BEHAVIOR',
       'PENDING',
       'HIGH',
       'Tài xế đến muộn 20 phút và không xin lỗi. Thái độ không chuyên nghiệp, nói chuyện điện thoại trong khi lái xe.',
       NOW() - INTERVAL '3 days',
       NOW() - INTERVAL '3 days'
LIMIT 1;

-- IN_PROGRESS ride report (MEDIUM priority - Payment issue)
INSERT INTO user_reports (reporter_id,
                          shared_ride_id,
                          driver_id,
                          report_type,
                          status,
                          priority,
                          description,
                          admin_notes,
                          created_at,
                          updated_at)
SELECT (SELECT user_id
        FROM users
        WHERE email = 'le.van.c@student.hcmut.edu.vn'
        LIMIT 1),
       (SELECT shared_ride_id
        FROM shared_rides
        ORDER BY shared_ride_id
        LIMIT 1 OFFSET 2), -- Use third ride if exists
       (SELECT dp.driver_id
        FROM driver_profiles dp
                 JOIN users u ON dp.driver_id = u.user_id
        WHERE u.email = 'do.van.h@student.hcmut.edu.vn'
        LIMIT 1),
       'PAYMENT',
       'IN_PROGRESS',
       'MEDIUM',
       'Tôi đã thanh toán nhưng hệ thống vẫn hiển thị chưa thanh toán. Số tiền đã bị trừ khỏi ví của tôi.',
       'Đang kiểm tra giao dịch thanh toán. Liên hệ với bộ phận tài chính để xác minh.',
       NOW() - INTERVAL '4 days',
       NOW() - INTERVAL '1 day'
LIMIT 1;

-- IN_PROGRESS ride report with driver response (HIGH priority - Route issue)
INSERT INTO user_reports (reporter_id,
                          shared_ride_id,
                          driver_id,
                          report_type,
                          status,
                          priority,
                          description,
                          driver_response,
                          driver_responded_at,
                          admin_notes,
                          created_at,
                          updated_at)
SELECT (SELECT user_id
        FROM users
        WHERE email = 'pham.thi.d@student.hcmut.edu.vn'
        LIMIT 1),
       (SELECT shared_ride_id
        FROM shared_rides
        ORDER BY shared_ride_id
        LIMIT 1 OFFSET 3), -- Use fourth ride if exists
       (SELECT dp.driver_id
        FROM driver_profiles dp
                 JOIN users u ON dp.driver_id = u.user_id
        WHERE u.email = 'bui.thi.i@student.hcmut.edu.vn'
        LIMIT 1),
       'ROUTE',
       'IN_PROGRESS',
       'HIGH',
       'Tài xế không đi đúng tuyến đường đã hẹn. Đi đường vòng và làm tôi đến muộn 30 phút.',
       'Tôi xin lỗi vì sự bất tiện. Tuyến đường chính bị tắc nghẽn do tai nạn, nên tôi phải đi đường vòng để tránh. Tôi đã cố gắng thông báo nhưng không liên lạc được.',
       NOW() - INTERVAL '2 days',
       'Đã nhận được phản hồi từ tài xế. Đang xác minh tình trạng giao thông tại thời điểm đó.',
       NOW() - INTERVAL '5 days',
       NOW() - INTERVAL '1 day'
LIMIT 1;

-- RESOLVED ride report (MEDIUM priority - Behavior issue)
INSERT INTO user_reports (reporter_id,
                          resolver_id,
                          shared_ride_id,
                          driver_id,
                          report_type,
                          status,
                          priority,
                          description,
                          resolution_message,
                          resolved_at,
                          created_at,
                          updated_at)
SELECT (SELECT user_id
        FROM users
        WHERE email = 'hoang.van.e@student.hcmut.edu.vn'
        LIMIT 1),
       (SELECT user_id
        FROM users
        WHERE email = 'admin@mssus.com'
        LIMIT 1),
       (SELECT shared_ride_id
        FROM shared_rides
        ORDER BY shared_ride_id
        LIMIT 1 OFFSET 4), -- Use fifth ride if exists
       (SELECT dp.driver_id
        FROM driver_profiles dp
                 JOIN users u ON dp.driver_id = u.user_id
        WHERE u.email = 'vo.van.f@student.hcmut.edu.vn'
        LIMIT 1),
       'BEHAVIOR',
       'RESOLVED',
       'MEDIUM',
       'Tài xế hút thuốc trong xe khi có hành khách. Điều này rất không phù hợp.',
       'Cảm ơn bạn đã báo cáo. Chúng tôi đã cảnh báo tài xế về hành vi này. Tài xế đã cam kết không tái phạm. Nếu vấn đề tiếp tục, vui lòng báo cáo lại.',
       NOW() - INTERVAL '2 days',
       NOW() - INTERVAL '8 days',
       NOW() - INTERVAL '2 days'
LIMIT 1;

-- RESOLVED ride report with driver response (LOW priority - Ride experience)
INSERT INTO user_reports (reporter_id,
                          resolver_id,
                          shared_ride_id,
                          driver_id,
                          report_type,
                          status,
                          priority,
                          description,
                          driver_response,
                          driver_responded_at,
                          resolution_message,
                          resolved_at,
                          created_at,
                          updated_at)
SELECT (SELECT user_id
        FROM users
        WHERE email = 'nguyen.van.a@student.hcmut.edu.vn'
        LIMIT 1),
       (SELECT user_id
        FROM users
        WHERE email = 'admin@mssus.com'
        LIMIT 1),
       (SELECT shared_ride_id
        FROM shared_rides
        ORDER BY shared_ride_id
        LIMIT 1 OFFSET 5), -- Use sixth ride if exists
       (SELECT dp.driver_id
        FROM driver_profiles dp
                 JOIN users u ON dp.driver_id = u.user_id
        WHERE u.email = 'dang.thi.g@student.hcmut.edu.vn'
        LIMIT 1),
       'RIDE_EXPERIENCE',
       'RESOLVED',
       'LOW',
       'Xe có mùi khó chịu và không sạch sẽ.',
       'Tôi xin lỗi về vấn đề này. Tôi đã vệ sinh xe kỹ lưỡng sau chuyến đi và sẽ chú ý hơn trong tương lai.',
       NOW() - INTERVAL '5 days',
       'Tài xế đã phản hồi và cam kết cải thiện. Vấn đề đã được giải quyết.',
       NOW() - INTERVAL '4 days',
       NOW() - INTERVAL '10 days',
       NOW() - INTERVAL '4 days'
LIMIT 1;

-- DISMISSED ride report (MEDIUM priority - Payment issue)
INSERT INTO user_reports (reporter_id,
                          resolver_id,
                          shared_ride_id,
                          driver_id,
                          report_type,
                          status,
                          priority,
                          description,
                          admin_notes,
                          resolution_message,
                          resolved_at,
                          created_at,
                          updated_at)
SELECT (SELECT user_id
        FROM users
        WHERE email = 'tran.thi.b@student.hcmut.edu.vn'
        LIMIT 1),
       (SELECT user_id
        FROM users
        WHERE email = 'admin@mssus.com'
        LIMIT 1),
       (SELECT shared_ride_id
        FROM shared_rides
        ORDER BY shared_ride_id
        LIMIT 1 OFFSET 6), -- Use seventh ride if exists
       (SELECT dp.driver_id
        FROM driver_profiles dp
                 JOIN users u ON dp.driver_id = u.user_id
        WHERE u.email = 'do.van.h@student.hcmut.edu.vn'
        LIMIT 1),
       'PAYMENT',
       'DISMISSED',
       'MEDIUM',
       'Tôi nghĩ tôi đã bị tính phí sai. Giá cao hơn so với dự kiến.',
       'Đã kiểm tra: Giá đúng theo bảng giá hiện hành. Không có sai sót trong tính toán.',
       'Sau khi kiểm tra, chúng tôi xác nhận rằng giá được tính đúng theo bảng giá hiện hành. Không có sai sót.',
       NOW() - INTERVAL '1 day',
       NOW() - INTERVAL '6 days',
       NOW() - INTERVAL '1 day'
LIMIT 1;

-- =====================================================
-- 3. ESCALATED REPORTS (Auto-escalated after 48 hours)
-- =====================================================

-- Escalated report (was PENDING, now HIGH priority)
INSERT INTO user_reports (reporter_id,
                          shared_ride_id,
                          driver_id,
                          report_type,
                          status,
                          priority,
                          description,
                          escalated_at,
                          escalation_reason,
                          created_at,
                          updated_at)
SELECT (SELECT user_id
        FROM users
        WHERE email = 'le.van.c@student.hcmut.edu.vn'
        LIMIT 1),
       (SELECT shared_ride_id
        FROM shared_rides
        ORDER BY shared_ride_id
        LIMIT 1 OFFSET 7), -- Use eighth ride if exists
       (SELECT dp.driver_id
        FROM driver_profiles dp
                 JOIN users u ON dp.driver_id = u.user_id
        WHERE u.email = 'bui.thi.i@student.hcmut.edu.vn'
        LIMIT 1),
       'SAFETY',
       'PENDING',
       'HIGH',
       'Tài xế lái xe quá nhanh và không chú ý đến an toàn. Tôi rất lo lắng.',
       NOW() - INTERVAL '1 day',
       'Automatically escalated: No resolution within 48 hours',
       NOW() - INTERVAL '3 days',
       NOW() - INTERVAL '1 day'
LIMIT 1;

-- Escalated report (was MEDIUM, now HIGH priority)
INSERT INTO user_reports (reporter_id,
                          report_type,
                          status,
                          priority,
                          description,
                          escalated_at,
                          escalation_reason,
                          created_at,
                          updated_at)
SELECT (SELECT user_id
        FROM users
        WHERE email = 'pham.thi.d@student.hcmut.edu.vn'
        LIMIT 1),
       'TECHNICAL',
       'OPEN',
       'HIGH',
       'Không thể đăng nhập vào tài khoản. Đã thử reset password nhưng vẫn không được.',
       NOW() - INTERVAL '2 days',
       'Automatically escalated: No resolution within 48 hours',
       NOW() - INTERVAL '4 days',
       NOW() - INTERVAL '2 days'
LIMIT 1;

-- =====================================================
-- 4. RECENT REPORTS (Created today/this week)
-- =====================================================

-- Recent PENDING report (CRITICAL priority)
INSERT INTO user_reports (reporter_id,
                          shared_ride_id,
                          driver_id,
                          report_type,
                          status,
                          priority,
                          description,
                          created_at,
                          updated_at)
SELECT (SELECT user_id
        FROM users
        WHERE email = 'hoang.van.e@student.hcmut.edu.vn'
        LIMIT 1),
       (SELECT shared_ride_id
        FROM shared_rides
        ORDER BY shared_ride_id
        LIMIT 1 OFFSET 8), -- Use ninth ride if exists
       (SELECT dp.driver_id
        FROM driver_profiles dp
                 JOIN users u ON dp.driver_id = u.user_id
        WHERE u.email = 'vo.van.f@student.hcmut.edu.vn'
        LIMIT 1),
       'SAFETY',
       'PENDING',
       'CRITICAL',
       'Tài xế có dấu hiệu say rượu. Mùi rượu rất nồng và lái xe không ổn định. Tôi yêu cầu dừng xe ngay lập tức.',
       NOW() - INTERVAL '2 hours',
       NOW() - INTERVAL '2 hours'
LIMIT 1;

-- Recent OPEN report (HIGH priority)
INSERT INTO user_reports (reporter_id,
                          report_type,
                          status,
                          priority,
                          description,
                          created_at,
                          updated_at)
SELECT (SELECT user_id
        FROM users
        WHERE email = 'nguyen.van.a@student.hcmut.edu.vn'
        LIMIT 1),
       'PAYMENT',
       'OPEN',
       'HIGH',
       'Giao dịch thanh toán bị lỗi. Tiền đã bị trừ nhưng chuyến đi không được xác nhận.',
       NOW() - INTERVAL '5 hours',
       NOW() - INTERVAL '5 hours'
LIMIT 1;

-- Recent PENDING report (MEDIUM priority)
INSERT INTO user_reports (reporter_id,
                          shared_ride_id,
                          driver_id,
                          report_type,
                          status,
                          priority,
                          description,
                          created_at,
                          updated_at)
SELECT (SELECT user_id
        FROM users
        WHERE email = 'tran.thi.b@student.hcmut.edu.vn'
        LIMIT 1),
       (SELECT shared_ride_id
        FROM shared_rides
        ORDER BY shared_ride_id
        LIMIT 1 OFFSET 9), -- Use tenth ride if exists
       (SELECT dp.driver_id
        FROM driver_profiles dp
                 JOIN users u ON dp.driver_id = u.user_id
        WHERE u.email = 'dang.thi.g@student.hcmut.edu.vn'
        LIMIT 1),
       'ROUTE',
       'PENDING',
       'MEDIUM',
       'Tài xế đi sai đường một chút nhưng đã sửa lại kịp thời. Chỉ muốn báo cáo để cải thiện.',
       NOW() - INTERVAL '1 day',
       NOW() - INTERVAL '1 day'
LIMIT 1;

-- =====================================================
-- 5. VARIOUS REPORT TYPES
-- =====================================================

-- RIDE_EXPERIENCE report
INSERT INTO user_reports (reporter_id,
                          shared_ride_id,
                          driver_id,
                          report_type,
                          status,
                          priority,
                          description,
                          created_at,
                          updated_at)
SELECT (SELECT user_id
        FROM users
        WHERE email = 'le.van.c@student.hcmut.edu.vn'
        LIMIT 1),
       (SELECT shared_ride_id
        FROM shared_rides
        ORDER BY shared_ride_id
        LIMIT 1 OFFSET 10), -- Use eleventh ride if exists
       (SELECT dp.driver_id
        FROM driver_profiles dp
                 JOIN users u ON dp.driver_id = u.user_id
        WHERE u.email = 'do.van.h@student.hcmut.edu.vn'
        LIMIT 1),
       'RIDE_EXPERIENCE',
       'PENDING',
       'LOW',
       'Chuyến đi khá tốt nhưng có thể cải thiện về thời gian đợi.',
       NOW() - INTERVAL '2 days',
       NOW() - INTERVAL '2 days'
LIMIT 1;

-- TECHNICAL report (general)
INSERT INTO user_reports (reporter_id,
                          report_type,
                          status,
                          priority,
                          description,
                          created_at,
                          updated_at)
SELECT (SELECT user_id
        FROM users
        WHERE email = 'pham.thi.d@student.hcmut.edu.vn'
        LIMIT 1),
       'TECHNICAL',
       'OPEN',
       'MEDIUM',
       'Thông báo push notification không hoạt động. Tôi không nhận được thông báo về chuyến đi.',
       NOW() - INTERVAL '3 days',
       NOW() - INTERVAL '3 days'
LIMIT 1;

-- OTHER report
INSERT INTO user_reports (reporter_id,
                          report_type,
                          status,
                          priority,
                          description,
                          created_at,
                          updated_at)
SELECT (SELECT user_id
        FROM users
        WHERE email = 'hoang.van.e@student.hcmut.edu.vn'
        LIMIT 1),
       'OTHER',
       'OPEN',
       'LOW',
       'Đề xuất thêm tính năng đánh giá chi tiết hơn cho tài xế sau mỗi chuyến đi.',
       NOW() - INTERVAL '4 days',
       NOW() - INTERVAL '4 days'
LIMIT 1;

-- =====================================================
-- 6. REPORTS WITH DIFFERENT PRIORITIES
-- =====================================================

-- LOW priority report
INSERT INTO user_reports (reporter_id,
                          report_type,
                          status,
                          priority,
                          description,
                          created_at,
                          updated_at)
SELECT (SELECT user_id
        FROM users
        WHERE email = 'nguyen.van.a@student.hcmut.edu.vn'
        LIMIT 1),
       'OTHER',
       'OPEN',
       'LOW',
       'Gợi ý: Có thể thêm tính năng chia sẻ vị trí với bạn bè không?',
       NOW() - INTERVAL '6 days',
       NOW() - INTERVAL '6 days'
LIMIT 1;

-- MEDIUM priority report (default)
INSERT INTO user_reports (reporter_id,
                          shared_ride_id,
                          driver_id,
                          report_type,
                          status,
                          priority,
                          description,
                          created_at,
                          updated_at)
SELECT (SELECT user_id
        FROM users
        WHERE email = 'tran.thi.b@student.hcmut.edu.vn'
        LIMIT 1),
       (SELECT shared_ride_id
        FROM shared_rides
        ORDER BY shared_ride_id
        LIMIT 1 OFFSET 11), -- Use twelfth ride if exists
       (SELECT dp.driver_id
        FROM driver_profiles dp
                 JOIN users u ON dp.driver_id = u.user_id
        WHERE u.email = 'bui.thi.i@student.hcmut.edu.vn'
        LIMIT 1),
       'BEHAVIOR',
       'PENDING',
       'MEDIUM',
       'Tài xế hơi vội vàng khi lái xe. Có thể lái cẩn thận hơn một chút.',
       NOW() - INTERVAL '1 day',
       NOW() - INTERVAL '1 day'
LIMIT 1;

-- HIGH priority report
INSERT INTO user_reports (reporter_id,
                          shared_ride_id,
                          driver_id,
                          report_type,
                          status,
                          priority,
                          description,
                          created_at,
                          updated_at)
SELECT (SELECT user_id
        FROM users
        WHERE email = 'le.van.c@student.hcmut.edu.vn'
        LIMIT 1),
       (SELECT shared_ride_id
        FROM shared_rides
        ORDER BY shared_ride_id
        LIMIT 1 OFFSET 12), -- Use thirteenth ride if exists
       (SELECT dp.driver_id
        FROM driver_profiles dp
                 JOIN users u ON dp.driver_id = u.user_id
        WHERE u.email = 'vo.van.f@student.hcmut.edu.vn'
        LIMIT 1),
       'SAFETY',
       'PENDING',
       'HIGH',
       'Tài xế không đội mũ bảo hiểm và yêu cầu tôi cũng không đội. Điều này rất nguy hiểm.',
       NOW() - INTERVAL '4 hours',
       NOW() - INTERVAL '4 hours'
LIMIT 1;

-- CRITICAL priority report
INSERT INTO user_reports (reporter_id,
                          shared_ride_id,
                          driver_id,
                          report_type,
                          status,
                          priority,
                          description,
                          escalated_at,
                          escalation_reason,
                          created_at,
                          updated_at)
SELECT (SELECT user_id
        FROM users
        WHERE email = 'pham.thi.d@student.hcmut.edu.vn'
        LIMIT 1),
       (SELECT shared_ride_id
        FROM shared_rides
        ORDER BY shared_ride_id
        LIMIT 1 OFFSET 13), -- Use fourteenth ride if exists
       (SELECT dp.driver_id
        FROM driver_profiles dp
                 JOIN users u ON dp.driver_id = u.user_id
        WHERE u.email = 'dang.thi.g@student.hcmut.edu.vn'
        LIMIT 1),
       'SAFETY',
       'PENDING',
       'CRITICAL',
       'Tài xế có hành vi quấy rối tình dục. Tôi cảm thấy rất không an toàn và cần được xử lý ngay lập tức.',
       NOW() - INTERVAL '1 hour',
       'Automatically escalated: Critical safety issue',
       NOW() - INTERVAL '1 hour',
       NOW() - INTERVAL '1 hour'
LIMIT 1;

-- =====================================================
-- 7. SUMMARY
-- =====================================================
-- Total reports inserted: ~20 reports
-- Status distribution:
--   - PENDING: ~8 reports
--   - OPEN: ~5 reports
--   - IN_PROGRESS: ~2 reports
--   - RESOLVED: ~3 reports
--   - DISMISSED: ~2 reports
-- Priority distribution:
--   - LOW: ~3 reports
--   - MEDIUM: ~8 reports
--   - HIGH: ~6 reports
--   - CRITICAL: ~3 reports
-- With driver responses: ~2 reports
-- Escalated: ~3 reports
-- Ride-specific: ~12 reports
-- General reports: ~8 reports