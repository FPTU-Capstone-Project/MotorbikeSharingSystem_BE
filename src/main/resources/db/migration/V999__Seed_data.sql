-- V999__Seed_data.sql
-- Mock data for Motorbike Sharing System
-- This data is completely different from V1_Initialize data

-- Truncate tables in reverse order of dependencies
TRUNCATE TABLE verifications CASCADE;
TRUNCATE TABLE emergency_contacts CASCADE;
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

ALTER TABLE driver_profiles ADD COLUMN IF NOT EXISTS suspended_at TIMESTAMP;
ALTER TABLE driver_profiles ADD COLUMN IF NOT EXISTS activated_at TIMESTAMP;
ALTER TABLE rider_profiles ADD COLUMN IF NOT EXISTS suspended_at TIMESTAMP;
ALTER TABLE rider_profiles ADD COLUMN IF NOT EXISTS activated_at TIMESTAMP;
ALTER TABLE rider_profiles ADD CONSTRAINT chk_status CHECK (status IN ('ACTIVE', 'PENDING','SUSPENDED','REJECTED'));
ALTER TABLE wallets ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- =====================================================
-- 0. ADMIN USER (Must be first!)
-- =====================================================
-- Password for admin: "Password1!" (hashed with BCrypt)
INSERT INTO users (email, phone, password_hash, full_name, student_id, user_type, profile_photo_url, email_verified, phone_verified, token_version, status, created_at, updated_at)
VALUES
    ('admin@mssus.com', '0901234567', '$2a$10$BaeiCK1yapOvw.WrcaGb1OqHVOqqSD4TkEAvhHThm.F85BvxYH7ru', 'System Administrator', NULL, 'ADMIN', 'https://i.pravatar.cc/150?img=1', true, true, 1, 'ACTIVE', NOW(), NOW());

