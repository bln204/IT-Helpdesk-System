package com.example.ticketing.problem;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Phase 6.6.4 - Repository for the {@code known_error_embeddings} table.
 *
 * <p>The shape mirrors {@code TicketEmbeddingRepository} so the same
 * FOR UPDATE SKIP LOCKED claim pattern and status transitions can be
 * reused. {@code known_error_id} is not a persistent field on the entity
 * (only the {@link KnownErrorEmbedding#getKnownError()} association is),
 * so all lookup queries use explicit JPQL on the {@code knownError}
 * association.
 */
@Repository
public interface KnownErrorEmbeddingRepository extends JpaRepository<KnownErrorEmbedding, Long> {

    /**
     * Find the embedding record for a KnownError.
     */
    @Query("SELECT e FROM KnownErrorEmbedding e WHERE e.knownError.id = :knownErrorId")
    Optional<KnownErrorEmbedding> findByKnownErrorId(@Param("knownErrorId") Long knownErrorId);

    /**
     * All embeddings in a given status.
     */
    List<KnownErrorEmbedding> findByEmbeddingStatus(KnownErrorEmbeddingStatus status);

    /**
     * All PENDING embeddings, oldest first.
     */
    @Query("SELECT e FROM KnownErrorEmbedding e WHERE e.embeddingStatus = 'PENDING' ORDER BY e.createdAt ASC")
    List<KnownErrorEmbedding> findAllPending();

    /**
     * Count by status.
     */
    long countByEmbeddingStatus(KnownErrorEmbeddingStatus status);

    // ============================================================
    // STARTUP BACKFILL LOOKUPS
    // ============================================================

    /**
     * Check whether an embedding record already exists for the given Known Error.
     */
    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END "
            + "FROM KnownErrorEmbedding e WHERE e.knownError.id = :knownErrorId")
    boolean existsEmbeddingForKnownError(@Param("knownErrorId") Long knownErrorId);

    /**
     * Known Error IDs that do NOT have a usable embedding (COMPLETED + vector).
     * Backfill candidate query: idempotent across restarts.
     */
    @Query("""
        SELECT k.id FROM KnownError k
        WHERE NOT EXISTS (
            SELECT e.id FROM KnownErrorEmbedding e
            WHERE e.knownError.id = k.id
              AND e.embeddingStatus = 'COMPLETED'
              AND e.embedding IS NOT NULL
        )
        ORDER BY k.id ASC
        """)
    List<Long> findKnownErrorIdsWithoutCompletedEmbedding();

    /**
     * Count of candidates that reached COMPLETED + vector.
     */
    @Query("""
        SELECT COUNT(e) FROM KnownErrorEmbedding e
        WHERE e.embeddingStatus = 'COMPLETED'
          AND e.embedding IS NOT NULL
          AND e.knownError.id IN :knownErrorIds
        """)
    long countCompletedWithVectorByKnownErrorIds(@Param("knownErrorIds") List<Long> knownErrorIds);

    /**
     * Count of candidates whose embedding is FAILED.
     */
    @Query("""
        SELECT COUNT(e) FROM KnownErrorEmbedding e
        WHERE e.embeddingStatus = 'FAILED'
          AND e.knownError.id IN :knownErrorIds
        """)
    long countFailedByKnownErrorIds(@Param("knownErrorIds") List<Long> knownErrorIds);

    /**
     * Claim a batch of pending embeddings for processing. SKIP LOCKED prevents
     * duplicate processing across instances.
     */
    @Modifying
    @Query(value = """
        UPDATE known_error_embeddings
        SET embedding_status = 'PROCESSING',
            updated_at = CURRENT_TIMESTAMP
        WHERE id IN (
            SELECT id FROM known_error_embeddings
            WHERE embedding_status = 'PENDING'
            ORDER BY created_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
        )
        """, nativeQuery = true)
    int claimPendingEmbeddings(@Param("limit") int limit);

    /**
     * Find embeddings claimed by the current batch (PROCESSING and updated
     * after the supplied timestamp).
     */
    @Query("""
        SELECT e FROM KnownErrorEmbedding e
        WHERE e.embeddingStatus = 'PROCESSING'
          AND e.updatedAt >= :since
        ORDER BY e.updatedAt ASC
        """)
    List<KnownErrorEmbedding> findRecentlyClaimedEmbeddings(@Param("since") LocalDateTime since);

    /**
     * Find embeddings stuck in PROCESSING for too long (for the cleanup job).
     */
    @Query(value = """
        SELECT e FROM KnownErrorEmbedding e
        WHERE e.embeddingStatus = 'PROCESSING'
          AND e.updatedAt < :cutoffTime
        """)
    List<KnownErrorEmbedding> findStaleProcessingEmbeddings(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Reset stale PROCESSING embeddings back to PENDING.
     */
    @Modifying
    @Query(value = """
        UPDATE known_error_embeddings
        SET embedding_status = 'PENDING',
            updated_at = CURRENT_TIMESTAMP
        WHERE embedding_status = 'PROCESSING'
          AND updated_at < :cutoffTime
        """, nativeQuery = true)
    int resetStaleEmbeddings(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Persist the vector + COMPLETED status. Uses explicit CAST to vector
     * (same trick as {@code TicketEmbeddingRepository} for the V68 fix).
     */
    @Modifying
    @Query(value = """
        UPDATE known_error_embeddings
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
     * Persist the FAILED status.
     * Uses CAST(NULL AS vector) for pgvector compatibility.
     */
    @Modifying
    @Query(value = """
        UPDATE known_error_embeddings
        SET embedding = CAST(NULL AS vector),
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

    /**
     * Reset embedding to PENDING status (for re-embedding).
     * Uses CAST(NULL AS vector) for pgvector compatibility.
     */
    @Modifying
    @Query(value = """
        UPDATE known_error_embeddings
        SET embedding = CAST(NULL AS vector),
            embedding_status = 'PENDING',
            embedded_at = NULL,
            embedding_error = NULL,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = :id
        """, nativeQuery = true)
    void resetEmbeddingToPending(@Param("id") Long id);

    /**
     * Count completed embeddings for status endpoint.
     */
    long countByEmbeddingStatusAndEmbeddingIsNotNull(KnownErrorEmbeddingStatus status);
}
