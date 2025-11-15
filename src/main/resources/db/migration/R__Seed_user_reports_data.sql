-- R__Seed_user_reports_data.sql
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
DO $$
DECLARE
    max_id INTEGER;
BEGIN
    SELECT COALESCE(MAX(report_id), 0) INTO max_id FROM user_reports;
    EXECUTE format('ALTER SEQUENCE IF EXISTS user_reports_report_id_seq RESTART WITH %s', max_id + 1);
END $$;

-- =====================================================
-- 1. GENERAL USER REPORTS (No ride-specific)
-- =====================================================

-- PENDING general report (HIGH priority)
INSERT INTO user_reports (reporter_id, report_type, status, priority, description, created_at, updated_at)
SELECT user_id, 'TECHNICAL', 'OPEN', 'HIGH',
       'Ứng dụng bị crash khi tôi cố gắng xem lịch sử chuyến đi. Đã thử khởi động lại nhưng vẫn lỗi.',
       NOW() - INTERVAL '2 days',
       NOW() - INTERVAL '2 days'
FROM users
WHERE email = 'nguyen.van.a@student.hcmut.edu.vn'
LIMIT 1;

-- IN_PROGRESS general report (MEDIUM priority)
INSERT INTO user_reports (reporter_id, report_type, status, priority, description, admin_notes, created_at, updated_at)
SELECT user_id, 'TECHNICAL', 'IN_PROGRESS', 'MEDIUM',
       'Không thể cập nhật thông tin cá nhân. Form bị lỗi validation.',
       'Đang kiểm tra vấn đề với form validation. Sẽ sửa trong bản cập nhật tiếp theo.',
       NOW() - INTERVAL '5 days',
       NOW() - INTERVAL '1 day'
FROM users
WHERE email = 'tran.thi.b@student.hcmut.edu.vn'
LIMIT 1;

-- RESOLVED general report (LOW priority)
INSERT INTO user_reports (reporter_id, resolver_id, report_type, status, priority, description, resolution_message, resolved_at, created_at, updated_at)
SELECT 
    (SELECT user_id FROM users WHERE email = 'le.van.c@student.hcmut.edu.vn' LIMIT 1),
    (SELECT user_id FROM users WHERE email = 'admin@mssus.com' LIMIT 1),
    'OTHER', 'RESOLVED', 'LOW',
    'Đề xuất thêm tính năng lọc chuyến đi theo ngày tháng.',
    'Cảm ơn bạn đã đề xuất! Tính năng này đã được thêm vào trong phiên bản 2.1. Bạn có thể sử dụng ngay bây giờ.',
    NOW() - INTERVAL '3 days',
    NOW() - INTERVAL '10 days',
    NOW() - INTERVAL '3 days'
LIMIT 1;

-- DISMISSED general report (MEDIUM priority)
INSERT INTO user_reports (reporter_id, resolver_id, report_type, status, priority, description, admin_notes, resolution_message, resolved_at, created_at, updated_at)
SELECT 
    (SELECT user_id FROM users WHERE email = 'pham.thi.d@student.hcmut.edu.vn' LIMIT 1),
    (SELECT user_id FROM users WHERE email = 'admin@mssus.com' LIMIT 1),
    'OTHER', 'DISMISSED', 'MEDIUM',
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
INSERT INTO user_reports (reporter_id, shared_ride_id, driver_id, report_type, status, priority, description, created_at, updated_at)
SELECT 
    (SELECT user_id FROM users WHERE email = 'nguyen.van.a@student.hcmut.edu.vn' LIMIT 1),
    (SELECT shared_ride_id FROM shared_rides ORDER BY shared_ride_id LIMIT 1 OFFSET 0), -- Use first ride if exists
    (SELECT dp.driver_id FROM driver_profiles dp 
     JOIN users u ON dp.driver_id = u.user_id 
     WHERE u.email = 'vo.van.f@student.hcmut.edu.vn' LIMIT 1),
    'SAFETY', 'PENDING', 'CRITICAL',
    'Tài xế lái xe rất nguy hiểm, vượt đèn đỏ và không tuân thủ luật giao thông. Tôi cảm thấy không an toàn trong suốt chuyến đi.',
    NOW() - INTERVAL '1 day',
    NOW() - INTERVAL '1 day'
LIMIT 1;

