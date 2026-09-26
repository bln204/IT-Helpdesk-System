package com.example.ticketing.auth;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Entity lưu trữ notification preferences cho mỗi user.
 * Mỗi user có một NotificationPreferences.
 */
@Entity
@Table(name = "notification_preferences")
public class NotificationPreferences {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;
    
    // ============ Notification Types ============
    
    @Column(name = "notify_ticket_created", nullable = false)
    private boolean notifyTicketCreated = true;
    
    @Column(name = "notify_ticket_assigned", nullable = false)
    private boolean notifyTicketAssigned = true;
    
    @Column(name = "notify_status_changed", nullable = false)
    private boolean notifyStatusChanged = true;
    
    @Column(name = "notify_comment_added", nullable = false)
    private boolean notifyCommentAdded = true;
    
    @Column(name = "notify_sla_warning", nullable = false)
    private boolean notifySlaWarning = true;
    
    @Column(name = "notify_sla_breached", nullable = false)
    private boolean notifySlaBreached = true;
    
    @Column(name = "notify_escalated", nullable = false)
    private boolean notifyEscalated = true;
    
    // ============ Channel Settings ============
    
    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled = true;
    
    @Column(name = "in_app_enabled", nullable = false)
    private boolean inAppEnabled = true;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }
    
    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // ============ Factory Method ============
    
    /**
     * Tạo preferences mới với giá trị mặc định.
     */
    public static NotificationPreferences createDefault(UserAccount user) {
        NotificationPreferences prefs = new NotificationPreferences();
        prefs.setUser(user);
        prefs.setNotifyTicketCreated(true);
        prefs.setNotifyTicketAssigned(true);
        prefs.setNotifyStatusChanged(true);
        prefs.setNotifyCommentAdded(true);
        prefs.setNotifySlaWarning(true);
        prefs.setNotifySlaBreached(true);
        prefs.setNotifyEscalated(true);
        prefs.setEmailEnabled(true);
        prefs.setInAppEnabled(true);
        return prefs;
    }
    
    // ============ Getters & Setters ============
    
    public Long getId() {
        return id;
    }
    
    public UserAccount getUser() {
        return user;
    }
    
    public void setUser(UserAccount user) {
        this.user = user;
    }
    
    public Long getUserId() {
        return user != null ? user.getId() : null;
    }
    
    public String getUsername() {
        return user != null ? user.getUsername() : null;
    }
    
    // Notification type getters/setters
    public boolean isNotifyTicketCreated() { return notifyTicketCreated; }
    public void setNotifyTicketCreated(boolean notifyTicketCreated) { this.notifyTicketCreated = notifyTicketCreated; }
    
    public boolean isNotifyTicketAssigned() { return notifyTicketAssigned; }
    public void setNotifyTicketAssigned(boolean notifyTicketAssigned) { this.notifyTicketAssigned = notifyTicketAssigned; }
    
    public boolean isNotifyStatusChanged() { return notifyStatusChanged; }
    public void setNotifyStatusChanged(boolean notifyStatusChanged) { this.notifyStatusChanged = notifyStatusChanged; }
    
    public boolean isNotifyCommentAdded() { return notifyCommentAdded; }
    public void setNotifyCommentAdded(boolean notifyCommentAdded) { this.notifyCommentAdded = notifyCommentAdded; }
    
    public boolean isNotifySlaWarning() { return notifySlaWarning; }
    public void setNotifySlaWarning(boolean notifySlaWarning) { this.notifySlaWarning = notifySlaWarning; }
    
    public boolean isNotifySlaBreached() { return notifySlaBreached; }
    public void setNotifySlaBreached(boolean notifySlaBreached) { this.notifySlaBreached = notifySlaBreached; }
    
    public boolean isNotifyEscalated() { return notifyEscalated; }
    public void setNotifyEscalated(boolean notifyEscalated) { this.notifyEscalated = notifyEscalated; }
    
    // Channel getters/setters
    public boolean isEmailEnabled() { return emailEnabled; }
    public void setEmailEnabled(boolean emailEnabled) { this.emailEnabled = emailEnabled; }
    
    public boolean isInAppEnabled() { return inAppEnabled; }
    public void setInAppEnabled(boolean inAppEnabled) { this.inAppEnabled = inAppEnabled; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    
    // ============ Convenience Methods ============
    
    /**
     * Kiểm tra user có muốn nhận notification qua channel này không.
     */
    public boolean shouldSendEmail(Notification.NotificationType type) {
        if (!emailEnabled) return false;
        return shouldNotify(type);
    }
    
    public boolean shouldSendInApp(Notification.NotificationType type) {
        if (!inAppEnabled) return false;
        return shouldNotify(type);
    }
    
    private boolean shouldNotify(Notification.NotificationType type) {
        return switch (type) {
            case TICKET_CREATED -> notifyTicketCreated;
            case TICKET_ASSIGNED, TICKET_ASSIGNED_TO_TEAM -> notifyTicketAssigned;
            case TICKET_STATUS_CHANGED, TICKET_IN_PROGRESS, TICKET_WAITING_FOR_INFO, 
                 TICKET_RESOLVED, TICKET_CLOSED, TICKET_REOPENED, TICKET_CANCELLED -> notifyStatusChanged;
            case TICKET_COMMENT_ADDED -> notifyCommentAdded;
            case TICKET_SLA_WARNING -> notifySlaWarning;
            case TICKET_SLA_BREACHED -> notifySlaBreached;
            case TICKET_ESCALATED -> notifyEscalated;
            default -> true; // Default cho các loại khác
        };
    }
    
    // ============ DTO ============
    
    public static class NotificationPreferencesResponse {
        private Long id;
        private Long userId;
        private String username;
        private boolean notifyTicketCreated;
        private boolean notifyTicketAssigned;
        private boolean notifyStatusChanged;
        private boolean notifyCommentAdded;
        private boolean notifySlaWarning;
        private boolean notifySlaBreached;
        private boolean notifyEscalated;
        private boolean emailEnabled;
        private boolean inAppEnabled;
        
        public NotificationPreferencesResponse() {}
        
        public NotificationPreferencesResponse(NotificationPreferences prefs) {
            this.id = prefs.getId();
            this.userId = prefs.getUserId();
            this.username = prefs.getUsername();
            this.notifyTicketCreated = prefs.isNotifyTicketCreated();
            this.notifyTicketAssigned = prefs.isNotifyTicketAssigned();
            this.notifyStatusChanged = prefs.isNotifyStatusChanged();
            this.notifyCommentAdded = prefs.isNotifyCommentAdded();
            this.notifySlaWarning = prefs.isNotifySlaWarning();
            this.notifySlaBreached = prefs.isNotifySlaBreached();
            this.notifyEscalated = prefs.isNotifyEscalated();
            this.emailEnabled = prefs.isEmailEnabled();
            this.inAppEnabled = prefs.isInAppEnabled();
        }
        
        // Getters
        public Long getId() { return id; }
        public Long getUserId() { return userId; }
        public String getUsername() { return username; }
        public boolean isNotifyTicketCreated() { return notifyTicketCreated; }
        public boolean isNotifyTicketAssigned() { return notifyTicketAssigned; }
        public boolean isNotifyStatusChanged() { return notifyStatusChanged; }
        public boolean isNotifyCommentAdded() { return notifyCommentAdded; }
        public boolean isNotifySlaWarning() { return notifySlaWarning; }
        public boolean isNotifySlaBreached() { return notifySlaBreached; }
        public boolean isNotifyEscalated() { return notifyEscalated; }
        public boolean isEmailEnabled() { return emailEnabled; }
        public boolean isInAppEnabled() { return inAppEnabled; }
        
        // Setters
        public void setId(Long id) { this.id = id; }
        public void setUserId(Long userId) { this.userId = userId; }
        public void setUsername(String username) { this.username = username; }
        public void setNotifyTicketCreated(boolean notifyTicketCreated) { this.notifyTicketCreated = notifyTicketCreated; }
        public void setNotifyTicketAssigned(boolean notifyTicketAssigned) { this.notifyTicketAssigned = notifyTicketAssigned; }
        public void setNotifyStatusChanged(boolean notifyStatusChanged) { this.notifyStatusChanged = notifyStatusChanged; }
        public void setNotifyCommentAdded(boolean notifyCommentAdded) { this.notifyCommentAdded = notifyCommentAdded; }
        public void setNotifySlaWarning(boolean notifySlaWarning) { this.notifySlaWarning = notifySlaWarning; }
        public void setNotifySlaBreached(boolean notifySlaBreached) { this.notifySlaBreached = notifySlaBreached; }
        public void setNotifyEscalated(boolean notifyEscalated) { this.notifyEscalated = notifyEscalated; }
        public void setEmailEnabled(boolean emailEnabled) { this.emailEnabled = emailEnabled; }
        public void setInAppEnabled(boolean inAppEnabled) { this.inAppEnabled = inAppEnabled; }
    }
    
    /**
     * Request DTO for updating notification preferences.
     */
    public static class NotificationPreferencesRequest {
        public Boolean notifyTicketCreated;
        public Boolean notifyTicketAssigned;
        public Boolean notifyStatusChanged;
        public Boolean notifyCommentAdded;
        public Boolean notifySlaWarning;
        public Boolean notifySlaBreached;
        public Boolean notifyEscalated;
        public Boolean emailEnabled;
        public Boolean inAppEnabled;
    }
}
