package com.example.ticketing.ticket;

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
@Table(name = "ticket_audit")
public class TicketAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 24)
    private TicketTypes.AuditAction action;

    @Column(name = "field_name", length = 64)
    private String fieldName;

    @Column(name = "old_value", length = 512)
    private String oldValue;

    @Column(name = "new_value", length = 512)
    private String newValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", nullable = false, length = 16)
    private TicketTypes.TicketRole actorRole;

    @Column(name = "actor_name", length = 120)
    private String actorName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    public TicketTypes.AuditAction getAction() {
        return action;
    }

    public void setAction(TicketTypes.AuditAction action) {
        this.action = action;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
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

    public TicketTypes.TicketRole getActorRole() {
        return actorRole;
    }

    public void setActorRole(TicketTypes.TicketRole actorRole) {
        this.actorRole = actorRole;
    }

    public String getActorName() {
        return actorName;
    }

    public void setActorName(String actorName) {
        this.actorName = actorName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
