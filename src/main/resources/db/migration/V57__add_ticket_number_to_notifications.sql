-- ============================================================
-- V57: Add ticket_number column to notifications
-- ============================================================
-- Root cause: Notification.java entity has ticketNumber field
-- but V37 migration did not create this column in the database

ALTER TABLE notifications ADD COLUMN ticket_number VARCHAR(32);

COMMENT ON COLUMN notifications.ticket_number IS 'Ticket number for display in notifications';
