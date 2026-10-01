-- Add password_must_change column to force password change on first login
-- This allows the system to require users to change their password before using the system

ALTER TABLE users ADD COLUMN IF NOT EXISTS password_must_change BOOLEAN NOT NULL DEFAULT TRUE;

-- For ALL existing users, set password_must_change to FALSE
-- Existing users have already been using the system and don't need to change password
-- Only NEW users (created/approved after this migration) will have passwordMustChange = TRUE
UPDATE users SET password_must_change = FALSE;

COMMENT ON COLUMN users.password_must_change IS 'Flag to force user to change password on first login';
-- TRUE = user must change password on first login
-- FALSE = user can use the system normally'