-- =====================================================
-- 1. USERS (Different from V1_Initialize)
-- =====================================================
-- Password for all users: "Password1!" (hashed with BCrypt)
INSERT INTO users (email, phone, password_hash, full_name, student_id, user_type, profile_photo_url, email_verified, phone_verified, token_version, status, created_at, updated_at)
VALUES
    -- Student Users (will be riders)
    ('nguyen.van.a@student.hcmut.edu.vn', '0909111222', '$2a$10$BaeiCK1yapOvw.WrcaGb1OqHVOqqSD4TkEAvhHThm.F85BvxYH7ru', 'Nguyen Van A', '2110001', 'USER', 'https://i.pravatar.cc/150?img=11', true, true, 1, 'ACTIVE', NOW(), NOW()),
    ('tran.thi.b@student.hcmut.edu.vn', '0909222333', '$2a$10$BaeiCK1yapOvw.WrcaGb1OqHVOqqSD4TkEAvhHThm.F85BvxYH7ru', 'Tran Thi B', '2110002', 'USER', 'https://i.pravatar.cc/150?img=12', true, true, 1, 'ACTIVE', NOW(), NOW()),
    ('le.van.c@student.hcmut.edu.vn', '0909333444', '$2a$10$BaeiCK1yapOvw.WrcaGb1OqHVOqqSD4TkEAvhHThm.F85BvxYH7ru', 'Le Van C', '2110003', 'USER', 'https://i.pravatar.cc/150?img=13', true, true, 1, 'ACTIVE', NOW(), NOW()),
    ('pham.thi.d@student.hcmut.edu.vn', '0909444555', '$2a$10$BaeiCK1yapOvw.WrcaGb1OqHVOqqSD4TkEAvhHThm.F85BvxYH7ru', 'Pham Thi D', '2110004', 'USER', 'https://i.pravatar.cc/150?img=14', true, true, 1, 'ACTIVE', NOW(), NOW()),
    ('hoang.van.e@student.hcmut.edu.vn', '0909555666', '$2a$10$BaeiCK1yapOvw.WrcaGb1OqHVOqqSD4TkEAvhHThm.F85BvxYH7ru', 'Hoang Van E', '2110005', 'USER', 'https://i.pravatar.cc/150?img=15', true, true, 1, 'ACTIVE', NOW(), NOW()),

    -- Driver Users
    ('vo.van.f@student.hcmut.edu.vn', '0909666777', '$2a$10$BaeiCK1yapOvw.WrcaGb1OqHVOqqSD4TkEAvhHThm.F85BvxYH7ru', 'Vo Van F', '2110006', 'USER', 'https://i.pravatar.cc/150?img=16', true, true, 1, 'ACTIVE', NOW(), NOW()),
    ('dang.thi.g@student.hcmut.edu.vn', '0909777888', '$2a$10$BaeiCK1yapOvw.WrcaGb1OqHVOqqSD4TkEAvhHThm.F85BvxYH7ru', 'Dang Thi G', '2110007', 'USER', 'https://i.pravatar.cc/150?img=17', true, true, 1, 'ACTIVE', NOW(), NOW()),
    ('do.van.h@student.hcmut.edu.vn', '0909888999', '$2a$10$BaeiCK1yapOvw.WrcaGb1OqHVOqqSD4TkEAvhHThm.F85BvxYH7ru', 'Do Van H', '2110008', 'USER', 'https://i.pravatar.cc/150?img=18', true, true, 1, 'ACTIVE', NOW(), NOW()),
    ('bui.thi.i@student.hcmut.edu.vn', '0909999000', '$2a$10$BaeiCK1yapOvw.WrcaGb1OqHVOqqSD4TkEAvhHThm.F85BvxYH7ru', 'Bui Thi I', '2110009', 'USER', 'https://i.pravatar.cc/150?img=19', true, true, 1, 'ACTIVE', NOW(), NOW()),

    -- Pending/Email Verifying Users
    ('truong.van.k@student.hcmut.edu.vn', '0909000111', '$2a$10$BaeiCK1yapOvw.WrcaGb1OqHVOqqSD4TkEAvhHThm.F85BvxYH7ru', 'Truong Van K', '2110010', 'USER', NULL, false, false, 1, 'EMAIL_VERIFYING', NOW(), NOW()),
    ('ngo.thi.l@student.hcmut.edu.vn', '0909111333', '$2a$10$BaeiCK1yapOvw.WrcaGb1OqHVOqqSD4TkEAvhHThm.F85BvxYH7ru', 'Ngo Thi L', '2110011', 'USER', NULL, false, false, 1, 'PENDING', NOW(), NOW());

-- =====================================================
-- 2. RIDER PROFILES
-- =====================================================
INSERT INTO rider_profiles (rider_id, emergency_contact, total_rides, total_spent, status, preferred_payment_method, created_at)
SELECT user_id, '0988777666', 25, 825000.00, 'ACTIVE', 'WALLET', NOW() FROM users WHERE email = 'nguyen.van.a@student.hcmut.edu.vn'
UNION ALL
SELECT user_id, '0977666555', 12, 432000.00, 'ACTIVE', 'WALLET', NOW() FROM users WHERE email = 'tran.thi.b@student.hcmut.edu.vn'
UNION ALL
SELECT user_id, '0966555444', 35, 1120000.00, 'ACTIVE', 'WALLET', NOW() FROM users WHERE email = 'le.van.c@student.hcmut.edu.vn'
UNION ALL
SELECT user_id, '0955444333', 8, 256000.00, 'ACTIVE', 'WALLET', NOW() FROM users WHERE email = 'pham.thi.d@student.hcmut.edu.vn'
UNION ALL
SELECT user_id, '0944333222', 18, 612000.00, 'ACTIVE', 'WALLET', NOW() FROM users WHERE email = 'hoang.van.e@student.hcmut.edu.vn'
UNION ALL
SELECT user_id, '0933222111', 42, 1386000.00, 'ACTIVE', 'WALLET', NOW() FROM users WHERE email = 'vo.van.f@student.hcmut.edu.vn'
UNION ALL
SELECT user_id, '0922111000', 28, 896000.00, 'ACTIVE', 'WALLET', NOW() FROM users WHERE email = 'dang.thi.g@student.hcmut.edu.vn';

