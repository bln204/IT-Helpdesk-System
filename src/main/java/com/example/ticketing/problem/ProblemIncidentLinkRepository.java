package com.example.ticketing.problem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository cho ProblemIncidentLink entity.
 */
@Repository
public interface ProblemIncidentLinkRepository extends JpaRepository<ProblemIncidentLink, Long> {

    List<ProblemIncidentLink> findByIncidentId(Long incidentId);

    List<ProblemIncidentLink> findByProblemId(Long problemId);

    Optional<ProblemIncidentLink> findByProblemIdAndIncidentId(Long problemId, Long incidentId);

    long countByIncidentId(Long incidentId);

    long countByProblemId(Long problemId);
}
