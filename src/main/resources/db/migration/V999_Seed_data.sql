-- V999_Seed_data.sql
-- Mock data for Motorbike Sharing System
-- This data is completely different from V1_Initialize data

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
INSERT INTO driver_profiles (driver_id, license_number, license_verified_at, status, rating_avg, total_shared_rides, total_earned, commission_rate, is_available, max_passengers, created_at)
SELECT user_id, 'B2-987654321', NOW() - INTERVAL '90 days', 'ACTIVE', 4.85, 68, 2244000.00, 0.15, true, 2, NOW() FROM users WHERE email = 'vo.van.f@student.hcmut.edu.vn'
UNION ALL
SELECT user_id, 'B2-876543210', NOW() - INTERVAL '75 days', 'ACTIVE', 4.92, 55, 1815000.00, 0.15, true, 1, NOW() FROM users WHERE email = 'dang.thi.g@student.hcmut.edu.vn'
UNION ALL
SELECT user_id, 'B2-765432109', NOW() - INTERVAL '60 days', 'ACTIVE', 4.78, 72, 2376000.00, 0.15, false, 2, NOW() FROM users WHERE email = 'do.van.h@student.hcmut.edu.vn'
UNION ALL
SELECT user_id, 'B2-654321098', NOW() - INTERVAL '100 days', 'ACTIVE', 4.95, 85, 2805000.00, 0.15, true, 1, NOW() FROM users WHERE email = 'bui.thi.i@student.hcmut.edu.vn';

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
INSERT INTO vehicles (driver_id, plate_number, model, color, year, capacity, helmet_count, insurance_expiry, last_maintenance, fuel_type, status, verified_at, created_at)
SELECT dp.driver_id, '59-X1 98765', 'Honda Future', 'Black', 2021, 2, 2, NOW() + INTERVAL '220 days', NOW() - INTERVAL '12 days', 'GASOLINE', 'ACTIVE', NOW() - INTERVAL '85 days', NOW()
FROM driver_profiles dp JOIN users u ON dp.driver_id = u.user_id WHERE u.email = 'vo.van.f@student.hcmut.edu.vn'
UNION ALL
SELECT dp.driver_id, '59-Y2 87654', 'Yamaha Jupiter', 'Blue', 2022, 2, 2, NOW() + INTERVAL '240 days', NOW() - INTERVAL '8 days', 'GASOLINE', 'ACTIVE', NOW() - INTERVAL '85 days', NOW()
FROM driver_profiles dp JOIN users u ON dp.driver_id = u.user_id WHERE u.email = 'vo.van.f@student.hcmut.edu.vn'
UNION ALL
SELECT dp.driver_id, '59-Z3 76543', 'Honda Vision', 'Red', 2023, 1, 2, NOW() + INTERVAL '280 days', NOW() - INTERVAL '5 days', 'GASOLINE', 'ACTIVE', NOW() - INTERVAL '70 days', NOW()
FROM driver_profiles dp JOIN users u ON dp.driver_id = u.user_id WHERE u.email = 'dang.thi.g@student.hcmut.edu.vn'
UNION ALL
SELECT dp.driver_id, '59-A4 65432', 'Yamaha Grande', 'White', 2022, 2, 2, NOW() + INTERVAL '190 days', NOW() - INTERVAL '18 days', 'GASOLINE', 'ACTIVE', NOW() - INTERVAL '55 days', NOW()
FROM driver_profiles dp JOIN users u ON dp.driver_id = u.user_id WHERE u.email = 'do.van.h@student.hcmut.edu.vn'
UNION ALL
SELECT dp.driver_id, '59-B5 54321', 'Honda PCX', 'Gray', 2020, 2, 2, NOW() + INTERVAL '160 days', NOW() - INTERVAL '25 days', 'GASOLINE', 'MAINTENANCE', NOW() - INTERVAL '55 days', NOW()
FROM driver_profiles dp JOIN users u ON dp.driver_id = u.user_id WHERE u.email = 'do.van.h@student.hcmut.edu.vn'
UNION ALL
SELECT dp.driver_id, '59-C6 43210', 'Yamaha NVX', 'Orange', 2024, 1, 2, NOW() + INTERVAL '330 days', NOW() - INTERVAL '2 days', 'GASOLINE', 'ACTIVE', NOW() - INTERVAL '95 days', NOW()
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
SELECT user_id, 'STUDENT_ID', 'PENDING', 'https://cdn.mssus.com/verify/student_2110010_pending.jpg', 'IMAGE', NULL, NULL, NULL, '{"student_id":"2110010","faculty":"Architecture"}', NOW() - INTERVAL '2 days'
FROM users WHERE email = 'truong.van.k@student.hcmut.edu.vn'
UNION ALL
SELECT user_id, 'STUDENT_ID', 'PENDING', 'https://cdn.mssus.com/verify/student_2110011_pending.jpg', 'IMAGE', NULL, NULL, NULL, '{"student_id":"2110011","faculty":"Environment"}', NOW() - INTERVAL '1 day'
FROM users WHERE email = 'ngo.thi.l@student.hcmut.edu.vn';

