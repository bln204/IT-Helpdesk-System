-- ============================================================
-- V29: Add created_by column and notifications table
-- ============================================================
-- Purpose: 
-- 1. Track who created each user account for notification purposes
-- 2. Create notifications table for user account status changes

-- Add created_by column to users table
ALTER TABLE users ADD COLUMN IF NOT EXISTS created_by VARCHAR(80);
ALTER TABLE users DROP CONSTRAINT IF EXISTS fk_users_created_by;
ALTER TABLE users ADD CONSTRAINT fk_users_created_by FOREIGN KEY (created_by) REFERENCES users(username);

-- Drop existing notifications table if it exists (to fix column size from VARCHAR(20) to VARCHAR(50))
DROP TABLE IF EXISTS notifications CASCADE;

-- Create notifications table with correct column sizes
CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    recipient_username VARCHAR(80) NOT NULL,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(100) NOT NULL,
    message VARCHAR(500) NOT NULL,
    related_user_id BIGINT,
    actor_username VARCHAR(80),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    read BOOLEAN NOT NULL DEFAULT FALSE
);

-- Create indexes for notifications
CREATE INDEX IF NOT EXISTS idx_notifications_recipient ON notifications(recipient_username);
CREATE INDEX IF NOT EXISTS idx_notifications_read ON notifications(recipient_username, read);
CREATE INDEX IF NOT EXISTS idx_notifications_created_at ON notifications(created_at DESC);

-- Add constraint for actor username
ALTER TABLE notifications ADD CONSTRAINT fk_notifications_actor 
    FOREIGN KEY (actor_username) REFERENCES users(username);

-- Comments
COMMENT ON TABLE notifications IS 'User notifications for account status changes';
COMMENT ON COLUMN notifications.type IS 'Notification type: ACCOUNT_CREATED_PENDING, ACCOUNT_APPROVED, ACCOUNT_REJECTED, INFO';
