-- ============================================================
-- V30: Add PROFILE_CHANGED notification type
-- ============================================================
-- Purpose: Add support for profile change notifications

-- Drop the existing check constraint if it exists
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;

-- Add new check constraint that includes PROFILE_CHANGED
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check 
    CHECK (type IN (
        'ACCOUNT_CREATED_PENDING',
        'ACCOUNT_APPROVED', 
        'ACCOUNT_REJECTED',
        'PROFILE_CHANGED',
        'INFO'
    ));

-- Update comment to reflect new type
COMMENT ON COLUMN notifications.type IS 'Notification type: ACCOUNT_CREATED_PENDING, ACCOUNT_APPROVED, ACCOUNT_REJECTED, PROFILE_CHANGED, INFO';
