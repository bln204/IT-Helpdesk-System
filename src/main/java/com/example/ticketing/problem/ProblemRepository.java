package com.example.ticketing.problem;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository cho Problem entity.
 */
@Repository
public interface ProblemRepository extends JpaRepository<Problem, Long> {

    Optional<Problem> findByProblemNumber(String problemNumber);

    Page<Problem> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Problem> findByStatusOrderByCreatedAtDesc(Problem.ProblemStatus status, Pageable pageable);

    Page<Problem> findByPriorityOrderByCreatedAtDesc(Problem.Priority priority, Pageable pageable);

    Page<Problem> findByCategoryOrderByCreatedAtDesc(String category, Pageable pageable);

    Page<Problem> findByAssignedToOrderByCreatedAtDesc(String assignedTo, Pageable pageable);

    @Query("SELECT p FROM Problem p WHERE " +
           "LOWER(p.title) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(p.description) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "p.problemNumber LIKE CONCAT('%', :search, '%')")
    Page<Problem> searchProblems(@Param("search") String search, Pageable pageable);

    @Query("SELECT p FROM Problem p WHERE p.status IN :statuses ORDER BY p.priority DESC, p.createdAt DESC")
    Page<Problem> findActiveByStatusIn(@Param("statuses") List<Problem.ProblemStatus> statuses, Pageable pageable);

    long countByStatus(Problem.ProblemStatus status);

    List<Problem> findByStatusIn(List<Problem.ProblemStatus> statuses);
}
