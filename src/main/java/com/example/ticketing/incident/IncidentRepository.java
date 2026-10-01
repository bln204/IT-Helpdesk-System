package com.example.ticketing.incident;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository cho Incident entity.
 */
@Repository
public interface IncidentRepository extends JpaRepository<Incident, Long> {

    Optional<Incident> findByIncidentNumber(String incidentNumber);

    List<Incident> findByStatus(Incident.IncidentStatus status);

    List<Incident> findByStatusIn(List<Incident.IncidentStatus> statuses);

    List<Incident> findByPriority(String priority);

    Page<Incident> findByStatusOrderByCreatedAtDesc(Incident.IncidentStatus status, Pageable pageable);

    Page<Incident> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("SELECT i FROM Incident i WHERE i.status != 'CLOSED' ORDER BY i.createdAt DESC")
    List<Incident> findActiveIncidents();

    @Query("SELECT i FROM Incident i WHERE i.status IN :statuses ORDER BY i.createdAt DESC")
    Page<Incident> findByStatusInOrderByCreatedAtDesc(
            @Param("statuses") List<Incident.IncidentStatus> statuses,
            Pageable pageable);

    @Query("SELECT i FROM Incident i WHERE " +
           "(LOWER(i.title) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(i.description) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "i.incidentNumber LIKE CONCAT('%', :search, '%'))")
    Page<Incident> searchIncidents(@Param("search") String search, Pageable pageable);

    @Query("SELECT i FROM Incident i WHERE i.assignedTo.username = :username ORDER BY i.createdAt DESC")
    List<Incident> findByAssignedToUsername(@Param("username") String username);

    @Query("SELECT i FROM Incident i WHERE i.team.id = :teamId ORDER BY i.createdAt DESC")
    List<Incident> findByTeamId(@Param("teamId") Long teamId);

    long countByStatus(Incident.IncidentStatus status);

    @Query("SELECT COUNT(i) FROM Incident i WHERE i.status IN ('INVESTIGATING', 'IDENTIFIED')")
    long countActiveIncidents();

    @Query("SELECT i FROM Incident i WHERE i.createdAt >= :from AND i.createdAt <= :to ORDER BY i.createdAt DESC")
    List<Incident> findByCreatedAtBetween(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // Statistics
    @Query("SELECT i.status, COUNT(i) FROM Incident i GROUP BY i.status")
    List<Object[]> countByStatusGrouped();

    // SLA Check queries - tìm incidents active để check SLA
    @Query("SELECT i FROM Incident i WHERE i.status NOT IN ('RESOLVED', 'CLOSED') AND i.responseDeadline IS NOT NULL ORDER BY i.responseDeadline ASC")
    List<Incident> findActiveIncidentsForSlaCheck();

    @Query("SELECT i FROM Incident i WHERE i.status NOT IN ('RESOLVED', 'CLOSED') AND i.resolutionDeadline IS NOT NULL ORDER BY i.resolutionDeadline ASC")
    List<Incident> findActiveIncidentsForResolutionSlaCheck();
}
