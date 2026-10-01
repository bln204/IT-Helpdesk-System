-- ============================================================
-- V58: Add display_order to teams table
-- Fix: Team entity has displayOrder field but table is missing it
-- ============================================================

ALTER TABLE teams ADD COLUMN IF NOT EXISTS display_order INTEGER DEFAULT 0;

-- Set default order based on team code for existing teams
UPDATE teams SET display_order = 
    CASE code
        WHEN 'NET' THEN 1
        WHEN 'HW' THEN 2
        WHEN 'SW' THEN 3
        WHEN 'SEC' THEN 4
        WHEN 'GEN' THEN 5
        ELSE 10
    END;

-- Add index for ordering
CREATE INDEX IF NOT EXISTS idx_teams_display_order ON teams(display_order);

COMMENT ON COLUMN teams.display_order IS 'Thứ tự hiển thị team trong danh sách';
