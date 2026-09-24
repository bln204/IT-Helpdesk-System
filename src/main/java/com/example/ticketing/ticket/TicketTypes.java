package com.example.ticketing.ticket;

public final class TicketTypes {
    private TicketTypes() {
    }

    // ============================================================
    // CATEGORY
    // ============================================================
    // Giữ lại enum cho tương thích ngược, nhưng sẽ dùng Category entity
    public enum TicketCategory {
        SOFTWARE,
        ACCESS,
        NETWORK,
        HARDWARE,
        SECURITY,
        EMAIL,
        ACCOUNT,
        OTHER
    }

    // ============================================================
    // PRIORITY - Theo yêu cầu Phase 2
    // ============================================================
    public enum TicketPriority {
        LOW,      // Ưu tiên thấp - có thể xử lý sau
        MEDIUM,   // Ưu tiên trung bình
        HIGH,     // Ưu tiên cao - cần xử lý sớm
        URGENT,   // Khẩn cấp - cần xử lý ngay
        CRITICAL  // Nghiêm trọng - cần xử lý ngay lập tức
    }

    // ============================================================
    // STATUS - Theo yêu cầu Phase 2
    // ============================================================
    public enum TicketStatus {
        // Luồng chính
        NEW,                    // Ticket mới được tạo
        ASSIGNED,               // Đã được giao cho IT
        IN_PROGRESS,            // IT đang xử lý
        WAITING_FOR_USER,       // IT đang chờ user cung cấp thông tin
        RESOLVED,               // Đã giải quyết, chờ confirm
        CLOSED,                 // Đã đóng (hoàn tất)
        
        // Các trạng thái đặc biệt
        BLOCKED,                // Ticket bị block (chờ bên thứ 3)
        ESCALATED,              // Đã escalate lên cấp cao hơn
        REOPENED,               // Được mở lại sau khi đóng
        CANCELLED               // Bị hủy (admin only)
    }

    // ============================================================
    // AUDIT ACTIONS - Mở rộng cho Phase 2
    // ============================================================
    public enum AuditAction {
        CREATED,                // Ticket được tạo
        STATUS_CHANGED,         // Trạng thái thay đổi
        PRIORITY_CHANGED,       // Độ ưu tiên thay đổi
        ASSIGNEE_CHANGED,       // Người được giao thay đổi
        TEAM_CHANGED,           // Team được giao thay đổi
        CATEGORY_CHANGED,       // Category thay đổi
        COMMENT_ADDED,          // Có bình luận mới
        
        // Phase 2 Actions
        ESCALATED,              // Ticket được escalate
        REOPENED,               // Ticket được mở lại
        CANCELLED,              // Ticket bị hủy
        RESOLVED,               // Ticket được giải quyết
        CLOSED,                 // Ticket được đóng
        WAITING_FOR_INFO,       // IT chờ thông tin từ user
        INFO_PROVIDED,          // User cung cấp thông tin
        
        // Assignment Events
        ASSIGNED_TO_AGENT,      // Gán cho agent cụ thể
        ASSIGNED_TO_TEAM,       // Gán cho team
        UNASSIGNED,             // Bỏ gán
        
        // SLA Events
        SLA_RESPONSE_BREACHED,  // SLA phản hồi bị breached
        SLA_RESOLUTION_BREACHED // SLA giải quyết bị breached
    }

    // ============================================================
    // COMMENT VISIBILITY
    // ============================================================
    public enum CommentVisibility {
        PUBLIC,     // User có thể thấy
        INTERNAL    // Chỉ IT staff thấy
    }

    // ============================================================
    // TIMELINE ENTRY TYPES - Cho ticket timeline
    // ============================================================
    public enum TimelineEntryType {
        STATUS_CHANGE,      // Thay đổi trạng thái
        COMMENT,            // Bình luận
        ASSIGNMENT,         // Phân công
        ESCALATION,        // Escalate
        REOPEN,             // Mở lại
        CLOSE,              // Đóng
        CANCEL,             // Hủy
        PRIORITY_CHANGE,    // Thay đổi ưu tiên
        CATEGORY_CHANGE,    // Thay đổi category
        SYSTEM              // Sự kiện hệ thống
    }

    // ============================================================
    // NOTIFICATION TYPES - Cho ticket notifications
    // ============================================================
    public enum TicketNotificationType {
        // Ticket Events
        TICKET_CREATED,             // Ticket mới được tạo
        TICKET_ASSIGNED,           // Ticket được giao
        TICKET_UNASSIGNED,         // Ticket bị bỏ gán
        TICKET_STATUS_CHANGED,     // Status thay đổi
        TICKET_COMMENT_ADDED,      // Có bình luận mới
        
        // Resolution Events
        TICKET_RESOLVED,           // Ticket được giải quyết
        TICKET_CLOSED,             // Ticket được đóng
        TICKET_REOPENED,           // Ticket được mở lại
        TICKET_CANCELLED,          // Ticket bị hủy
        
        // Special Status Events
        TICKET_ESCALATED,          // Ticket được escalate
        TICKET_WAITING_FOR_INFO,   // IT chờ thông tin
        TICKET_INFO_PROVIDED,      // User cung cấp thông tin
        
        // SLA Events
        TICKET_SLA_WARNING,        // Cảnh báo SLA sắp hết hạn
        TICKET_SLA_BREACHED,       // SLA bị breached
        
        // Assignment Events
        TICKET_ASSIGNED_TO_YOU,   // Ticket được giao cho bạn
        TICKET_ASSIGNED_TO_TEAM,   // Ticket được giao cho team của bạn
        TICKET_REASSIGNED          // Ticket được gán lại
    }

    // ============================================================
    // SLA PRIORITY LEVELS - Định nghĩa SLA theo priority
    // ============================================================
    public enum SLAPriority {
        CRITICAL(1, 4),    // 1 giờ response, 4 giờ resolution
        HIGH(2, 8),        // 2 giờ response, 8 giờ resolution
        MEDIUM(4, 24),     // 4 giờ response, 24 giờ resolution
        LOW(8, 72);        // 8 giờ response, 72 giờ resolution
        
        private final int responseHours;
        private final int resolutionHours;
        
        SLAPriority(int responseHours, int resolutionHours) {
            this.responseHours = responseHours;
            this.resolutionHours = resolutionHours;
        }
        
        public int getResponseHours() {
            return responseHours;
        }
        
        public int getResolutionHours() {
            return resolutionHours;
        }
        
        public static SLAPriority fromTicketPriority(TicketPriority priority) {
            switch (priority) {
                case CRITICAL: return CRITICAL;
                case HIGH: return HIGH;
                case MEDIUM: return MEDIUM;
                case LOW: return LOW;
                default: return MEDIUM;
            }
        }
    }
}