-- Rejected Verification (with reason)
INSERT INTO verifications (user_id, type, status, document_url, document_type, rejection_reason, verified_by, verified_at, expires_at, metadata, created_at)
SELECT u1.user_id, 'DRIVER_LICENSE', 'REJECTED', 'https://cdn.mssus.com/verify/license_rejected_hoang.jpg', 'IMAGE',
       'Photo is unclear, please submit a high-quality scan of your driver license',
       (SELECT user_id FROM users WHERE email = 'admin@mssus.com'),
       NOW() - INTERVAL '10 days', NULL, NULL, NOW() - INTERVAL '11 days'
FROM users u1 WHERE u1.email = 'hoang.van.e@student.hcmut.edu.vn';

-- =====================================================
-- 8. SHARED RIDES
-- =====================================================
INSERT INTO shared_rides (driver_id, vehicle_id, start_location_id, end_location_id, status, max_passengers, current_passengers, base_fare, per_km_rate, estimated_duration, estimated_distance, actual_distance, scheduled_time, started_at, completed_at, created_at)
SELECT
    dp.driver_id, v.vehicle_id, 1, 4, 'COMPLETED', 2, 2, 18000.00, 3500.00, 30, 10.2, 10.5,
    NOW() - INTERVAL '7 days', NOW() - INTERVAL '7 days' + INTERVAL '8 minutes', NOW() - INTERVAL '7 days' + INTERVAL '38 minutes', NOW() - INTERVAL '8 days'
FROM driver_profiles dp
JOIN users u ON dp.driver_id = u.user_id
JOIN vehicles v ON v.driver_id = dp.driver_id
WHERE u.email = 'vo.van.f@student.hcmut.edu.vn' AND v.plate_number = '59-X1 98765'
UNION ALL
SELECT
    dp.driver_id, v.vehicle_id, 2, 5, 'COMPLETED', 1, 1, 22000.00, 4000.00, 28, 8.8, 9.1,
    NOW() - INTERVAL '6 days', NOW() - INTERVAL '6 days' + INTERVAL '5 minutes', NOW() - INTERVAL '6 days' + INTERVAL '33 minutes', NOW() - INTERVAL '7 days'
FROM driver_profiles dp
JOIN users u ON dp.driver_id = u.user_id
JOIN vehicles v ON v.driver_id = dp.driver_id
WHERE u.email = 'dang.thi.g@student.hcmut.edu.vn' AND v.plate_number = '59-Z3 76543'
UNION ALL
SELECT
    dp.driver_id, v.vehicle_id, 3, 7, 'COMPLETED', 2, 1, 28000.00, 4200.00, 35, 12.5, 12.8,
    NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days' + INTERVAL '3 minutes', NOW() - INTERVAL '5 days' + INTERVAL '38 minutes', NOW() - INTERVAL '6 days'
FROM driver_profiles dp
JOIN users u ON dp.driver_id = u.user_id
JOIN vehicles v ON v.driver_id = dp.driver_id
WHERE u.email = 'do.van.h@student.hcmut.edu.vn' AND v.plate_number = '59-A4 65432'
UNION ALL
SELECT
    dp.driver_id, v.vehicle_id, 1, 9, 'COMPLETED', 1, 1, 55000.00, 5500.00, 50, 18.3, 18.6,
    NOW() - INTERVAL '4 days', NOW() - INTERVAL '4 days' + INTERVAL '7 minutes', NOW() - INTERVAL '4 days' + INTERVAL '57 minutes', NOW() - INTERVAL '5 days'
FROM driver_profiles dp
JOIN users u ON dp.driver_id = u.user_id
JOIN vehicles v ON v.driver_id = dp.driver_id
WHERE u.email = 'bui.thi.i@student.hcmut.edu.vn' AND v.plate_number = '59-C6 43210'
UNION ALL
SELECT
    dp.driver_id, v.vehicle_id, 4, 6, 'COMPLETED', 2, 2, 32000.00, 3800.00, 25, 9.5, 9.7,
    NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days' + INTERVAL '4 minutes', NOW() - INTERVAL '3 days' + INTERVAL '29 minutes', NOW() - INTERVAL '4 days'
FROM driver_profiles dp
JOIN users u ON dp.driver_id = u.user_id
JOIN vehicles v ON v.driver_id = dp.driver_id
WHERE u.email = 'vo.van.f@student.hcmut.edu.vn' AND v.plate_number = '59-Y2 87654'
UNION ALL
SELECT
    dp.driver_id, v.vehicle_id, 5, 8, 'COMPLETED', 1, 1, 26000.00, 3600.00, 32, 11.2, 11.5,
    NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days' + INTERVAL '6 minutes', NOW() - INTERVAL '2 days' + INTERVAL '38 minutes', NOW() - INTERVAL '3 days'
