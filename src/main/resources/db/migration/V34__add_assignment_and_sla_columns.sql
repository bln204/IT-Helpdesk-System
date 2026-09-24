-- ============================================================
-- V34: Thêm các cột mới cho assignment và SLA vào bảng tickets
-- ============================================================

-- 1. Thêm assignee_id để thay thế assignee_name (String -> FK)
ALTER TABLE tickets ADD COLUMN assignee_id BIGINT REFERENCES users(id);

-- 2. Thêm team_id cho multi-level assignment
ALTER TABLE tickets ADD COLUMN team_id BIGINT REFERENCES teams(id);

-- 3. Thêm category_id để link với bảng categories mới
ALTER TABLE tickets ADD COLUMN category_id BIGINT REFERENCES categories(id);

-- 4. SLA Fields
-- Response time SLA (thời gian phản hồi tối đa)
ALTER TABLE tickets ADD COLUMN sla_response_at TIMESTAMP;

-- Resolution time SLA (thời gian giải quyết tối đa)
ALTER TABLE tickets ADD COLUMN sla_resolution_at TIMESTAMP;

-- 5. Đếm số lần escalate
ALTER TABLE tickets ADD COLUMN escalation_count INT DEFAULT 0;

-- 6. Đếm số lần reopen
ALTER TABLE tickets ADD COLUMN reopen_count INT DEFAULT 0;

-- 7. Subcategory - liên kết với category (nếu là subcategory)
ALTER TABLE tickets ADD COLUMN subcategory_id BIGINT REFERENCES categories(id);

-- 8. First response timestamp
ALTER TABLE tickets ADD COLUMN first_response_at TIMESTAMP;

-- Indexes cho các cột mới
CREATE INDEX idx_tickets_assignee ON tickets(assignee_id);
CREATE INDEX idx_tickets_team ON tickets(team_id);
CREATE INDEX idx_tickets_category ON tickets(category_id);
CREATE INDEX idx_tickets_subcategory ON tickets(subcategory_id);
CREATE INDEX idx_tickets_sla_response ON tickets(sla_response_at);
CREATE INDEX idx_tickets_sla_resolution ON tickets(sla_resolution_at);

-- Constraints để đảm bảo data integrity
-- Subcategory phải thuộc về parent category
-- (Sẽ được enforce bằng application logic hoặc trigger nếu cần)

COMMENT ON TABLE tickets IS NULL;  -- Bỏ comment nếu muốn thêm
COMMENT ON COLUMN tickets.assignee_id IS 'Người được giao xử lý ticket';
COMMENT ON COLUMN tickets.team_id IS 'Nhóm IT được giao xử lý ticket';
COMMENT ON COLUMN tickets.category_id IS 'Danh mục chính của ticket (từ bảng categories)';
COMMENT ON COLUMN tickets.subcategory_id IS 'Danh mục con của ticket (từ bảng categories)';
COMMENT ON COLUMN tickets.sla_response_at IS 'Thời hạn phản hồi SLA';
COMMENT ON COLUMN tickets.sla_resolution_at IS 'Thời hạn giải quyết SLA';
COMMENT ON COLUMN tickets.first_response_at IS 'Thời điểm IT phản hồi lần đầu';
COMMENT ON COLUMN tickets.escalation_count IS 'Số lần ticket được escalate';
COMMENT ON COLUMN tickets.reopen_count IS 'Số lần ticket được reopen';
