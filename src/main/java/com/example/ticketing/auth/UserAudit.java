package com.example.ticketing.auth;

import java.time.LocalDateTime;

import com.example.ticketing.ticket.TicketTypes;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", nullable = false, length = 16)
    private TicketTypes.TicketRole actorRole;

    @Column(name = "target_username", nullable = false, length = 80)
    private String targetUsername;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

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

    public TicketTypes.TicketRole getActorRole() {
        return actorRole;
    }

    public void setActorRole(TicketTypes.TicketRole actorRole) {
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
}