-- =====================================================
-- 3. DRIVER PROFILES
-- =====================================================
INSERT INTO driver_profiles (driver_id, license_number, license_verified_at, status, rating_avg, total_shared_rides, total_earned, is_available, max_passengers, created_at)
SELECT user_id, 'B2-987654321', NOW() - INTERVAL '90 days', 'ACTIVE', 4.85, 68, 2244000.00, true, 2, NOW() FROM users WHERE email = 'vo.van.f@student.hcmut.edu.vn'
UNION ALL
SELECT user_id, 'B2-876543210', NOW() - INTERVAL '75 days', 'ACTIVE', 4.92, 55, 1815000.00, true, 1, NOW() FROM users WHERE email = 'dang.thi.g@student.hcmut.edu.vn'
UNION ALL
SELECT user_id, 'B2-765432109', NOW() - INTERVAL '60 days', 'ACTIVE', 4.78, 72, 2376000.00, false, 2, NOW() FROM users WHERE email = 'do.van.h@student.hcmut.edu.vn'
UNION ALL
SELECT user_id, 'B2-654321098', NOW() - INTERVAL '100 days', 'ACTIVE', 4.95, 85, 2805000.00, true, 1, NOW() FROM users WHERE email = 'bui.thi.i@student.hcmut.edu.vn';

-- =====================================================
-- 4. WALLETS
-- =====================================================
INSERT INTO wallets (user_id, shadow_balance, pending_balance, total_topped_up, total_spent, last_synced_at, is_active, created_at, updated_at)
SELECT user_id, 375000.00, 0.00, 1200000.00, 825000.00, NOW(), true, NOW(), NOW() FROM users WHERE email = 'nguyen.van.a@student.hcmut.edu.vn'
UNION ALL
SELECT user_id, 168000.00, 0.00, 600000.00, 432000.00, NOW(), true, NOW(), NOW() FROM users WHERE email = 'tran.thi.b@student.hcmut.edu.vn'
UNION ALL
SELECT user_id, 180000.00, 0.00, 1300000.00, 1120000.00, NOW(), true, NOW(), NOW() FROM users WHERE email = 'le.van.c@student.hcmut.edu.vn'
UNION ALL
SELECT user_id, 444000.00, 0.00, 700000.00, 256000.00, NOW(), true, NOW(), NOW() FROM users WHERE email = 'pham.thi.d@student.hcmut.edu.vn'
UNION ALL
SELECT user_id, 288000.00, 0.00, 900000.00, 612000.00, NOW(), true, NOW(), NOW() FROM users WHERE email = 'hoang.van.e@student.hcmut.edu.vn'
UNION ALL
SELECT user_id, 2114000.00, 80000.00, 3500000.00, 1386000.00, NOW(), true, NOW(), NOW() FROM users WHERE email = 'vo.van.f@student.hcmut.edu.vn'
UNION ALL
SELECT user_id, 1419000.00, 60000.00, 2315000.00, 896000.00, NOW(), true, NOW(), NOW() FROM users WHERE email = 'dang.thi.g@student.hcmut.edu.vn'
UNION ALL
SELECT user_id, 1024000.00, 70000.00, 2000000.00, 976000.00, NOW(), true, NOW(), NOW() FROM users WHERE email = 'do.van.h@student.hcmut.edu.vn'
UNION ALL
SELECT user_id, 1595000.00, 90000.00, 2800000.00, 1205000.00, NOW(), true, NOW(), NOW() FROM users WHERE email = 'bui.thi.i@student.hcmut.edu.vn'
UNION ALL
SELECT user_id, 0.00, 0.00, 0.00, 0.00, NOW(), true, NOW(), NOW() FROM users WHERE email = 'truong.van.k@student.hcmut.edu.vn'
UNION ALL
SELECT user_id, 0.00, 0.00, 0.00, 0.00, NOW(), true, NOW(), NOW() FROM users WHERE email = 'ngo.thi.l@student.hcmut.edu.vn';