-- PENDING ride report (HIGH priority - Behavior issue)
INSERT INTO user_reports (reporter_id, shared_ride_id, driver_id, report_type, status, priority, description, created_at, updated_at)
SELECT 
    (SELECT user_id FROM users WHERE email = 'tran.thi.b@student.hcmut.edu.vn' LIMIT 1),
    (SELECT shared_ride_id FROM shared_rides ORDER BY shared_ride_id LIMIT 1 OFFSET 1), -- Use second ride if exists
    (SELECT dp.driver_id FROM driver_profiles dp 
     JOIN users u ON dp.driver_id = u.user_id 
     WHERE u.email = 'dang.thi.g@student.hcmut.edu.vn' LIMIT 1),
    'BEHAVIOR', 'PENDING', 'HIGH',
    'Tài xế đến muộn 20 phút và không xin lỗi. Thái độ không chuyên nghiệp, nói chuyện điện thoại trong khi lái xe.',
    NOW() - INTERVAL '3 days',
    NOW() - INTERVAL '3 days'
LIMIT 1;

-- IN_PROGRESS ride report (MEDIUM priority - Payment issue)
INSERT INTO user_reports (reporter_id, shared_ride_id, driver_id, report_type, status, priority, description, admin_notes, created_at, updated_at)
SELECT 
    (SELECT user_id FROM users WHERE email = 'le.van.c@student.hcmut.edu.vn' LIMIT 1),
    (SELECT shared_ride_id FROM shared_rides ORDER BY shared_ride_id LIMIT 1 OFFSET 2), -- Use third ride if exists
    (SELECT dp.driver_id FROM driver_profiles dp 
     JOIN users u ON dp.driver_id = u.user_id 
     WHERE u.email = 'do.van.h@student.hcmut.edu.vn' LIMIT 1),
    'PAYMENT', 'IN_PROGRESS', 'MEDIUM',
    'Tôi đã thanh toán nhưng hệ thống vẫn hiển thị chưa thanh toán. Số tiền đã bị trừ khỏi ví của tôi.',
    'Đang kiểm tra giao dịch thanh toán. Liên hệ với bộ phận tài chính để xác minh.',
    NOW() - INTERVAL '4 days',
    NOW() - INTERVAL '1 day'
LIMIT 1;

-- IN_PROGRESS ride report with driver response (HIGH priority - Route issue)
INSERT INTO user_reports (reporter_id, shared_ride_id, driver_id, report_type, status, priority, description, driver_response, driver_responded_at, admin_notes, created_at, updated_at)
SELECT 
    (SELECT user_id FROM users WHERE email = 'pham.thi.d@student.hcmut.edu.vn' LIMIT 1),
    (SELECT shared_ride_id FROM shared_rides ORDER BY shared_ride_id LIMIT 1 OFFSET 3), -- Use fourth ride if exists
    (SELECT dp.driver_id FROM driver_profiles dp 
     JOIN users u ON dp.driver_id = u.user_id 
     WHERE u.email = 'bui.thi.i@student.hcmut.edu.vn' LIMIT 1),
    'ROUTE', 'IN_PROGRESS', 'HIGH',
    'Tài xế không đi đúng tuyến đường đã hẹn. Đi đường vòng và làm tôi đến muộn 30 phút.',
    'Tôi xin lỗi vì sự bất tiện. Tuyến đường chính bị tắc nghẽn do tai nạn, nên tôi phải đi đường vòng để tránh. Tôi đã cố gắng thông báo nhưng không liên lạc được.',
    NOW() - INTERVAL '2 days',
    'Đã nhận được phản hồi từ tài xế. Đang xác minh tình trạng giao thông tại thời điểm đó.',
    NOW() - INTERVAL '5 days',
    NOW() - INTERVAL '1 day'
LIMIT 1;

