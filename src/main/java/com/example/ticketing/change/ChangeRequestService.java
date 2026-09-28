package com.example.ticketing.change;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service cho Change Management.
 */
@Service
@Transactional
public class ChangeRequestService {

    private static final Logger log = LoggerFactory.getLogger(ChangeRequestService.class);

    private final ChangeRequestRepository changeRepository;
    private final ChangeApprovalRepository approvalRepository;
    private final ChangeTimelineRepository timelineRepository;
    private final ChangeTaskRepository taskRepository;

    public ChangeRequestService(
            ChangeRequestRepository changeRepository,
            ChangeApprovalRepository approvalRepository,
            ChangeTimelineRepository timelineRepository,
            ChangeTaskRepository taskRepository) {
        this.changeRepository = changeRepository;
        this.approvalRepository = approvalRepository;
        this.timelineRepository = timelineRepository;
        this.taskRepository = taskRepository;
    }

    // ==================== Change Request CRUD ====================

    /**
     * Tạo change request mới.
     */
    public ChangeRequest createChangeRequest(ChangeRequest request, String createdBy) {
        log.info("Creating change request: {}", request.getTitle());
        
        request.setChangeNumber(generateChangeNumber());
        request.setCreatedBy(createdBy);
        request.setRequesterUsername(createdBy);
        request.setStatus(ChangeRequest.ChangeStatus.DRAFT);
        
        ChangeRequest saved = changeRepository.save(request);
        
        // Add timeline event
        addTimelineEvent(saved, ChangeTimeline.EventType.CREATED, createdBy, "Change request created");
        
        return saved;
    }

    /**
     * Lấy change request theo ID.
     */
    @Transactional(readOnly = true)
    public ChangeRequest getChangeById(Long id) {
        return changeRepository.findById(id)
                .orElseThrow(() -> new ChangeNotFoundException(id));
    }

    /**
     * Lấy change request theo số.
     */
    @Transactional(readOnly = true)
    public ChangeRequest getChangeByNumber(String number) {
        return changeRepository.findByChangeNumber(number)
                .orElseThrow(() -> new ChangeNotFoundException(number));
    }

    /**
     * Cập nhật change request.
     */
    public ChangeRequest updateChangeRequest(Long id, ChangeRequest updates, String updatedBy) {
        ChangeRequest existing = getChangeById(id);
        
        existing.setTitle(updates.getTitle());
        existing.setDescription(updates.getDescription());
        existing.setChangeType(updates.getChangeType());
        existing.setCategory(updates.getCategory());
        existing.setRiskLevel(updates.getRiskLevel());
        existing.setRiskScore(updates.getRiskScore());
        existing.setRiskFactors(updates.getRiskFactors());
        existing.setRiskMitigation(updates.getRiskMitigation());
        existing.setImpactLevel(updates.getImpactLevel());
        existing.setAffectedSystems(updates.getAffectedSystems());
        existing.setAffectedUsersCount(updates.getAffectedUsersCount());
        existing.setEstimatedDowntimeMinutes(updates.getEstimatedDowntimeMinutes());
        existing.setImplementationPlan(updates.getImplementationPlan());
        existing.setRollbackPlan(updates.getRollbackPlan());
        existing.setScheduledStartDate(updates.getScheduledStartDate());
        existing.setScheduledEndDate(updates.getScheduledEndDate());
        existing.setChangeOwner(updates.getChangeOwner());
        existing.setPriority(updates.getPriority());
        existing.setUrgency(updates.getUrgency());
        existing.setUpdatedBy(updatedBy);
        
        return changeRepository.save(existing);
    }

    /**
     * Xóa change request.
     */
    public void deleteChangeRequest(Long id) {
        log.info("Deleting change request: {}", id);
        changeRepository.deleteById(id);
    }

    // ==================== Status Management ====================

