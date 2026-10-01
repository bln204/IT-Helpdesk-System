package com.example.ticketing.change;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Change Request Entity - Yêu cầu thay đổi hạ tầng IT.
 */
@Entity
@Table(name = "change_requests")
public class ChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "change_number", unique = true, nullable = false, length = 50)
    private String changeNumber;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", length = 30)
    private ChangeType changeType = ChangeType.STANDARD;

    @Column(length = 100)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 20)
    private RiskLevel riskLevel = RiskLevel.MEDIUM;

    @Column(name = "risk_score")
    private Integer riskScore = 0;

    @Column(name = "risk_factors", columnDefinition = "jsonb")
    private String riskFactors;

    @Column(name = "risk_mitigation", columnDefinition = "TEXT")
    private String riskMitigation;

    @Enumerated(EnumType.STRING)
    @Column(name = "impact_level", length = 20)
    private ImpactLevel impactLevel = ImpactLevel.MODERATE;

    @Column(name = "affected_systems", columnDefinition = "TEXT")
    private String affectedSystems;

    @Column(name = "affected_users_count")
    private Integer affectedUsersCount = 0;

    @Column(name = "estimated_downtime_minutes")
    private Integer estimatedDowntimeMinutes = 0;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ChangeStatus status = ChangeStatus.DRAFT;

    @Column(name = "implementation_plan", columnDefinition = "TEXT")
    private String implementationPlan;

    @Column(name = "rollback_plan", columnDefinition = "TEXT")
    private String rollbackPlan;

    @Column(name = "scheduled_start_date")
    private LocalDateTime scheduledStartDate;

    @Column(name = "scheduled_end_date")
    private LocalDateTime scheduledEndDate;

    @Column(name = "actual_start_date")
    private LocalDateTime actualStartDate;

    @Column(name = "actual_end_date")
    private LocalDateTime actualEndDate;

    @Column(name = "requester_name", length = 100)
    private String requesterName;

    @Column(name = "requester_username", length = 100)
    private String requesterUsername;

    @Column(name = "requester_email", length = 160)
    private String requesterEmail;

    @Column(name = "requester_department", length = 100)
    private String requesterDepartment;

    @Column(name = "assigned_to", length = 100)
    private String assignedTo;

    @Column(name = "team_id")
    private Long teamId;

    @Column(name = "cab_board", length = 50)
    private String cabBoard;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approval_notes", columnDefinition = "TEXT")
    private String approvalNotes;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "rejected_by", length = 100)
    private String rejectedBy;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "change_owner", length = 100)
    private String changeOwner;

    @Column(name = "completion_notes", columnDefinition = "TEXT")
    private String completionNotes;

    @Column(name = "completed_by", length = 100)
    private String completedBy;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;

    @Column(name = "lessons_learned", columnDefinition = "TEXT")
    private String lessonsLearned;

    @Column(name = "backout_confirmed")
    private Boolean backoutConfirmed = false;

    @Column(name = "backout_performed")
    private Boolean backoutPerformed = false;

    @Column(name = "linked_tickets", columnDefinition = "TEXT")
    private String linkedTickets;

    @Column(name = "linked_problems", columnDefinition = "TEXT")
    private String linkedProblems;

    @Column(name = "linked_incidents", columnDefinition = "TEXT")
    private String linkedIncidents;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Priority priority = Priority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Urgency urgency = Urgency.NORMAL;

    @OneToMany(mappedBy = "changeRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("approvalLevel ASC")
    private List<ChangeApproval> approvals = new ArrayList<>();

    @OneToMany(mappedBy = "changeRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt DESC")
    private List<ChangeTimeline> timeline = new ArrayList<>();

    @OneToMany(mappedBy = "changeRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("taskOrder ASC")
    private List<ChangeTask> tasks = new ArrayList<>();

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

    public enum ChangeType {
        STANDARD("Standard"),
        NORMAL("Normal"),
        EMERGENCY("Khẩn cấp");

        private final String label;
        ChangeType(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum ChangeStatus {
        DRAFT("Nháp"),
        SUBMITTED("Đã gửi"),
        PENDING_APPROVAL("Chờ phê duyệt"),
        APPROVED("Đã phê duyệt"),
        REJECTED("Từ chối"),
        SCHEDULED("Đã lên lịch"),
        IN_PROGRESS("Đang thực hiện"),
        COMPLETED("Hoàn thành"),
        ROLLED_BACK("Đã rollback"),
        CANCELLED("Hủy bỏ");

        private final String label;
        ChangeStatus(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum RiskLevel {
        LOW("Thấp"),
        MEDIUM("Trung bình"),
        HIGH("Cao"),
        CRITICAL("Nghiêm trọng");

        private final String label;
        RiskLevel(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum ImpactLevel {
        MINIMAL("Tối thiểu"),
        MODERATE("Trung bình"),
        SIGNIFICANT("Đáng kể"),
        SEVERE("Nghiêm trọng"),
        CATASTROPHIC("Thảm khốc");

        private final String label;
        ImpactLevel(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum Priority {
        LOW("Thấp"),
        MEDIUM("Trung bình"),
        HIGH("Cao"),
        CRITICAL("Nghiêm trọng");

        private final String label;
        Priority(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum Urgency {
        LOW("Thấp"),
        NORMAL("Bình thường"),
        HIGH("Cao"),
        CRITICAL("Nghiêm trọng");

        private final String label;
        Urgency(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    // ==================== Constructors ====================

    public ChangeRequest() {
    }

    // ==================== Getters and Setters ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getChangeNumber() { return changeNumber; }
    public void setChangeNumber(String changeNumber) { this.changeNumber = changeNumber; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public ChangeType getChangeType() { return changeType; }
    public void setChangeType(ChangeType changeType) { this.changeType = changeType; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public void setRiskLevel(RiskLevel riskLevel) { this.riskLevel = riskLevel; }
    public Integer getRiskScore() { return riskScore; }
    public void setRiskScore(Integer riskScore) { this.riskScore = riskScore; }
    public String getRiskFactors() { return riskFactors; }
    public void setRiskFactors(String riskFactors) { this.riskFactors = riskFactors; }
    public String getRiskMitigation() { return riskMitigation; }
    public void setRiskMitigation(String riskMitigation) { this.riskMitigation = riskMitigation; }
    public ImpactLevel getImpactLevel() { return impactLevel; }
    public void setImpactLevel(ImpactLevel impactLevel) { this.impactLevel = impactLevel; }
    public String getAffectedSystems() { return affectedSystems; }
    public void setAffectedSystems(String affectedSystems) { this.affectedSystems = affectedSystems; }
    public Integer getAffectedUsersCount() { return affectedUsersCount; }
    public void setAffectedUsersCount(Integer affectedUsersCount) { this.affectedUsersCount = affectedUsersCount; }
    public Integer getEstimatedDowntimeMinutes() { return estimatedDowntimeMinutes; }
    public void setEstimatedDowntimeMinutes(Integer estimatedDowntimeMinutes) { this.estimatedDowntimeMinutes = estimatedDowntimeMinutes; }
    public ChangeStatus getStatus() { return status; }
    public void setStatus(ChangeStatus status) { this.status = status; }
    public String getImplementationPlan() { return implementationPlan; }
    public void setImplementationPlan(String implementationPlan) { this.implementationPlan = implementationPlan; }
    public String getRollbackPlan() { return rollbackPlan; }
    public void setRollbackPlan(String rollbackPlan) { this.rollbackPlan = rollbackPlan; }
    public LocalDateTime getScheduledStartDate() { return scheduledStartDate; }
    public void setScheduledStartDate(LocalDateTime scheduledStartDate) { this.scheduledStartDate = scheduledStartDate; }
    public LocalDateTime getScheduledEndDate() { return scheduledEndDate; }
    public void setScheduledEndDate(LocalDateTime scheduledEndDate) { this.scheduledEndDate = scheduledEndDate; }
    public LocalDateTime getActualStartDate() { return actualStartDate; }
    public void setActualStartDate(LocalDateTime actualStartDate) { this.actualStartDate = actualStartDate; }
    public LocalDateTime getActualEndDate() { return actualEndDate; }
    public void setActualEndDate(LocalDateTime actualEndDate) { this.actualEndDate = actualEndDate; }
    public String getRequesterName() { return requesterName; }
    public void setRequesterName(String requesterName) { this.requesterName = requesterName; }
    public String getRequesterUsername() { return requesterUsername; }
    public void setRequesterUsername(String requesterUsername) { this.requesterUsername = requesterUsername; }
    public String getRequesterEmail() { return requesterEmail; }
    public void setRequesterEmail(String requesterEmail) { this.requesterEmail = requesterEmail; }
    public String getRequesterDepartment() { return requesterDepartment; }
    public void setRequesterDepartment(String requesterDepartment) { this.requesterDepartment = requesterDepartment; }
    public String getAssignedTo() { return assignedTo; }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }
    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }
    public String getCabBoard() { return cabBoard; }
    public void setCabBoard(String cabBoard) { this.cabBoard = cabBoard; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
    public String getApprovalNotes() { return approvalNotes; }
    public void setApprovalNotes(String approvalNotes) { this.approvalNotes = approvalNotes; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public String getRejectedBy() { return rejectedBy; }
    public void setRejectedBy(String rejectedBy) { this.rejectedBy = rejectedBy; }
    public LocalDateTime getRejectedAt() { return rejectedAt; }
    public void setRejectedAt(LocalDateTime rejectedAt) { this.rejectedAt = rejectedAt; }
    public String getChangeOwner() { return changeOwner; }
    public void setChangeOwner(String changeOwner) { this.changeOwner = changeOwner; }
    public String getCompletionNotes() { return completionNotes; }
    public void setCompletionNotes(String completionNotes) { this.completionNotes = completionNotes; }
    public String getCompletedBy() { return completedBy; }
    public void setCompletedBy(String completedBy) { this.completedBy = completedBy; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public String getReviewNotes() { return reviewNotes; }
    public void setReviewNotes(String reviewNotes) { this.reviewNotes = reviewNotes; }
    public String getLessonsLearned() { return lessonsLearned; }
    public void setLessonsLearned(String lessonsLearned) { this.lessonsLearned = lessonsLearned; }
    public Boolean getBackoutConfirmed() { return backoutConfirmed; }
    public void setBackoutConfirmed(Boolean backoutConfirmed) { this.backoutConfirmed = backoutConfirmed; }
    public Boolean getBackoutPerformed() { return backoutPerformed; }
    public void setBackoutPerformed(Boolean backoutPerformed) { this.backoutPerformed = backoutPerformed; }
    public String getLinkedTickets() { return linkedTickets; }
    public void setLinkedTickets(String linkedTickets) { this.linkedTickets = linkedTickets; }
    public String getLinkedProblems() { return linkedProblems; }
    public void setLinkedProblems(String linkedProblems) { this.linkedProblems = linkedProblems; }
    public String getLinkedIncidents() { return linkedIncidents; }
    public void setLinkedIncidents(String linkedIncidents) { this.linkedIncidents = linkedIncidents; }
    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }
    public Urgency getUrgency() { return urgency; }
    public void setUrgency(Urgency urgency) { this.urgency = urgency; }
    public List<ChangeApproval> getApprovals() { return approvals; }
    public void setApprovals(List<ChangeApproval> approvals) { this.approvals = approvals; }
    public List<ChangeTimeline> getTimeline() { return timeline; }
    public void setTimeline(List<ChangeTimeline> timeline) { this.timeline = timeline; }
    public List<ChangeTask> getTasks() { return tasks; }
    public void setTasks(List<ChangeTask> tasks) { this.tasks = tasks; }
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
        return "ChangeRequest{" +
                "id=" + id +
                ", changeNumber='" + changeNumber + '\'' +
                ", title='" + title + '\'' +
                ", status=" + status +
                '}';
    }
}
