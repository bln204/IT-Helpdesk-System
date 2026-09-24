-- ============================================================
-- V36: Thêm các cột mới vào bảng ticket_assignments
-- ============================================================

-- Thêm assignee_id để link với UserAccount
ALTER TABLE ticket_assignments ADD COLUMN assignee_id BIGINT REFERENCES users(id);

-- Thêm team_id cho multi-level assignment
ALTER TABLE ticket_assignments ADD COLUMN team_id BIGINT REFERENCES teams(id);

-- Thêm loại assignment (INDIVIDUAL hoặc TEAM)
ALTER TABLE ticket_assignments ADD COLUMN assignment_type VARCHAR(20) DEFAULT 'INDIVIDUAL';

COMMENT ON COLUMN ticket_assignments.assignee_id IS 'User được giao (nếu assignment_type = INDIVIDUAL)';
COMMENT ON COLUMN ticket_assignments.team_id IS 'Team được giao (nếu assignment_type = TEAM)';
COMMENT ON COLUMN ticket_assignments.assignment_type IS 'Loại assignment: INDIVIDUAL hoặc TEAM';

-- Index cho các cột mới
CREATE INDEX idx_ticket_assignments_assignee ON ticket_assignments(assignee_id);
CREATE INDEX idx_ticket_assignments_team ON ticket_assignments(team_id);
