-- ============================================================
-- V22: Add missing GIAM_DOC user (mayagiamdoc)
-- ============================================================

-- Password: 12345678
-- BCrypt hash: $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZRGdjGj/n3.rsS7/kXwQiP8x8qWKi

INSERT INTO users (username, password_hash, role, enabled, display_name, title, avatar_url, email, department_id)
VALUES (
    'mayagiamdoc',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZRGdjGj/n3.rsS7/kXwQiP8x8qWKi',
    'GIAM_DOC',
    TRUE,
    'Maya Giám Đốc',
    'Chief Executive Officer',
    'https://i.pravatar.cc/150?img=5',
    'ceo@company.local',
    (SELECT id FROM departments WHERE code = 'EXEC')
)
ON CONFLICT (username) DO UPDATE SET
    password_hash = EXCLUDED.password_hash,
    role = EXCLUDED.role,
    enabled = EXCLUDED.enabled,
    display_name = EXCLUDED.display_name,
    title = EXCLUDED.title,
    avatar_url = EXCLUDED.avatar_url,
    email = EXCLUDED.email,
    department_id = EXCLUDED.department_id;
