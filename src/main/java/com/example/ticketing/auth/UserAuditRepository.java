package com.example.ticketing.auth;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAuditRepository extends JpaRepository<UserAudit, Long> {
    List<UserAudit> findByTargetUsernameOrderByCreatedAtDesc(String targetUsername);
    
    List<UserAudit> findByActionOrderByCreatedAtDesc(UserAuditAction action);
    
    List<UserAudit> findByActorUsernameOrderByCreatedAtDesc(String actorUsername);
    
    /**
     * Find recent audit records with pagination.
     */
    @Query("SELECT a FROM UserAudit a ORDER BY a.createdAt DESC")
    List<UserAudit> findRecentAudit(@Param("limit") int limit);
    
    /**
     * Find audit records for a specific action type and target.
     */
    List<UserAudit> findByActionAndTargetUsernameOrderByCreatedAtDesc(
        UserAuditAction action, String targetUsername);
}