-- =====================================================
-- 5. LOCATIONS
-- =====================================================
INSERT INTO locations (name, lat, lng, address, created_at)
VALUES
    ('BK Campus - CS1', 10.772015, 106.657248, 'CS1 Building, 268 Ly Thuong Kiet, Ward 14, District 10, HCMC', NOW()),
    ('BK Campus - CS2', 10.772890, 106.659180, 'CS2 Building, 268 Ly Thuong Kiet, Ward 14, District 10, HCMC', NOW()),
    ('BK Campus - Main Library', 10.773215, 106.658456, 'Main Library, 268 Ly Thuong Kiet, Ward 14, District 10, HCMC', NOW()),
    ('District 1 - Nguyen Hue', 10.774410, 106.702538, 'Nguyen Hue Boulevard, District 1, HCMC', NOW()),
    ('District 1 - Opera House', 10.776934, 106.703098, 'Saigon Opera House, District 1, HCMC', NOW()),
    ('District 3 - Turtle Lake', 10.786230, 106.690810, 'Turtle Lake, District 3, HCMC', NOW()),
    ('Binh Thanh - Vincom', 10.800760, 106.716000, 'Vincom Center, Binh Thanh District, HCMC', NOW()),
    ('Phu Nhuan - Coffee Street', 10.799510, 106.677890, 'Phan Xich Long, Phu Nhuan District, HCMC', NOW()),
    ('Thu Duc - Mega Market', 10.850120, 106.762340, 'Mega Market Thu Duc, Thu Duc City, HCMC', NOW()),
    ('Go Vap - Emart', 10.839456, 106.677123, 'Emart Go Vap, Go Vap District, HCMC', NOW());

-- =====================================================
-- 6. VEHICLES
-- =====================================================
INSERT INTO vehicles (driver_id, plate_number, model, color, year, capacity, insurance_expiry, last_maintenance, fuel_type, status, verified_at, created_at)
SELECT dp.driver_id, '59-X1 98765', 'Honda Future', 'Black', 2021, 2, NOW() + INTERVAL '220 days', NOW() - INTERVAL '12 days', 'GASOLINE', 'ACTIVE', NOW() - INTERVAL '85 days', NOW()
FROM driver_profiles dp JOIN users u ON dp.driver_id = u.user_id WHERE u.email = 'vo.van.f@student.hcmut.edu.vn'
UNION ALL
SELECT dp.driver_id, '59-Y2 87654', 'Yamaha Jupiter', 'Blue', 2022, 2, NOW() + INTERVAL '240 days', NOW() - INTERVAL '8 days', 'GASOLINE', 'ACTIVE', NOW() - INTERVAL '85 days', NOW()
FROM driver_profiles dp JOIN users u ON dp.driver_id = u.user_id WHERE u.email = 'vo.van.f@student.hcmut.edu.vn'
UNION ALL
SELECT dp.driver_id, '59-Z3 76543', 'Honda Vision', 'Red', 2023, 2, NOW() + INTERVAL '280 days', NOW() - INTERVAL '5 days', 'GASOLINE', 'ACTIVE', NOW() - INTERVAL '70 days', NOW()
FROM driver_profiles dp JOIN users u ON dp.driver_id = u.user_id WHERE u.email = 'dang.thi.g@student.hcmut.edu.vn'
UNION ALL
SELECT dp.driver_id, '59-A4 65432', 'Yamaha Grande', 'White', 2022, 2, NOW() + INTERVAL '190 days', NOW() - INTERVAL '18 days', 'GASOLINE', 'ACTIVE', NOW() - INTERVAL '55 days', NOW()
FROM driver_profiles dp JOIN users u ON dp.driver_id = u.user_id WHERE u.email = 'do.van.h@student.hcmut.edu.vn'
UNION ALL
SELECT dp.driver_id, '59-B5 54321', 'Honda PCX', 'Gray', 2020, 2, NOW() + INTERVAL '160 days', NOW() - INTERVAL '25 days', 'GASOLINE', 'MAINTENANCE', NOW() - INTERVAL '55 days', NOW()
FROM driver_profiles dp JOIN users u ON dp.driver_id = u.user_id WHERE u.email = 'do.van.h@student.hcmut.edu.vn'
UNION ALL
SELECT dp.driver_id, '59-C6 43210', 'Yamaha NVX', 'Orange', 2024, 2, NOW() + INTERVAL '330 days', NOW() - INTERVAL '2 days', 'GASOLINE', 'ACTIVE', NOW() - INTERVAL '95 days', NOW()
FROM driver_profiles dp JOIN users u ON dp.driver_id = u.user_id WHERE u.email = 'bui.thi.i@student.hcmut.edu.vn';

