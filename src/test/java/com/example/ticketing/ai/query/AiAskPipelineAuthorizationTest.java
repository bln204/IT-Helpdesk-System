package com.example.ticketing.ai.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.example.ticketing.ai.AiAssistantDto;
import com.example.ticketing.ai.EmbeddingService;
import com.example.ticketing.ticket.Ticket;
import com.example.ticketing.ticket.TicketAuthorizationFixtures;
import com.example.ticketing.ticket.TicketRepository;
import com.example.ticketing.ticket.TicketTypes.TicketPriority;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/** /ask RBAC end to end with the real pipeline, executor, adapters and sessions; only the LLM planner is stubbed. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AiAskPipelineAuthorizationTest {

    @MockitoBean private EmbeddingService embeddingService;
    @MockitoBean private QueryPlanner planner;
    @Autowired private AiAssistantPipeline pipeline;
    @Autowired private TicketAuthorizationFixtures fixtures;
    @Autowired private TicketRepository tickets;

    private void planned(String action, String domain, String targetId) {
        when(planner.plan(anyString(), any(), anyString()))
                .thenReturn(new QueryPlan(action, domain, List.of(), null, null, null, targetId, null));
    }

    private String ask(String user, String q) {
        fixtures.truongPhongIT();
        return pipeline.answer(new AiAssistantDto.AskRequest(q, 5, 0.65), user).getAnswer();
    }

    @Test
    void nonItStaffSeesOnlyOwnDepartmentTickets() {
        Ticket mkt = tickets.save(fixtures.newTicket("Máy in MKT", TicketPriority.LOW, fixtures.nhanVienMkt()));
        Ticket it = tickets.save(fixtures.newTicket("VPN IT", TicketPriority.LOW, fixtures.nhanVienIT()));
        planned("list", "ticket", null);

        String answer = ask("nv_mkt", "các ticket");

        assertThat(answer).contains(mkt.getTicketNumber()).doesNotContain(it.getTicketNumber());
    }

    @Test
    void nhanVienIsDeniedTeams() {
        fixtures.nhanVienMkt();
        planned("list", "team", null);
        assertThat(ask("nv_mkt", "liệt kê team"))
                .isEqualTo("Bạn không có quyền xem team. Chức năng này dành cho Admin, Giám đốc, Trưởng phòng.");
    }

    @Test
    void outOfScopeDetailIsDenied() {
        Ticket it = tickets.save(fixtures.newTicket("VPN IT", TicketPriority.LOW, fixtures.nhanVienIT()));
        fixtures.nhanVienMkt();
        planned("detail", "ticket", it.getTicketNumber());
        assertThat(ask("nv_mkt", it.getTicketNumber() + " sao rồi")).isEqualTo("Bạn không có quyền xem " + it.getTicketNumber() + ".");
    }
}
