-- V40__extend_notification_types.sql
-- Purpose: Add ticket-related notification types to the check constraint
--
-- Current constraint only allows account and profile notification types.
-- This migration adds support for ticket lifecycle notifications.

-- Drop existing check constraint
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;

-- Add new check constraint with all notification types from NotificationType enum
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check 
    CHECK (type IN (
        'ACCOUNT_CREATED_PENDING',
        'ACCOUNT_APPROVED', 
        'ACCOUNT_REJECTED',
        'PROFILE_CHANGED',
        'TICKET_CREATED',
        'TICKET_ASSIGNED',
        'TICKET_UNASSIGNED',
        'TICKET_REASSIGNED',
        'TICKET_STATUS_CHANGED',
        'TICKET_IN_PROGRESS',
        'TICKET_WAITING_FOR_INFO',
        'TICKET_INFO_PROVIDED',
        'TICKET_RESOLVED',
        'TICKET_CLOSED',
        'TICKET_REOPENED',
        'TICKET_CANCELLED',
        'TICKET_ESCALATED',
        'TICKET_COMMENT_ADDED',
        'TICKET_ASSIGNED_TO_TEAM',
        'TICKET_SLA_WARNING',
        'TICKET_SLA_BREACHED',
        'INFO'
    ));

-- Update comment to reflect new types
COMMENT ON COLUMN notifications.type IS 'Notification type: ACCOUNT_*, PROFILE_CHANGED, TICKET_*, INFO';
