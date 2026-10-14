package com.example.ticketing.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Startup trigger for the one-time ticket embedding backfill.
 *
 * Uses ApplicationReadyEvent so the backfill never runs before Flyway
 * migrations, datasource initialisation or the Ollama configuration beans
 * are ready.
 *
 * The listener is @Async so the application context is fully started and the
 * application accepts requests while the backfill runs in the background. The
 * backfill calls Ollama for every legacy ticket, which must not delay
 * startup.
 *
 * Any failure is logged and swallowed: a temporarily unavailable Ollama must
 * never prevent the application from starting. The next startup retries,
 * because the backfill is idempotent based on database state.
 *
 * This class performs no embedding work itself; it only delegates to
 * EmbeddingBackfillService.
 *
 * Disabled under the "test" profile, matching the existing @Profile("!test")
 * convention used by DataInitializer, ChangeInitializer and
 * ServiceCatalogInitializer, so tests are not affected by a real Ollama call.
 */
@Component
@Profile("!test")
public class EmbeddingBackfillRunner {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingBackfillRunner.class);

    private final EmbeddingBackfillService backfillService;

    public EmbeddingBackfillRunner(EmbeddingBackfillService backfillService) {
        this.backfillService = backfillService;
    }

    /**
     * Run the embedding startup backfill once the application is ready.
     */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void runStartupBackfill() {
        try {
            backfillService.backfillMissingEmbeddings();
        } catch (Exception e) {
            // Never propagate: the application must stay healthy even when
            // Ollama is unavailable or the database rejects a statement.
            log.error("Embedding startup backfill aborted: {}", e.getMessage(), e);
        }
    }
}
