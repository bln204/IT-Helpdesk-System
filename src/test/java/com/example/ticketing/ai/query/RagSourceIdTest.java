package com.example.ticketing.ai.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ticketing.ai.EmbeddingService;
import com.example.ticketing.ai.KnownErrorRagRetrievalService;
import com.example.ticketing.ai.RagContextDto;
import com.example.ticketing.ai.RagRetrievalService;
import com.example.ticketing.problem.KnownError;
import com.example.ticketing.problem.KnownErrorRepository;
import com.example.ticketing.ticket.Ticket;
import com.example.ticketing.ticket.TicketAuthorizationFixtures;
import com.example.ticketing.ticket.TicketRepository;
import com.example.ticketing.ticket.TicketTypes.TicketPriority;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

/** semantic_match intersects RAG hits by id, so the RAG sources must carry the entity id. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RagSourceIdTest {

    @MockitoBean private EmbeddingService embeddingService;
    @Autowired private RagRetrievalService ragRetrievalService;
    @Autowired private KnownErrorRagRetrievalService knownErrorRagRetrievalService;
    @Autowired private TicketAuthorizationFixtures fixtures;
    @Autowired private TicketRepository tickets;
    @Autowired private KnownErrorRepository knownErrors;

    @Test
    void ticketRagSourceCarriesTicketId() {
        Ticket t = tickets.save(fixtures.newTicket("Excel chậm", TicketPriority.MEDIUM, fixtures.nhanVienMkt()));
        RagContextDto.RagSource s = ReflectionTestUtils.invokeMethod(
                (Object) AopTestUtils.getTargetObject(ragRetrievalService), "toRagSource", t, 0.9);
        assertThat(s.getId()).isEqualTo(t.getId());
    }

    @Test
    void knownErrorRagSourceCarriesId() {
        KnownError k = new KnownError();
        k.setErrorCode("KE-ID1");
        k.setTitle("Outlook treo");
        k = knownErrors.save(k);
        RagContextDto.RagSource s = ReflectionTestUtils.invokeMethod(
                (Object) AopTestUtils.getTargetObject(knownErrorRagRetrievalService), "toRagSource", k, 0.9);
        assertThat(s.getId()).isEqualTo(k.getId());
    }
}
