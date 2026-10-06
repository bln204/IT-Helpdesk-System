package com.example.ticketing.ticket;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for ticket_embeddings table.
 * Includes methods for concurrent processing with pessimistic locking.
 */
@Repository
public interface TicketEmbeddingRepository extends JpaRepository<TicketEmbedding, Long> {

    /**
    * Find embedding by ticket ID.
    */
    Optional<TicketEmbedding> findByTicketId(Long ticketId);

    /**
     * Find all embeddings by status.
     */
    List<TicketEmbedding> findByEmbeddingStatus(EmbeddingStatus status);

    /**
     * Find all PENDING embeddings for batch processing.
     */
    @Query("SELECT e FROM TicketEmbedding e WHERE e.embeddingStatus = 'PENDING' ORDER BY e.createdAt ASC")
    List<TicketEmbedding> findAllPending();

    /**
     * Count pending embeddings.
     */
    long countByEmbeddingStatus(EmbeddingStatus status);

    /**
     * Check if ticket has embedding record.
     */
    boolean existsByTicketId(Long ticketId);

    // ============================================================
    // STARTUP BACKFILL LOOKUPS
    // Idempotent candidate detection for the one-time startup backfill.
    // Only IDs and counts are selected, so full ticket entities and
    // 768-dimension vectors are never loaded into memory.
    //
    // NOTE: these use explicit JPQL on the `ticket` association because
    // TicketEmbedding exposes no persistent `ticketId` field (only a derived
    // getTicketId() accessor), so derived "ByTicketId" query methods cannot
    // resolve their path. The pre-existing findByTicketId/existsByTicketId
    // declarations are left untouched to preserve current behavior.
    // ============================================================

