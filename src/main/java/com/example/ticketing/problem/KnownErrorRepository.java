package com.example.ticketing.problem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository cho KnownError entity.
 */
@Repository
public interface KnownErrorRepository extends JpaRepository<KnownError, Long> {

    Optional<KnownError> findByErrorCode(String errorCode);

    List<KnownError> findByStatus(KnownError.KnownErrorStatus status);
    
    List<KnownError> findByCategory(String category);

    List<KnownError> findByStatusOrderByOccurrenceCountDesc(KnownError.KnownErrorStatus status);

    List<KnownError> findTop10ByStatusOrderByOccurrenceCountDesc(KnownError.KnownErrorStatus status);

    @Query(value = "SELECT ke.* FROM known_errors ke WHERE " +
           "ke.error_code ILIKE CONCAT('%', CAST(:keyword AS text), '%') OR " +
           "ke.title ILIKE CONCAT('%', CAST(:keyword AS text), '%') OR " +
           "ke.description ILIKE CONCAT('%', CAST(:keyword AS text), '%') OR " +
           "ke.workaround ILIKE CONCAT('%', CAST(:keyword AS text), '%') OR " +
           "ke.symptoms ILIKE CONCAT('%', CAST(:keyword AS text), '%') OR " +
           "ke.root_cause ILIKE CONCAT('%', CAST(:keyword AS text), '%')",
           nativeQuery = true)
    List<KnownError> findByKeyword(@Param("keyword") String keyword);
}
