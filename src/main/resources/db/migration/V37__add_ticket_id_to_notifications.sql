-- ============================================================
-- V37: Thêm ticket_id vào bảng notifications
-- ============================================================

-- Thêm ticket_id để link notification với ticket
ALTER TABLE notifications ADD COLUMN ticket_id BIGINT REFERENCES tickets(id);

-- Thêm related_ticket_id cho các notification phụ (VD: khi ticket được assign, user nhận notification)
ALTER TABLE notifications ADD COLUMN related_ticket_id BIGINT REFERENCES tickets(id);

-- Index cho performance
CREATE INDEX idx_notifications_ticket ON notifications(ticket_id);
CREATE INDEX idx_notifications_related_ticket ON notifications(related_ticket_id);

COMMENT ON COLUMN notifications.ticket_id IS 'Ticket liên quan đến notification (nếu có)';
COMMENT ON COLUMN notifications.related_ticket_id IS 'Ticket phụ (VD: ticket được assign thay thế)';