FROM driver_profiles dp
JOIN users u ON dp.driver_id = u.user_id
JOIN vehicles v ON v.driver_id = dp.driver_id
WHERE u.email = 'dang.thi.g@student.hcmut.edu.vn' AND v.plate_number = '59-Z3 76543'
UNION ALL
SELECT
    dp.driver_id, v.vehicle_id, 1, 6, 'ACTIVE', 2, 1, 30000.00, 4000.00, 28, 11.0, NULL,
    NOW() + INTERVAL '45 minutes', NULL, NULL, NOW() - INTERVAL '3 hours'
FROM driver_profiles dp
JOIN users u ON dp.driver_id = u.user_id
JOIN vehicles v ON v.driver_id = dp.driver_id
WHERE u.email = 'vo.van.f@student.hcmut.edu.vn' AND v.plate_number = '59-X1 98765'
UNION ALL
SELECT
    dp.driver_id, v.vehicle_id, 2, 9, 'SCHEDULED', 1, 1, 58000.00, 5200.00, 55, 19.5, NULL,
    NOW() + INTERVAL '3 hours', NULL, NULL, NOW() - INTERVAL '2 hours'
FROM driver_profiles dp
JOIN users u ON dp.driver_id = u.user_id
JOIN vehicles v ON v.driver_id = dp.driver_id
WHERE u.email = 'bui.thi.i@student.hcmut.edu.vn' AND v.plate_number = '59-C6 43210'
UNION ALL
SELECT
    dp.driver_id, v.vehicle_id, 3, 10, 'CANCELLED', 2, 0, 38000.00, 4500.00, 42, 15.8, NULL,
    NOW() + INTERVAL '2 hours', NULL, NULL, NOW() - INTERVAL '4 hours'
FROM driver_profiles dp
JOIN users u ON dp.driver_id = u.user_id
JOIN vehicles v ON v.driver_id = dp.driver_id
WHERE u.email = 'do.van.h@student.hcmut.edu.vn' AND v.plate_number = '59-A4 65432';

-- =====================================================
-- 9. EMERGENCY CONTACTS
-- =====================================================
INSERT INTO emergency_contacts (user_id, name, phone, relationship, is_primary, created_at)
SELECT user_id, 'Nguyen Thi X', '0988999000', 'Mother', true, NOW() FROM users WHERE email = 'nguyen.van.a@student.hcmut.edu.vn'
UNION ALL
SELECT user_id, 'Tran Van Y', '0977888999', 'Father', true, NOW() FROM users WHERE email = 'tran.thi.b@student.hcmut.edu.vn'
UNION ALL
SELECT user_id, 'Le Thi Z', '0966777888', 'Sister', true, NOW() FROM users WHERE email = 'le.van.c@student.hcmut.edu.vn'
UNION ALL
SELECT user_id, 'Pham Van M', '0955666777', 'Brother', true, NOW() FROM users WHERE email = 'pham.thi.d@student.hcmut.edu.vn'
UNION ALL
SELECT user_id, 'Hoang Thi N', '0944555666', 'Mother', true, NOW() FROM users WHERE email = 'hoang.van.e@student.hcmut.edu.vn'
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
INSERT INTO promotions (code, title, description, discount_type, discount_value, target_user_type, min_shared_ride_amount, max_discount, usage_limit, usage_limit_per_user, used_count, valid_from, valid_until, is_active, created_at)
VALUES
    ('HCMUT2024', 'HCMUT Student Discount', 'Special discount for HCMUT students', 'PERCENTAGE', 25.00, 'RIDER', 30000.00, 60000.00, 1500, 3, 287, NOW() - INTERVAL '60 days', NOW() + INTERVAL '305 days', true, NOW() - INTERVAL '60 days'),
    ('NEWDRIVER', 'New Driver Bonus', 'Welcome bonus for new drivers', 'FIXED_AMOUNT', 100000.00, 'DRIVER', 0.00, 100000.00, 200, 1, 45, NOW() - INTERVAL '45 days', NOW() + INTERVAL '320 days', true, NOW() - INTERVAL '45 days'),
    ('WEEKEND50', 'Weekend Special', '50k off for weekend rides', 'FIXED_AMOUNT', 50000.00, 'ALL', 80000.00, 50000.00, 3000, 5, 678, NOW() - INTERVAL '30 days', NOW() + INTERVAL '335 days', true, NOW() - INTERVAL '30 days'),
    ('MORNING20', 'Morning Commute', '20% off morning rides (6-9 AM)', 'PERCENTAGE', 20.00, 'RIDER', 25000.00, 40000.00, 5000, 10, 1234, NOW() - INTERVAL '90 days', NOW() + INTERVAL '275 days', true, NOW() - INTERVAL '90 days'),
    ('LONGRIDE', 'Long Distance Reward', '15% off rides over 15km', 'PERCENTAGE', 15.00, 'ALL', 100000.00, 80000.00, 2000, 5, 456, NOW() - INTERVAL '75 days', NOW() + INTERVAL '290 days', true, NOW() - INTERVAL '75 days');

-- =====================================================
-- SEED DATA COMPLETED
-- =====================================================
