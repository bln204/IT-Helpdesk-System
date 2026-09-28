package com.example.ticketing.escalation;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Escalation History Entity - Lưu trữ lịch sử các escalation.
 */
@Entity
@Table(name = "escalation_history")
public class EscalationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "ticket_number", length = 50)
    private String ticketNumber;

    /**
     * Rule đã trigger escalation này.
     */
    @Column(name = "rule_id")
    private Long ruleId;

    @Column(name = "rule_name", length = 100)
    private String ruleName;

    /**
     * Level của escalation.
     */
    @Column(name = "escalation_level", length = 20)
    private String escalationLevel;

    /**
     * Lý do escalation.
     */
    @Column(name = "escalation_reason", length = 200)
    private String escalationReason;

    /**
     * Action đã thực hiện.
     */
    @Column(name = "action_taken", length = 50)
    private String actionTaken;

    @Column(name = "notification_sent")
    private Boolean notificationSent = false;

    @Column(name = "notification_recipients", length = 500)
    private String notificationRecipients;

    /**
     * Thông tin reassignment.
     */
    @Column(name = "previous_assignee", length = 100)
    private String previousAssignee;

    @Column(name = "new_assignee", length = 100)
    private String newAssignee;

    @Column(name = "previous_team_id")
    private Long previousTeamId;

    @Column(name = "new_team_id")
    private Long newTeamId;

    /**
     * Thông tin status change.
     */
    @Column(name = "previous_status", length = 30)
    private String previousStatus;

    @Column(name = "new_status", length = 30)
    private String newStatus;

    @Column(name = "escalated_at")
    private LocalDateTime escalatedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // ==================== Constructors ====================

    public EscalationHistory() {
        this.escalatedAt = LocalDateTime.now();
    }

    public EscalationHistory(Long ticketId, String ticketNumber) {
        this();
        this.ticketId = ticketId;
        this.ticketNumber = ticketNumber;
    }

    // ==================== Builder Pattern ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final EscalationHistory history;

        public Builder() {
            history = new EscalationHistory();
        }

        public Builder ticketId(Long ticketId) {
            history.ticketId = ticketId;
            return this;
        }

        public Builder ticketNumber(String ticketNumber) {
            history.ticketNumber = ticketNumber;
            return this;
        }

        public Builder rule(EscalationRule rule) {
            history.ruleId = rule.getId();
            history.ruleName = rule.getName();
            history.escalationLevel = rule.getEscalationLevel() != null ? rule.getEscalationLevel().name() : null;
            history.actionTaken = rule.getActionType() != null ? rule.getActionType().name() : null;
            return this;
        }

        public Builder reason(String reason) {
            history.escalationReason = reason;
            return this;
        }

        public Builder previousAssignee(String previousAssignee) {
            history.previousAssignee = previousAssignee;
            return this;
        }

        public Builder newAssignee(String newAssignee) {
            history.newAssignee = newAssignee;
            return this;
        }

        public Builder previousTeamId(Long previousTeamId) {
            history.previousTeamId = previousTeamId;
            return this;
        }

        public Builder newTeamId(Long newTeamId) {
            history.newTeamId = newTeamId;
            return this;
        }

        public Builder previousStatus(String previousStatus) {
            history.previousStatus = previousStatus;
            return this;
        }

        public Builder newStatus(String newStatus) {
            history.newStatus = newStatus;
            return this;
        }

        public Builder notificationSent(Boolean sent) {
            history.notificationSent = sent;
            return this;
        }

        public Builder recipients(String recipients) {
            history.notificationRecipients = recipients;
            return this;
        }

        public Builder notes(String notes) {
            history.notes = notes;
            return this;
        }

        public Builder createdBy(String createdBy) {
            history.createdBy = createdBy;
            return this;
        }

        public EscalationHistory build() {
            return history;
        }
    }

    // ==================== Getters and Setters ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTicketId() { return ticketId; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }
    public String getTicketNumber() { return ticketNumber; }
    public void setTicketNumber(String ticketNumber) { this.ticketNumber = ticketNumber; }
    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public String getEscalationLevel() { return escalationLevel; }
    public void setEscalationLevel(String escalationLevel) { this.escalationLevel = escalationLevel; }
    public String getEscalationReason() { return escalationReason; }
    public void setEscalationReason(String escalationReason) { this.escalationReason = escalationReason; }
    public String getActionTaken() { return actionTaken; }
    public void setActionTaken(String actionTaken) { this.actionTaken = actionTaken; }
    public Boolean getNotificationSent() { return notificationSent; }
    public void setNotificationSent(Boolean notificationSent) { this.notificationSent = notificationSent; }
    public String getNotificationRecipients() { return notificationRecipients; }
    public void setNotificationRecipients(String notificationRecipients) { this.notificationRecipients = notificationRecipients; }
    public String getPreviousAssignee() { return previousAssignee; }
    public void setPreviousAssignee(String previousAssignee) { this.previousAssignee = previousAssignee; }
    public String getNewAssignee() { return newAssignee; }
    public void setNewAssignee(String newAssignee) { this.newAssignee = newAssignee; }
    public Long getPreviousTeamId() { return previousTeamId; }
    public void setPreviousTeamId(Long previousTeamId) { this.previousTeamId = previousTeamId; }
    public Long getNewTeamId() { return newTeamId; }
    public void setNewTeamId(Long newTeamId) { this.newTeamId = newTeamId; }
    public String getPreviousStatus() { return previousStatus; }
    public void setPreviousStatus(String previousStatus) { this.previousStatus = previousStatus; }
    public String getNewStatus() { return newStatus; }
    public void setNewStatus(String newStatus) { this.newStatus = newStatus; }
    public LocalDateTime getEscalatedAt() { return escalatedAt; }
    public void setEscalatedAt(LocalDateTime escalatedAt) { this.escalatedAt = escalatedAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "EscalationHistory{" +
                "id=" + id +
                ", ticketId=" + ticketId +
                ", ticketNumber='" + ticketNumber + '\'' +
                ", ruleName='" + ruleName + '\'' +
                ", escalationLevel='" + escalationLevel + '\'' +
                '}';
    }
}