-- RESOLVED ride report (MEDIUM priority - Behavior issue)
INSERT INTO user_reports (reporter_id, resolver_id, shared_ride_id, driver_id, report_type, status, priority, description, resolution_message, resolved_at, created_at, updated_at)
SELECT 
    (SELECT user_id FROM users WHERE email = 'hoang.van.e@student.hcmut.edu.vn' LIMIT 1),
    (SELECT user_id FROM users WHERE email = 'admin@mssus.com' LIMIT 1),
    (SELECT shared_ride_id FROM shared_rides ORDER BY shared_ride_id LIMIT 1 OFFSET 4), -- Use fifth ride if exists
    (SELECT dp.driver_id FROM driver_profiles dp 
     JOIN users u ON dp.driver_id = u.user_id 
     WHERE u.email = 'vo.van.f@student.hcmut.edu.vn' LIMIT 1),
    'BEHAVIOR', 'RESOLVED', 'MEDIUM',
    'Tài xế hút thuốc trong xe khi có hành khách. Điều này rất không phù hợp.',
    'Cảm ơn bạn đã báo cáo. Chúng tôi đã cảnh báo tài xế về hành vi này. Tài xế đã cam kết không tái phạm. Nếu vấn đề tiếp tục, vui lòng báo cáo lại.',
    NOW() - INTERVAL '2 days',
    NOW() - INTERVAL '8 days',
    NOW() - INTERVAL '2 days'
LIMIT 1;

-- RESOLVED ride report with driver response (LOW priority - Ride experience)
INSERT INTO user_reports (reporter_id, resolver_id, shared_ride_id, driver_id, report_type, status, priority, description, driver_response, driver_responded_at, resolution_message, resolved_at, created_at, updated_at)
SELECT 
    (SELECT user_id FROM users WHERE email = 'nguyen.van.a@student.hcmut.edu.vn' LIMIT 1),
    (SELECT user_id FROM users WHERE email = 'admin@mssus.com' LIMIT 1),
    (SELECT shared_ride_id FROM shared_rides ORDER BY shared_ride_id LIMIT 1 OFFSET 5), -- Use sixth ride if exists
    (SELECT dp.driver_id FROM driver_profiles dp 
     JOIN users u ON dp.driver_id = u.user_id 
     WHERE u.email = 'dang.thi.g@student.hcmut.edu.vn' LIMIT 1),
    'RIDE_EXPERIENCE', 'RESOLVED', 'LOW',
    'Xe có mùi khó chịu và không sạch sẽ.',
    'Tôi xin lỗi về vấn đề này. Tôi đã vệ sinh xe kỹ lưỡng sau chuyến đi và sẽ chú ý hơn trong tương lai.',
    NOW() - INTERVAL '5 days',
    'Tài xế đã phản hồi và cam kết cải thiện. Vấn đề đã được giải quyết.',
    NOW() - INTERVAL '4 days',
    NOW() - INTERVAL '10 days',
    NOW() - INTERVAL '4 days'
LIMIT 1;

-- DISMISSED ride report (MEDIUM priority - Payment issue)
INSERT INTO user_reports (reporter_id, resolver_id, shared_ride_id, driver_id, report_type, status, priority, description, admin_notes, resolution_message, resolved_at, created_at, updated_at)
SELECT 
    (SELECT user_id FROM users WHERE email = 'tran.thi.b@student.hcmut.edu.vn' LIMIT 1),
    (SELECT user_id FROM users WHERE email = 'admin@mssus.com' LIMIT 1),
    (SELECT shared_ride_id FROM shared_rides ORDER BY shared_ride_id LIMIT 1 OFFSET 6), -- Use seventh ride if exists
    (SELECT dp.driver_id FROM driver_profiles dp 
     JOIN users u ON dp.driver_id = u.user_id 
     WHERE u.email = 'do.van.h@student.hcmut.edu.vn' LIMIT 1),
    'PAYMENT', 'DISMISSED', 'MEDIUM',
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
INSERT INTO user_reports (reporter_id, shared_ride_id, driver_id, report_type, status, priority, description, escalated_at, escalation_reason, created_at, updated_at)
SELECT 
    (SELECT user_id FROM users WHERE email = 'le.van.c@student.hcmut.edu.vn' LIMIT 1),
    (SELECT shared_ride_id FROM shared_rides ORDER BY shared_ride_id LIMIT 1 OFFSET 7), -- Use eighth ride if exists
    (SELECT dp.driver_id FROM driver_profiles dp 
     JOIN users u ON dp.driver_id = u.user_id 
     WHERE u.email = 'bui.thi.i@student.hcmut.edu.vn' LIMIT 1),
    'SAFETY', 'PENDING', 'HIGH',
    'Tài xế lái xe quá nhanh và không chú ý đến an toàn. Tôi rất lo lắng.',
    NOW() - INTERVAL '1 day',
    'Automatically escalated: No resolution within 48 hours',
    NOW() - INTERVAL '3 days',
    NOW() - INTERVAL '1 day'