-- =====================================================
-- 7. VERIFICATIONS
-- =====================================================
-- Student ID Verifications - Approved
INSERT INTO verifications (user_id, type, status, document_url, document_type, verified_by, verified_at, expires_at, metadata, created_at)
SELECT u1.user_id, 'STUDENT_ID', 'APPROVED', 'https://cdn.mssus.com/verify/student_2110001.jpg', 'IMAGE',
       (SELECT user_id FROM users WHERE email = 'admin@mssus.com'),
       NOW() - INTERVAL '85 days', NOW() + INTERVAL '1095 days', '{"student_id":"2110001","faculty":"Computer Science"}', NOW() - INTERVAL '86 days'
FROM users u1 WHERE u1.email = 'nguyen.van.a@student.hcmut.edu.vn'
UNION ALL
SELECT u1.user_id, 'STUDENT_ID', 'APPROVED', 'https://cdn.mssus.com/verify/student_2110002.jpg', 'IMAGE',
       (SELECT user_id FROM users WHERE email = 'admin@mssus.com'),
       NOW() - INTERVAL '80 days', NOW() + INTERVAL '1095 days', '{"student_id":"2110002","faculty":"Electrical Engineering"}', NOW() - INTERVAL '81 days'
FROM users u1 WHERE u1.email = 'tran.thi.b@student.hcmut.edu.vn'
UNION ALL
SELECT u1.user_id, 'STUDENT_ID', 'APPROVED', 'https://cdn.mssus.com/verify/student_2110003.jpg', 'IMAGE',
       (SELECT user_id FROM users WHERE email = 'admin@mssus.com'),
       NOW() - INTERVAL '75 days', NOW() + INTERVAL '1095 days', '{"student_id":"2110003","faculty":"Mechanical Engineering"}', NOW() - INTERVAL '76 days'
FROM users u1 WHERE u1.email = 'le.van.c@student.hcmut.edu.vn'
UNION ALL
SELECT u1.user_id, 'STUDENT_ID', 'APPROVED', 'https://cdn.mssus.com/verify/student_2110004.pdf', 'PDF',
       (SELECT user_id FROM users WHERE email = 'admin@mssus.com'),
       NOW() - INTERVAL '70 days', NOW() + INTERVAL '1095 days', '{"student_id":"2110004","faculty":"Civil Engineering"}', NOW() - INTERVAL '71 days'
FROM users u1 WHERE u1.email = 'pham.thi.d@student.hcmut.edu.vn'
UNION ALL
SELECT u1.user_id, 'STUDENT_ID', 'APPROVED', 'https://cdn.mssus.com/verify/student_2110005.jpg', 'IMAGE',
       (SELECT user_id FROM users WHERE email = 'admin@mssus.com'),
       NOW() - INTERVAL '65 days', NOW() + INTERVAL '1095 days', '{"student_id":"2110005","faculty":"Chemical Engineering"}', NOW() - INTERVAL '66 days'
FROM users u1 WHERE u1.email = 'hoang.van.e@student.hcmut.edu.vn';

