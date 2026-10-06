package com.example.ticketing.ai;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.ticketing.ticket.TicketEmbeddingRepository;

/**
 * One-time startup backfill for tickets created before the embedding
 * infrastructure existed.
 *
 * Those legacy tickets have no ticket_embeddings row, so they can never be
 * retrieved by pgvector RAG search. This service creates the missing PENDING
 * records and then reuses the existing embedding pipeline to process them.
 *
 * The backfill is idempotent by database state, not by an in-memory flag:
 * every startup re-runs the same candidate lookup, and once all tickets hold a
 * COMPLETED embedding the candidate list is empty and nothing happens. No
 * extra migration or "already ran" column is required.
 *
 * The actual vector generation is NOT implemented here. It is delegated to
 * EmbeddingService (text construction, Ollama call, vector persistence) and
 * EmbeddingSchedulerService (batching, FOR UPDATE SKIP LOCKED claiming,
 * status transitions), which remain the single source of truth.
 */
@Service
public class EmbeddingBackfillService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingBackfillService.class);

    private final TicketEmbeddingRepository embeddingRepository;
    private final EmbeddingService embeddingService;
    private final EmbeddingSchedulerService embeddingSchedulerService;

    public EmbeddingBackfillService(
            TicketEmbeddingRepository embeddingRepository,
            EmbeddingService embeddingService,
            EmbeddingSchedulerService embeddingSchedulerService) {
        this.embeddingRepository = embeddingRepository;
        this.embeddingService = embeddingService;
        this.embeddingSchedulerService = embeddingSchedulerService;
    }

    /**
     * Backfill result summary.
     *
     * @param candidates tickets that did not have a usable embedding at start
     * @param created    PENDING embedding records created by this run
     * @param processed  candidates that reached COMPLETED with a vector
     * @param skipped    candidates that already held a usable embedding
     * @param failed     candidates left in FAILED status
     */
    public record BackfillResult(int candidates, int created, int processed, int skipped, int failed) {
    }

    /**
     * Ensure every existing ticket has a usable embedding.
     *
     * Steps:
     * 1. Find tickets without a COMPLETED embedding (idempotent candidate query).
     * 2. Create the missing PENDING records through the existing
     *    EmbeddingService.createEmbedding path.
     * 3. Reuse EmbeddingSchedulerService to process the PENDING records.
     *
     * Never throws for an individual ticket: a failure on one ticket is
     * recorded as FAILED by the existing pipeline and the remaining tickets
     * continue to be processed.
     *
     * @return summary of the backfill run
     */
    public BackfillResult backfillMissingEmbeddings() {
        log.info("Embedding startup backfill started");

        List<Long> candidateIds = embeddingRepository.findTicketIdsWithoutCompletedEmbedding();

        if (candidateIds == null || candidateIds.isEmpty()) {
            log.info("Embedding startup backfill: no tickets require processing");
            return new BackfillResult(0, 0, 0, 0, 0);
        }

        log.info("Embedding startup backfill: found {} tickets requiring embeddings", candidateIds.size());

        int candidates = candidateIds.size();
        int created = 0;
        int creationFailed = 0;

        // Create the missing PENDING records. A failure for one ticket must not
        // stop the others, so each creation is handled independently.
        for (Long ticketId : candidateIds) {
            try {
                if (ensurePendingEmbedding(ticketId)) {
                    created++;
                }
            } catch (Exception e) {
                // Do not propagate: the remaining tickets must still be processed.
                creationFailed++;
                log.error("Embedding backfill failed for ticketId={}: {}", ticketId, e.getMessage());
            }
        }

        // Reuse the existing pipeline: batch processing, SKIP LOCKED claiming,
        // PROCESSING -> COMPLETED/FAILED transitions and Ollama calls.
        try {
            embeddingSchedulerService.runEmbeddingProcessingForBackfill();
        } catch (Exception e) {
            // Startup must not fail because Ollama is temporarily unavailable.
            log.error("Embedding startup backfill processing failed: {}", e.getMessage(), e);
        }

        // Derive the outcome from database state, scoped to this run's
        // candidates, so the numbers reflect what the existing lifecycle did.
        int succeeded = (int) embeddingRepository.countCompletedWithVectorByTicketIds(candidateIds);
        int failed = (int) embeddingRepository.countFailedByTicketIds(candidateIds)
                + creationFailed;

        // Candidates that already held a usable embedding when this run began
        // are reported as skipped. Creation failures are failures, not skips.
        int skipped = Math.max(0, candidates - created - creationFailed);

        log.info("Embedding startup backfill completed: processed={}, skipped={}, failed={}",
                succeeded, skipped, failed);

        return new BackfillResult(candidates, created, succeeded, skipped, failed);
    }

    /**
     * Ensure the ticket has a PENDING embedding record.
     *
     * Reuses the existing creation path so the record is identical to what a
     * newly created ticket receives (PENDING, no vector, no timestamps, no
     * error). Tickets that already completed are never reset to PENDING, which
     * is what prevents re-embedding on later startups.
     *
     * The existence re-check guards against a record created concurrently by
     * the normal ticket-creation pipeline, so the UNIQUE(ticket_id) constraint
     * is never violated and no duplicate rows appear.
     *
     * @param ticketId ticket to backfill
     * @return true if a PENDING record was created, false if one already existed
     */
    private boolean ensurePendingEmbedding(Long ticketId) {
        if (embeddingRepository.existsEmbeddingForTicket(ticketId)) {
            return false;
        }
        embeddingService.createEmbedding(ticketId);
        return true;
    }
}
