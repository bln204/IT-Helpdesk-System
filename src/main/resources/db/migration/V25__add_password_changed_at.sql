-- Add password_changed_at column to track password changes for token invalidation
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_changed_at TIMESTAMP NULL;

-- Create index for faster lookups during token validation
CREATE INDEX IF NOT EXISTS idx_users_password_changed_at ON users(password_changed_at);

-- Comments for documentation
COMMENT ON COLUMN users.password_changed_at IS 'Timestamp of last password change. Used to invalidate all tokens issued before this time.';