-- Driver License Verifications - Approved
INSERT INTO verifications (user_id, type, status, document_url, document_type, verified_by, verified_at, expires_at, metadata, created_at)
SELECT u1.user_id, 'DRIVER_LICENSE', 'APPROVED', 'https://cdn.mssus.com/verify/license_B2987654321.jpg', 'IMAGE',
       (SELECT user_id FROM users WHERE email = 'admin@mssus.com'),
       NOW() - INTERVAL '90 days', NOW() + INTERVAL '1095 days', '{"license_number":"B2-987654321","issue_date":"2018-05-15"}', NOW() - INTERVAL '92 days'
FROM users u1 WHERE u1.email = 'vo.van.f@student.hcmut.edu.vn'
UNION ALL
SELECT u1.user_id, 'DRIVER_LICENSE', 'APPROVED', 'https://cdn.mssus.com/verify/license_B2876543210.jpg', 'IMAGE',
       (SELECT user_id FROM users WHERE email = 'admin@mssus.com'),
       NOW() - INTERVAL '75 days', NOW() + INTERVAL '1095 days', '{"license_number":"B2-876543210","issue_date":"2019-08-20"}', NOW() - INTERVAL '77 days'
FROM users u1 WHERE u1.email = 'dang.thi.g@student.hcmut.edu.vn'
UNION ALL
SELECT u1.user_id, 'DRIVER_LICENSE', 'APPROVED', 'https://cdn.mssus.com/verify/license_B2765432109.pdf', 'PDF',
       (SELECT user_id FROM users WHERE email = 'admin@mssus.com'),
       NOW() - INTERVAL '60 days', NOW() + INTERVAL '1095 days', '{"license_number":"B2-765432109","issue_date":"2020-03-10"}', NOW() - INTERVAL '62 days'
FROM users u1 WHERE u1.email = 'do.van.h@student.hcmut.edu.vn'
UNION ALL
SELECT u1.user_id, 'DRIVER_LICENSE', 'APPROVED', 'https://cdn.mssus.com/verify/license_B2654321098.jpg', 'IMAGE',
       (SELECT user_id FROM users WHERE email = 'admin@mssus.com'),
       NOW() - INTERVAL '100 days', NOW() + INTERVAL '1095 days', '{"license_number":"B2-654321098","issue_date":"2017-11-25"}', NOW() - INTERVAL '102 days'
FROM users u1 WHERE u1.email = 'bui.thi.i@student.hcmut.edu.vn';

-- Vehicle Registration Verifications
INSERT INTO verifications (user_id, type, status, document_url, document_type, verified_by, verified_at, expires_at, metadata, created_at)
SELECT u1.user_id, 'VEHICLE_REGISTRATION', 'APPROVED', 'https://cdn.mssus.com/verify/vehicle_59X198765.pdf', 'PDF',
       (SELECT user_id FROM users WHERE email = 'admin@mssus.com'),
       NOW() - INTERVAL '85 days', NOW() + INTERVAL '365 days', '{"plate":"59-X1 98765","model":"Honda Future"}', NOW() - INTERVAL '86 days'
FROM users u1 WHERE u1.email = 'vo.van.f@student.hcmut.edu.vn'
UNION ALL
SELECT u1.user_id, 'VEHICLE_REGISTRATION', 'APPROVED', 'https://cdn.mssus.com/verify/vehicle_59Z376543.pdf', 'PDF',
       (SELECT user_id FROM users WHERE email = 'admin@mssus.com'),
       NOW() - INTERVAL '70 days', NOW() + INTERVAL '365 days', '{"plate":"59-Z3 76543","model":"Honda Vision"}', NOW() - INTERVAL '71 days'
FROM users u1 WHERE u1.email = 'dang.thi.g@student.hcmut.edu.vn'
UNION ALL
SELECT u1.user_id, 'VEHICLE_REGISTRATION', 'APPROVED', 'https://cdn.mssus.com/verify/vehicle_59C643210.pdf', 'PDF',
       (SELECT user_id FROM users WHERE email = 'admin@mssus.com'),
       NOW() - INTERVAL '95 days', NOW() + INTERVAL '365 days', '{"plate":"59-C6 43210","model":"Yamaha NVX"}', NOW() - INTERVAL '96 days'
FROM users u1 WHERE u1.email = 'bui.thi.i@student.hcmut.edu.vn';

