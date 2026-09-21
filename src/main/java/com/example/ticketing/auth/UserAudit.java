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

@Entity
@Table(name = "user_audit")
public class UserAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserAuditAction action;

    @Column(name = "actor_username", nullable = false, length = 80)
    private String actorUsername;

    @Column(name = "actor_role", nullable = false, length = 16)
    private String actorRole;

    @Column(name = "target_username", nullable = false, length = 80)
    private String targetUsername;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    // ============ NEW: Extended Audit Fields ============
    
    /**
     * Additional context or details about the action.
     */
    @Column(columnDefinition = "TEXT")
    private String details;
    
    /**
     * Previous value before the change (for tracking modifications).
     */
    @Column(name = "old_value", length = 255)
    private String oldValue;
    
    /**
     * New value after the change (for tracking modifications).
     */
    @Column(name = "new_value", length = 255)
    private String newValue;
    
    // ============ END NEW Fields ============

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public UserAuditAction getAction() {
        return action;
    }

    public void setAction(UserAuditAction action) {
        this.action = action;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public void setActorUsername(String actorUsername) {
        this.actorUsername = actorUsername;
    }

    public String getActorRole() {
        return actorRole;
    }

    public void setActorRole(String actorRole) {
        this.actorRole = actorRole;
    }

    public String getTargetUsername() {
        return targetUsername;
    }

    public void setTargetUsername(String targetUsername) {
        this.targetUsername = targetUsername;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    // ============ NEW: Getters and Setters ============
    
    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public String getOldValue() {
        return oldValue;
    }

    public void setOldValue(String oldValue) {
        this.oldValue = oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public void setNewValue(String newValue) {
        this.newValue = newValue;
    }
    
    // ============ END NEW ============
}
