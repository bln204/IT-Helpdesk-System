package com.example.ticketing.servicerequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Service Request Entity - Yêu cầu dịch vụ từ user.
 */
@Entity
@Table(name = "service_requests")
public class ServiceRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_number", unique = true, nullable = false, length = 50)
    private String requestNumber;

    @Column(name = "service_id")
    private Long serviceId;

    @Column(name = "service_name", length = 200)
    private String serviceName;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "requester_name", length = 100)
    private String requesterName;

    @Column(name = "requester_username", length = 100)
    private String requesterUsername;

    @Column(name = "requester_email", length = 160)
    private String requesterEmail;

    @Column(name = "requester_department", length = 100)
    private String requesterDepartment;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ServiceStatus status = ServiceStatus.SUBMITTED;

    @Column(name = "assigned_to", length = 100)
    private String assignedTo;

    @Column(name = "team_id")
    private Long teamId;

    @Column(name = "sla_response_at")
    private LocalDateTime slaResponseAt;

    @Column(name = "sla_resolution_at")
    private LocalDateTime slaResolutionAt;

    @Column(name = "first_response_at")
    private LocalDateTime firstResponseAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "approval_required")
    private Boolean approvalRequired = false;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approval_notes", columnDefinition = "TEXT")
    private String approvalNotes;

    @Column(name = "form_data", columnDefinition = "jsonb")
    private String formData;

    @Column(name = "estimated_cost", precision = 10, scale = 2)
    private BigDecimal estimatedCost;

    @Column(name = "actual_cost", precision = 10, scale = 2)
    private BigDecimal actualCost;

    @Column
    private Boolean billable = false;

    @Column
    private Boolean invoiced = false;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Priority priority = Priority.MEDIUM;

    @Column(name = "linked_ticket_id")
    private Long linkedTicketId;

    @Column
    private Integer rating;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "feedback_at")
    private LocalDateTime feedbackAt;

    @OneToMany(mappedBy = "serviceRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt DESC")
    private List<ServiceRequestComment> comments = new ArrayList<>();

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

    public enum ServiceStatus {
        SUBMITTED("Đã gửi"),
        PENDING_APPROVAL("Chờ phê duyệt"),
        APPROVED("Đã phê duyệt"),
        IN_PROGRESS("Đang xử lý"),
        COMPLETED("Hoàn thành"),
        REJECTED("Từ chối"),
        CANCELLED("Hủy bỏ");

        private final String label;

        ServiceStatus(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum Priority {
        LOW("Thấp"),
        MEDIUM("Trung bình"),
        HIGH("Cao"),
        CRITICAL("Nghiêm trọng");

        private final String label;

        Priority(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    // ==================== Constructors ====================

    public ServiceRequest() {
    }

    // ==================== Getters and Setters ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRequestNumber() { return requestNumber; }
    public void setRequestNumber(String requestNumber) { this.requestNumber = requestNumber; }
    public Long getServiceId() { return serviceId; }
    public void setServiceId(Long serviceId) { this.serviceId = serviceId; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getRequesterName() { return requesterName; }
    public void setRequesterName(String requesterName) { this.requesterName = requesterName; }
    public String getRequesterUsername() { return requesterUsername; }
    public void setRequesterUsername(String requesterUsername) { this.requesterUsername = requesterUsername; }
    public String getRequesterEmail() { return requesterEmail; }
    public void setRequesterEmail(String requesterEmail) { this.requesterEmail = requesterEmail; }
    public String getRequesterDepartment() { return requesterDepartment; }
    public void setRequesterDepartment(String requesterDepartment) { this.requesterDepartment = requesterDepartment; }
    public ServiceStatus getStatus() { return status; }
    public void setStatus(ServiceStatus status) { this.status = status; }
    public String getAssignedTo() { return assignedTo; }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }
    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }
    public LocalDateTime getSlaResponseAt() { return slaResponseAt; }
    public void setSlaResponseAt(LocalDateTime slaResponseAt) { this.slaResponseAt = slaResponseAt; }
    public LocalDateTime getSlaResolutionAt() { return slaResolutionAt; }
    public void setSlaResolutionAt(LocalDateTime slaResolutionAt) { this.slaResolutionAt = slaResolutionAt; }
    public LocalDateTime getFirstResponseAt() { return firstResponseAt; }
    public void setFirstResponseAt(LocalDateTime firstResponseAt) { this.firstResponseAt = firstResponseAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public Boolean getApprovalRequired() { return approvalRequired; }
    public void setApprovalRequired(Boolean approvalRequired) { this.approvalRequired = approvalRequired; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
    public String getApprovalNotes() { return approvalNotes; }
    public void setApprovalNotes(String approvalNotes) { this.approvalNotes = approvalNotes; }
    public String getFormData() { return formData; }
    public void setFormData(String formData) { this.formData = formData; }
    public BigDecimal getEstimatedCost() { return estimatedCost; }
    public void setEstimatedCost(BigDecimal estimatedCost) { this.estimatedCost = estimatedCost; }
    public BigDecimal getActualCost() { return actualCost; }
    public void setActualCost(BigDecimal actualCost) { this.actualCost = actualCost; }
    public Boolean getBillable() { return billable; }
    public void setBillable(Boolean billable) { this.billable = billable; }
    public Boolean getInvoiced() { return invoiced; }
    public void setInvoiced(Boolean invoiced) { this.invoiced = invoiced; }
    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }
    public Long getLinkedTicketId() { return linkedTicketId; }
    public void setLinkedTicketId(Long linkedTicketId) { this.linkedTicketId = linkedTicketId; }
    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }
    public String getFeedback() { return feedback; }
    public void setFeedback(String feedback) { this.feedback = feedback; }
    public LocalDateTime getFeedbackAt() { return feedbackAt; }
    public void setFeedbackAt(LocalDateTime feedbackAt) { this.feedbackAt = feedbackAt; }
    public List<ServiceRequestComment> getComments() { return comments; }
    public void setComments(List<ServiceRequestComment> comments) { this.comments = comments; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    // ==================== Helper Methods ====================

    public boolean isSlaResponseBreached() {
        if (slaResponseAt == null || firstResponseAt != null) return false;
        return LocalDateTime.now().isAfter(slaResponseAt);
    }

    public boolean isSlaResolutionBreached() {
        if (slaResolutionAt == null || completedAt != null) return false;
        return LocalDateTime.now().isAfter(slaResolutionAt);
    }

    @Override
    public String toString() {
        return "ServiceRequest{" +
                "id=" + id +
                ", requestNumber='" + requestNumber + '\'' +
                ", title='" + title + '\'' +
                ", status=" + status +
                '}';
    }
}