-- Pending Verifications
INSERT INTO verifications (user_id, type, status, document_url, document_type, verified_by, verified_at, expires_at, metadata, created_at)
SELECT u1.user_id, 'STUDENT_ID', 'PENDING', 'https://cdn.mssus.com/verify/student_2110010_pending.jpg', 'IMAGE',
       NULL::INTEGER, NULL::TIMESTAMP, NULL::TIMESTAMP, '{"student_id":"2110010","faculty":"Architecture"}', NOW() - INTERVAL '2 days'
FROM users u1 WHERE u1.email = 'truong.van.k@student.hcmut.edu.vn'
UNION ALL
SELECT u1.user_id, 'STUDENT_ID', 'PENDING', 'https://cdn.mssus.com/verify/student_2110011_pending.jpg', 'IMAGE',
       NULL::INTEGER, NULL::TIMESTAMP, NULL::TIMESTAMP, '{"student_id":"2110011","faculty":"Environment"}', NOW() - INTERVAL '1 day'
FROM users u1 WHERE u1.email = 'ngo.thi.l@student.hcmut.edu.vn';

-- Rejected Verification (with reason)
INSERT INTO verifications (user_id, type, status, document_url, document_type, rejection_reason, verified_by, verified_at, expires_at, metadata, created_at)
SELECT u1.user_id, 'DRIVER_LICENSE', 'REJECTED', 'https://cdn.mssus.com/verify/license_rejected_hoang.jpg', 'IMAGE',
       'Photo is unclear, please submit a high-quality scan of your driver license',
       (SELECT user_id FROM users WHERE email = 'admin@mssus.com'),
       NOW() - INTERVAL '10 days', NULL::TIMESTAMP, NULL, NOW() - INTERVAL '11 days'
FROM users u1 WHERE u1.email = 'hoang.van.e@student.hcmut.edu.vn';

-- =====================================================
-- 9. EMERGENCY CONTACTS
-- =====================================================
INSERT INTO emergency_contacts (user_id, name, phone, relationship, is_primary, created_at )
SELECT user_id, 'Nguyen Thi X', '0988999000','Mother', true, NOW() FROM users WHERE email = 'nguyen.van.a@student.hcmut.edu.vn'
UNION ALL
SELECT user_id, 'Tran Van Y', '0977888999', 'Father', true, NOW() FROM users WHERE email = 'tran.thi.b@student.hcmut.edu.vn'
UNION ALL
SELECT user_id, 'Le Thi Z', '0966777888', 'Sister', true, NOW() FROM users WHERE email = 'le.van.c@student.hcmut.edu.vn'
UNION ALL
SELECT user_id, 'Pham Van M', '0955666777', 'Brother', true, NOW() FROM users WHERE email = 'pham.thi.d@student.hcmut.edu.vn'
UNION ALL
SELECT user_id, 'Hoang Thi N', '0944555666','Mother', true, NOW()FROM users WHERE email = 'hoang.van.e@student.hcmut.edu.vn'
UNION ALL
SELECT user_id, 'Vo Thi P', '0933444555', 'Wife', true, NOW() FROM users WHERE email = 'vo.van.f@student.hcmut.edu.vn'
UNION ALL
SELECT user_id, 'Dang Van Q', '0922333444', 'Husband', true, NOW() FROM users WHERE email = 'dang.thi.g@student.hcmut.edu.vn'
UNION ALL
SELECT user_id, 'Do Thi R', '0911222333', 'Mother', true, NOW() FROM users WHERE email = 'do.van.h@student.hcmut.edu.vn'
UNION ALL
SELECT user_id, 'Bui Van S', '0900111222', 'Father', true, NOW() FROM users WHERE email = 'bui.thi.i@student.hcmut.edu.vn';

