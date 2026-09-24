package com.example.ticketing.ticket;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Entity lưu trữ timeline events của ticket.
 * Mỗi event là một mục trong timeline hiển thị lịch sử thay đổi.
 */
@Entity
@Table(name = "ticket_timeline")
public class TicketTimeline {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    // ============ Event Categories ============
    
    public enum EventCategory {
        STATUS,      // Thay đổi status
        ASSIGNMENT,  // Thay đổi assignment
        COMMENT,    // Bình luận
        SYSTEM,     // Sự kiện hệ thống
        SLA,        // Sự kiện SLA
        CATEGORY,   // Thay đổi category
        PRIORITY,   // Thay đổi priority
        ESCALATION, // Escalation
        CLOSURE     // Đóng ticket
    }
    
    // ============ Event Types ============
    
    public enum EventType {
        // Status events
        CREATED,
        STATUS_CHANGED,
        IN_PROGRESS,
        WAITING_FOR_USER,
        INFO_PROVIDED,
        RESOLVED,
        CLOSED,
        REOPENED,
        CANCELLED,
        ESCALATED,
        
        // Assignment events
        ASSIGNED,
        REASSIGNED,
        UNASSIGNED,
        TEAM_ASSIGNED,
        AUTO_ASSIGNED,
        
        // Comment events
        COMMENT_ADDED,
        INTERNAL_COMMENT,
        PUBLIC_COMMENT,
        
        // System events
        TICKET_CREATED,
        TICKET_UPDATED,
        FILE_ATTACHED,
        
        // SLA events
        SLA_WARNING_SENT,
        SLA_BREACHED,
        FIRST_RESPONSE_SENT,
        RESOLUTION_SLA_UPDATED,
        
        // Category/Priority events
        CATEGORY_CHANGED,
        PRIORITY_CHANGED,
        
        // Others
        ESCALATION_APPROVED,
        ESCALATION_REJECTED
    }
    
    // ============ Actor Types ============
    
    public enum ActorType {
        USER,
        SYSTEM
    }
    
