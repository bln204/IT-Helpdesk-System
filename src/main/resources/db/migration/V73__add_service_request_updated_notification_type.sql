-- Allow notifications for service request lifecycle updates (approve, reject, assign, complete, cancel).
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
        'INFO',
        'SERVICE_REQUEST_CREATED',
        'SERVICE_REQUEST_UPDATED'
    ));
