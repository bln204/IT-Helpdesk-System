-- ============================================================
-- V18: Seed users với departments + Trưởng phòng mới
-- ============================================================

-- Password cho tất cả demo users: 12345678
-- BCrypt hash: $2y$05$7sqaEdZLuV8K0.qMkA44XeJ4S.4dnPdPUjap/i4xkwM6AnvmC3C5y

-- ============================================================
-- 1. TẠO ADMIN MỚI (Tách biệt - không thuộc phòng nào)
-- ============================================================
INSERT INTO users (username, password_hash, role, enabled, display_name, title, avatar_url, email, department_id)
VALUES (
    'admin', 
    '$2y$05$7sqaEdZLuV8K0.qMkA44XeJ4S.4dnPdPUjap/i4xkwM6AnvmC3C5y', 
    'ADMIN', 
    TRUE,
    'System Administrator', 
    'System Admin', 
    'https://i.pravatar.cc/150?img=1', 
    'admin@company.local',
    NULL  -- Admin tách biệt
)
ON CONFLICT (username) DO UPDATE SET
    password_hash = EXCLUDED.password_hash,
    role = EXCLUDED.role,
    enabled = EXCLUDED.enabled,
    display_name = EXCLUDED.display_name,
    title = EXCLUDED.title,
    avatar_url = EXCLUDED.avatar_url,
    email = EXCLUDED.email,
    department_id = NULL;

-- ============================================================
-- 2. TẠO TRUONG_PHONG MỚI CHO CÁC PHÒNG BAN
-- ============================================================

-- Trưởng phòng IT (có quyền assign tickets)
INSERT INTO users (username, password_hash, role, enabled, display_name, title, avatar_url, email, department_id)
VALUES (
    'tp.it', 
    '$2y$05$7sqaEdZLuV8K0.qMkA44XeJ4S.4dnPdPUjap/i4xkwM6AnvmC3C5y', 
    'TRUONG_PHONG', 
    TRUE,
    'Nguyễn Văn IT', 
    'IT Manager', 
    'https://i.pravatar.cc/150?img=51', 
    'it.manager@company.local',
    (SELECT id FROM departments WHERE code = 'IT')
)
ON CONFLICT (username) DO NOTHING;

-- Trưởng phòng HR
INSERT INTO users (username, password_hash, role, enabled, display_name, title, avatar_url, email, department_id)
VALUES (
    'tp.hr', 
    '$2y$05$7sqaEdZLuV8K0.qMkA44XeJ4S.4dnPdPUjap/i4xkwM6AnvmC3C5y', 
    'TRUONG_PHONG', 
    TRUE,
    'Trần Thị HR', 
    'HR Manager', 
    'https://i.pravatar.cc/150?img=52', 
    'hr.manager@company.local',
    (SELECT id FROM departments WHERE code = 'HR')
)
ON CONFLICT (username) DO NOTHING;

-- Trưởng phòng SALE
INSERT INTO users (username, password_hash, role, enabled, display_name, title, avatar_url, email, department_id)
VALUES (
    'tp.sale', 
    '$2y$05$7sqaEdZLuV8K0.qMkA44XeJ4S.4dnPdPUjap/i4xkwM6AnvmC3C5y', 
    'TRUONG_PHONG', 
    TRUE,
    'Lê Văn SALE', 
    'Sales Manager', 
    'https://i.pravatar.cc/150?img=53', 
    'sale.manager@company.local',
    (SELECT id FROM departments WHERE code = 'SALE')
)
ON CONFLICT (username) DO NOTHING;

-- Trưởng phòng MKT
INSERT INTO users (username, password_hash, role, enabled, display_name, title, avatar_url, email, department_id)
VALUES (
    'tp.mkt', 
    '$2y$05$7sqaEdZLuV8K0.qMkA44XeJ4S.4dnPdPUjap/i4xkwM6AnvmC3C5y', 
    'TRUONG_PHONG', 
    TRUE,
    'Phạm Thị MKT', 
    'Marketing Manager', 
    'https://i.pravatar.cc/150?img=54', 
    'mkt.manager@company.local',
    (SELECT id FROM departments WHERE code = 'MKT')
)
ON CONFLICT (username) DO NOTHING;

