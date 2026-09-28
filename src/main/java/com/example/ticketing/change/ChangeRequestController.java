package com.example.ticketing.change;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller cho Change Management.
 */
@RestController
@RequestMapping("/api/changes")
@CrossOrigin(origins = "*")
public class ChangeRequestController {

    private static final Logger log = LoggerFactory.getLogger(ChangeRequestController.class);

    private final ChangeRequestService changeService;

    public ChangeRequestController(ChangeRequestService changeService) {
        this.changeService = changeService;
    }

    // ==================== Change Requests ====================

    /**
     * Lấy tất cả changes.
     * GET /api/changes
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'NHAN_VIEN')")
    public ResponseEntity<Page<ChangeRequestDto>> getAllChanges(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String risk,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("GET /api/changes - status: {}, search: {}", status, search);

        Pageable pageable = Pageable.ofSize(size).withPage(page);
        Page<ChangeRequest> changes;

        if (search != null && !search.isBlank()) {
            changes = changeService.searchChanges(search, pageable);
        } else if (status != null && !status.isBlank()) {
            ChangeRequest.ChangeStatus changeStatus = ChangeRequest.ChangeStatus.valueOf(status);
            changes = changeService.getChangesByStatus(changeStatus, pageable);
        } else {
            changes = changeService.getAllChanges(pageable);
        }

        return ResponseEntity.ok(changes.map(ChangeRequestDto::fromEntity));
    }

    /**
     * Lấy changes của user hiện tại.
     * GET /api/changes/my
     */
    @GetMapping("/my")
    public ResponseEntity<Page<ChangeRequestDto>> getMyChanges(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("GET /api/changes/my");
        Pageable pageable = Pageable.ofSize(size).withPage(page);
        // In real app, get from SecurityContext
        Page<ChangeRequest> changes = changeService.getMyChanges("current_user", pageable);
        return ResponseEntity.ok(changes.map(ChangeRequestDto::fromEntity));
    }

    /**
     * Lấy change theo ID.
     * GET /api/changes/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ChangeRequestDto> getChangeById(@PathVariable Long id) {
        log.info("GET /api/changes/{}", id);
        ChangeRequest change = changeService.getChangeById(id);
        return ResponseEntity.ok(ChangeRequestDto.fromEntity(change));
    }

    /**
     * Tạo change request.
     * POST /api/changes
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'NHAN_VIEN')")
    public ResponseEntity<ChangeRequestDto> createChange(@RequestBody ChangeRequestDto request) {
        log.info("POST /api/changes - Creating: {}", request.getTitle());

        ChangeRequest change = new ChangeRequest();
        change.setTitle(request.getTitle());
        change.setDescription(request.getDescription());
        change.setChangeType(request.getChangeTypeEnum());
        change.setCategory(request.getCategory());
        change.setRiskLevel(request.getRiskLevelEnum());
        change.setRiskScore(request.getRiskScore());
        change.setRiskFactors(request.getRiskFactors());
        change.setRiskMitigation(request.getRiskMitigation());
        change.setImpactLevel(request.getImpactLevelEnum());
        change.setAffectedSystems(request.getAffectedSystems());
        change.setAffectedUsersCount(request.getAffectedUsersCount());
        change.setEstimatedDowntimeMinutes(request.getEstimatedDowntimeMinutes());
        change.setImplementationPlan(request.getImplementationPlan());
        change.setRollbackPlan(request.getRollbackPlan());
        change.setScheduledStartDate(request.getScheduledStartDate());
        change.setScheduledEndDate(request.getScheduledEndDate());
        change.setPriority(request.getPriorityEnum());
        change.setUrgency(request.getUrgencyEnum());
        change.setRequesterName(request.getRequesterName());
        change.setRequesterUsername(request.getRequesterUsername());
        change.setRequesterEmail(request.getRequesterEmail());
        change.setRequesterDepartment(request.getRequesterDepartment());

        ChangeRequest created = changeService.createChangeRequest(change, "admin");
        return ResponseEntity.status(HttpStatus.CREATED).body(ChangeRequestDto.fromEntity(created));
    }

    /**
     * Cập nhật change.
     * PUT /api/changes/{id}
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'NHAN_VIEN')")
    public ResponseEntity<ChangeRequestDto> updateChange(
            @PathVariable Long id,
            @RequestBody ChangeRequestDto request) {
        log.info("PUT /api/changes/{}", id);

        ChangeRequest updates = new ChangeRequest();
        updates.setTitle(request.getTitle());
        updates.setDescription(request.getDescription());
        updates.setChangeType(request.getChangeTypeEnum());
        updates.setCategory(request.getCategory());
        updates.setRiskLevel(request.getRiskLevelEnum());
        updates.setRiskScore(request.getRiskScore());
        updates.setRiskFactors(request.getRiskFactors());
        updates.setRiskMitigation(request.getRiskMitigation());
        updates.setImpactLevel(request.getImpactLevelEnum());
        updates.setAffectedSystems(request.getAffectedSystems());
        updates.setAffectedUsersCount(request.getAffectedUsersCount());
        updates.setEstimatedDowntimeMinutes(request.getEstimatedDowntimeMinutes());
        updates.setImplementationPlan(request.getImplementationPlan());
        updates.setRollbackPlan(request.getRollbackPlan());
        updates.setScheduledStartDate(request.getScheduledStartDate());
        updates.setScheduledEndDate(request.getScheduledEndDate());
        updates.setPriority(request.getPriorityEnum());
        updates.setUrgency(request.getUrgencyEnum());
        updates.setChangeOwner(request.getChangeOwner());

        ChangeRequest updated = changeService.updateChangeRequest(id, updates, "admin");
        return ResponseEntity.ok(ChangeRequestDto.fromEntity(updated));
    }

    /**
     * Xóa change.
     * DELETE /api/changes/{id}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteChange(@PathVariable Long id) {
        log.info("DELETE /api/changes/{}", id);
        changeService.deleteChangeRequest(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== Status Actions ====================

    /**
     * Gửi để review.
     * POST /api/changes/{id}/submit
     */
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('ADMIN', 'NHAN_VIEN')")
    public ResponseEntity<ChangeRequestDto> submitForReview(@PathVariable Long id) {
        log.info("POST /api/changes/{}/submit", id);
        ChangeRequest updated = changeService.submitForReview(id, "admin");
        return ResponseEntity.ok(ChangeRequestDto.fromEntity(updated));
    }

