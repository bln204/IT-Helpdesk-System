package com.example.ticketing.change;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Change Approval Entity - Approval workflow cho Change Requests.
 */
@Entity
@Table(name = "change_approvals")
public class ChangeApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "change_request_id", nullable = false)
    private ChangeRequest changeRequest;

    @Column(name = "approver_name", length = 100)
    private String approverName;

    @Column(name = "approver_username", length = 100)
    private String approverUsername;

    @Column(name = "approver_role", length = 50)
    private String approverRole;

    @Column(name = "approval_level")
    private Integer approvalLevel = 1;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @Column(name = "decision_at")
    private LocalDateTime decisionAt;

    @Column(columnDefinition = "TEXT")
    private String comments;

    @Column(name = "notified_at")
    private LocalDateTime notifiedAt;

    @Column(name = "reminded_at")
    private LocalDateTime remindedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // ==================== Enums ====================

    public enum ApprovalStatus {
        PENDING("Chờ duyệt"),
        APPROVED("Đã duyệt"),
        REJECTED("Từ chối"),
        SKIPPED("Bỏ qua");

        private final String label;
        ApprovalStatus(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    // ==================== Constructors ====================

    public ChangeApproval() {
    }

    public ChangeApproval(ChangeRequest changeRequest, String approverName, String approverUsername, int level) {
        this.changeRequest = changeRequest;
        this.approverName = approverName;
        this.approverUsername = approverUsername;
        this.approvalLevel = level;
    }

    // ==================== Getters and Setters ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public ChangeRequest getChangeRequest() { return changeRequest; }
    public void setChangeRequest(ChangeRequest changeRequest) { this.changeRequest = changeRequest; }
    public String getApproverName() { return approverName; }
    public void setApproverName(String approverName) { this.approverName = approverName; }
    public String getApproverUsername() { return approverUsername; }
    public void setApproverUsername(String approverUsername) { this.approverUsername = approverUsername; }
    public String getApproverRole() { return approverRole; }
    public void setApproverRole(String approverRole) { this.approverRole = approverRole; }
    public Integer getApprovalLevel() { return approvalLevel; }
    public void setApprovalLevel(Integer approvalLevel) { this.approvalLevel = approvalLevel; }
    public ApprovalStatus getStatus() { return status; }
    public void setStatus(ApprovalStatus status) { this.status = status; }
    public LocalDateTime getDecisionAt() { return decisionAt; }
    public void setDecisionAt(LocalDateTime decisionAt) { this.decisionAt = decisionAt; }
    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }
    public LocalDateTime getNotifiedAt() { return notifiedAt; }
    public void setNotifiedAt(LocalDateTime notifiedAt) { this.notifiedAt = notifiedAt; }
    public LocalDateTime getRemindedAt() { return remindedAt; }
    public void setRemindedAt(LocalDateTime remindedAt) { this.remindedAt = remindedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "ChangeApproval{" +
                "id=" + id +
                ", approverUsername='" + approverUsername + '\'' +
                ", status=" + status +
                '}';
    }
}