    // ============ Fields ============
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private EventType eventType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "event_category", nullable = false, length = 20)
    private EventCategory eventCategory;
    
    @Column(nullable = false, length = 200)
    private String title;
    
    @Column(length = 1000)
    private String description;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", length = 20)
    private ActorType actorType;
    
    @Column(name = "actor_username", length = 80)
    private String actorUsername;
    
    @Column(name = "actor_name", length = 120)
    private String actorName;
    
    @Column(name = "actor_role", length = 20)
    private String actorRole;
    
    @Column(name = "old_value", length = 500)
    private String oldValue;
    
    @Column(name = "new_value", length = 500)
    private String newValue;
    
    @Column(columnDefinition = "TEXT")
    private String metadata;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
    
    // ============ Builder Pattern ============
    
    public static TicketTimelineBuilder builder() {
        return new TicketTimelineBuilder();
    }
    
    public static class TicketTimelineBuilder {
        private final TicketTimeline timeline;
        
        public TicketTimelineBuilder() {
            timeline = new TicketTimeline();
        }
        
        public TicketTimelineBuilder ticket(Ticket ticket) {
            timeline.ticket = ticket;
            return this;
        }
        
        public TicketTimelineBuilder eventType(EventType eventType) {
            timeline.eventType = eventType;
            return this;
        }
        
        public TicketTimelineBuilder eventCategory(EventCategory eventCategory) {
            timeline.eventCategory = eventCategory;
            return this;
        }
        
        public TicketTimelineBuilder title(String title) {
            timeline.title = title;
            return this;
        }
        
        public TicketTimelineBuilder description(String description) {
            timeline.description = description;
            return this;
        }
        
        public TicketTimelineBuilder actor(ActorType type, String username, String name, String role) {
            timeline.actorType = type;
            timeline.actorUsername = username;
            timeline.actorName = name;
            timeline.actorRole = role;
            return this;
        }
        
        public TicketTimelineBuilder userActor(String username, String name, String role) {
            return actor(ActorType.USER, username, name, role);
        }
        
        public TicketTimelineBuilder systemActor() {
            return actor(ActorType.SYSTEM, "SYSTEM", "System", null);
        }
        
        public TicketTimelineBuilder oldValue(String oldValue) {
            timeline.oldValue = oldValue;
            return this;
        }
        
        public TicketTimelineBuilder newValue(String newValue) {
            timeline.newValue = newValue;
            return this;
        }
        
        public TicketTimelineBuilder metadata(Object metadata) {
            try {
                timeline.metadata = objectMapper.writeValueAsString(metadata);
            } catch (JsonProcessingException e) {
                timeline.metadata = null;
            }
            return this;
        }
        
        public TicketTimeline build() {
            return timeline;
        }
    }
    
    // ============ Factory Methods ============
    
    /**
     * Tạo timeline event cho ticket mới.
     */
    public static TicketTimeline forTicketCreated(Ticket ticket, String createdBy) {
        return builder()
            .ticket(ticket)
            .eventType(EventType.TICKET_CREATED)
            .eventCategory(EventCategory.STATUS)
            .title("Ticket được tạo")
            .description("Ticket mới #" + ticket.getTicketNumber() + " - " + ticket.getTitle())
            .userActor(createdBy, createdBy, null)
            .build();
    }
    
    /**
     * Tạo timeline event cho status change.
     */
    public static TicketTimeline forStatusChange(Ticket ticket, String oldStatus, String newStatus, 
                                                 String actorName, String actorRole) {
        String title = "Status thay đổi: " + oldStatus + " → " + newStatus;
        return builder()
            .ticket(ticket)
            .eventType(EventType.STATUS_CHANGED)
            .eventCategory(EventCategory.STATUS)
            .title(title)
            .oldValue(oldStatus)
            .newValue(newStatus)
            .userActor(actorName, actorName, actorRole)
            .build();
    }
    
    /**
     * Tạo timeline event cho assignment.
     */
    public static TicketTimeline forAssignment(Ticket ticket, String oldAssignee, String newAssignee,
                                               String actorName, String actorRole, boolean isAutoAssign) {
        String title = isAutoAssign ? "Tự động gán cho " + newAssignee : "Được gán cho " + newAssignee;
        return builder()
            .ticket(ticket)
            .eventType(isAutoAssign ? EventType.AUTO_ASSIGNED : EventType.ASSIGNED)
            .eventCategory(EventCategory.ASSIGNMENT)
            .title(title)
            .oldValue(oldAssignee != null ? oldAssignee : "Chưa gán")
            .newValue(newAssignee)
            .userActor(actorName, actorName, actorRole)
            .build();
    }
    
    /**
     * Tạo timeline event cho comment.
     */
    public static TicketTimeline forComment(Ticket ticket, boolean isInternal, String actorName, String actorRole) {
        String title = isInternal ? "Bình luận nội bộ" : "Bình luận";
        return builder()
            .ticket(ticket)
            .eventType(isInternal ? EventType.INTERNAL_COMMENT : EventType.PUBLIC_COMMENT)
            .eventCategory(EventCategory.COMMENT)
            .title(title)
            .userActor(actorName, actorName, actorRole)
            .build();
    }
    
    /**
     * Tạo timeline event cho SLA breach.
     */
    public static TicketTimeline forSlaBreach(Ticket ticket, String slaType, int hoursElapsed) {
        String title = slaType + " SLA đã bị vi phạm sau " + hoursElapsed + " giờ";
        return builder()
            .ticket(ticket)
            .eventType(EventType.SLA_BREACHED)
            .eventCategory(EventCategory.SLA)
            .title(title)
            .description("Ticket đã vượt quá " + slaType + " SLA target")
            .systemActor()
            .build();
    }
    
    /**
     * Tạo timeline event cho SLA warning.
     */
    public static TicketTimeline forSlaWarning(Ticket ticket, String slaType, int hoursRemaining) {
        String title = "Cảnh báo: " + slaType + " SLA còn " + hoursRemaining + " giờ";
        return builder()
            .ticket(ticket)
            .eventType(EventType.SLA_WARNING_SENT)
            .eventCategory(EventCategory.SLA)
            .title(title)
            .systemActor()
            .build();
    }
    
    /**
     * Tạo timeline event cho category change.
     */
    public static TicketTimeline forCategoryChange(Ticket ticket, String oldCategory, String newCategory,
                                                   String actorName, String actorRole) {
        return builder()
            .ticket(ticket)
            .eventType(EventType.CATEGORY_CHANGED)
            .eventCategory(EventCategory.CATEGORY)
            .title("Category thay đổi")
            .oldValue(oldCategory != null ? oldCategory : "Không có")
            .newValue(newCategory)
            .userActor(actorName, actorName, actorRole)
            .build();
    }
    
    /**
     * Tạo timeline event cho escalation.
     */
    public static TicketTimeline forEscalation(Ticket ticket, String escalatedBy, String reason) {
        return builder()
            .ticket(ticket)
            .eventType(EventType.ESCALATED)
            .eventCategory(EventCategory.ESCALATION)
            .title("Ticket được escalation")
            .description(reason)
            .userActor(escalatedBy, escalatedBy, null)
            .build();
    }
    
    // ============ Getters & Setters ============
    
    public Long getId() { return id; }
    
    public Ticket getTicket() { return ticket; }
    public void setTicket(Ticket ticket) { this.ticket = ticket; }
    public Long getTicketId() { return ticket != null ? ticket.getId() : null; }
    public String getTicketNumber() { return ticket != null ? ticket.getTicketNumber() : null; }
    
    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }
    
    public EventCategory getEventCategory() { return eventCategory; }
    public void setEventCategory(EventCategory eventCategory) { this.eventCategory = eventCategory; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public ActorType getActorType() { return actorType; }
    public void setActorType(ActorType actorType) { this.actorType = actorType; }
    
    public String getActorUsername() { return actorUsername; }
    public void setActorUsername(String actorUsername) { this.actorUsername = actorUsername; }
    
    public String getActorName() { return actorName; }
    public void setActorName(String actorName) { this.actorName = actorName; }
    
    public String getActorRole() { return actorRole; }
    public void setActorRole(String actorRole) { this.actorRole = actorRole; }
    
    public String getOldValue() { return oldValue; }
    public void setOldValue(String oldValue) { this.oldValue = oldValue; }
    
    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }
    
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    
    /**
     * Get metadata as typed object.
     */
    public <T> T getMetadataAs(Class<T> clazz) {
        if (metadata == null) return null;
        try {
            return objectMapper.readValue(metadata, clazz);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
    
    // ============ DTO ============
    
    public static class TimelineResponse {
        private Long id;
        private Long ticketId;
        private String ticketNumber;
        private String eventType;
        private String eventCategory;
        private String title;
        private String description;
        private String actorType;
        private String actorUsername;
        private String actorName;
        private String actorRole;
        private String oldValue;
        private String newValue;
        private Object metadata;
        private String createdAt;
        private String icon;
        private String color;
        
        public TimelineResponse() {}
        
        public TimelineResponse(TicketTimeline timeline) {
            this.id = timeline.getId();
            this.ticketId = timeline.getTicketId();
            this.ticketNumber = timeline.getTicketNumber();
            this.eventType = timeline.getEventType() != null ? timeline.getEventType().name() : null;
            this.eventCategory = timeline.getEventCategory() != null ? timeline.getEventCategory().name() : null;
            this.title = timeline.getTitle();
            this.description = timeline.getDescription();
            this.actorType = timeline.getActorType() != null ? timeline.getActorType().name() : null;
            this.actorUsername = timeline.getActorUsername();
            this.actorName = timeline.getActorName();
            this.actorRole = timeline.getActorRole();
            this.oldValue = timeline.getOldValue();
            this.newValue = timeline.getNewValue();
            this.createdAt = timeline.getCreatedAt() != null ? timeline.getCreatedAt().toString() : null;
            
            // Set icon và color dựa trên event type
            setIconAndColor(timeline.getEventType(), timeline.getEventCategory());
        }
        
        private void setIconAndColor(EventType eventType, EventCategory category) {
            if (eventType == null) return;
            
            switch (eventType) {
                case TICKET_CREATED, CREATED -> { this.icon = "📝"; this.color = "#28a745"; }
                case STATUS_CHANGED, IN_PROGRESS, RESOLVED, CLOSED -> { this.icon = "🔄"; this.color = "#007bff"; }
                case ASSIGNED, REASSIGNED, TEAM_ASSIGNED, AUTO_ASSIGNED -> { this.icon = "👤"; this.color = "#17a2b8"; }
                case UNASSIGNED -> { this.icon = "❌"; this.color = "#6c757d"; }
                case COMMENT_ADDED, INTERNAL_COMMENT, PUBLIC_COMMENT -> { this.icon = "💬"; this.color = "#ffc107"; }
                case SLA_WARNING_SENT -> { this.icon = "⚠️"; this.color = "#ffc107"; }
                case SLA_BREACHED -> { this.icon = "🚨"; this.color = "#dc3545"; }
                case ESCALATED -> { this.icon = "📈"; this.color = "#fd7e14"; }
                case CATEGORY_CHANGED -> { this.icon = "📁"; this.color = "#6f42c1"; }
                case PRIORITY_CHANGED -> { this.icon = "⬆️"; this.color = "#e83e8c"; }
                default -> { this.icon = "ℹ️"; this.color = "#6c757d"; }
            }
        }
        
        // Getters
        public Long getId() { return id; }
        public Long getTicketId() { return ticketId; }
        public String getTicketNumber() { return ticketNumber; }
        public String getEventType() { return eventType; }
        public String getEventCategory() { return eventCategory; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getActorType() { return actorType; }
        public String getActorUsername() { return actorUsername; }
        public String getActorName() { return actorName; }
        public String getActorRole() { return actorRole; }
        public String getOldValue() { return oldValue; }
        public String getNewValue() { return newValue; }
        public Object getMetadata() { return metadata; }
        public String getCreatedAt() { return createdAt; }
        public String getIcon() { return icon; }
        public String getColor() { return color; }
        
        // Setters
        public void setId(Long id) { this.id = id; }
        public void setTicketId(Long ticketId) { this.ticketId = ticketId; }
        public void setTicketNumber(String ticketNumber) { this.ticketNumber = ticketNumber; }
        public void setEventType(String eventType) { this.eventType = eventType; }
        public void setEventCategory(String eventCategory) { this.eventCategory = eventCategory; }
        public void setTitle(String title) { this.title = title; }
        public void setDescription(String description) { this.description = description; }
        public void setActorType(String actorType) { this.actorType = actorType; }
        public void setActorUsername(String actorUsername) { this.actorUsername = actorUsername; }
        public void setActorName(String actorName) { this.actorName = actorName; }
        public void setActorRole(String actorRole) { this.actorRole = actorRole; }
        public void setOldValue(String oldValue) { this.oldValue = oldValue; }
        public void setNewValue(String newValue) { this.newValue = newValue; }
        public void setMetadata(Object metadata) { this.metadata = metadata; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
        public void setIcon(String icon) { this.icon = icon; }
        public void setColor(String color) { this.color = color; }
    }
}
