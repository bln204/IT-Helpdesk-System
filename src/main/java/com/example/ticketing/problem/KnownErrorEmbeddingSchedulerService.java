package com.example.ticketing.problem;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 6.6.4 - Scheduler for the Known Error embedding pipeline.
 *
 * <p>Mirrors {@code EmbeddingSchedulerService} so the two pipelines
 * (tickets, known errors) share the same {@code FOR UPDATE SKIP LOCKED}
 * claim pattern and the same PROCESSING -> COMPLETED/FAILED transitions.
 *
 * <p>The batch size is the same (10) as the ticket pipeline to keep
 * Ollama pressure predictable when both schedulers fire in the same
 * minute.
 */
@Service
public class KnownErrorEmbeddingSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(KnownErrorEmbeddingSchedulerService.class);

    private static final int BATCH_SIZE = 10;
    private static final int LOCK_TIMEOUT_SECONDS = 300;

    private final KnownErrorEmbeddingService embeddingService;
    private final KnownErrorEmbeddingRepository embeddingRepository;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    public KnownErrorEmbeddingSchedulerService(
            KnownErrorEmbeddingService embeddingService,
            KnownErrorEmbeddingRepository embeddingRepository) {
        this.embeddingService = embeddingService;
        this.embeddingRepository = embeddingRepository;
    }

    @Scheduled(fixedRate = 300000) // 5 minutes
    @Transactional
    public void processPendingEmbeddings() {
        processPendingEmbeddingsInternal();
    }

    /**
     * Trigger the same guarded batch loop from a non-scheduled caller
     * (e.g. the startup backfill). Returns the number of embeddings
     * processed (success + failure).
     */
    @Transactional
    public int runEmbeddingProcessingForBackfill() {
        return processPendingEmbeddingsInternal();
    }

    private int processPendingEmbeddingsInternal() {
        if (!isRunning.compareAndSet(false, true)) {
            log.debug("KnownError embedding job already running, skipping");
            return 0;
        }

        try {
            log.info("Starting KnownError embedding job...");
            if (!embeddingService.isOllamaAvailable()) {
                log.warn("Ollama is not available, skipping KnownError embedding job");
                return 0;
            }

            int totalProcessed = 0;
            int batchProcessed;
            do {
                batchProcessed = processBatch();
                totalProcessed += batchProcessed;
                if (batchProcessed > 0) {
                    log.info("Processed KnownError batch of {} embeddings, total: {}",
                            batchProcessed, totalProcessed);
                }
            } while (batchProcessed == BATCH_SIZE);

            log.info("KnownError embedding job completed. Total processed: {}", totalProcessed);
            return totalProcessed;
        } catch (Exception e) {
            log.error("KnownError embedding job failed: {}", e.getMessage(), e);
            return 0;
        } finally {
            isRunning.set(false);
        }
    }

    @Transactional
    public int processBatch() {
        LocalDateTime batchStartTime = LocalDateTime.now();
        int claimed = embeddingRepository.claimPendingEmbeddings(BATCH_SIZE);
        if (claimed == 0) {
            return 0;
        }
        log.debug("Claimed {} KnownError embeddings for processing at {}", claimed, batchStartTime);

        List<KnownErrorEmbedding> claimedRows = embeddingRepository.findRecentlyClaimedEmbeddings(batchStartTime);
        if (claimedRows.isEmpty()) {
            log.warn("No KnownError embeddings found after claiming {}. Possible timing issue.", claimed);
            return 0;
        }

        int success = 0;
        int fail = 0;
        for (KnownErrorEmbedding e : claimedRows) {
            try {
                embeddingService.processEmbeddingDirect(e.getId());
                success++;
            } catch (Exception ex) {
                log.error("Failed to process KnownError embedding {}: {}", e.getId(), ex.getMessage());
                fail++;
            }
        }
        return success + fail;
    }

    @Scheduled(fixedRate = 600000) // 10 minutes
    @Transactional
    public void cleanupStaleEmbeddings() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusSeconds(LOCK_TIMEOUT_SECONDS * 2L);
        int reset = embeddingRepository.resetStaleEmbeddings(cutoffTime);
        if (reset > 0) {
            log.info("Reset {} stale KnownError embeddings to PENDING", reset);
        }
    }

    public boolean isJobRunning() {
        return isRunning.get();
    }
}
