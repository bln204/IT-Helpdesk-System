package com.example.ticketing.escalation;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository cho EscalationHistory entity.
 */
@Repository
public interface EscalationHistoryRepository extends JpaRepository<EscalationHistory, Long> {

    /**
     * Tìm lịch sử escalation theo ticket.
     */
    List<EscalationHistory> findByTicketIdOrderByEscalatedAtDesc(Long ticketId);

    /**
     * Tìm lịch sử escalation theo ticket number.
     */
    List<EscalationHistory> findByTicketNumberOrderByEscalatedAtDesc(String ticketNumber);

    /**
     * Đếm số lần escalation của một ticket.
     */
    long countByTicketId(Long ticketId);

    /**
     * Đếm số lần escalation theo rule.
     */
    long countByRuleId(Long ruleId);

    /**
     * Tìm escalation chưa resolved.
     */
    List<EscalationHistory> findByTicketIdAndResolvedAtIsNull(Long ticketId);

    /**
     * Tìm lịch sử escalation theo thời gian.
     */
    Page<EscalationHistory> findByEscalatedAtBetweenOrderByEscalatedAtDesc(
            LocalDateTime from, LocalDateTime to, Pageable pageable);

    /**
     * Tìm escalation theo level.
     */
    List<EscalationHistory> findByEscalationLevelOrderByEscalatedAtDesc(String level);

    /**
     * Đếm escalation theo ngày.
     */
    @Query("SELECT COUNT(e) FROM EscalationHistory e WHERE DATE(e.escalatedAt) = DATE(:date)")
    long countByDate(@Param("date") LocalDateTime date);

    /**
     * Tìm escalation chưa resolved theo ticket.
     */
    @Query("SELECT e FROM EscalationHistory e WHERE e.ticketId = :ticketId AND e.resolvedAt IS NULL")
    List<EscalationHistory> findUnresolvedByTicketId(@Param("ticketId") Long ticketId);
}
