package com.example.ticketing.servicerequest;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository cho ServiceRequestComment entity.
 */
@Repository
public interface ServiceRequestCommentRepository extends JpaRepository<ServiceRequestComment, Long> {

    List<ServiceRequestComment> findByServiceRequestIdOrderByCreatedAtDesc(Long serviceRequestId);
}
