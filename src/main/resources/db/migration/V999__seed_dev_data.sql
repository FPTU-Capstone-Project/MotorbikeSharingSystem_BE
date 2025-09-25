-- V999__seed_dev_data.sql
-- Development seed data (only runs in dev profile)

-- Insert test admin user
INSERT INTO users (email, phone, password_hash, full_name, user_type, is_active, email_verified, phone_verified)
VALUES (
    'admin@mssus.com', 
    '0900000001', 
    '$2a$10$N.zmdr9k7uOIW25A.sB8Ou4bJdLCiNkbGSjCJJjZYpjyLqKZRZQAG', -- password: Password1!
    'System Administrator', 
    'admin', 
    true, 
    true, 
    true
) ON CONFLICT (email) DO NOTHING;

-- Insert admin profile
INSERT INTO admin_profiles (admin_id, department, permissions)
SELECT user_id, 'IT Department', '["USER_MANAGEMENT", "VERIFICATION_APPROVAL", "SYSTEM_ADMIN"]'
FROM users WHERE email = 'admin@mssus.com'
ON CONFLICT (admin_id) DO NOTHING;

-- Insert test rider user
INSERT INTO users (email, phone, password_hash, full_name, user_type, is_active, email_verified, phone_verified)
VALUES (
    'rider@student.edu', 
    '0900000002', 
    '$2a$10$N.zmdr9k7uOIW25A.sB8Ou4bJdLCiNkbGSjCJJjZYpjyLqKZRZQAG', -- password: Admin123!
    'Test Rider', 
    'student', 
    true, 
    true, 
    true
) ON CONFLICT (email) DO NOTHING;

-- Insert rider profile
INSERT INTO rider_profiles (rider_id, emergency_contact, preferred_payment_method)
SELECT user_id, '+84909999888', 'wallet'
FROM users WHERE email = 'rider@student.edu'
ON CONFLICT (rider_id) DO NOTHING;

-- Insert wallet for rider
INSERT INTO wallets (user_id, cached_balance, is_active)
SELECT user_id, 100000, true
FROM users WHERE email = 'rider@student.edu'
ON CONFLICT (user_id) DO NOTHING;

-- Insert test driver user
INSERT INTO users (email, phone, password_hash, full_name, user_type, is_active, email_verified, phone_verified)
VALUES (
    'driver@student.edu', 
    '0900000003', 
    '$2a$10$N.zmdr9k7uOIW25A.sB8Ou4bJdLCiNkbGSjCJJjZYpjyLqKZRZQAG', -- password: Admin123!
    'Test Driver', 
    'student', 
    true, 
    true, 
    true
) ON CONFLICT (email) DO NOTHING;

-- Insert rider profile for driver (drivers also have rider profiles)
INSERT INTO rider_profiles (rider_id, emergency_contact, preferred_payment_method)
SELECT user_id, '+84909999777', 'wallet'
FROM users WHERE email = 'driver@student.edu'
ON CONFLICT (rider_id) DO NOTHING;

-- Insert driver profile
INSERT INTO driver_profiles (driver_id, license_number, status, is_available)
SELECT user_id, 'B12345678', 'active', true
FROM users WHERE email = 'driver@student.edu'
ON CONFLICT (driver_id) DO NOTHING;

-- Insert wallet for driver
INSERT INTO wallets (user_id, cached_balance, is_active)
SELECT user_id, 50000, true
FROM users WHERE email = 'driver@student.edu'
ON CONFLICT (user_id) DO NOTHING;

-- Insert test vehicle for driver
INSERT INTO vehicles (driver_id, plate_number, model, color, year, insurance_expiry)
SELECT dp.driver_id, '59A-12345', 'Honda Wave', 'Black', 2020, CURRENT_TIMESTAMP + INTERVAL '1 year'
FROM driver_profiles dp
JOIN users u ON u.user_id = dp.driver_id
WHERE u.email = 'driver@student.edu'
ON CONFLICT (plate_number) DO NOTHING;


