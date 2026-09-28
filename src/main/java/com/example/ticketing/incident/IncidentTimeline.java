package com.example.ticketing.incident;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Timeline entry cho Incident.
 */
@Entity
@Table(name = "incident_timeline")
public class IncidentTimeline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private EventType eventType;

    @Column(length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "actor_name", length = 100)
    private String actorName;

    @Column(name = "actor_role", length = 50)
    private String actorRole;

    @Column(name = "old_value", length = 200)
    private String oldValue;

    @Column(name = "new_value", length = 200)
    private String newValue;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // ==================== Enums ====================

    public enum EventType {
        CREATED("Đã tạo incident"),
        STATUS_CHANGED("Thay đổi trạng thái"),
        TICKET_LINKED("Liên kết ticket"),
        TICKET_UNLINKED("Hủy liên kết ticket"),
        ASSIGNEE_CHANGED("Thay đổi người phụ trách"),
        IDENTIFIED("Đã xác định nguyên nhân"),
        RESOLVED("Đã giải quyết"),
        CLOSED("Đã đóng"),
        NOTE_ADDED("Thêm ghi chú");

        private final String label;

        EventType(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    // ==================== Constructors ====================

    public IncidentTimeline() {
    }

    public IncidentTimeline(Incident incident, EventType eventType, String title) {
        this.incident = incident;
        this.eventType = eventType;
        this.title = title;
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final IncidentTimeline timeline;

        public Builder() {
            timeline = new IncidentTimeline();
        }

        public Builder incident(Incident incident) {
            timeline.incident = incident;
            return this;
        }

        public Builder eventType(EventType eventType) {
            timeline.eventType = eventType;
            return this;
        }

        public Builder title(String title) {
            timeline.title = title;
            return this;
        }

        public Builder description(String description) {
            timeline.description = description;
            return this;
        }

        public Builder actor(String actorName, String actorRole) {
            timeline.actorName = actorName;
            timeline.actorRole = actorRole;
            return this;
        }

        public Builder oldValue(String oldValue) {
            timeline.oldValue = oldValue;
            return this;
        }

        public Builder newValue(String newValue) {
            timeline.newValue = newValue;
            return this;
        }

        public IncidentTimeline build() {
            return timeline;
        }
    }

    // ==================== Getters and Setters ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Incident getIncident() { return incident; }
    public void setIncident(Incident incident) { this.incident = incident; }
    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getActorName() { return actorName; }
    public void setActorName(String actorName) { this.actorName = actorName; }
    public String getActorRole() { return actorRole; }
    public void setActorRole(String actorRole) { this.actorRole = actorRole; }
    public String getOldValue() { return oldValue; }
    public void setOldValue(String oldValue) { this.oldValue = oldValue; }
    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "IncidentTimeline{" +
                "id=" + id +
                ", eventType=" + eventType +
                ", title='" + title + '\'' +
                '}';
    }
}
