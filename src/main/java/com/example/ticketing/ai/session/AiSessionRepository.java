package com.example.ticketing.ai.session;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AiSessionRepository extends JpaRepository<AiSession, UUID> {

    /** Recent non-archived sessions for a user. */
    @Query("SELECT s FROM AiSession s " +
           "WHERE s.userUsername = :username AND s.archived = false " +
           "ORDER BY s.lastActiveAt DESC")
    List<AiSession> findActiveByUser(@Param("username") String username, Pageable pageable);

    Optional<AiSession> findByIdAndUserUsername(UUID id, String userUsername);
}
