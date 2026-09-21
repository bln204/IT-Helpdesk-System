-- ============================================================
-- V26: Add User Approval System
-- ============================================================
-- Purpose: Add fields to support account approval workflow

-- Note: Tables exist without schema prefix (created by earlier migrations)
-- We use simple names to match how V5 created the users table

-- Add approved field (TRUE = approved, FALSE = pending approval)
ALTER TABLE users ADD COLUMN IF NOT EXISTS approved BOOLEAN NOT NULL DEFAULT FALSE;

-- Add approved_at timestamp (when approved/rejected)
ALTER TABLE users ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP NULL;

-- Add approved_by (username of admin who approved/rejected)
ALTER TABLE users ADD COLUMN IF NOT EXISTS approved_by VARCHAR(80) NULL;

-- Add rejection_reason (reason if rejected)
ALTER TABLE users ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(255) NULL;

-- Mark all existing users as approved
UPDATE users SET approved = TRUE, approved_at = NOW(), approved_by = NULL;

-- Add foreign key constraint (use IF NOT EXISTS if supported, otherwise skip if exists)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_users_approved_by'
    ) THEN
        ALTER TABLE users ADD CONSTRAINT fk_users_approved_by
            FOREIGN KEY (approved_by) REFERENCES users(username) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED;
    END IF;
END
$$;

-- Comments for documentation
COMMENT ON COLUMN users.approved IS 'Account approval status: TRUE = approved and can login, FALSE = pending approval';
COMMENT ON COLUMN users.approved_at IS 'Timestamp when account was approved or rejected';
COMMENT ON COLUMN users.approved_by IS 'Username of admin who approved or rejected this account';
COMMENT ON COLUMN users.rejection_reason IS 'Reason for rejection (if rejected)';

-- ============================================================
-- V26.1: Extend user_audit table for better tracking
-- ============================================================

-- Add details field for additional context
ALTER TABLE user_audit ADD COLUMN IF NOT EXISTS details TEXT NULL;

-- Add old_value and new_value for change tracking
ALTER TABLE user_audit ADD COLUMN IF NOT EXISTS old_value VARCHAR(255) NULL;
ALTER TABLE user_audit ADD COLUMN IF NOT EXISTS new_value VARCHAR(255) NULL;

-- Add index for filtering by action
CREATE INDEX IF NOT EXISTS idx_user_audit_action ON user_audit(action);

-- Comments for documentation
COMMENT ON COLUMN user_audit.details IS 'Additional context or details about the action';
COMMENT ON COLUMN user_audit.old_value IS 'Previous value before the change';
COMMENT ON COLUMN user_audit.new_value IS 'New value after the change';
