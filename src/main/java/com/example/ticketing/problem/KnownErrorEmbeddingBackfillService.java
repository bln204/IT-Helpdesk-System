package com.example.ticketing.problem;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Phase 6.6.4 - One-time startup backfill for Known Errors.
 *
 * <p>
 * Mirrors {@code EmbeddingBackfillService} so Known Errors created
 * before the embedding infrastructure existed can be picked up and
 * indexed. The candidate query is idempotent (KnownErrors with a
 * COMPLETED + vector embedding are excluded), so running this on
 * every startup is safe - subsequent runs are no-ops.
 */
@Service
public class KnownErrorEmbeddingBackfillService {

    private static final Logger log = LoggerFactory.getLogger(KnownErrorEmbeddingBackfillService.class);

    private final KnownErrorEmbeddingRepository embeddingRepository;
    private final KnownErrorEmbeddingService embeddingService;
    private final KnownErrorEmbeddingSchedulerService schedulerService;

    public KnownErrorEmbeddingBackfillService(
            KnownErrorEmbeddingRepository embeddingRepository,
            KnownErrorEmbeddingService embeddingService,
            KnownErrorEmbeddingSchedulerService schedulerService) {
        this.embeddingRepository = embeddingRepository;
        this.embeddingService = embeddingService;
        this.schedulerService = schedulerService;
    }

    public record BackfillResult(int candidates, int created, int processed, int skipped, int failed) {
    }

    /**
     * Ensure every existing Known Error has a usable embedding.
     *
     * Never throws for an individual KnownError: a failure on one is
     * recorded as FAILED by the existing pipeline and the rest continue.
     */
    public BackfillResult backfillMissingEmbeddings() {
        log.info("KnownError embedding startup backfill started");

        List<Long> candidates = embeddingRepository.findKnownErrorIdsWithoutCompletedEmbedding();
        if (candidates == null || candidates.isEmpty()) {
            log.info("KnownError embedding startup backfill: no KnownErrors require processing");
            return new BackfillResult(0, 0, 0, 0, 0);
        }

        log.info("KnownError embedding startup backfill: found {} KnownErrors requiring embeddings",
                candidates.size());

        int created = 0;
        int creationFailed = 0;
        for (Long id : candidates) {
            try {
                if (ensurePendingEmbedding(id)) {
                    created++;
                }
            } catch (Exception e) {
                creationFailed++;
                log.error("KnownError embedding backfill failed for id={}: {}", id, e.getMessage());
            }
        }

        try {
            schedulerService.runEmbeddingProcessingForBackfill();
        } catch (Exception e) {
            log.error("KnownError embedding startup backfill processing failed: {}", e.getMessage(), e);
        }

        int succeeded = (int) embeddingRepository.countCompletedWithVectorByKnownErrorIds(candidates);
        int failed = (int) embeddingRepository.countFailedByKnownErrorIds(candidates) + creationFailed;
        int skipped = Math.max(0, candidates.size() - created - creationFailed);

        log.info("KnownError embedding startup backfill completed: processed={}, skipped={}, failed={}",
                succeeded, skipped, failed);
        return new BackfillResult(candidates.size(), created, succeeded, skipped, failed);
    }

    private boolean ensurePendingEmbedding(Long knownErrorId) {
        if (embeddingRepository.existsEmbeddingForKnownError(knownErrorId)) {
            return false;
        }
        embeddingService.createEmbedding(knownErrorId);
        return true;
    }
}
