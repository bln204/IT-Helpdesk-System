package com.example.ticketing.change;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Change Timeline Entity - Audit trail cho Change Requests.
 */
@Entity
@Table(name = "change_timeline")
public class ChangeTimeline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "change_request_id", nullable = false)
    private ChangeRequest changeRequest;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 50, nullable = false)
    private EventType eventType;

    @Column(name = "actor_name", length = 100)
    private String actorName;

    @Column(name = "actor_username", length = 100)
    private String actorUsername;

    @Column(name = "actor_role", length = 50)
    private String actorRole;

    @Column(name = "event_data", columnDefinition = "TEXT")
    private String eventData;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // ==================== Enums ====================

    public enum EventType {
        CREATED,
        SUBMITTED,
        APPROVED,
        REJECTED,
        SCHEDULED,
        STARTED,
        COMPLETED,
        ROLLED_BACK,
        CANCELLED,
        COMMENT_ADDED,
        FILE_ATTACHED,
        TASK_COMPLETED,
        REVIEW_STARTED,
        REVIEW_COMPLETED,
        CLOSED,
        EMERGENCY_MODE
    }

    // ==================== Constructors ====================

    public ChangeTimeline() {
    }

    public ChangeTimeline(ChangeRequest changeRequest, EventType eventType, String actorName, String actorUsername) {
        this.changeRequest = changeRequest;
        this.eventType = eventType;
        this.actorName = actorName;
        this.actorUsername = actorUsername;
    }

    // ==================== Getters and Setters ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public ChangeRequest getChangeRequest() { return changeRequest; }
    public void setChangeRequest(ChangeRequest changeRequest) { this.changeRequest = changeRequest; }
    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }
    public String getActorName() { return actorName; }
    public void setActorName(String actorName) { this.actorName = actorName; }
    public String getActorUsername() { return actorUsername; }
    public void setActorUsername(String actorUsername) { this.actorUsername = actorUsername; }
    public String getActorRole() { return actorRole; }
    public void setActorRole(String actorRole) { this.actorRole = actorRole; }
    public String getEventData() { return eventData; }
    public void setEventData(String eventData) { this.eventData = eventData; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "ChangeTimeline{" +
                "id=" + id +
                ", eventType=" + eventType +
                '}';
    }
}
