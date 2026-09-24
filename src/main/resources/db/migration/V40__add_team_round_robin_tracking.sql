-- ============================================================
-- V40: Thêm cột cho round-robin tracking
-- ============================================================

-- Round-robin counter cho mỗi team
ALTER TABLE teams ADD COLUMN last_assigned_user_id BIGINT REFERENCES users(id);

ALTER TABLE teams ADD COLUMN last_assigned_at TIMESTAMP;

-- Index
CREATE INDEX idx_teams_last_assigned ON teams(last_assigned_user_id);

COMMENT ON COLUMN teams.last_assigned_user_id IS 'User cuối cùng được assign trong round-robin';
COMMENT ON COLUMN teams.last_assigned_at IS 'Thời điểm assign cuối cùng';
