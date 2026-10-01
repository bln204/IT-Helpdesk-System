package com.example.ticketing.sla;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.ticketing.ticket.TicketTypes.TicketPriority;

/**
 * Repository cho SlaPolicy entity.
 */
@Repository
public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, Long> {

    /**
     * Tìm tất cả policy đang enabled.
     */
    List<SlaPolicy> findByEnabledTrue();

    /**
     * Tìm policy theo priority.
     */
    List<SlaPolicy> findByPriority(TicketPriority priority);

    /**
     * Tìm policy theo priority và đang enabled.
     */
    List<SlaPolicy> findByPriorityAndEnabledTrue(TicketPriority priority);

    /**
     * Tìm policy mặc định cho một priority.
     */
    Optional<SlaPolicy> findByPriorityAndIsDefaultTrueAndEnabledTrue(TicketPriority priority);

    /**
     * Tìm policy mặc định cho một priority (bất kể enabled).
     */
    Optional<SlaPolicy> findByPriorityAndIsDefaultTrue(TicketPriority priority);

    /**
     * Kiểm tra policy name đã tồn tại chưa.
     */
    boolean existsByName(String name);

    /**
     * Kiểm tra có policy mặc định nào cho priority này chưa (trừ policy hiện tại).
     */
    @Query("SELECT COUNT(p) > 0 FROM SlaPolicy p WHERE p.priority = :priority AND p.isDefault = true AND p.id != :policyId")
    boolean existsOtherDefaultForPriority(@Param("priority") TicketPriority priority, @Param("policyId") Long policyId);

    /**
     * Lấy tất cả priorities có ít nhất 1 policy.
     */
    @Query("SELECT DISTINCT p.priority FROM SlaPolicy p WHERE p.enabled = true")
    List<TicketPriority> findDistinctEnabledPriorities();

    /**
     * Đếm số policy theo priority.
     */
    long countByPriority(TicketPriority priority);

    /**
     * Đếm số policy theo priority và enabled.
     */
    long countByPriorityAndEnabled(TicketPriority priority, Boolean enabled);
}