    /**
     * Gửi change request để review.
     */
    public ChangeRequest submitForReview(Long id, String actorName) {
        ChangeRequest change = getChangeById(id);
        
        if (change.getStatus() != ChangeRequest.ChangeStatus.DRAFT) {
            throw new IllegalStateException("Chỉ có thể gửi từ trạng thái DRAFT");
        }
        
        change.setStatus(ChangeRequest.ChangeStatus.SUBMITTED);
        change.setUpdatedBy(actorName);
        
        ChangeRequest saved = changeRepository.save(change);
        
        // Determine approval workflow based on risk level
        setupApprovalWorkflow(saved);
        
        addTimelineEvent(saved, ChangeTimeline.EventType.SUBMITTED, actorName, "Submitted for review");
        
        return saved;
    }

    /**
     * Phê duyệt change request.
     */
    public ChangeRequest approveChange(Long id, String approverName, String notes, String actorName) {
        ChangeRequest change = getChangeById(id);
        
        change.setStatus(ChangeRequest.ChangeStatus.APPROVED);
        change.setApprovedBy(approverName);
        change.setApprovedAt(LocalDateTime.now());
        change.setApprovalNotes(notes);
        change.setUpdatedBy(actorName);
        
        ChangeRequest saved = changeRepository.save(change);
        
        addTimelineEvent(saved, ChangeTimeline.EventType.APPROVED, actorName, notes);
        
        return saved;
    }

    /**
     * Từ chối change request.
     */
    public ChangeRequest rejectChange(Long id, String rejectorName, String reason, String actorName) {
        ChangeRequest change = getChangeById(id);
        
        change.setStatus(ChangeRequest.ChangeStatus.REJECTED);
        change.setRejectedBy(rejectorName);
        change.setRejectedAt(LocalDateTime.now());
        change.setRejectionReason(reason);
        change.setUpdatedBy(actorName);
        
        ChangeRequest saved = changeRepository.save(change);
        
        addTimelineEvent(saved, ChangeTimeline.EventType.REJECTED, actorName, reason);
        
        return saved;
    }

    /**
     * Bắt đầu thực hiện change.
     */
    public ChangeRequest startImplementation(Long id, String actorName) {
        ChangeRequest change = getChangeById(id);
        
        if (change.getStatus() != ChangeRequest.ChangeStatus.APPROVED &&
            change.getStatus() != ChangeRequest.ChangeStatus.SCHEDULED) {
            throw new IllegalStateException("Chỉ có thể bắt đầu từ trạng thái APPROVED hoặc SCHEDULED");
        }
        
        change.setStatus(ChangeRequest.ChangeStatus.IN_PROGRESS);
        change.setActualStartDate(LocalDateTime.now());
        change.setUpdatedBy(actorName);
        
        ChangeRequest saved = changeRepository.save(change);
        
        addTimelineEvent(saved, ChangeTimeline.EventType.STARTED, actorName, "Implementation started");
        
        return saved;
    }

    /**
     * Hoàn thành change.
     */
    public ChangeRequest completeChange(Long id, String completionNotes, String actorName) {
        ChangeRequest change = getChangeById(id);
        
        if (change.getStatus() != ChangeRequest.ChangeStatus.IN_PROGRESS) {
            throw new IllegalStateException("Chỉ có thể hoàn thành từ trạng thái IN_PROGRESS");
        }
        
        change.setStatus(ChangeRequest.ChangeStatus.COMPLETED);
        change.setActualEndDate(LocalDateTime.now());
        change.setCompletionNotes(completionNotes);
        change.setCompletedBy(actorName);
        change.setCompletedAt(LocalDateTime.now());
        change.setUpdatedBy(actorName);
        
        ChangeRequest saved = changeRepository.save(change);
        
        addTimelineEvent(saved, ChangeTimeline.EventType.COMPLETED, actorName, completionNotes);
        
        return saved;
    }

    /**
     * Hủy change request.
     */
    public ChangeRequest cancelChange(Long id, String reason, String actorName) {
        ChangeRequest change = getChangeById(id);
        
        change.setStatus(ChangeRequest.ChangeStatus.CANCELLED);
        change.setUpdatedBy(actorName);
        
        ChangeRequest saved = changeRepository.save(change);
        
        addTimelineEvent(saved, ChangeTimeline.EventType.CANCELLED, actorName, reason);
        
        return saved;
    }

