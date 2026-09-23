-- ============================================================
-- V24: Insert or update IT staff members
-- ============================================================

-- Password: 12345678
-- BCrypt hash: $2y$05$7sqaEdZLuV8K0.qMkA44XeJ4S.4dnPdPUjap/i4xkwM6AnvmC3C5y

-- Insert tech.smith if not exists
INSERT INTO users (username, password_hash, role, enabled, display_name, title, avatar_url, email, department_id)
VALUES (
    'tech.smith', 
    '$2y$05$7sqaEdZLuV8K0.qMkA44XeJ4S.4dnPdPUjap/i4xkwM6AnvmC3C5y', 
    'NHAN_VIEN', 
    TRUE,
    'John Smith', 
    'Junior Developer', 
    'https://i.pravatar.cc/150?img=3', 
    'john.smith@company.local',
    (SELECT id FROM departments WHERE code = 'IT')
)
ON CONFLICT (username) DO UPDATE SET
    enabled = TRUE,
    department_id = (SELECT id FROM departments WHERE code = 'IT');

-- Insert tech.jane if not exists
INSERT INTO users (username, password_hash, role, enabled, display_name, title, avatar_url, email, department_id)
VALUES (
    'tech.jane', 
    '$2y$05$7sqaEdZLuV8K0.qMkA44XeJ4S.4dnPdPUjap/i4xkwM6AnvmC3C5y', 
    'NHAN_VIEN', 
    TRUE,
    'Jane Doe', 
    'DevOps Engineer', 
    'https://i.pravatar.cc/150?img=9', 
    'jane.doe@company.local',
    (SELECT id FROM departments WHERE code = 'IT')
)
ON CONFLICT (username) DO UPDATE SET
    enabled = TRUE,
    department_id = (SELECT id FROM departments WHERE code = 'IT');