LIMIT 1;

-- Escalated report (was MEDIUM, now HIGH priority)
INSERT INTO user_reports (reporter_id, report_type, status, priority, description, escalated_at, escalation_reason, created_at, updated_at)
SELECT 
    (SELECT user_id FROM users WHERE email = 'pham.thi.d@student.hcmut.edu.vn' LIMIT 1),
    'TECHNICAL', 'OPEN', 'HIGH',
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
INSERT INTO user_reports (reporter_id, shared_ride_id, driver_id, report_type, status, priority, description, created_at, updated_at)
SELECT 
    (SELECT user_id FROM users WHERE email = 'hoang.van.e@student.hcmut.edu.vn' LIMIT 1),
    (SELECT shared_ride_id FROM shared_rides ORDER BY shared_ride_id LIMIT 1 OFFSET 8), -- Use ninth ride if exists
    (SELECT dp.driver_id FROM driver_profiles dp 
     JOIN users u ON dp.driver_id = u.user_id 
     WHERE u.email = 'vo.van.f@student.hcmut.edu.vn' LIMIT 1),
    'SAFETY', 'PENDING', 'CRITICAL',
    'Tài xế có dấu hiệu say rượu. Mùi rượu rất nồng và lái xe không ổn định. Tôi yêu cầu dừng xe ngay lập tức.',
    NOW() - INTERVAL '2 hours',
    NOW() - INTERVAL '2 hours'
LIMIT 1;

-- Recent OPEN report (HIGH priority)
INSERT INTO user_reports (reporter_id, report_type, status, priority, description, created_at, updated_at)
SELECT 
    (SELECT user_id FROM users WHERE email = 'nguyen.van.a@student.hcmut.edu.vn' LIMIT 1),
    'PAYMENT', 'OPEN', 'HIGH',
    'Giao dịch thanh toán bị lỗi. Tiền đã bị trừ nhưng chuyến đi không được xác nhận.',
    NOW() - INTERVAL '5 hours',
    NOW() - INTERVAL '5 hours'
LIMIT 1;

-- Recent PENDING report (MEDIUM priority)
INSERT INTO user_reports (reporter_id, shared_ride_id, driver_id, report_type, status, priority, description, created_at, updated_at)
SELECT 
    (SELECT user_id FROM users WHERE email = 'tran.thi.b@student.hcmut.edu.vn' LIMIT 1),
    (SELECT shared_ride_id FROM shared_rides ORDER BY shared_ride_id LIMIT 1 OFFSET 9), -- Use tenth ride if exists
    (SELECT dp.driver_id FROM driver_profiles dp 
     JOIN users u ON dp.driver_id = u.user_id 
     WHERE u.email = 'dang.thi.g@student.hcmut.edu.vn' LIMIT 1),
    'ROUTE', 'PENDING', 'MEDIUM',
    'Tài xế đi sai đường một chút nhưng đã sửa lại kịp thời. Chỉ muốn báo cáo để cải thiện.',
    NOW() - INTERVAL '1 day',
    NOW() - INTERVAL '1 day'
LIMIT 1;

-- =====================================================
-- 5. VARIOUS REPORT TYPES
-- =====================================================

-- RIDE_EXPERIENCE report
INSERT INTO user_reports (reporter_id, shared_ride_id, driver_id, report_type, status, priority, description, created_at, updated_at)
SELECT 
    (SELECT user_id FROM users WHERE email = 'le.van.c@student.hcmut.edu.vn' LIMIT 1),
    (SELECT shared_ride_id FROM shared_rides ORDER BY shared_ride_id LIMIT 1 OFFSET 10), -- Use eleventh ride if exists
    (SELECT dp.driver_id FROM driver_profiles dp 
     JOIN users u ON dp.driver_id = u.user_id 
     WHERE u.email = 'do.van.h@student.hcmut.edu.vn' LIMIT 1),
    'RIDE_EXPERIENCE', 'PENDING', 'LOW',
    'Chuyến đi khá tốt nhưng có thể cải thiện về thời gian đợi.',
    NOW() - INTERVAL '2 days',
    NOW() - INTERVAL '2 days'
LIMIT 1;