    /**
     * Check whether an embedding record already exists for the given ticket.
     *
     * Used by the startup backfill to avoid creating a duplicate
     * ticket_embeddings row.
     *
     * @param ticketId ticket ID
     * @return true if a record exists
     */
    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END "
            + "FROM TicketEmbedding e WHERE e.ticket.id = :ticketId")
    boolean existsEmbeddingForTicket(@Param("ticketId") Long ticketId);

    /**
     * Find ticket IDs that do NOT have a usable embedding.
     *
     * A ticket is a candidate when it has no ticket_embeddings record with
     * embedding_status = COMPLETED AND a stored vector. COMPLETED embeddings
     * are therefore never selected, which makes the backfill idempotent
     * across application restarts.
     *
     * Tickets whose record exists in another state (PENDING, PROCESSING,
     * FAILED) are still returned, so the caller can decide per the existing
     * embedding lifecycle whether the record needs to be created.
     *
     * @return ticket IDs ordered by ID ASC
     */
    @Query("""
        SELECT t.id FROM Ticket t
        WHERE NOT EXISTS (
            SELECT e.id FROM TicketEmbedding e
            WHERE e.ticket.id = t.id
              AND e.embeddingStatus = 'COMPLETED'
              AND e.embedding IS NOT NULL
        )
        ORDER BY t.id ASC
        """)
    List<Long> findTicketIdsWithoutCompletedEmbedding();

    /**
     * Count candidates that hold a usable embedding (COMPLETED + vector).
     *
     * Used to report how many candidates the pipeline actually completed
     * during a backfill run.
     *
     * @param ticketIds candidate ticket IDs
     * @return number of candidates that are COMPLETED and have a vector
     */
    @Query("""
        SELECT COUNT(e) FROM TicketEmbedding e
        WHERE e.embeddingStatus = 'COMPLETED'
          AND e.embedding IS NOT NULL
          AND e.ticket.id IN :ticketIds
        """)
    long countCompletedWithVectorByTicketIds(@Param("ticketIds") List<Long> ticketIds);

    /**
     * Count candidates whose embedding is FAILED.
     *
     * @param ticketIds candidate ticket IDs
     * @return number of candidates currently in FAILED status
     */
    @Query("""
        SELECT COUNT(e) FROM TicketEmbedding e
        WHERE e.embeddingStatus = 'FAILED'
          AND e.ticket.id IN :ticketIds
        """)
    long countFailedByTicketIds(@Param("ticketIds") List<Long> ticketIds);

    /**
     * Delete embedding by ticket ID.
     */
    @Modifying
    @Query("DELETE FROM TicketEmbedding e WHERE e.ticket.id = :ticketId")
    void deleteByTicketId(@Param("ticketId") Long ticketId);

    /**
     * Claim pending embeddings for processing.
     * Uses pessimistic locking via SELECT FOR UPDATE SKIP LOCKED.
     * Returns the number of rows updated.
     * 
     * This prevents multiple scheduler instances from processing the same embedding.
     */
    @Modifying
    @Query(value = """
        UPDATE ticket_embeddings 
        SET embedding_status = 'PROCESSING', 
            updated_at = CURRENT_TIMESTAMP 
        WHERE id IN (
            SELECT id FROM ticket_embeddings 
            WHERE embedding_status = 'PENDING' 
            ORDER BY created_at ASC 
            LIMIT :limit 
            FOR UPDATE SKIP LOCKED
        )
        """, nativeQuery = true)
    int claimPendingEmbeddings(@Param("limit") int limit, @Param("timeoutSeconds") int timeoutSeconds);

    /**
     * Find embeddings stuck in PROCESSING for too long.
     * These should be reset to PENDING.
     */
    @Query(value = """
        SELECT e FROM TicketEmbedding e 
        WHERE e.embeddingStatus = 'PROCESSING' 
          AND e.updatedAt < :cutoffTime
        """)
    List<TicketEmbedding> findStaleProcessingEmbeddings(@Param("cutoffTime") java.time.LocalDateTime cutoffTime);

    /**
     * Reset stale PROCESSING embeddings to PENDING.
     * Run as scheduled cleanup job.
     */
    @Modifying
    @Query(value = """
        UPDATE ticket_embeddings 
        SET embedding_status = 'PENDING', 
            updated_at = CURRENT_TIMESTAMP 
        WHERE embedding_status = 'PROCESSING' 
          AND updated_at < :cutoffTime
        """, nativeQuery = true)
    int resetStaleEmbeddings(@Param("cutoffTime") java.time.LocalDateTime cutoffTime);

    /**
     * Find embeddings in PROCESSING status that were recently claimed.
     * Uses updated_at timestamp to identify embeddings claimed by current batch.
     * 
     * @param since Timestamp after which embeddings were claimed
     * @return List of recently claimed PROCESSING embeddings
     */
    @Query("""
        SELECT e FROM TicketEmbedding e 
        WHERE e.embeddingStatus = 'PROCESSING' 
          AND e.updatedAt >= :since
        ORDER BY e.updatedAt ASC
        """)
    List<TicketEmbedding> findRecentlyClaimedEmbeddings(@Param("since") LocalDateTime since);

    // ============================================================
    // VECTOR PERSISTENCE METHODS (V68 fix)
    // These use explicit CAST to vector for pgvector compatibility
    // Must be called within a transaction
    // ============================================================

    /**
     * Update embedding with vector data (COMPLETED status).
     * Uses explicit CAST to vector for pgvector compatibility.
     *
     * @param id embedding record ID
     * @param embeddingVector string representation of vector "[val1,val2,...]"
     * @param embeddedAt timestamp when embedding was generated
     */
    @Modifying
    @Query(value = """
        UPDATE ticket_embeddings
        SET embedding = CAST(:embedding AS vector),
            embedding_status = 'COMPLETED',
            embedded_at = :embeddedAt,
            embedding_error = NULL,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = :id
        """, nativeQuery = true)
    void updateEmbeddingCompleted(
        @Param("id") Long id,
        @Param("embedding") String embeddingVector,
        @Param("embeddedAt") LocalDateTime embeddedAt
    );

    /**
     * Update embedding to FAILED status.
     * Does NOT cast embedding since it's NULL for failures.
     *
     * @param id embedding record ID
     * @param errorMessage error description
     */
    @Modifying
    @Query(value = """
        UPDATE ticket_embeddings
        SET embedding = NULL,
            embedding_status = 'FAILED',
            embedded_at = NULL,
            embedding_error = :errorMessage,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = :id
        """, nativeQuery = true)
    void updateEmbeddingFailed(
        @Param("id") Long id,
        @Param("errorMessage") String errorMessage
    );
}