-- =====================================================
-- 10. PROMOTIONS
-- =====================================================
INSERT INTO promotions (code, title, description, discount_type, discount_rate, target_user_type, min_shared_ride_amount, max_discount, usage_limit, usage_limit_per_user, used_count, valid_from, valid_until, is_active, created_at)
VALUES
    ('HCMUT2024', 'HCMUT Student Discount', 'Special discount for HCMUT students', 'PERCENTAGE', 25.00, 'RIDER', 30000.00, 60000.00, 1500, 3, 287, NOW() - INTERVAL '60 days', NOW() + INTERVAL '305 days', true, NOW() - INTERVAL '60 days'),
    ('NEWDRIVER', 'New Driver Bonus', 'Welcome bonus for new drivers', 'FIXED_AMOUNT', 100000.00, 'DRIVER', 0.00, 100000.00, 200, 1, 45, NOW() - INTERVAL '45 days', NOW() + INTERVAL '320 days', true, NOW() - INTERVAL '45 days'),
    ('WEEKEND50', 'Weekend Special', '50k off for weekend rides', 'FIXED_AMOUNT', 50000.00, 'ALL', 80000.00, 50000.00, 3000, 5, 678, NOW() - INTERVAL '30 days', NOW() + INTERVAL '335 days', true, NOW() - INTERVAL '30 days'),
    ('MORNING20', 'Morning Commute', '20% off morning rides (6-9 AM)', 'PERCENTAGE', 20.00, 'RIDER', 25000.00, 40000.00, 5000, 10, 1234, NOW() - INTERVAL '90 days', NOW() + INTERVAL '275 days', true, NOW() - INTERVAL '90 days'),
    ('LONGRIDE', 'Long Distance Reward', '15% off rides over 15km', 'PERCENTAGE', 15.00, 'ALL', 100000.00, 80000.00, 2000, 5, 456, NOW() - INTERVAL '75 days', NOW() + INTERVAL '290 days', true, NOW() - INTERVAL '75 days');

-- =====================================================
-- SEED DATA COMPLETED
-- =====================================================

-- Admin user
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

-- Ordinary user (John Doe)
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

-- Rider profile for John Doe
INSERT INTO rider_profiles (rider_id, emergency_contact, preferred_payment_method)
SELECT user_id, '0901234567', 'WALLET'
FROM users
WHERE email = 'john.doe@example.com';

-- Driver profile for John Doe
INSERT INTO driver_profiles (driver_id, license_number, status, is_available)
SELECT user_id, 'DL123456789', 'ACTIVE', true
FROM users
WHERE email = 'john.doe@example.com';

-- Driver 1
INSERT INTO users (email, phone, password_hash, full_name, student_id, user_type, status, email_verified,
                   phone_verified)
VALUES ('driver1@example.com',
        '0987652321',
        '$2a$10$BaeiCK1yapOvw.WrcaGb1OqHVOqqSD4TkEAvhHThm.F85BvxYH7ru',
        'Driver One',
        'SE111111',
        'USER',
        'ACTIVE',
        true,
        true)
ON CONFLICT (email) DO NOTHING;

-- Rider profile for Driver 1
INSERT INTO rider_profiles (rider_id, emergency_contact, preferred_payment_method)
SELECT user_id, '0901234567', 'WALLET'
FROM users
WHERE email = 'driver1@example.com';

-- Driver profile for Driver 1
INSERT INTO driver_profiles (driver_id, license_number, status, is_available)
SELECT user_id, 'DL123236789', 'ACTIVE', true
FROM users
WHERE email = 'driver1@example.com';

-- Locations
INSERT INTO locations (name, lat, lng, address)
VALUES ('Tòa S2.02 Vinhomes Grand Park', 10.8386317, 106.8318038, NULL),
       ('FPT University - HCMC Campus', 10.841480, 106.809844, NULL),
       ('Tòa S6.02 Vinhomes Grand Park', 10.8426113, 106.8374642, NULL),
       ('Sảnh C6-C5, Ký túc xá Khu B ĐHQG TP.HCM', 10.8833471, 106.7795158, NULL);

-- Vehicle for John Doe
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

-- Vehicle for Driver 1
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
       '29A-12344',
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
WHERE u.email = 'driver1@example.com';


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