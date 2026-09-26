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

import com.example.ticketing.auth.UserAccount;
import com.example.ticketing.team.Team;

/**
 * Entity lưu trữ lịch sử phân công ticket.
 * Mở rộng để hỗ trợ cả assignment cho individual và team.
 */
@Entity
@Table(name = "ticket_assignments")
public class TicketAssignment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    // Legacy fields
    @Column(name = "previous_assignee", length = 120)
    private String previousAssignee;

    @Column(name = "new_assignee", length = 120)
    private String newAssignee;
    
    // NEW: Assignee ID (FK to UserAccount)
    @Column(name = "assignee_id")
    private Long assigneeId;
    
    // NEW: Team ID (FK to Team)
    @Column(name = "team_id")
    private Long teamId;
    
    // NEW: Assignment type
    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_type", length = 20)
    private AssignmentType assignmentType = AssignmentType.INDIVIDUAL;
    
    public enum AssignmentType {
        INDIVIDUAL,  // Gán cho cá nhân
        TEAM         // Gán cho team
    }
    
    @Column(name = "actor_role", nullable = false, length = 16)
    private String actorRole;

    @Column(name = "actor_name", length = 120)
    private String actorName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // ============ Getters & Setters ============

    public Long getId() {
        return id;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    public String getPreviousAssignee() {
        return previousAssignee;
    }

    public void setPreviousAssignee(String previousAssignee) {
        this.previousAssignee = previousAssignee;
    }

    public String getNewAssignee() {
        return newAssignee;
    }

    public void setNewAssignee(String newAssignee) {
        this.newAssignee = newAssignee;
    }
    
    public Long getAssigneeId() {
        return assigneeId;
    }
    
    public void setAssigneeId(Long assigneeId) {
        this.assigneeId = assigneeId;
    }
    
    public Long getTeamId() {
        return teamId;
    }
    
    public void setTeamId(Long teamId) {
        this.teamId = teamId;
    }
    
    public AssignmentType getAssignmentType() {
        return assignmentType;
    }
    
    public void setAssignmentType(AssignmentType assignmentType) {
        this.assignmentType = assignmentType;
    }

    public String getActorRole() {
        return actorRole;
    }

    public void setActorRole(String actorRole) {
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
