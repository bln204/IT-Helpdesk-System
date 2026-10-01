-- ============================================================
-- V65: Add version column for optimistic locking
-- Purpose: Enable concurrent edit conflict detection
-- ============================================================

-- Add version column to tickets table for JPA optimistic locking
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;

-- Initialize version for existing records
UPDATE tickets SET version = 0 WHERE version IS NULL;

-- Make version column NOT NULL
ALTER TABLE tickets ALTER COLUMN version SET NOT NULL;

-- Add comment for documentation
COMMENT ON COLUMN tickets.version IS 'Version column for optimistic locking - auto-incremented on each update';

-- ============================================================
-- Also add version column to other main entities that may need it
-- ============================================================

-- Users table
ALTER TABLE users ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
UPDATE users SET version = 0 WHERE version IS NULL;
ALTER TABLE users ALTER COLUMN version SET NOT NULL;
COMMENT ON COLUMN users.version IS 'Version column for optimistic locking';

-- Departments table  
ALTER TABLE departments ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
UPDATE departments SET version = 0 WHERE version IS NULL;
ALTER TABLE departments ALTER COLUMN version SET NOT NULL;
COMMENT ON COLUMN departments.version IS 'Version column for optimistic locking';

-- Teams table
ALTER TABLE teams ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
UPDATE teams SET version = 0 WHERE version IS NULL;
ALTER TABLE teams ALTER COLUMN version SET NOT NULL;
COMMENT ON COLUMN teams.version IS 'Version column for optimistic locking';

-- Categories table
ALTER TABLE categories ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
UPDATE categories SET version = 0 WHERE version IS NULL;
ALTER TABLE categories ALTER COLUMN version SET NOT NULL;
COMMENT ON COLUMN categories.version IS 'Version column for optimistic locking';
