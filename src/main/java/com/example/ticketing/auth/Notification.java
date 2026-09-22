package com.example.ticketing.auth;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entity for user notifications.
 * Stores notifications for account approvals, rejections, and creation requests.
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
        ACCOUNT_APPROVED,          // Sent to TRUONG_PHONG when ADMIN approves
        ACCOUNT_REJECTED,           // Sent to TRUONG_PHONG when ADMIN rejects
        
        // Profile changes
        PROFILE_CHANGED,           // Sent to user when their profile is changed by others
        
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

    // ============ Getters & Setters ============

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    // ============ DTO for API Response ============

    public static class NotificationResponse {
        private Long id;
        private String type;
        private String title;
        private String message;
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
        public Long getRelatedUserId() { return relatedUserId; }
        public String getActorUsername() { return actorUsername; }
        public String getCreatedAt() { return createdAt; }
        public boolean isRead() { return read; }

        // Setters
        public void setId(Long id) { this.id = id; }
        public void setType(String type) { this.type = type; }
        public void setTitle(String title) { this.title = title; }
        public void setMessage(String message) { this.message = message; }
        public void setRelatedUserId(Long relatedUserId) { this.relatedUserId = relatedUserId; }
        public void setActorUsername(String actorUsername) { this.actorUsername = actorUsername; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
        public void setRead(boolean read) { this.read = read; }
    }
}
