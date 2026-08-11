-- ================================================================
-- V2: Dữ liệu khởi tạo (roles, admin mặc định, loại sự cố mẫu)
-- ================================================================

INSERT INTO roles (name, description) VALUES
    ('ADMIN', 'Quản trị hệ thống'),
    ('MANAGER', 'Quản lý / trưởng nhóm ATTT'),
    ('HELPDESK', 'Cán bộ tiếp nhận sự cố'),
    ('ANALYST', 'Chuyên viên xử lý sự cố'),
    ('REPORTER', 'Người khai báo sự cố');

-- Tài khoản admin mặc định: username = admin / password = Admin@123
-- ĐỔI MẬT KHẨU NGAY sau khi triển khai thực tế!
INSERT INTO users (username, password, email, full_name, department, enabled)
VALUES ('admin', '$2b$10$TQ1B5UvVETjjqKOsPtAqG.tGCIQEJKAtL0HVTIcSEqp8ZBhjpF3ca',
        'admin@example.com', 'Quản trị viên hệ thống', 'IT/ATTT', TRUE);

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.username = 'admin' AND r.name = 'ADMIN';

-- Danh mục loại sự cố mẫu
INSERT INTO incident_categories (name, description, default_severity) VALUES
    ('Mã độc (Malware)', 'Phát hiện virus, ransomware, trojan trên hệ thống', 'HIGH'),
    ('Tấn công mạng (Network Attack)', 'DDoS, xâm nhập trái phép qua mạng', 'CRITICAL'),
    ('Rò rỉ dữ liệu (Data Leak)', 'Lộ lọt dữ liệu nội bộ, dữ liệu khách hàng', 'CRITICAL'),
    ('Phishing', 'Email/website giả mạo nhằm đánh cắp thông tin', 'MEDIUM'),
    ('Truy cập trái phép', 'Đăng nhập/truy cập hệ thống không được phép', 'HIGH'),
    ('Lỗi hệ thống', 'Sự cố kỹ thuật ảnh hưởng đến vận hành, không do tấn công', 'LOW');
