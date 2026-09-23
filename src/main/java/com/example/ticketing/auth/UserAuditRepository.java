package com.example.ticketing.auth;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAuditRepository extends JpaRepository<UserAudit, Long> {
    List<UserAudit> findByTargetUsernameOrderByCreatedAtDesc(String targetUsername);
}
