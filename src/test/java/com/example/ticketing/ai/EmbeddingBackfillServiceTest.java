package com.example.ticketing.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.ticketing.ai.EmbeddingBackfillService.BackfillResult;
import com.example.ticketing.ticket.TicketEmbeddingRepository;

/**
 * Unit tests for the one-time startup embedding backfill.
 *
 * The existing embedding pipeline (EmbeddingService + EmbeddingSchedulerService)
 * is mocked, so these tests verify only the backfill's own responsibility:
 * candidate detection, creation of missing PENDING records, delegation to the
 * existing pipeline, per-ticket failure isolation and idempotency.
 *
 * No real Ollama call and no real database is required.
 */
@ExtendWith(MockitoExtension.class)
class EmbeddingBackfillServiceTest {

    @Mock
    private TicketEmbeddingRepository embeddingRepository;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private EmbeddingSchedulerService embeddingSchedulerService;

    private EmbeddingBackfillService backfillService;

    private static final Long TICKET_10 = 10L;
    private static final Long TICKET_11 = 11L;
    private static final Long TICKET_12 = 12L;
    private static final Long TICKET_13 = 13L;

    @BeforeEach
    void setUp() {
        backfillService = new EmbeddingBackfillService(
                embeddingRepository,
                embeddingService,
                embeddingSchedulerService);
    }

    // ============================================================
    // Test 1 - missing embedding is detected
    // ============================================================

    @Nested
    @DisplayName("Test 1: Ticket without a completed embedding is selected for backfill")
    class MissingEmbeddingIsDetected {

        @Test
        @DisplayName("Ticket with no COMPLETED embedding is returned as a candidate")
        void ticketWithoutCompletedEmbeddingIsCandidate() {
            when(embeddingRepository.findTicketIdsWithoutCompletedEmbedding())
                    .thenReturn(List.of(TICKET_10));
            when(embeddingRepository.existsEmbeddingForTicket(TICKET_10)).thenReturn(false);
            when(embeddingRepository.countCompletedWithVectorByTicketIds(anyList())).thenReturn(1L);
            when(embeddingRepository.countFailedByTicketIds(anyList())).thenReturn(0L);
            when(embeddingSchedulerService.runEmbeddingProcessingForBackfill()).thenReturn(1);

            BackfillResult result = backfillService.backfillMissingEmbeddings();

            assertEquals(1, result.candidates());
            assertEquals(1, result.created());
            assertEquals(1, result.processed());
            assertEquals(0, result.failed());
        }
    }

    // ============================================================
    // Test 2 - completed embedding is skipped
    // ============================================================

    @Nested
    @DisplayName("Test 2: Ticket with a COMPLETED embedding is skipped")
    class CompletedEmbeddingIsSkipped {

        @Test
        @DisplayName("COMPLETED ticket is not a candidate, so no processing happens for it")
        void completedTicketIsNotSelected() {
            // COMPLETED + vector tickets are excluded by the candidate query.
            when(embeddingRepository.findTicketIdsWithoutCompletedEmbedding())
                    .thenReturn(List.of());

            BackfillResult result = backfillService.backfillMissingEmbeddings();

            assertEquals(0, result.candidates());
            assertEquals(0, result.created());
            verify(embeddingService, never()).createEmbedding(anyLong());
            // Nothing to do, so the existing pipeline is never triggered.
            verify(embeddingSchedulerService, never()).runEmbeddingProcessingForBackfill();
        }

        @Test
        @DisplayName("A COMPLETED ticket alongside a legacy ticket only processes the legacy one")
        void completedTicketIsSkippedWhileLegacyTicketIsProcessed() {
            // Ticket #10 has no record; ticket #12 is COMPLETED and is not returned.
            when(embeddingRepository.findTicketIdsWithoutCompletedEmbedding())
                    .thenReturn(List.of(TICKET_10));
            when(embeddingRepository.existsEmbeddingForTicket(TICKET_10)).thenReturn(false);
            when(embeddingRepository.countCompletedWithVectorByTicketIds(anyList())).thenReturn(1L);
            when(embeddingRepository.countFailedByTicketIds(anyList())).thenReturn(0L);
            when(embeddingSchedulerService.runEmbeddingProcessingForBackfill()).thenReturn(1);

            BackfillResult result = backfillService.backfillMissingEmbeddings();

            assertEquals(1, result.candidates());
            // The COMPLETED ticket is never re-embedded.
            verify(embeddingService, never()).createEmbedding(TICKET_12);
            verify(embeddingService).createEmbedding(TICKET_10);
        }
    }

