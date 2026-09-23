-- ============================================================
-- V15: Thêm department_id vào users
-- ============================================================

-- Thêm cột department_id (nullable cho Admin tách biệt)
ALTER TABLE users ADD COLUMN department_id BIGINT;

-- Thêm foreign key constraint
ALTER TABLE users ADD CONSTRAINT fk_users_department 
    FOREIGN KEY (department_id) REFERENCES departments(id);

-- Index cho performance
CREATE INDEX idx_users_department ON users(department_id);

COMMENT ON COLUMN users.department_id IS 'Phòng ban của user (NULL nếu là Admin tách biệt)';

-- NOTE: Admin sẽ có department_id = NULL (tách biệt không thuộc phòng nào)
-- Giám đốc, Trưởng phòng, Nhân viên sẽ có department_id tương ứng
-- Sẽ update trong V18 sau khi seed users