-- Trưởng phòng FIN
INSERT INTO users (username, password_hash, role, enabled, display_name, title, avatar_url, email, department_id)
VALUES (
    'tp.fin', 
    '$2y$05$7sqaEdZLuV8K0.qMkA44XeJ4S.4dnPdPUjap/i4xkwM6AnvmC3C5y', 
    'TRUONG_PHONG', 
    TRUE,
    'Hoàng Văn FIN', 
    'Finance Manager', 
    'https://i.pravatar.cc/150?img=55', 
    'fin.manager@company.local',
    (SELECT id FROM departments WHERE code = 'FIN')
)
ON CONFLICT (username) DO NOTHING;

-- ============================================================
-- 3. GÁN DEPARTMENT CHO CÁC USERS HIỆN TẠI (NHAN_VIEN)
-- ============================================================

-- IT Department - Nhân viên IT (từ ENGINEER cũ)
UPDATE users SET department_id = (SELECT id FROM departments WHERE code = 'IT')
WHERE username IN ('maya', 'liam', 'noah');

-- HR Department
UPDATE users SET department_id = (SELECT id FROM departments WHERE code = 'HR')
WHERE username = 'emma';

-- SALE Department
UPDATE users SET department_id = (SELECT id FROM departments WHERE code = 'SALE')
WHERE username = 'carlos';

-- MKT Department
UPDATE users SET department_id = (SELECT id FROM departments WHERE code = 'MKT')
WHERE username = 'zara';

-- ============================================================
-- 4. GÁN MANAGER (TRUONG_PHONG) CHO CÁC DEPARTMENTS
-- ============================================================

-- IT Manager
UPDATE departments SET manager_id = (SELECT id FROM users WHERE username = 'tp.it')
WHERE code = 'IT';

-- HR Manager
UPDATE departments SET manager_id = (SELECT id FROM users WHERE username = 'tp.hr')
WHERE code = 'HR';

-- SALE Manager
UPDATE departments SET manager_id = (SELECT id FROM users WHERE username = 'tp.sale')
WHERE code = 'SALE';

-- MKT Manager
UPDATE departments SET manager_id = (SELECT id FROM users WHERE username = 'tp.mkt')
WHERE code = 'MKT';

-- FIN Manager
UPDATE departments SET manager_id = (SELECT id FROM users WHERE username = 'tp.fin')
WHERE code = 'FIN';

-- Giám đốc (GIAM_DOC - từ ADMIN cũ) thuộc EXEC
UPDATE departments SET manager_id = (SELECT id FROM users WHERE role = 'GIAM_DOC' LIMIT 1)
WHERE code = 'EXEC';

-- ============================================================
-- 5. UPDATE TICKETS VỚI DEPARTMENT_ID
-- ============================================================

-- Mỗi ticket sẽ có department_id dựa trên department của requester
UPDATE tickets SET department_id = (
    SELECT u.department_id FROM users u WHERE u.username = tickets.requester_username
);

-- ============================================================
-- 6. THÊM DEMO NHAN_VIEN MỚI
-- ============================================================

-- IT Staff
INSERT INTO users (username, password_hash, role, enabled, display_name, title, avatar_url, email, department_id)
VALUES
('tech.smith', '$2y$05$7sqaEdZLuV8K0.qMkA44XeJ4S.4dnPdPUjap/i4xkwM6AnvmC3C5y', 'NHAN_VIEN', TRUE,
 'John Smith', 'Junior Developer', 'https://i.pravatar.cc/150?img=3', 'john.smith@company.local',
 (SELECT id FROM departments WHERE code = 'IT')),
('tech.jane', '$2y$05$7sqaEdZLuV8K0.qMkA44XeJ4S.4dnPdPUjap/i4xkwM6AnvmC3C5y', 'NHAN_VIEN', TRUE,
 'Jane Doe', 'DevOps Engineer', 'https://i.pravatar.cc/150?img=9', 'jane.doe@company.local',
 (SELECT id FROM departments WHERE code = 'IT'))