    // ============================================================
    // Test 3 - missing embedding record is created
    // ============================================================

    @Nested
    @DisplayName("Test 3: Missing embedding record is created via the existing path")
    class MissingEmbeddingRecordIsCreated {

        @Test
        @DisplayName("Ticket with no ticket_embeddings row gets a PENDING record created")
        void pendingRecordIsCreatedForTicketWithoutRecord() {
            when(embeddingRepository.findTicketIdsWithoutCompletedEmbedding())
                    .thenReturn(List.of(TICKET_10, TICKET_11));
            when(embeddingRepository.existsEmbeddingForTicket(anyLong())).thenReturn(false);
            when(embeddingRepository.countCompletedWithVectorByTicketIds(anyList())).thenReturn(2L);
            when(embeddingRepository.countFailedByTicketIds(anyList())).thenReturn(0L);
            when(embeddingSchedulerService.runEmbeddingProcessingForBackfill()).thenReturn(2);

            BackfillResult result = backfillService.backfillMissingEmbeddings();

            assertEquals(2, result.candidates());
            assertEquals(2, result.created());
            // Creation reuses EmbeddingService.createEmbedding, which is the same
            // path used for newly created tickets and sets PENDING status.
            verify(embeddingService).createEmbedding(TICKET_10);
            verify(embeddingService).createEmbedding(TICKET_11);
        }

        @Test
        @DisplayName("Existing record is not duplicated")
        void existingRecordIsNotDuplicated() {
            // Ticket #11 already has a PENDING record; only #10 needs creating.
            when(embeddingRepository.findTicketIdsWithoutCompletedEmbedding())
                    .thenReturn(List.of(TICKET_10, TICKET_11));
            when(embeddingRepository.existsEmbeddingForTicket(TICKET_10)).thenReturn(false);
            when(embeddingRepository.existsEmbeddingForTicket(TICKET_11)).thenReturn(true);
            when(embeddingRepository.countCompletedWithVectorByTicketIds(anyList())).thenReturn(2L);
            when(embeddingRepository.countFailedByTicketIds(anyList())).thenReturn(0L);
            when(embeddingSchedulerService.runEmbeddingProcessingForBackfill()).thenReturn(2);

            BackfillResult result = backfillService.backfillMissingEmbeddings();

            assertEquals(2, result.candidates());
            assertEquals(1, result.created());
            assertEquals(1, result.skipped());
            verify(embeddingService).createEmbedding(TICKET_10);
            verify(embeddingService, never()).createEmbedding(TICKET_11);
        }

        @Test
        @DisplayName("The existing embedding pipeline is reused for processing")
        void existingPipelineIsReused() {
            when(embeddingRepository.findTicketIdsWithoutCompletedEmbedding())
                    .thenReturn(List.of(TICKET_10));
            when(embeddingRepository.existsEmbeddingForTicket(TICKET_10)).thenReturn(false);
            when(embeddingRepository.countCompletedWithVectorByTicketIds(anyList())).thenReturn(1L);
            when(embeddingRepository.countFailedByTicketIds(anyList())).thenReturn(0L);
            when(embeddingSchedulerService.runEmbeddingProcessingForBackfill()).thenReturn(1);

            backfillService.backfillMissingEmbeddings();

            // Processing must go through the existing scheduler, which owns the
            // batching, SKIP LOCKED claiming and Ollama calls.
            verify(embeddingSchedulerService).runEmbeddingProcessingForBackfill();
        }
    }

    // ============================================================
    // Test 4 - one ticket failure does not stop others
    // ============================================================

    @Nested
    @DisplayName("Test 4: A single ticket failure does not stop the remaining backfill")
    class SingleFailureDoesNotStopOthers {

