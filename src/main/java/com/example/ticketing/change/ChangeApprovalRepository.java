package com.example.ticketing.change;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository cho ChangeApproval entity.
 */
@Repository
public interface ChangeApprovalRepository extends JpaRepository<ChangeApproval, Long> {

    List<ChangeApproval> findByChangeRequestIdOrderByApprovalLevelAsc(Long changeRequestId);

    List<ChangeApproval> findByApproverUsernameOrderByCreatedAtDesc(String approverUsername);

    List<ChangeApproval> findByStatusOrderByCreatedAtDesc(ChangeApproval.ApprovalStatus status);
}
