package com.example.ticketing.auth;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Entity for user notifications.
 * Stores notifications for account approvals, rejections, creation requests, and ticket events.
 */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Username of the recipient (who receives the notification).
     */
    @Column(name = "recipient_username", nullable = false, length = 80)
    private String recipientUsername;

    /**
     * Type of notification.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationType type;

    /**
     * Notification title.
     */
    @Column(nullable = false, length = 100)
    private String title;

    /**
     * Notification message content.
     */
    @Column(nullable = false, length = 500)
    private String message;
    
    /**
     * Related ticket ID (if applicable).
     */
    @Column(name = "ticket_id")
    private Long ticketId;
    
    /**
     * Related ticket number (for display).
     */
    @Column(name = "ticket_number", length = 32)
    private String ticketNumber;

    /**
     * Related user account ID (if applicable).
     */
    @Column(name = "related_user_id")
    private Long relatedUserId;

    /**
     * Username of the actor who triggered this notification.
     */
    @Column(name = "actor_username", length = 80)
    private String actorUsername;

    /**
     * Timestamp when notification was created.
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * Whether the notification has been read.
     */
    @Column(nullable = false)
    private boolean read = false;

    /**
     * Notification types.
     */
    public enum NotificationType {
        // Account creation requests
        ACCOUNT_CREATED_PENDING,    // Sent to ADMIN when TRUONG_PHONG creates account
        
        // Account status changes
        ACCOUNT_APPROVED,            // Sent to TRUONG_PHONG when ADMIN approves
        ACCOUNT_REJECTED,            // Sent to TRUONG_PHONG when ADMIN rejects
        
        // Profile changes
        PROFILE_CHANGED,            // Sent to user when their profile is changed by others
        
        // ============ TICKET NOTIFICATIONS (Phase 2) ============
        
        // Ticket lifecycle
        TICKET_CREATED,             // Ticket mới được tạo
        TICKET_ASSIGNED,           // Ticket được giao cho user
        TICKET_UNASSIGNED,          // Ticket bị bỏ gán
        TICKET_REASSIGNED,          // Ticket được gán lại
        
        // Status changes
        TICKET_STATUS_CHANGED,      // Status thay đổi (generic)
        TICKET_IN_PROGRESS,         // Chuyển sang IN_PROGRESS
        TICKET_WAITING_FOR_INFO,    // IT chờ thông tin từ user
        TICKET_INFO_PROVIDED,       // User cung cấp thông tin
        TICKET_RESOLVED,            // Ticket được giải quyết
        TICKET_CLOSED,              // Ticket được đóng
        TICKET_REOPENED,            // Ticket được mở lại
        TICKET_CANCELLED,           // Ticket bị hủy
        
        // Special events
        TICKET_ESCALATED,           // Ticket được escalate
        TICKET_COMMENT_ADDED,       // Có bình luận mới
        
        // Team notifications
        TICKET_ASSIGNED_TO_TEAM,    // Ticket được gán cho team
        
        // SLA
        TICKET_SLA_WARNING,         // SLA sắp hết hạn
        TICKET_SLA_BREACHED,        // SLA đã bị breached
        
        // Generic
        INFO
    }

    // ============ Constructors ============

    public Notification() {
        this.createdAt = LocalDateTime.now();
        this.read = false;
    }

    public Notification(String recipientUsername, NotificationType type, String title, String message) {
        this();
        this.recipientUsername = recipientUsername;
        this.type = type;
        this.title = title;
        this.message = message;
    }

    public Notification(String recipientUsername, NotificationType type, String title, String message,
                       Long relatedUserId, String actorUsername) {
        this(recipientUsername, type, title, message);
        this.relatedUserId = relatedUserId;
        this.actorUsername = actorUsername;
    }
    
    // ============ Ticket Notification Factory Methods ============
    
    /**
     * Tạo notification cho ticket mới.
     */
    public static Notification forTicketCreated(String recipientUsername, Long ticketId, String ticketNumber, String title) {
        Notification n = new Notification(recipientUsername, NotificationType.TICKET_CREATED,
            "Ticket mới được tạo", "Ticket #" + ticketNumber + " - " + truncate(title, 80));
        n.setTicketId(ticketId);
        n.setTicketNumber(ticketNumber);
        return n;
    }
    
    /**
     * Tạo notification khi ticket được giao.
     */
    public static Notification forTicketAssigned(String recipientUsername, Long ticketId, String ticketNumber, 
                                                 String title, String assignedBy) {
        Notification n = new Notification(recipientUsername, NotificationType.TICKET_ASSIGNED,
            "Ticket được giao cho bạn", "Ticket #" + ticketNumber + " đã được giao bởi " + assignedBy);
        n.setTicketId(ticketId);
        n.setTicketNumber(ticketNumber);
        n.setActorUsername(assignedBy);
        return n;
    }
    
    /**
     * Tạo notification khi status thay đổi.
     */
    public static Notification forStatusChanged(String recipientUsername, Long ticketId, String ticketNumber,
                                                String title, String oldStatus, String newStatus, String changedBy) {
        NotificationType type = NotificationType.TICKET_STATUS_CHANGED;
        if ("IN_PROGRESS".equals(newStatus)) {
            type = NotificationType.TICKET_IN_PROGRESS;
        } else if ("WAITING_FOR_USER".equals(newStatus)) {
            type = NotificationType.TICKET_WAITING_FOR_INFO;
        } else if ("RESOLVED".equals(newStatus)) {
            type = NotificationType.TICKET_RESOLVED;
        } else if ("CLOSED".equals(newStatus)) {
            type = NotificationType.TICKET_CLOSED;
        } else if ("REOPENED".equals(newStatus)) {
            type = NotificationType.TICKET_REOPENED;
        } else if ("ESCALATED".equals(newStatus)) {
            type = NotificationType.TICKET_ESCALATED;
        }
        
        Notification n = new Notification(recipientUsername, type,
            "Ticket #" + ticketNumber + " - Status thay đổi",
            "Ticket đã chuyển từ " + oldStatus + " sang " + newStatus);
        n.setTicketId(ticketId);
        n.setTicketNumber(ticketNumber);
        n.setActorUsername(changedBy);
        return n;
    }
    
    /**
     * Tạo notification khi ticket được giải quyết.
     */
    public static Notification forTicketResolved(String recipientUsername, Long ticketId, String ticketNumber,
                                                 String title, String resolvedBy) {
        Notification n = new Notification(recipientUsername, NotificationType.TICKET_RESOLVED,
            "Ticket đã được giải quyết", "Ticket #" + ticketNumber + " đã được giải quyết bởi " + resolvedBy);
        n.setTicketId(ticketId);
        n.setTicketNumber(ticketNumber);
        n.setActorUsername(resolvedBy);
        return n;
    }
    
    /**
     * Tạo notification khi SLA sắp hết hạn.
     */
    public static Notification forSlaWarning(String recipientUsername, Long ticketId, String ticketNumber,
                                             String title, String slaType) {
        Notification n = new Notification(recipientUsername, NotificationType.TICKET_SLA_WARNING,
            "Cảnh báo SLA", "Ticket #" + ticketNumber + " - " + slaType + " sắp hết hạn");
        n.setTicketId(ticketId);
        n.setTicketNumber(ticketNumber);
        return n;
    }
    
    /**
     * Tạo notification khi SLA bị breached.
     */
    public static Notification forSlaBreached(String recipientUsername, Long ticketId, String ticketNumber,
                                             String title, String slaType) {
        Notification n = new Notification(recipientUsername, NotificationType.TICKET_SLA_BREACHED,
            "SLA bị vi phạm!", "Ticket #" + ticketNumber + " - " + slaType + " đã bị breached");
        n.setTicketId(ticketId);
        n.setTicketNumber(ticketNumber);
        return n;
    }
    
    private static String truncate(String str, int maxLen) {
        if (str == null) return "";
        return str.length() > maxLen ? str.substring(0, maxLen - 3) + "..." : str;
    }

    // ============ Getters & Setters ============

    public Long getId() {
        return id;
    }

    public String getRecipientUsername() {
        return recipientUsername;
    }

    public void setRecipientUsername(String recipientUsername) {
        this.recipientUsername = recipientUsername;
    }

    public NotificationType getType() {
        return type;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
    
    public Long getTicketId() {
        return ticketId;
    }
    
    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }
    
    public String getTicketNumber() {
        return ticketNumber;
    }
    
    public void setTicketNumber(String ticketNumber) {
        this.ticketNumber = ticketNumber;
    }

    public Long getRelatedUserId() {
        return relatedUserId;
    }

    public void setRelatedUserId(Long relatedUserId) {
        this.relatedUserId = relatedUserId;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public void setActorUsername(String actorUsername) {
        this.actorUsername = actorUsername;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }
    
    /**
     * Check if this is a ticket-related notification.
     */
    public boolean isTicketNotification() {
        return ticketId != null || type.name().startsWith("TICKET_");
    }

    // ============ DTO for API Response ============
    
    public static class NotificationResponse {
        private Long id;
        private String type;
        private String title;
        private String message;
        private Long ticketId;
        private String ticketNumber;
        private Long relatedUserId;
        private String actorUsername;
        private String createdAt;
        private boolean read;
        
        public NotificationResponse() {}

        public NotificationResponse(Notification notification) {
            this.id = notification.getId();
            this.type = notification.getType().name();
            this.title = notification.getTitle();
            this.message = notification.getMessage();
            this.ticketId = notification.getTicketId();
            this.ticketNumber = notification.getTicketNumber();
            this.relatedUserId = notification.getRelatedUserId();
            this.actorUsername = notification.getActorUsername();
            this.createdAt = notification.getCreatedAt() != null 
                ? notification.getCreatedAt().toString() 
                : null;
            this.read = notification.isRead();
        }

        // Getters
        public Long getId() { return id; }
        public String getType() { return type; }
        public String getTitle() { return title; }
        public String getMessage() { return message; }
        public Long getTicketId() { return ticketId; }
        public String getTicketNumber() { return ticketNumber; }
        public Long getRelatedUserId() { return relatedUserId; }
        public String getActorUsername() { return actorUsername; }
        public String getCreatedAt() { return createdAt; }
        public boolean isRead() { return read; }

        // Setters
        public void setId(Long id) { this.id = id; }
        public void setType(String type) { this.type = type; }
        public void setTitle(String title) { this.title = title; }
        public void setMessage(String message) { this.message = message; }
        public void setTicketId(Long ticketId) { this.ticketId = ticketId; }
        public void setTicketNumber(String ticketNumber) { this.ticketNumber = ticketNumber; }
        public void setRelatedUserId(Long relatedUserId) { this.relatedUserId = relatedUserId; }
        public void setActorUsername(String actorUsername) { this.actorUsername = actorUsername; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
        public void setRead(boolean read) { this.read = read; }
    }
}
