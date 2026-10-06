package com.example.ticketing.ai;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ticketing.ticket.EmbeddingStatus;
import com.example.ticketing.ticket.TicketEmbedding;
import com.example.ticketing.ticket.TicketEmbeddingRepository;

/**
 * Scheduler service for processing pending ticket embeddings.
 * Uses pessimistic locking to prevent duplicate processing.
 */
@Service
public class EmbeddingSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingSchedulerService.class);

    private static final int BATCH_SIZE = 10;
    private static final int LOCK_TIMEOUT_SECONDS = 300; // 5 minutes max per embedding

    private final EmbeddingService embeddingService;
    private final TicketEmbeddingRepository embeddingRepository;

    // Prevent concurrent job runs (in-memory lock for single instance)
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    public EmbeddingSchedulerService(
            EmbeddingService embeddingService,
            TicketEmbeddingRepository embeddingRepository) {
        this.embeddingService = embeddingService;
        this.embeddingRepository = embeddingRepository;
    }

    /**
     * Process pending embeddings every 5 minutes.
     * Uses pessimistic locking to prevent duplicate processing across instances.
     *
     * NOTE: the AtomicBoolean guard is intentionally applied here, on the
     * transactional entry point, because processBatch() must never run
     * self-invoked: its native claim SQL requires a real transaction. The
     * guard must therefore wrap the transactional boundary, not sit inside it.
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    @Transactional
    public void processPendingEmbeddings() {
        processPendingEmbeddingsInternal();
    }

    /**
     * Process all currently PENDING embeddings for the one-time startup
     * backfill, reusing the same guard, batch size and processing path as the
     * scheduled job.
     *
     * This exists so the backfill does not have to wait for the 5-minute
     * scheduler interval. It deliberately does NOT create a second scheduler:
     * the same AtomicBoolean guard, the same FOR UPDATE SKIP LOCKED claiming
     * and the same status transitions are used, so a backfill run and a
     * scheduled run can never process the same embedding concurrently.
     *
     * Invoked through the Spring proxy so that @Transactional applies to
     * processBatch.
     *
     * @return number of embeddings claimed and processed (success + failure)
     */
    @Transactional
    public int runEmbeddingProcessingForBackfill() {
        return processPendingEmbeddingsInternal();
    }

    /**
     * Guarded batch loop shared by the scheduled job and the startup backfill.
     * Must only be called from within a transactional boundary.
     *
     * @return number of embeddings claimed and processed (success + failure)
     */
    private int processPendingEmbeddingsInternal() {
        // Check if already running (prevents overlapping job runs)
        if (!isRunning.compareAndSet(false, true)) {
            log.debug("Embedding job already running, skipping");
            return 0;
        }

        try {
            log.info("Starting embedding job...");

            // Check if Ollama is available
            if (!embeddingService.isOllamaAvailable()) {
                log.warn("Ollama is not available, skipping embedding job");
                return 0;
            }

            // Process in batches until no more pending
            int totalProcessed = 0;
            int batchProcessed;

            do {
                batchProcessed = processBatch();
                totalProcessed += batchProcessed;

                if (batchProcessed > 0) {
                    log.info("Processed batch of {} embeddings, total: {}", batchProcessed, totalProcessed);
                }
            } while (batchProcessed == BATCH_SIZE); // Continue if batch was full

            log.info("Embedding job completed. Total processed: {}", totalProcessed);
            return totalProcessed;

        } catch (Exception e) {
            log.error("Embedding job failed: {}", e.getMessage(), e);
            return 0;
        } finally {
            isRunning.set(false);
        }
    }

    /**
     * Process a batch of pending embeddings with locking.
     * Uses SELECT FOR UPDATE SKIP LOCKED to prevent duplicate processing.
     * 
     * Flow:
     * 1. Record batch start time
     * 2. Claim embeddings atomically (PENDING -> PROCESSING)
     * 3. Fetch only embeddings claimed in this batch (by updatedAt timestamp)
     * 4. Process each embedding
     * 
     * This ensures no two schedulers (even across JVMs) process the same embedding.
     */
    @Transactional
    public int processBatch() {
        // Record batch start time for identifying our claimed embeddings
        java.time.LocalDateTime batchStartTime = java.time.LocalDateTime.now();

        // Step 1: Atomically claim embeddings (PENDING -> PROCESSING)
        // FOR UPDATE SKIP LOCKED ensures:
        // - Multiple schedulers can claim different embeddings concurrently
        // - No two schedulers claim the same embedding
        int claimed = embeddingRepository.claimPendingEmbeddings(BATCH_SIZE, LOCK_TIMEOUT_SECONDS);

        if (claimed == 0) {
            return 0;
        }

        log.debug("Claimed {} embeddings for processing at {}", claimed, batchStartTime);

        // Step 2: Fetch ONLY embeddings we just claimed (by timestamp)
        // This is the key fix: we use batchStartTime to isolate our claimed records
        // from any other PROCESSING records that might exist
        List<TicketEmbedding> ourClaimedEmbeddings = embeddingRepository
                .findRecentlyClaimedEmbeddings(batchStartTime);

        if (ourClaimedEmbeddings.isEmpty()) {
            log.warn("No embeddings found after claiming {}. Possible timing issue.", claimed);
            return 0;
        }

        int successCount = 0;
        int failCount = 0;

        // Step 3: Process each claimed embedding
        for (TicketEmbedding embedding : ourClaimedEmbeddings) {
            try {
                embeddingService.processEmbeddingDirect(embedding.getId());
                successCount++;
            } catch (Exception e) {
                log.error("Failed to process embedding {}: {}", embedding.getId(), e.getMessage());
                failCount++;
            }
        }

        log.debug("Batch result: {} success, {} failed", successCount, failCount);
        return successCount + failCount;
    }

    /**
     * Cleanup stale embeddings (scheduled separately).
     * Resets embeddings stuck in PROCESSING for too long back to PENDING.
     */
    @Scheduled(fixedRate = 600000) // Every 10 minutes
    @Transactional
    public void cleanupStaleEmbeddings() {
        java.time.LocalDateTime cutoffTime = java.time.LocalDateTime.now()
                .minusSeconds(LOCK_TIMEOUT_SECONDS * 2); // 2x timeout

        int reset = embeddingRepository.resetStaleEmbeddings(cutoffTime);
        if (reset > 0) {
            log.info("Reset {} stale embeddings to PENDING", reset);
        }
    }

    /**
     * Manual trigger for embedding job.
     */
    public void triggerEmbeddingJob() {
        if (!isRunning.compareAndSet(false, true)) {
            log.warn("Embedding job already running");
            return;
        }

        try {
            log.info("Manual embedding job triggered");
            processPendingEmbeddings();
        } finally {
            isRunning.set(false);
        }
    }

    /**
     * Check if job is currently running.
     */
    public boolean isJobRunning() {
        return isRunning.get();
    }
}
