package com.example.ticketing.servicerequest;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository cho ServiceRequest entity.
 */
@Repository
public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, Long> {

    Optional<ServiceRequest> findByRequestNumber(String requestNumber);

    Page<ServiceRequest> findByRequesterUsernameOrderByCreatedAtDesc(String requesterUsername, Pageable pageable);

    Page<ServiceRequest> findByStatusOrderByCreatedAtDesc(ServiceRequest.ServiceStatus status, Pageable pageable);

    Page<ServiceRequest> findByAssignedToOrderByCreatedAtDesc(String assignedTo, Pageable pageable);

    Page<ServiceRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("SELECT sr FROM ServiceRequest sr WHERE " +
           "sr.requesterUsername = :username OR sr.assignedTo = :username " +
           "ORDER BY sr.createdAt DESC")
    Page<ServiceRequest> findByRequesterOrAssignee(
            @Param("username") String username, Pageable pageable);

    @Query("SELECT sr FROM ServiceRequest sr WHERE " +
           "(LOWER(sr.title) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(sr.description) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "sr.requestNumber LIKE CONCAT('%', :search, '%'))")
    Page<ServiceRequest> searchServiceRequests(
            @Param("search") String search, Pageable pageable);

    @Query("SELECT sr FROM ServiceRequest sr WHERE sr.status IN :statuses ORDER BY sr.createdAt DESC")
    Page<ServiceRequest> findByStatusInOrderByCreatedAtDesc(
            @Param("statuses") List<ServiceRequest.ServiceStatus> statuses, Pageable pageable);

    long countByStatus(ServiceRequest.ServiceStatus status);

    @Query("SELECT COUNT(sr) FROM ServiceRequest sr WHERE sr.status NOT IN ('COMPLETED', 'REJECTED', 'CANCELLED')")
    long countActiveRequests();

    List<ServiceRequest> findByServiceId(Long serviceId);
}
