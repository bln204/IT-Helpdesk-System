package com.example.ticketing.escalation;

import java.time.LocalDateTime;
import java.time.LocalTime;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.example.ticketing.ticket.TicketTypes.TicketPriority;

/**
 * Escalation Rule Entity - Lưu trữ cấu hình escalation tự động.
 */
@Entity
@Table(name = "escalation_rules")
public class EscalationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    /**
     * Trigger type - khi nào rule được kích hoạt.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 50)
    private EscalationTrigger triggerType;

    /**
     * Minimum priority để áp dụng rule này.
     * Ví dụ: HIGH = áp dụng cho HIGH, CRITICAL
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "min_priority", length = 20)
    private TicketPriority minPriority;

    /**
     * Số phút trước/sau breach để trigger.
     * Positive = trước breach, Negative = sau breach
     */
    @Column(name = "minutes_threshold")
    private Integer minutesThreshold = 0;

    /**
     * User để escalate đến.
     */
    @Column(name = "escalate_to_user_id")
    private Long escalateToUserId;

    /**
     * Team để escalate đến.
     */
    @Column(name = "escalate_to_team_id")
    private Long escalateToTeamId;

    /**
     * Escalation level.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "escalation_level", length = 20)
    private EscalationLevel escalationLevel = EscalationLevel.LEVEL_1;

    /**
     * Action type - hành động khi trigger.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", length = 50)
    private EscalationAction actionType = EscalationAction.NOTIFY;

    /**
     * Template message cho notification.
     */
    @Column(name = "notification_message", length = 500)
    private String notificationMessage;

    /**
     * Thời gian áp dụng (business hours).
     */
    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    /**
     * Ngày trong tuần áp dụng (MON,TUE,WED,THU,FRI).
     */
    @Column(name = "days_of_week", length = 50)
    private String daysOfWeek;

    /**
     * Số lần tối đa rule này có thể trigger cho 1 ticket.
     */
    @Column(name = "max_escalations")
    private Integer maxEscalations = 3;

    @Column(nullable = false)
    private Boolean enabled = true;

    /**
     * Thứ tự ưu tiên (cao hơn = kiểm tra trước).
     */
    @Column
    private Integer priority = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    // ==================== Enums ====================

    public enum EscalationTrigger {
        SLA_RESPONSE_WARNING,    // 75% SLA response time used
        SLA_RESPONSE_BREACHED,   // SLA response breached
        SLA_RESOLUTION_WARNING,  // 75% SLA resolution time used
        SLA_RESOLUTION_BREACHED, // SLA resolution breached
        CRITICAL_TICKET,         // New ticket with CRITICAL priority
        MANUAL                   // Manual escalation only
    }

    public enum EscalationLevel {
        LEVEL_1,  // Team Lead
        LEVEL_2,  // IT Manager
        LEVEL_3   // Director
    }

    public enum EscalationAction {
        NOTIFY,     // Gửi thông báo
        REASSIGN,   // Gán lại cho escalation target
        ESCALATE_STATUS // Đổi status thành ESCALATED
    }

    // ==================== Constructors ====================

    public EscalationRule() {
    }

    // ==================== Utility Methods ====================

    /**
     * Kiểm tra xem rule có applicable vào thời điểm hiện tại không.
     */
    public boolean isCurrentlyApplicable() {
        if (!enabled) return false;

        LocalDateTime now = LocalDateTime.now();
        
        // Check day of week
        if (daysOfWeek != null && !daysOfWeek.isEmpty()) {
            String currentDay = now.getDayOfWeek().name().substring(0, 3);
            if (!daysOfWeek.contains(currentDay)) {
                return false;
            }
        }

        // Check time range
        if (startTime != null && endTime != null) {
            LocalTime currentTime = now.toLocalTime();
            if (currentTime.isBefore(startTime) || currentTime.isAfter(endTime)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Kiểm tra xem priority có đủ điều kiện không.
     */
    public boolean isPriorityApplicable(TicketPriority ticketPriority) {
        if (minPriority == null) return true;
        
        return ticketPriority.ordinal() >= minPriority.ordinal();
    }

    /**
     * Format trigger type thành label.
     */
    public String getTriggerLabel() {
        if (triggerType == null) return "";
        return switch (triggerType) {
            case SLA_RESPONSE_WARNING -> "Cảnh báo SLA phản hồi";
            case SLA_RESPONSE_BREACHED -> "SLA phản hồi vi phạm";
            case SLA_RESOLUTION_WARNING -> "Cảnh báo SLA giải quyết";
            case SLA_RESOLUTION_BREACHED -> "SLA giải quyết vi phạm";
            case CRITICAL_TICKET -> "Ticket Critical mới";
            case MANUAL -> "Thủ công";
        };
    }

    /**
     * Format action type thành label.
     */
    public String getActionLabel() {
        if (actionType == null) return "";
        return switch (actionType) {
            case NOTIFY -> "Gửi thông báo";
            case REASSIGN -> "Gán lại";
            case ESCALATE_STATUS -> "Đổi trạng thái";
        };
    }

    // ==================== Getters and Setters ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public EscalationTrigger getTriggerType() { return triggerType; }
    public void setTriggerType(EscalationTrigger triggerType) { this.triggerType = triggerType; }
    public TicketPriority getMinPriority() { return minPriority; }
    public void setMinPriority(TicketPriority minPriority) { this.minPriority = minPriority; }
    public Integer getMinutesThreshold() { return minutesThreshold; }
    public void setMinutesThreshold(Integer minutesThreshold) { this.minutesThreshold = minutesThreshold; }
    public Long getEscalateToUserId() { return escalateToUserId; }
    public void setEscalateToUserId(Long escalateToUserId) { this.escalateToUserId = escalateToUserId; }
    public Long getEscalateToTeamId() { return escalateToTeamId; }
    public void setEscalateToTeamId(Long escalateToTeamId) { this.escalateToTeamId = escalateToTeamId; }
    public EscalationLevel getEscalationLevel() { return escalationLevel; }
    public void setEscalationLevel(EscalationLevel escalationLevel) { this.escalationLevel = escalationLevel; }
    public EscalationAction getActionType() { return actionType; }
    public void setActionType(EscalationAction actionType) { this.actionType = actionType; }
    public String getNotificationMessage() { return notificationMessage; }
    public void setNotificationMessage(String notificationMessage) { this.notificationMessage = notificationMessage; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
    public String getDaysOfWeek() { return daysOfWeek; }
    public void setDaysOfWeek(String daysOfWeek) { this.daysOfWeek = daysOfWeek; }
    public Integer getMaxEscalations() { return maxEscalations; }
    public void setMaxEscalations(Integer maxEscalations) { this.maxEscalations = maxEscalations; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    @Override
    public String toString() {
        return "EscalationRule{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", triggerType=" + triggerType +
                ", escalationLevel=" + escalationLevel +
                '}';
    }
}