    /**
     * Phê duyệt.
     * POST /api/changes/{id}/approve
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'NHAN_VIEN')")
    public ResponseEntity<ChangeRequestDto> approveChange(
            @PathVariable Long id,
            @RequestBody ApprovalRequest request) {
        log.info("POST /api/changes/{}/approve", id);
        ChangeRequest updated = changeService.approveChange(id, request.getApprover(), request.getNotes(), "admin");
        return ResponseEntity.ok(ChangeRequestDto.fromEntity(updated));
    }

    /**
     * Từ chối.
     * POST /api/changes/{id}/reject
     */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'NHAN_VIEN')")
    public ResponseEntity<ChangeRequestDto> rejectChange(
            @PathVariable Long id,
            @RequestBody ApprovalRequest request) {
        log.info("POST /api/changes/{}/reject", id);
        ChangeRequest updated = changeService.rejectChange(id, request.getApprover(), request.getNotes(), "admin");
        return ResponseEntity.ok(ChangeRequestDto.fromEntity(updated));
    }

    /**
     * Bắt đầu implementation.
     * POST /api/changes/{id}/start
     */
    @PostMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('ADMIN', 'NHAN_VIEN')")
    public ResponseEntity<ChangeRequestDto> startImplementation(@PathVariable Long id) {
        log.info("POST /api/changes/{}/start", id);
        ChangeRequest updated = changeService.startImplementation(id, "admin");
        return ResponseEntity.ok(ChangeRequestDto.fromEntity(updated));
    }

    /**
     * Hoàn thành.
     * POST /api/changes/{id}/complete
     */
    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('ADMIN', 'NHAN_VIEN')")
    public ResponseEntity<ChangeRequestDto> completeChange(
            @PathVariable Long id,
            @RequestBody CompletionRequest request) {
        log.info("POST /api/changes/{}/complete", id);
        ChangeRequest updated = changeService.completeChange(id, request.getNotes(), "admin");
        return ResponseEntity.ok(ChangeRequestDto.fromEntity(updated));
    }

    /**
     * Hủy.
     * POST /api/changes/{id}/cancel
     */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'NHAN_VIEN')")
    public ResponseEntity<ChangeRequestDto> cancelChange(
            @PathVariable Long id,
            @RequestBody CancelRequest request) {
        log.info("POST /api/changes/{}/cancel", id);
        ChangeRequest updated = changeService.cancelChange(id, request.getReason(), "admin");
        return ResponseEntity.ok(ChangeRequestDto.fromEntity(updated));
    }

    // ==================== Timeline ====================

    /**
     * Lấy timeline.
     * GET /api/changes/{id}/timeline
     */
    @GetMapping("/{id}/timeline")
    public ResponseEntity<List<ChangeTimelineDto>> getTimeline(@PathVariable Long id) {
        log.info("GET /api/changes/{}/timeline", id);
        List<ChangeTimeline> timeline = changeService.getTimeline(id);
        return ResponseEntity.ok(timeline.stream()
                .map(ChangeTimelineDto::fromEntity)
                .collect(Collectors.toList()));
    }

    // ==================== Exception Handlers ====================

    @ExceptionHandler(ChangeRequestService.ChangeNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<ErrorResponse> handleNotFound(ChangeRequestService.ChangeNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("BAD_REQUEST", ex.getMessage()));
    }

    // ==================== DTOs ====================

    public static class ErrorResponse {
        private String code;
        private String message;
        public ErrorResponse(String code, String message) {
            this.code = code;
            this.message = message;
        }
        public String getCode() { return code; }
        public String getMessage() { return message; }
    }

    public static class ChangeRequestDto {
        private Long id;
        private String changeNumber;
        private String title;
        private String description;
        private String changeType;
        private String changeTypeLabel;
        private String category;
        private String riskLevel;
        private String riskLevelLabel;
        private Integer riskScore;
        private String impactLevel;
        private String impactLevelLabel;
        private Integer affectedUsersCount;
        private Integer estimatedDowntimeMinutes;
        private String status;
        private String statusLabel;
        private String implementationPlan;
        private String rollbackPlan;
        private java.time.LocalDateTime scheduledStartDate;
        private java.time.LocalDateTime scheduledEndDate;
        private String requesterName;
        private String requesterUsername;
        private String assignedTo;
        private String changeOwner;
        private String priority;
        private String urgency;
        private java.time.LocalDateTime createdAt;
        private java.time.LocalDateTime updatedAt;

        public static ChangeRequestDto fromEntity(ChangeRequest cr) {
            ChangeRequestDto dto = new ChangeRequestDto();
            dto.setId(cr.getId());
            dto.setChangeNumber(cr.getChangeNumber());
            dto.setTitle(cr.getTitle());
            dto.setDescription(cr.getDescription());
            dto.setChangeType(cr.getChangeType() != null ? cr.getChangeType().name() : null);
            dto.setChangeTypeLabel(cr.getChangeType() != null ? cr.getChangeType().getLabel() : null);
            dto.setCategory(cr.getCategory());
            dto.setRiskLevel(cr.getRiskLevel() != null ? cr.getRiskLevel().name() : null);
            dto.setRiskLevelLabel(cr.getRiskLevel() != null ? cr.getRiskLevel().getLabel() : null);
            dto.setRiskScore(cr.getRiskScore());
            dto.setImpactLevel(cr.getImpactLevel() != null ? cr.getImpactLevel().name() : null);
            dto.setImpactLevelLabel(cr.getImpactLevel() != null ? cr.getImpactLevel().getLabel() : null);
            dto.setAffectedUsersCount(cr.getAffectedUsersCount());
            dto.setEstimatedDowntimeMinutes(cr.getEstimatedDowntimeMinutes());
            dto.setStatus(cr.getStatus() != null ? cr.getStatus().name() : null);
            dto.setStatusLabel(cr.getStatus() != null ? cr.getStatus().getLabel() : null);
            dto.setImplementationPlan(cr.getImplementationPlan());
            dto.setRollbackPlan(cr.getRollbackPlan());
            dto.setScheduledStartDate(cr.getScheduledStartDate());
            dto.setScheduledEndDate(cr.getScheduledEndDate());
            dto.setRequesterName(cr.getRequesterName());
            dto.setRequesterUsername(cr.getRequesterUsername());
            dto.setAssignedTo(cr.getAssignedTo());
            dto.setChangeOwner(cr.getChangeOwner());
            dto.setPriority(cr.getPriority() != null ? cr.getPriority().name() : null);
            dto.setUrgency(cr.getUrgency() != null ? cr.getUrgency().name() : null);
            dto.setCreatedAt(cr.getCreatedAt());
            dto.setUpdatedAt(cr.getUpdatedAt());
            return dto;
        }

        // Getters/Setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getChangeNumber() { return changeNumber; }
        public void setChangeNumber(String changeNumber) { this.changeNumber = changeNumber; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getChangeType() { return changeType; }
        public void setChangeType(String changeType) { this.changeType = changeType; }
        public String getChangeTypeLabel() { return changeTypeLabel; }
        public void setChangeTypeLabel(String changeTypeLabel) { this.changeTypeLabel = changeTypeLabel; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getRiskLevel() { return riskLevel; }
        public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
        public String getRiskLevelLabel() { return riskLevelLabel; }
        public void setRiskLevelLabel(String riskLevelLabel) { this.riskLevelLabel = riskLevelLabel; }
        public Integer getRiskScore() { return riskScore; }
        public void setRiskScore(Integer riskScore) { this.riskScore = riskScore; }
        public String getImpactLevel() { return impactLevel; }
        public void setImpactLevel(String impactLevel) { this.impactLevel = impactLevel; }
        public String getImpactLevelLabel() { return impactLevelLabel; }
        public void setImpactLevelLabel(String impactLevelLabel) { this.impactLevelLabel = impactLevelLabel; }
        public Integer getAffectedUsersCount() { return affectedUsersCount; }
        public void setAffectedUsersCount(Integer affectedUsersCount) { this.affectedUsersCount = affectedUsersCount; }
        public Integer getEstimatedDowntimeMinutes() { return estimatedDowntimeMinutes; }
        public void setEstimatedDowntimeMinutes(Integer estimatedDowntimeMinutes) { this.estimatedDowntimeMinutes = estimatedDowntimeMinutes; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getStatusLabel() { return statusLabel; }
        public void setStatusLabel(String statusLabel) { this.statusLabel = statusLabel; }
        public String getImplementationPlan() { return implementationPlan; }
        public void setImplementationPlan(String implementationPlan) { this.implementationPlan = implementationPlan; }
        public String getRollbackPlan() { return rollbackPlan; }
        public void setRollbackPlan(String rollbackPlan) { this.rollbackPlan = rollbackPlan; }
        public java.time.LocalDateTime getScheduledStartDate() { return scheduledStartDate; }
        public void setScheduledStartDate(java.time.LocalDateTime scheduledStartDate) { this.scheduledStartDate = scheduledStartDate; }
        public java.time.LocalDateTime getScheduledEndDate() { return scheduledEndDate; }
        public void setScheduledEndDate(java.time.LocalDateTime scheduledEndDate) { this.scheduledEndDate = scheduledEndDate; }
        public String getRequesterName() { return requesterName; }
        public void setRequesterName(String requesterName) { this.requesterName = requesterName; }
        public String getRequesterUsername() { return requesterUsername; }
        public void setRequesterUsername(String requesterUsername) { this.requesterUsername = requesterUsername; }
        public String getAssignedTo() { return assignedTo; }
        public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }
        public String getChangeOwner() { return changeOwner; }
        public void setChangeOwner(String changeOwner) { this.changeOwner = changeOwner; }
        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
        public String getUrgency() { return urgency; }
        public void setUrgency(String urgency) { this.urgency = urgency; }
        public java.time.LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }
        public java.time.LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(java.time.LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

        // Additional fields for request
        public String getRiskFactors() { return null; }
        public void setRiskFactors(String riskFactors) {}
        public String getRiskMitigation() { return null; }
        public void setRiskMitigation(String riskMitigation) {}
        public String getAffectedSystems() { return null; }
        public void setAffectedSystems(String affectedSystems) {}
        public String getRequesterEmail() { return null; }
        public void setRequesterEmail(String requesterEmail) {}
        public String getRequesterDepartment() { return null; }
        public void setRequesterDepartment(String requesterDepartment) {}

        // Enum converters for DTO -> Entity mapping
        public ChangeRequest.ChangeType getChangeTypeEnum() {
            return changeType != null ? ChangeRequest.ChangeType.valueOf(changeType) : null;
        }
        
        public ChangeRequest.RiskLevel getRiskLevelEnum() {
            return riskLevel != null ? ChangeRequest.RiskLevel.valueOf(riskLevel) : null;
        }
        
        public ChangeRequest.ImpactLevel getImpactLevelEnum() {
            return impactLevel != null ? ChangeRequest.ImpactLevel.valueOf(impactLevel) : null;
        }
        
        public ChangeRequest.Priority getPriorityEnum() {
            return priority != null ? ChangeRequest.Priority.valueOf(priority) : null;
        }
        
        public ChangeRequest.Urgency getUrgencyEnum() {
            return urgency != null ? ChangeRequest.Urgency.valueOf(urgency) : null;
        }
    }

    public static class ChangeTimelineDto {
        private Long id;
        private String eventType;
        private String actorName;
        private String notes;
        private java.time.LocalDateTime createdAt;

        public static ChangeTimelineDto fromEntity(ChangeTimeline tl) {
            ChangeTimelineDto dto = new ChangeTimelineDto();
            dto.setId(tl.getId());
            dto.setEventType(tl.getEventType() != null ? tl.getEventType().name() : null);
            dto.setActorName(tl.getActorName());
            dto.setNotes(tl.getNotes());
            dto.setCreatedAt(tl.getCreatedAt());
            return dto;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
        public String getActorName() { return actorName; }
        public void setActorName(String actorName) { this.actorName = actorName; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
        public java.time.LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }
    }

    public static class ApprovalRequest {
        private String approver;
        private String notes;
        public String getApprover() { return approver; }
        public String getNotes() { return notes; }
    }

    public static class CompletionRequest {
        private String notes;
        public String getNotes() { return notes; }
    }

    public static class CancelRequest {
        private String reason;
        public String getReason() { return reason; }
    }
}
