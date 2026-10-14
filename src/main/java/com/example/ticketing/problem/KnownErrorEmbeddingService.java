package com.example.ticketing.problem;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.ticketing.ai.EmbeddingService;
import com.example.ticketing.ai.LlmGenerationException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

/**
 * Phase 6.6.4 - Embedding lifecycle for Known Errors.
 *
 * <p>Mirrors the existing {@code EmbeddingService} (Phase 5) shape but
 * operates on the {@code known_error_embeddings} table. The
 * {@link EmbeddingService#generateEmbeddingInternal(String)} call is reused
 * unchanged so both pipelines share the same Ollama endpoint, the same
 * {@code nomic-embed-text} model and the same 768-dimension vector format.
 *
 * <p>TRANSACTION BOUNDARY. The create / re-embed entry points are
 * {@code REQUIRES_NEW} so a failure on Ollama does NOT leave the parent
 * KnownError transaction hanging on an external IO. The transaction design
 * matches the established pattern in {@code EmbeddingService.createEmbedding}.
 *
 * <p>SEMANTIC TEXT. The text sent to Ollama is produced by
 * {@link KnownErrorEmbeddingTextBuilder}, which excludes the metadata fields
 * the task brief lists as not-semantic (id, errorCode, status, counts,
 * timestamps, etc.).
 */
@Service
public class KnownErrorEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(KnownErrorEmbeddingService.class);

    private final KnownErrorEmbeddingRepository embeddingRepository;
    private final KnownErrorRepository knownErrorRepository;
    private final EmbeddingService embeddingService;
    private final KnownErrorEmbeddingTextBuilder textBuilder;

    @PersistenceContext
    private EntityManager entityManager;

    public KnownErrorEmbeddingService(
            KnownErrorEmbeddingRepository embeddingRepository,
            KnownErrorRepository knownErrorRepository,
            EmbeddingService embeddingService,
            KnownErrorEmbeddingTextBuilder textBuilder) {
        this.embeddingRepository = embeddingRepository;
        this.knownErrorRepository = knownErrorRepository;
        this.embeddingService = embeddingService;
        this.textBuilder = textBuilder;
    }

    /**
     * Insert a PENDING row for a KnownError. Idempotent: if a row already
     * exists, the existing row is returned unchanged.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public KnownErrorEmbedding createEmbedding(Long knownErrorId) {
        KnownError knownError = knownErrorRepository.findById(knownErrorId)
                .orElseThrow(() -> new IllegalArgumentException("KnownError not found: " + knownErrorId));

        if (embeddingRepository.existsEmbeddingForKnownError(knownErrorId)) {
            // Idempotent: do not duplicate the PENDING row.
            return embeddingRepository.findByKnownErrorId(knownErrorId).orElseThrow();
        }

        log.info("Creating PENDING embedding for KnownError id={} code={}",
                knownError.getId(), knownError.getErrorCode());

        Query insert = entityManager.createNativeQuery("""
            INSERT INTO known_error_embeddings
                (known_error_id, embedding, embedding_status, embedded_at, embedding_error, created_at, updated_at)
            VALUES
                (:knownErrorId, NULL, 'PENDING', NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """);
        insert.setParameter("knownErrorId", knownError.getId());
        insert.executeUpdate();

        KnownErrorEmbedding ke = new KnownErrorEmbedding();
        ke.setKnownError(knownError);
        ke.setEmbeddingStatus(KnownErrorEmbeddingStatus.PENDING);
        return ke;
    }

    /**
     * Reset a Known Error's embedding to PENDING so the scheduler picks it
     * up again. Used when the semantic fields of a KnownError are updated
     * (title/description/symptoms/rootCause/workaround/resolution/fixSteps
     * /impactDescription/affectedSystems/rootCauseCategory).
     *
     * <p>If no row exists yet, one is created. Status-only updates
     * (ACTIVE / RESOLVED / OBSOLETE) do NOT call this method; section 12
     * of the task brief says non-semantic field changes must not trigger
     * re-embedding.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public KnownErrorEmbedding reembedKnownError(Long knownErrorId) {
        KnownErrorEmbedding existing = embeddingRepository.findByKnownErrorId(knownErrorId)
                .orElse(null);
        if (existing == null) {
            return createEmbedding(knownErrorId);
        }

        Long embeddingId = existing.getId();
        log.info("Re-embedding KnownError id={} code={}",
                knownErrorId,
                existing.getKnownError() != null ? existing.getKnownError().getErrorCode() : null);

        // Use native UPDATE with CAST(NULL AS vector) for pgvector compatibility
        embeddingRepository.resetEmbeddingToPending(embeddingId);

        // Return fresh entity state
        return embeddingRepository.findByKnownErrorId(knownErrorId)
                .orElseThrow(() -> new IllegalStateException("Embedding not found after reset: " + embeddingId));
    }

    /**
     * Process a single embedding by id. Called by the scheduler under
     * FOR UPDATE SKIP LOCKED claim protection.
     */
    public void processEmbeddingDirect(Long embeddingId) {
        KnownErrorEmbedding embedding = embeddingRepository.findById(embeddingId)
                .orElseThrow(() -> new IllegalArgumentException("Embedding not found: " + embeddingId));
        processEmbedding(embedding);
    }

    /**
     * Internal embedding processor. Calls Ollama and persists the resulting
     * vector via the repository's native UPDATE (CAST to vector).
     *
     * <p>Failures are recorded in {@code embedding_error} and the row is
     * set to FAILED. The KnownError business record is NEVER modified by
     * this method - the AI pipeline treats a row without a COMPLETED
     * embedding as not-yet-indexed and excludes it from retrieval.
     */
    private void processEmbedding(KnownErrorEmbedding embedding) {
        Long knownErrorId = embedding.getKnownErrorId();
        if (knownErrorId == null) {
            markEmbeddingFailed(embedding.getId(), "KnownError association is null");
            return;
        }
        KnownError ke = knownErrorRepository.findById(knownErrorId).orElse(null);
        if (ke == null) {
            markEmbeddingFailed(embedding.getId(), "KnownError not found");
            return;
        }

        try {
            String text = textBuilder.build(ke);
            if (text.isEmpty()) {
                markEmbeddingFailed(embedding.getId(), "Empty semantic text (all fields blank)");
                return;
            }
            float[] vector = embeddingService.generateEmbeddingInternal(text);
            if (vector == null || vector.length == 0) {
                markEmbeddingFailed(embedding.getId(), "Empty embedding returned from Ollama");
                return;
            }
            String vectorString = vectorToString(vector);
            embeddingRepository.updateEmbeddingCompleted(
                    embedding.getId(), vectorString, LocalDateTime.now());
            log.info("Embedding completed for KnownError id={} code={}",
                    knownErrorId, ke.getErrorCode());
        } catch (LlmGenerationException e) {
            markEmbeddingFailed(embedding.getId(), "Ollama error: " + e.getErrorCode());
        } catch (Exception e) {
            markEmbeddingFailed(embedding.getId(), e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private void markEmbeddingFailed(Long embeddingId, String errorMessage) {
        embeddingRepository.updateEmbeddingFailed(embeddingId, errorMessage);
        log.warn("Embedding failed for KnownError embedding id={}: {}", embeddingId, errorMessage);
    }

    private String vectorToString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Check if Ollama is available (delegated to the existing service).
     */
    public boolean isOllamaAvailable() {
        return embeddingService.isOllamaAvailable();
    }
}
