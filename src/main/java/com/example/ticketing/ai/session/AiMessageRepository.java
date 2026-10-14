package com.example.ticketing.ai.session;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AiMessageRepository extends JpaRepository<AiMessage, UUID> {

    /**
     * Most recent N messages in a session, ordered chronologically
     * (oldest first) so they can be appended straight into a prompt.
     */
    @Query(value = "SELECT * FROM ai_message " +
                   "WHERE session_id = :sessionId " +
                   "ORDER BY created_at DESC " +
                   "LIMIT :limit",
           nativeQuery = true)
    List<AiMessage> findRecent(@Param("sessionId") UUID sessionId,
                               @Param("limit") int limit);

    List<AiMessage> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    /**
     * Most recent ASSISTANT message in a session, ordered by
     * created_at DESC. Used by the manifest continuation reader
     * to locate the previous turn's manifest.
     */
    @Query(value = "SELECT * FROM ai_message " +
                   "WHERE session_id = :sessionId " +
                   "AND role = 'ASSISTANT' " +
                   "ORDER BY created_at DESC " +
                   "LIMIT 1",
           nativeQuery = true)
    AiMessage findLastAssistantMessage(@Param("sessionId") UUID sessionId);
}
