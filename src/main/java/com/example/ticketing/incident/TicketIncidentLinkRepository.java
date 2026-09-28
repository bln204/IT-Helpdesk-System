package com.example.ticketing.incident;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository cho TicketIncidentLink entity.
 */
@Repository
public interface TicketIncidentLinkRepository extends JpaRepository<TicketIncidentLink, Long> {

    List<TicketIncidentLink> findByIncidentIdOrderByLinkedAtDesc(Long incidentId);

    List<TicketIncidentLink> findByTicketIdOrderByLinkedAtDesc(Long ticketId);

    Optional<TicketIncidentLink> findByTicketIdAndIncidentId(Long ticketId, Long incidentId);

    boolean existsByTicketIdAndIncidentId(Long ticketId, Long incidentId);

    void deleteByTicketIdAndIncidentId(Long ticketId, Long incidentId);

    @Query("SELECT COUNT(l) FROM TicketIncidentLink l WHERE l.incident.id = :incidentId")
    long countByIncidentId(@Param("incidentId") Long incidentId);

    @Query("SELECT l FROM TicketIncidentLink l WHERE l.ticket.id = :ticketId")
    List<TicketIncidentLink> findAllByTicketId(@Param("ticketId") Long ticketId);
}
