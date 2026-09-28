package com.example.ticketing.escalation;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.ticketing.ticket.TicketTypes.TicketPriority;

/**
 * Repository cho EscalationRule entity.
 */
@Repository
public interface EscalationRuleRepository extends JpaRepository<EscalationRule, Long> {

    /**
     * Tìm tất cả rule đang enabled, sắp xếp theo priority giảm dần.
     */
    List<EscalationRule> findByEnabledTrueOrderByPriorityDesc();

    /**
     * Tìm rule theo trigger type.
     */
    List<EscalationRule> findByTriggerTypeAndEnabledTrue(EscalationRule.EscalationTrigger triggerType);

    /**
     * Tìm rule theo trigger type và priority.
     */
    @Query("SELECT r FROM EscalationRule r WHERE r.enabled = true " +
           "AND r.triggerType = :triggerType " +
           "AND (r.minPriority IS NULL OR r.minPriority <= :priority) " +
           "ORDER BY r.priority DESC")
    List<EscalationRule> findApplicableRules(
            @Param("triggerType") EscalationRule.EscalationTrigger triggerType,
            @Param("priority") TicketPriority priority);

    /**
     * Tìm tất cả rules cho một priority.
     */
    List<EscalationRule> findByMinPriorityAndEnabledTrue(TicketPriority minPriority);

    /**
     * Tìm rule mặc định cho escalation level.
     */
    Optional<EscalationRule> findByEscalationLevelAndEnabledTrue(EscalationRule.EscalationLevel level);

    /**
     * Kiểm tra rule name đã tồn tại chưa.
     */
    boolean existsByName(String name);

    /**
     * Đếm số rule theo trigger type.
     */
    long countByTriggerTypeAndEnabledTrue(EscalationRule.EscalationTrigger triggerType);
}
