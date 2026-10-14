package com.example.ticketing.ai.session;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One conversation thread for the AI assistant. Owned by a single
 * user (so the assistant cannot leak history across users).
 */
@Entity
@Table(name = "ai_session")
public class AiSession {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_username", nullable = false, length = 80)
    private String userUsername;

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "is_archived", nullable = false)
    private boolean archived;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "last_active_at", nullable = false)
    private LocalDateTime lastActiveAt;

    public AiSession() {
    }

    public AiSession(UUID id, String userUsername, String title) {
        this.id = id;
        this.userUsername = userUsername;
        this.title = title;
        this.archived = false;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getUserUsername() { return userUsername; }
    public void setUserUsername(String userUsername) { this.userUsername = userUsername; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(LocalDateTime lastActiveAt) { this.lastActiveAt = lastActiveAt; }
}
