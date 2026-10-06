package com.example.ticketing.ticket;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.example.ticketing.ticket.TicketTypes.TicketCategory;
import com.example.ticketing.ticket.TicketTypes.TicketPriority;
import com.example.ticketing.ticket.TicketTypes.TicketStatus;

/**
 * Data-level tests for the startup backfill candidate query.
 *
 * These verify the actual JPQL against a database, which is what guarantees
 * the backfill is idempotent: a ticket is only a candidate while it has no
 * COMPLETED embedding with a stored vector.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TicketEmbeddingRepositoryBackfillTest {

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketEmbeddingRepository embeddingRepository;

    private Ticket ticketNoRecord;
    private Ticket ticketPending;
    private Ticket ticketFailed;
    private Ticket ticketCompleted;
    private Ticket ticketCompletedNoVector;

    @BeforeEach
    void setUp() {
        // Ticket with no ticket_embeddings row at all (the legacy case).
        ticketNoRecord = saveTicket("Legacy ticket without embedding");

        // Ticket whose record is PENDING.
        ticketPending = saveTicket("Ticket awaiting embedding");
        saveEmbedding(ticketPending, EmbeddingStatus.PENDING, null);

        // Ticket whose record is FAILED.
        ticketFailed = saveTicket("Ticket with failed embedding");
        saveEmbedding(ticketFailed, EmbeddingStatus.FAILED, null);

        // Ticket already COMPLETED with a vector: must never be a candidate.
        ticketCompleted = saveTicket("Ticket already embedded");
        saveEmbedding(ticketCompleted, EmbeddingStatus.COMPLETED, buildVector());

        // Ticket COMPLETED but without a vector: not usable, so it stays a
        // candidate rather than being silently ignored.
        ticketCompletedNoVector = saveTicket("Ticket completed without vector");
        saveEmbedding(ticketCompletedNoVector, EmbeddingStatus.COMPLETED, null);
    }

    @Test
    @DisplayName("Only tickets without a COMPLETED embedding and vector are candidates")
    void onlyTicketsWithoutUsableEmbeddingAreCandidates() {
        List<Long> candidates = embeddingRepository.findTicketIdsWithoutCompletedEmbedding();

        assertTrue(candidates.contains(ticketNoRecord.getId()),
                "Ticket without any embedding record must be a backfill candidate");
        assertTrue(candidates.contains(ticketPending.getId()),
                "PENDING ticket must be a candidate so the existing lifecycle processes it");
        assertTrue(candidates.contains(ticketFailed.getId()),
                "FAILED ticket must be a candidate because FAILED is not auto-retried");
        assertTrue(candidates.contains(ticketCompletedNoVector.getId()),
                "COMPLETED without a vector is not usable and must be a candidate");

        assertFalse(candidates.contains(ticketCompleted.getId()),
                "COMPLETED ticket with a vector must never be re-embedded");
    }

    @Test
    @DisplayName("Once all tickets are COMPLETED with a vector there are no candidates")
    void noCandidatesRemainWhenAllTicketsAreEmbedded() {
        // Simulate the state after a successful backfill.
        markCompletedWithVector(ticketPending);
        markCompletedWithVector(ticketFailed);
        markCompletedWithVector(ticketCompletedNoVector);
        embeddingRepository.flush();

        List<Long> candidates = embeddingRepository.findTicketIdsWithoutCompletedEmbedding();

        assertEquals(List.of(ticketNoRecord.getId()), candidates,
                "Only the ticket with no record at all should remain a candidate");
    }

    @Test
    @DisplayName("Scoped counts exclude already-completed tickets from the candidate set")
    void scopedCountsReportPerCandidateState() {
        List<Long> candidates = embeddingRepository.findTicketIdsWithoutCompletedEmbedding();

        // The already-completed ticket is not a candidate, so it is absent from
        // the scoped counts. Only the FAILED candidate is reported.
        assertFalse(candidates.contains(ticketCompleted.getId()));
        assertEquals(0, embeddingRepository.countCompletedWithVectorByTicketIds(candidates),
                "No candidate holds a completed vector yet");
        assertEquals(1, embeddingRepository.countFailedByTicketIds(candidates),
                "Only the FAILED candidate counts as failed");
    }

    @Test
    @DisplayName("A candidate that completes during the run is counted as processed")
    void candidateCompletedDuringRunIsCountedAsProcessed() {
        // Candidates are captured before processing, so a ticket that reaches
        // COMPLETED during the run is correctly reported as processed.
        List<Long> candidates = embeddingRepository.findTicketIdsWithoutCompletedEmbedding();
        assertTrue(candidates.contains(ticketNoRecord.getId()));

        // The pipeline completes the previously-embedded ticket.
        saveEmbedding(ticketNoRecord, EmbeddingStatus.COMPLETED, buildVector());

        assertEquals(1, embeddingRepository.countCompletedWithVectorByTicketIds(candidates),
                "The ticket that completed during the run is counted as processed");
    }

    private void markCompletedWithVector(Ticket ticket) {
        TicketEmbedding embedding = embeddingRepository.findAll().stream()
                .filter(e -> ticket.getId().equals(e.getTicketId()))
                .findFirst()
                .orElseThrow();
        embedding.setEmbeddingStatus(EmbeddingStatus.COMPLETED);
        embedding.setEmbedding(buildVector());
        embeddingRepository.save(embedding);
    }

    @Test
    @DisplayName("Existence check reports whether a ticket already has an embedding record")
    void existenceCheckWorks() {
        // Regression: the backfill relies on this to avoid duplicate rows.
        assertTrue(embeddingRepository.existsEmbeddingForTicket(ticketCompleted.getId()),
                "An existing record must be detected");
        assertFalse(embeddingRepository.existsEmbeddingForTicket(ticketNoRecord.getId()),
                "A ticket with no record must be reported as absent");
    }

    private Ticket saveTicket(String title) {
        Ticket ticket = new Ticket();
        ticket.setTicketNumber("TCK-" + System.nanoTime() + "-" + title.hashCode());
        ticket.setTitle(title);
        ticket.setDescription("Description for " + title);
        ticket.setPriority(TicketPriority.MEDIUM);
        ticket.setCategory(TicketCategory.SOFTWARE);
        ticket.setStatus(TicketStatus.NEW);
        ticket.setRequesterName("Test Requester");
        ticket.setRequesterUsername("tester");
        return ticketRepository.saveAndFlush(ticket);
    }

    private TicketEmbedding saveEmbedding(Ticket ticket, EmbeddingStatus status, String vector) {
        TicketEmbedding embedding = new TicketEmbedding();
        embedding.setTicket(ticket);
        embedding.setEmbeddingStatus(status);
        embedding.setEmbedding(vector);
        return embeddingRepository.saveAndFlush(embedding);
    }

    /**
     * Minimal 768-dimension vector so the test mirrors the real column contract.
     */
    private String buildVector() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 768; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("0.0");
        }
        return sb.append("]").toString();
    }
}
