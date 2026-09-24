package com.example.ticketing.ticket;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    Optional<Ticket> findByTicketNumber(String ticketNumber);

    Page<Ticket> findByAssigneeName(String assigneeName, Pageable pageable);

    Page<Ticket> findByAssigneeNameIsNull(Pageable pageable);

    Page<Ticket> findByAssigneeNameIsNullOrAssigneeName(
        String assigneeName,
        Pageable pageable
    );

    Page<Ticket> findByAssigneeNameIsNullAndStatus(
        TicketTypes.TicketStatus status,
        Pageable pageable
    );

    Page<Ticket> findByAssigneeNameIsNullOrAssigneeNameAndStatus(
        String assigneeName,
        TicketTypes.TicketStatus status,
        Pageable pageable
    );

    long countByStatus(TicketTypes.TicketStatus status);

    long countByRequesterUsername(String requesterUsername);

    long countByRequesterUsernameAndStatus(String requesterUsername, TicketTypes.TicketStatus status);

    Page<Ticket> findByStatus(TicketTypes.TicketStatus status, Pageable pageable);

    List<Ticket> findByCreatedAtBetween(java.time.LocalDateTime from, java.time.LocalDateTime to);

    List<Ticket> findByClosedAtBetween(java.time.LocalDateTime from, java.time.LocalDateTime to);

    Page<Ticket> findByAssigneeNameAndStatus(
        String assigneeName,
        TicketTypes.TicketStatus status,
        Pageable pageable
    );

    Page<Ticket> findByStatusNot(TicketTypes.TicketStatus status, Pageable pageable);

    Page<Ticket> findByAssigneeNameAndStatusNot(
        String assigneeName,
        TicketTypes.TicketStatus status,
        Pageable pageable
    );

    Page<Ticket> findByAssigneeNameIsNullOrAssigneeNameAndStatusNot(
        String assigneeName,
        TicketTypes.TicketStatus status,
        Pageable pageable
    );

    @Query("""
        SELECT t FROM Ticket t
        WHERE (:status IS NULL OR t.status = :status)
          AND (:assignee IS NULL OR t.assigneeName = :assignee)
          AND (
            LOWER(t.title) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(t.description) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(t.ticketNumber) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(t.requesterName) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(t.requesterEmail) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(t.assigneeName) LIKE LOWER(CONCAT('%', :query, '%'))
          )
        """)
    Page<Ticket> searchTickets(
        @Param("query") String query,
        @Param("status") TicketTypes.TicketStatus status,
        @Param("assignee") String assignee,
        Pageable pageable
    );

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.status <> :excludedStatus
          AND (:assignee IS NULL OR t.assigneeName = :assignee)
          AND (
            LOWER(t.title) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(t.description) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(t.ticketNumber) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(t.requesterName) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(t.requesterEmail) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(t.assigneeName) LIKE LOWER(CONCAT('%', :query, '%'))
          )
        """)
    Page<Ticket> searchTicketsExcludeStatus(
        @Param("query") String query,
        @Param("assignee") String assignee,
        @Param("excludedStatus") TicketTypes.TicketStatus excludedStatus,
        Pageable pageable
    );

    @Query("""
        SELECT t FROM Ticket t
        WHERE (:status IS NULL OR t.status = :status)
          AND (t.assigneeName IS NULL OR t.assigneeName = '')
          AND (
            LOWER(t.title) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(t.description) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(t.ticketNumber) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(t.requesterName) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(t.requesterEmail) LIKE LOWER(CONCAT('%', :query, '%'))
          )
        """)
    Page<Ticket> searchTicketsUnassigned(
        @Param("query") String query,
        @Param("status") TicketTypes.TicketStatus status,
        Pageable pageable
    );

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.status <> :excludedStatus
          AND (t.assigneeName IS NULL OR t.assigneeName = '')
          AND (
            LOWER(t.title) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(t.description) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(t.ticketNumber) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(t.requesterName) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(t.requesterEmail) LIKE LOWER(CONCAT('%', :query, '%'))
          )
        """)
    Page<Ticket> searchTicketsUnassignedExcludeStatus(
        @Param("query") String query,
        @Param("excludedStatus") TicketTypes.TicketStatus excludedStatus,
        Pageable pageable
    );

    // Department-based queries for NHAN_VIEN and TRUONG_PHONG
    @Query("SELECT t FROM Ticket t WHERE t.department.id = :departmentId")
    Page<Ticket> findByDepartmentId(@Param("departmentId") Long departmentId, Pageable pageable);

    @Query("SELECT t FROM Ticket t WHERE t.department.id = :departmentId AND t.status = :status")
    Page<Ticket> findByDepartmentIdAndStatus(@Param("departmentId") Long departmentId, @Param("status") TicketTypes.TicketStatus status, Pageable pageable);

    @Query("SELECT t FROM Ticket t WHERE t.department.id = :departmentId AND t.assigneeName = :assignee")
    Page<Ticket> findByDepartmentIdAndAssigneeName(@Param("departmentId") Long departmentId, @Param("assignee") String assigneeName, Pageable pageable);

    @Query("SELECT t FROM Ticket t WHERE t.department.id = :departmentId AND t.assigneeName = :assignee AND t.status = :status")
    Page<Ticket> findByDepartmentIdAndAssigneeNameAndStatus(@Param("departmentId") Long departmentId, @Param("assignee") String assigneeName, @Param("status") TicketTypes.TicketStatus status, Pageable pageable);

    @Query("SELECT t FROM Ticket t WHERE t.department.id = :departmentId AND t.status <> :status")
    Page<Ticket> findByDepartmentIdAndStatusNot(@Param("departmentId") Long departmentId, @Param("status") TicketTypes.TicketStatus status, Pageable pageable);

    @Query("SELECT t FROM Ticket t WHERE t.department.id = :departmentId AND (t.assigneeName IS NULL OR t.assigneeName = '')")
    Page<Ticket> findUnassignedByDepartmentId(@Param("departmentId") Long departmentId, Pageable pageable);

    @Query("SELECT t FROM Ticket t WHERE t.department.id = :departmentId AND (t.assigneeName IS NULL OR t.assigneeName = '') AND t.status = :status")
    Page<Ticket> findUnassignedByDepartmentIdAndStatus(@Param("departmentId") Long departmentId, @Param("status") TicketTypes.TicketStatus status, Pageable pageable);

    @Query("SELECT t FROM Ticket t WHERE t.department.id = :departmentId AND (t.assigneeName IS NULL OR t.assigneeName = '') AND t.status <> :excludedStatus")
    Page<Ticket> findUnassignedByDepartmentIdAndStatusNot(@Param("departmentId") Long departmentId, @Param("excludedStatus") TicketTypes.TicketStatus excludedStatus, Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.department.id = :departmentId
          AND (:status IS NULL OR t.status = :status)
          AND (:assignee IS NULL OR t.assigneeName = :assignee)
          AND (
            LOWER(t.title) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(t.description) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(t.ticketNumber) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(t.requesterName) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(t.requesterEmail) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(t.assigneeName) LIKE LOWER(CONCAT('%', :query, '%'))
          )
        """)
    Page<Ticket> searchTicketsByDepartment(
        @Param("departmentId") Long departmentId,
        @Param("query") String query,
        @Param("status") TicketTypes.TicketStatus status,
        @Param("assignee") String assignee,
        Pageable pageable
    );

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.department.id = :departmentId
          AND t.status <> :excludedStatus
          AND (:assignee IS NULL OR t.assigneeName = :assignee)
          AND (
            LOWER(t.title) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(t.description) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(t.ticketNumber) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(t.requesterName) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(t.requesterEmail) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(t.assigneeName) LIKE LOWER(CONCAT('%', :query, '%'))
          )
        """)
    Page<Ticket> searchTicketsByDepartmentExcludeStatus(
        @Param("departmentId") Long departmentId,
        @Param("query") String query,
        @Param("assignee") String assignee,
        @Param("excludedStatus") TicketTypes.TicketStatus excludedStatus,
        Pageable pageable
    );
    
    // ============ SLA Queries ============
    
    /**
     * Lấy open tickets cho SLA check.
     */
    @Query("""
        SELECT t FROM Ticket t
        WHERE t.status NOT IN ('CLOSED', 'RESOLVED', 'CANCELLED')
        AND (t.slaResponseAt IS NOT NULL OR t.slaResolutionAt IS NOT NULL)
        """)
    Page<Ticket> findOpenTicketsForSlaCheck(Pageable pageable);
    
    /**
     * Đếm tickets có SLA breach (resolution).
     */
    @Query("""
        SELECT COUNT(t) FROM Ticket t
        WHERE t.status NOT IN ('CLOSED', 'RESOLVED', 'CANCELLED')
        AND t.slaResolutionAt IS NOT NULL
        AND t.slaResolutionAt < CURRENT_TIMESTAMP
        """)
    long countBySlaBreached();
    
    /**
     * Đếm tickets có Response SLA breach.
     */
    @Query("""
        SELECT COUNT(t) FROM Ticket t
        WHERE t.status NOT IN ('CLOSED', 'RESOLVED', 'CANCELLED')
        AND t.firstResponseAt IS NULL
        AND t.slaResponseAt IS NOT NULL
        AND t.slaResponseAt < CURRENT_TIMESTAMP
        """)
    long countByResponseSlaBreached();
    
    /**
     * Đếm tất cả open tickets.
     */
    @Query("""
        SELECT COUNT(t) FROM Ticket t
        WHERE t.status NOT IN ('CLOSED', 'RESOLVED', 'CANCELLED')
        """)
    long countOpenTickets();
    
    // ============ Dashboard Queries ============
    
    /**
     * Đếm tickets assigned cho user và không phải các status đã đóng.
     */
    @Query("""
        SELECT COUNT(t) FROM Ticket t
        WHERE (t.assignee.id = :userId OR t.assigneeName = :username)
        AND t.status NOT IN ('CLOSED', 'RESOLVED', 'CANCELLED')
        """)
    long countByAssigneeIdAndStatusNotIn(@Param("userId") Long userId, @Param("username") String username);
    
    /**
     * Đếm tickets theo priority.
     */
    long countByPriority(TicketTypes.TicketPriority priority);
    
    // ============ Dashboard Ticket Queries ============
    
    /**
     * Tìm tickets assigned cho user.
     * Hỗ trợ cả assignee entity (mới) và assigneeName (legacy).
     */
    @Query("""
        SELECT t FROM Ticket t
        WHERE (t.assignee.id = :userId OR t.assigneeName = :username)
        AND t.status NOT IN ('CLOSED', 'RESOLVED', 'CANCELLED')
        ORDER BY t.priority ASC, t.createdAt ASC
        """)
    Page<Ticket> findMyAssignedTickets(@Param("userId") Long userId, @Param("username") String username, Pageable pageable);
    
    /**
     * Tìm unassigned tickets (status NEW).
     */
    @Query("""
        SELECT t FROM Ticket t
        WHERE t.assignee IS NULL
        AND t.status = 'NEW'
        ORDER BY t.priority ASC, t.createdAt ASC
        """)
    Page<Ticket> findUnassignedOpenTickets(Pageable pageable);
    
    /**
     * Tìm urgent tickets (priority cao, SLA breach).
     */
    @Query("""
        SELECT t FROM Ticket t
        WHERE t.status NOT IN ('CLOSED', 'RESOLVED', 'CANCELLED')
        AND (t.priority IN ('CRITICAL', 'HIGH')
             OR (t.slaResolutionAt IS NOT NULL AND t.slaResolutionAt < CURRENT_TIMESTAMP)
             OR (t.firstResponseAt IS NULL AND t.slaResponseAt IS NOT NULL AND t.slaResponseAt < CURRENT_TIMESTAMP))
        ORDER BY t.priority ASC, t.createdAt ASC
        """)
    Page<Ticket> findUrgentTickets(Pageable pageable);
    
    /**
     * Tìm recent tickets.
     */
    @Query("""
        SELECT t FROM Ticket t
        ORDER BY t.updatedAt DESC
        """)
    Page<Ticket> findRecentTickets(Pageable pageable);
}