-- TECHNICAL report (general)
INSERT INTO user_reports (reporter_id, report_type, status, priority, description, created_at, updated_at)
SELECT 
    (SELECT user_id FROM users WHERE email = 'pham.thi.d@student.hcmut.edu.vn' LIMIT 1),
    'TECHNICAL', 'OPEN', 'MEDIUM',
    'Thông báo push notification không hoạt động. Tôi không nhận được thông báo về chuyến đi.',
    NOW() - INTERVAL '3 days',
    NOW() - INTERVAL '3 days'
LIMIT 1;

-- OTHER report
INSERT INTO user_reports (reporter_id, report_type, status, priority, description, created_at, updated_at)
SELECT 
    (SELECT user_id FROM users WHERE email = 'hoang.van.e@student.hcmut.edu.vn' LIMIT 1),
    'OTHER', 'OPEN', 'LOW',
    'Đề xuất thêm tính năng đánh giá chi tiết hơn cho tài xế sau mỗi chuyến đi.',
    NOW() - INTERVAL '4 days',
    NOW() - INTERVAL '4 days'
LIMIT 1;

-- =====================================================
-- 6. REPORTS WITH DIFFERENT PRIORITIES
-- =====================================================

-- LOW priority report
INSERT INTO user_reports (reporter_id, report_type, status, priority, description, created_at, updated_at)
SELECT 
    (SELECT user_id FROM users WHERE email = 'nguyen.van.a@student.hcmut.edu.vn' LIMIT 1),
    'OTHER', 'OPEN', 'LOW',
    'Gợi ý: Có thể thêm tính năng chia sẻ vị trí với bạn bè không?',
    NOW() - INTERVAL '6 days',
    NOW() - INTERVAL '6 days'
LIMIT 1;

-- MEDIUM priority report (default)
INSERT INTO user_reports (reporter_id, shared_ride_id, driver_id, report_type, status, priority, description, created_at, updated_at)
SELECT 
    (SELECT user_id FROM users WHERE email = 'tran.thi.b@student.hcmut.edu.vn' LIMIT 1),
    (SELECT shared_ride_id FROM shared_rides ORDER BY shared_ride_id LIMIT 1 OFFSET 11), -- Use twelfth ride if exists
    (SELECT dp.driver_id FROM driver_profiles dp 
     JOIN users u ON dp.driver_id = u.user_id 
     WHERE u.email = 'bui.thi.i@student.hcmut.edu.vn' LIMIT 1),
    'BEHAVIOR', 'PENDING', 'MEDIUM',
    'Tài xế hơi vội vàng khi lái xe. Có thể lái cẩn thận hơn một chút.',
    NOW() - INTERVAL '1 day',
    NOW() - INTERVAL '1 day'
LIMIT 1;

-- HIGH priority report
INSERT INTO user_reports (reporter_id, shared_ride_id, driver_id, report_type, status, priority, description, created_at, updated_at)
SELECT 
    (SELECT user_id FROM users WHERE email = 'le.van.c@student.hcmut.edu.vn' LIMIT 1),
    (SELECT shared_ride_id FROM shared_rides ORDER BY shared_ride_id LIMIT 1 OFFSET 12), -- Use thirteenth ride if exists
    (SELECT dp.driver_id FROM driver_profiles dp 
     JOIN users u ON dp.driver_id = u.user_id 
     WHERE u.email = 'vo.van.f@student.hcmut.edu.vn' LIMIT 1),
    'SAFETY', 'PENDING', 'HIGH',
    'Tài xế không đội mũ bảo hiểm và yêu cầu tôi cũng không đội. Điều này rất nguy hiểm.',
    NOW() - INTERVAL '4 hours',
    NOW() - INTERVAL '4 hours'
LIMIT 1;

-- CRITICAL priority report
INSERT INTO user_reports (reporter_id, shared_ride_id, driver_id, report_type, status, priority, description, escalated_at, escalation_reason, created_at, updated_at)
SELECT 
    (SELECT user_id FROM users WHERE email = 'pham.thi.d@student.hcmut.edu.vn' LIMIT 1),
    (SELECT shared_ride_id FROM shared_rides ORDER BY shared_ride_id LIMIT 1 OFFSET 13), -- Use fourteenth ride if exists
    (SELECT dp.driver_id FROM driver_profiles dp 
     JOIN users u ON dp.driver_id = u.user_id 
     WHERE u.email = 'dang.thi.g@student.hcmut.edu.vn' LIMIT 1),
    'SAFETY', 'PENDING', 'CRITICAL',
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

