package com.example.ticketing.problem;

import org.springframework.data.jpa.repository.JpaRepository;
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
}