    /**
     * Rollback change.
     */
    public ChangeRequest rollbackChange(Long id, String reason, String actorName) {
        ChangeRequest change = getChangeById(id);
        
        change.setStatus(ChangeRequest.ChangeStatus.ROLLED_BACK);
        change.setBackoutPerformed(true);
        change.setUpdatedBy(actorName);
        
        ChangeRequest saved = changeRepository.save(change);
        
        addTimelineEvent(saved, ChangeTimeline.EventType.ROLLED_BACK, actorName, reason);
        
        return saved;
    }

    // ==================== Approval Workflow ====================

    private void setupApprovalWorkflow(ChangeRequest change) {
        // Standard changes auto-approve if low risk
        if (change.getChangeType() == ChangeRequest.ChangeType.STANDARD && 
            change.getRiskLevel() == ChangeRequest.RiskLevel.LOW) {
            change.setStatus(ChangeRequest.ChangeStatus.APPROVED);
            change.setApprovedBy("SYSTEM");
            change.setApprovedAt(LocalDateTime.now());
            changeRepository.save(change);
            return;
        }
        
        // Normal changes need approval
        change.setStatus(ChangeRequest.ChangeStatus.PENDING_APPROVAL);
        changeRepository.save(change);
    }

    // ==================== Timeline ====================

    private void addTimelineEvent(ChangeRequest change, ChangeTimeline.EventType eventType, 
                                  String actorName, String notes) {
        ChangeTimeline timeline = new ChangeTimeline(change, eventType, actorName, actorName);
        timeline.setNotes(notes);
        timelineRepository.save(timeline);
    }

    /**
     * Lấy timeline của change.
     */
    @Transactional(readOnly = true)
    public List<ChangeTimeline> getTimeline(Long changeId) {
        return timelineRepository.findByChangeRequestIdOrderByCreatedAtAsc(changeId);
    }

    // ==================== Tasks ====================

    /**
     * Thêm task.
     */
    public ChangeTask addTask(Long changeId, String title, int order) {
        ChangeRequest change = getChangeById(changeId);
        ChangeTask task = new ChangeTask(change, title, order);
        return taskRepository.save(task);
    }

    /**
     * Lấy tasks.
     */
    @Transactional(readOnly = true)
    public List<ChangeTask> getTasks(Long changeId) {
        return taskRepository.findByChangeRequestIdOrderByTaskOrderAsc(changeId);
    }

    // ==================== Query Methods ====================

    /**
     * Lấy tất cả changes.
     */
    @Transactional(readOnly = true)
    public Page<ChangeRequest> getAllChanges(Pageable pageable) {
        return changeRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    /**
     * Tìm kiếm changes.
     */
    @Transactional(readOnly = true)
    public Page<ChangeRequest> searchChanges(String search, Pageable pageable) {
        return changeRepository.searchChangeRequests(search, pageable);
    }

    /**
     * Lấy changes theo status.
     */
    @Transactional(readOnly = true)
    public Page<ChangeRequest> getChangesByStatus(ChangeRequest.ChangeStatus status, Pageable pageable) {
        return changeRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
    }

    /**
     * Lấy changes của user.
     */
    @Transactional(readOnly = true)
    public Page<ChangeRequest> getMyChanges(String username, Pageable pageable) {
        return changeRepository.findByRequesterUsernameOrderByCreatedAtDesc(username, pageable);
    }

    // ==================== Helper Methods ====================

    private String generateChangeNumber() {
        String uuid = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "CHG-" + uuid;
    }

    // ==================== Exceptions ====================

    public static class ChangeNotFoundException extends RuntimeException {
        public ChangeNotFoundException(Long id) {
            super("Không tìm thấy Change Request với ID: " + id);
        }
        public ChangeNotFoundException(String number) {
            super("Không tìm thấy Change Request với số: " + number);
        }
    }
}
