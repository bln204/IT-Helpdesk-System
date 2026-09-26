package com.example.ticketing.ticket;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository cho SlaAlertLog entity.
 */
@Repository
public interface SlaAlertLogRepository extends JpaRepository<SlaAlertLog, Long> {
    
    /**
     * Tìm alert logs cho một ticket.
     */
    List<SlaAlertLog> findByTicketId(Long ticketId);
    
    /**
     * Kiểm tra alert đã được gửi chưa.
     */
    boolean existsByTicketIdAndSlaTypeAndAlertType(Long ticketId, String slaType, String alertType);
    
    /**
     * Tìm alert cụ thể.
     */
    Optional<SlaAlertLog> findByTicketIdAndSlaTypeAndAlertType(Long ticketId, String slaType, String alertType);
    
    /**
     * Đếm alerts chưa được notified.
     */
    long countByNotifiedFalse();
    
    /**
     * Tìm tickets có SLA warning chưa được notify.
     */
    @Query("SELECT DISTINCT s.ticketId FROM SlaAlertLog s WHERE s.notified = false AND s.alertType = 'WARNING'")
    List<Long> findTicketIdsWithPendingWarnings();
    
    /**
     * Tìm tickets có SLA breach chưa được notify.
     */
    @Query("SELECT DISTINCT s.ticketId FROM SlaAlertLog s WHERE s.notified = false AND s.alertType = 'BREACHED'")
    List<Long> findTicketIdsWithPendingBreaches();
}
