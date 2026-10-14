package com.example.ticketing.problem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Phase 6.6.4 - Startup trigger for the one-time Known Error backfill.
 *
 * <p>Disabled under the {@code test} profile to match the convention
 * used by the ticket embedding backfill, so the unit tests do not
 * trigger an Ollama call.
 */
@Component
@Profile("!test")
public class KnownErrorEmbeddingBackfillRunner {

    private static final Logger log = LoggerFactory.getLogger(KnownErrorEmbeddingBackfillRunner.class);

    private final KnownErrorEmbeddingBackfillService backfillService;

    public KnownErrorEmbeddingBackfillRunner(KnownErrorEmbeddingBackfillService backfillService) {
        this.backfillService = backfillService;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void runStartupBackfill() {
        try {
            backfillService.backfillMissingEmbeddings();
        } catch (Exception e) {
            // Never propagate: the application must stay healthy even when
            // Ollama is unavailable or the database rejects a statement.
            log.error("KnownError embedding startup backfill aborted: {}", e.getMessage(), e);
        }
    }
}