        @Test
        @DisplayName("Ticket B failing does not prevent A and C from being processed")
        void failureOnOneTicketDoesNotStopBackfill() {
            // A (#10) and C (#13) are fine; B (#11) fails during record creation.
            when(embeddingRepository.findTicketIdsWithoutCompletedEmbedding())
                    .thenReturn(List.of(TICKET_10, TICKET_11, TICKET_13));
            when(embeddingRepository.existsEmbeddingForTicket(anyLong())).thenReturn(false);

            // Single argument-aware stub so only ticket #11 fails.
            doAnswer(invocation -> {
                if (TICKET_11.equals(invocation.<Long>getArgument(0))) {
                    throw new RuntimeException("db error");
                }
                return null;
            }).when(embeddingService).createEmbedding(anyLong());

            // A and C complete; B is left FAILED by the existing lifecycle.
            when(embeddingRepository.countCompletedWithVectorByTicketIds(anyList())).thenReturn(2L);
            when(embeddingRepository.countFailedByTicketIds(anyList())).thenReturn(1L);
            when(embeddingSchedulerService.runEmbeddingProcessingForBackfill()).thenReturn(2);

            BackfillResult result = backfillService.backfillMissingEmbeddings();

            // The backfill completed and did not propagate the failure.
            assertEquals(3, result.candidates());
            assertEquals(2, result.created());
            assertEquals(2, result.processed());
            // B failed, both at record creation and as FAILED in the lifecycle.
            assertEquals(2, result.failed());
            assertEquals(0, result.skipped());

            // A and C were still created and the pipeline still ran.
            verify(embeddingService).createEmbedding(TICKET_10);
            verify(embeddingService).createEmbedding(TICKET_13);
            verify(embeddingSchedulerService).runEmbeddingProcessingForBackfill();
        }

        @Test
        @DisplayName("Ollama being unavailable does not throw out of the backfill")
        void ollamaFailureDoesNotPropagate() {
            when(embeddingRepository.findTicketIdsWithoutCompletedEmbedding())
                    .thenReturn(List.of(TICKET_10));
            when(embeddingRepository.existsEmbeddingForTicket(TICKET_10)).thenReturn(false);
            when(embeddingSchedulerService.runEmbeddingProcessingForBackfill())
                    .thenThrow(new RuntimeException("Ollama unavailable"));

            // No embeddings completed and none failed yet: the run stays healthy.
            when(embeddingRepository.countCompletedWithVectorByTicketIds(anyList())).thenReturn(0L);
            when(embeddingRepository.countFailedByTicketIds(anyList())).thenReturn(0L);

            BackfillResult result = assertDoesNotThrow(
                    () -> backfillService.backfillMissingEmbeddings());

            assertEquals(1, result.candidates());
            assertEquals(1, result.created());
            assertEquals(0, result.processed());
            assertEquals(0, result.failed());
        }
    }

    // ============================================================
    // Test 5 - idempotency
    // ============================================================

    @Nested
    @DisplayName("Test 5: Running the backfill twice is idempotent")
    class BackfillIsIdempotent {

        @Test
        @DisplayName("Second run finds no candidates and makes no second embedding call")
        void secondRunPerformsNoWork() {
            // First run: legacy tickets are found and processed.
            when(embeddingRepository.findTicketIdsWithoutCompletedEmbedding())
                    .thenReturn(List.of(TICKET_10, TICKET_11));
            when(embeddingRepository.existsEmbeddingForTicket(anyLong())).thenReturn(false);
            when(embeddingRepository.countCompletedWithVectorByTicketIds(anyList())).thenReturn(2L);
            when(embeddingRepository.countFailedByTicketIds(anyList())).thenReturn(0L);
            when(embeddingSchedulerService.runEmbeddingProcessingForBackfill()).thenReturn(2);

            BackfillResult first = backfillService.backfillMissingEmbeddings();
            assertEquals(2, first.created());
            assertEquals(2, first.processed());

            // Second run: all tickets now hold COMPLETED embeddings, so the
            // candidate query returns nothing.
            when(embeddingRepository.findTicketIdsWithoutCompletedEmbedding())
                    .thenReturn(List.of());

            BackfillResult second = backfillService.backfillMissingEmbeddings();

            assertEquals(0, second.candidates());
            assertEquals(0, second.created());

            // No additional embedding record creation for the second run.
            verify(embeddingService, times(2)).createEmbedding(anyLong());
        }
    }

    // ============================================================
    // Test 6 - no candidates
    // ============================================================

    @Nested
    @DisplayName("Test 6: No candidates means no work")
    class NoCandidatesScenario {

        @Test
        @DisplayName("Backfill is a no-op when all tickets are already embedded")
        void noCandidatesPerformsNoWork() {
            when(embeddingRepository.findTicketIdsWithoutCompletedEmbedding())
                    .thenReturn(List.of());

            BackfillResult result = backfillService.backfillMissingEmbeddings();

            assertEquals(0, result.candidates());
            assertEquals(0, result.created());
            assertEquals(0, result.processed());
            assertEquals(0, result.skipped());
            assertEquals(0, result.failed());

            verify(embeddingService, never()).createEmbedding(anyLong());
            // The pipeline is not even triggered when there is nothing to do.
            verify(embeddingSchedulerService, never()).runEmbeddingProcessingForBackfill();
        }
    }
}
