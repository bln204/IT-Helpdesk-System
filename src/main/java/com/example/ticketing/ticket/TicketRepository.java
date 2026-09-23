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
}