ON CONFLICT (username) DO NOTHING;

-- HR Staff
INSERT INTO users (username, password_hash, role, enabled, display_name, title, avatar_url, email, department_id)
VALUES
('hr.tina', '$2y$05$7sqaEdZLuV8K0.qMkA44XeJ4S.4dnPdPUjap/i4xkwM6AnvmC3C5y', 'NHAN_VIEN', TRUE,
 'Tina Williams', 'HR Coordinator', 'https://i.pravatar.cc/150?img=20', 'tina.williams@company.local',
 (SELECT id FROM departments WHERE code = 'HR')),
('hr.bob', '$2y$05$7sqaEdZLuV8K0.qMkA44XeJ4S.4dnPdPUjap/i4xkwM6AnvmC3C5y', 'NHAN_VIEN', TRUE,
 'Bob Johnson', 'Recruiter', 'https://i.pravatar.cc/150?img=11', 'bob.johnson@company.local',
 (SELECT id FROM departments WHERE code = 'HR'))
ON CONFLICT (username) DO NOTHING;

-- SALE Staff
INSERT INTO users (username, password_hash, role, enabled, display_name, title, avatar_url, email, department_id)
VALUES
('sale.alice', '$2y$05$7sqaEdZLuV8K0.qMkA44XeJ4S.4dnPdPUjap/i4xkwM6AnvmC3C5y', 'NHAN_VIEN', TRUE,
 'Alice Brown', 'Sales Representative', 'https://i.pravatar.cc/150?img=25', 'alice.brown@company.local',
 (SELECT id FROM departments WHERE code = 'SALE')),
('sale.david', '$2y$05$7sqaEdZLuV8K0.qMkA44XeJ4S.4dnPdPUjap/i4xkwM6AnvmC3C5y', 'NHAN_VIEN', TRUE,
 'David Lee', 'Account Manager', 'https://i.pravatar.cc/150?img=8', 'david.lee@company.local',
 (SELECT id FROM departments WHERE code = 'SALE'))
ON CONFLICT (username) DO NOTHING;

-- MKT Staff
INSERT INTO users (username, password_hash, role, enabled, display_name, title, avatar_url, email, department_id)
VALUES
('mkt.chris', '$2y$05$7sqaEdZLuV8K0.qMkA44XeJ4S.4dnPdPUjap/i4xkwM6AnvmC3C5y', 'NHAN_VIEN', TRUE,
 'Chris Taylor', 'Marketing Specialist', 'https://i.pravatar.cc/150?img=30', 'chris.taylor@company.local',
 (SELECT id FROM departments WHERE code = 'MKT')),
('mkt.eva', '$2y$05$7sqaEdZLuV8K0.qMkA44XeJ4S.4dnPdPUjap/i4xkwM6AnvmC3C5y', 'NHAN_VIEN', TRUE,
 'Eva Martinez', 'Content Creator', 'https://i.pravatar.cc/150?img=35', 'eva.martinez@company.local',
 (SELECT id FROM departments WHERE code = 'MKT'))
ON CONFLICT (username) DO NOTHING;

-- FIN Staff
INSERT INTO users (username, password_hash, role, enabled, display_name, title, avatar_url, email, department_id)
VALUES
('fin.anna', '$2y$05$7sqaEdZLuV8K0.qMkA44XeJ4S.4dnPdPUjap/i4xkwM6AnvmC3C5y', 'NHAN_VIEN', TRUE,
 'Anna Nguyen', 'Accountant', 'https://i.pravatar.cc/150?img=40', 'anna.nguyen@company.local',
 (SELECT id FROM departments WHERE code = 'FIN')),
('fin.tom', '$2y$05$7sqaEdZLuV8K0.qMkA44XeJ4S.4dnPdPUjap/i4xkwM6AnvmC3C5y', 'NHAN_VIEN', TRUE,
 'Tom Tran', 'Financial Analyst', 'https://i.pravatar.cc/150?img=45', 'tom.tran@company.local',
 (SELECT id FROM departments WHERE code = 'FIN'))
ON CONFLICT (username) DO NOTHING;
