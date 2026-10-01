package com.example.ticketing.change;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository cho ChangeRequest entity.
 */
@Repository
public interface ChangeRequestRepository extends JpaRepository<ChangeRequest, Long> {

    Optional<ChangeRequest> findByChangeNumber(String changeNumber);

    Page<ChangeRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<ChangeRequest> findByStatusOrderByCreatedAtDesc(ChangeRequest.ChangeStatus status, Pageable pageable);

    Page<ChangeRequest> findByChangeTypeOrderByCreatedAtDesc(ChangeRequest.ChangeType changeType, Pageable pageable);

    Page<ChangeRequest> findByRiskLevelOrderByCreatedAtDesc(ChangeRequest.RiskLevel riskLevel, Pageable pageable);

    Page<ChangeRequest> findByRequesterUsernameOrderByCreatedAtDesc(String requesterUsername, Pageable pageable);

    Page<ChangeRequest> findByAssignedToOrderByCreatedAtDesc(String assignedTo, Pageable pageable);

    @Query("SELECT cr FROM ChangeRequest cr WHERE " +
           "LOWER(cr.title) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(cr.description) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "cr.changeNumber LIKE CONCAT('%', :search, '%')")
    Page<ChangeRequest> searchChangeRequests(@Param("search") String search, Pageable pageable);

    @Query("SELECT cr FROM ChangeRequest cr WHERE " +
           "cr.status IN :statuses ORDER BY cr.priority DESC, cr.createdAt ASC")
    List<ChangeRequest> findByStatusInOrderByPriority(@Param("statuses") List<ChangeRequest.ChangeStatus> statuses);

    List<ChangeRequest> findByScheduledStartDateBetween(
            java.time.LocalDateTime start, java.time.LocalDateTime end);

    long countByStatus(ChangeRequest.ChangeStatus status);

    @Query("SELECT cr FROM ChangeRequest cr WHERE " +
           "cr.status NOT IN ('COMPLETED', 'CANCELLED', 'REJECTED') " +
           "ORDER BY cr.priority DESC, cr.riskScore DESC")
    List<ChangeRequest> findActiveChanges();

    List<ChangeRequest> findByStatusInOrderByScheduledStartDateAsc(List<ChangeRequest.ChangeStatus> statuses);

    List<ChangeRequest> findByStatusInOrderByCreatedAtDesc(List<ChangeRequest.ChangeStatus> statuses);

    // New methods added

    /**
     * Find changes by status and review status (ApprovalStatus).
     * Review status is determined by the approval workflow.
     */
    @Query("SELECT DISTINCT cr FROM ChangeRequest cr " +
           "LEFT JOIN cr.approvals ca " +
           "WHERE cr.status = :status AND ca.status = :reviewStatus")
    List<ChangeRequest> findByStatusAndReviewStatus(
            @Param("status") ChangeRequest.ChangeStatus status,
            @Param("reviewStatus") ChangeApproval.ApprovalStatus reviewStatus);

    /**
     * Find changes by change type and status.
     */
    List<ChangeRequest> findByChangeTypeAndStatus(
            ChangeRequest.ChangeType changeType, 
            ChangeRequest.ChangeStatus status);

    /**
     * Find changes by review status (ApprovalStatus).
     * Returns changes that have approvals with the given review status.
     */
    @Query("SELECT DISTINCT cr FROM ChangeRequest cr " +
           "JOIN cr.approvals ca " +
           "WHERE ca.status = :reviewStatus")
    List<ChangeRequest> findByReviewStatus(@Param("reviewStatus") ChangeApproval.ApprovalStatus reviewStatus);
}
