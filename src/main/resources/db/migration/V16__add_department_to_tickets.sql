-- ============================================================
-- V16: Thêm department_id vào tickets
-- ============================================================

-- Thêm cột department_id
ALTER TABLE tickets ADD COLUMN department_id BIGINT;

-- Thêm foreign key constraint
ALTER TABLE tickets ADD CONSTRAINT fk_tickets_department 
    FOREIGN KEY (department_id) REFERENCES departments(id);

-- Index cho performance
CREATE INDEX idx_tickets_department ON tickets(department_id);

-- Composite indexes cho các query thường dùng
CREATE INDEX idx_tickets_dept_status ON tickets(department_id, status);
CREATE INDEX idx_tickets_dept_priority ON tickets(department_id, priority);

COMMENT ON COLUMN tickets.department_id IS 'Phòng ban của người tạo ticket (lấy từ user.department_id)';
