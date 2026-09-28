package com.example.ticketing.problem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository cho ProblemWorkaroundNote entity.
 */
@Repository
public interface ProblemWorkaroundNoteRepository extends JpaRepository<ProblemWorkaroundNote, Long> {

    List<ProblemWorkaroundNote> findByProblemIdOrderByCreatedAtDesc(Long problemId);
}
