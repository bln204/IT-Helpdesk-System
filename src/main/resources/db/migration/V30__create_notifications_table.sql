-- ============================================================
-- V30: Create notifications table with all notification types
-- ============================================================
-- Purpose: Create notifications table for user account status changes and profile change notifications

-- Create notifications table
CREATE TABLE IF NOT EXISTS notifications (
    id BIGSERIAL PRIMARY KEY,
    recipient_username VARCHAR(80) NOT NULL,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(100) NOT NULL,
    message VARCHAR(500) NOT NULL,
    related_user_id BIGINT,
    actor_username VARCHAR(80),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    read BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT notifications_type_check 
        CHECK (type IN (
            'ACCOUNT_CREATED_PENDING',
            'ACCOUNT_APPROVED', 
            'ACCOUNT_REJECTED',
            'PROFILE_CHANGED',
            'INFO'
        ))
);

-- Create indexes for notifications
CREATE INDEX IF NOT EXISTS idx_notifications_recipient ON notifications(recipient_username);
CREATE INDEX IF NOT EXISTS idx_notifications_read ON notifications(recipient_username, read);
CREATE INDEX IF NOT EXISTS idx_notifications_created_at ON notifications(created_at DESC);

-- Add constraint for actor username (ignore if table already has data)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE constraint_name = 'fk_notifications_actor'
    ) THEN
        ALTER TABLE notifications ADD CONSTRAINT fk_notifications_actor 
            FOREIGN KEY (actor_username) REFERENCES users(username);
    END IF;
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'Constraint fk_notifications_actor may already exist or table is empty: %', SQLERRM;
END $$;

-- Comments
COMMENT ON TABLE notifications IS 'User notifications for account status changes and profile changes';
COMMENT ON COLUMN notifications.type IS 'Notification type: ACCOUNT_CREATED_PENDING, ACCOUNT_APPROVED, ACCOUNT_REJECTED, PROFILE_CHANGED, INFO';
