package com.example.ticketing.ai.query;

import static com.example.ticketing.ai.query.QueryTestPlans.filter;
import static com.example.ticketing.ai.query.QueryTestPlans.list;
import static com.example.ticketing.ai.query.QueryTestPlans.plan;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.ticketing.ai.EmbeddingService;
import com.example.ticketing.auth.UserAccount;
import com.example.ticketing.authorization.ActorContext;
import com.example.ticketing.department.ItDepartmentResolver;
import com.example.ticketing.escalation.EscalationHistory;
import com.example.ticketing.escalation.EscalationHistoryRepository;
import com.example.ticketing.ticket.Ticket;
import com.example.ticketing.ticket.TicketAuthorizationFixtures;
import com.example.ticketing.ticket.TicketRepository;
import com.example.ticketing.ticket.TicketTypes.TicketPriority;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EscalationHistoryQueryAdapterTest {

    @MockitoBean private EmbeddingService embeddingService;
    @Autowired private TicketAuthorizationFixtures fixtures;
    @Autowired private ItDepartmentResolver itDepartmentResolver;
    @Autowired private TicketRepository tickets;
    @Autowired private EscalationHistoryRepository history;
    @Autowired private EscalationHistoryQueryAdapter adapter;

    private final DomainDef d = EscalationHistoryQueryAdapter.DEF;

    private ActorContext actor(UserAccount u) {
        return ActorContext.of(u, itDepartmentResolver.isITDepartment(u.getDepartment()));
    }

    private Ticket escalated(String title, UserAccount requester, String rule) {
        Ticket t = tickets.save(fixtures.newTicket(title, TicketPriority.HIGH, requester));
        EscalationHistory h = new EscalationHistory();
        h.setTicketId(t.getId());
        h.setTicketNumber(t.getTicketNumber());
        h.setRuleName(rule);
        h.setEscalationLevel("LEVEL_1");
        history.save(h);
        return t;
    }

    @Test
    void nonItStaffSeesOnlyHistoryOfReadableTickets() {
        escalated("MKT ticket", fixtures.nhanVienMkt(), "Rule MKT");
        escalated("IT ticket", fixtures.nhanVienIT(), "Rule IT");
        QueryResult r = adapter.execute(list(d, filter(d, "ruleName", FilterOp.CONTAINS, "Rule ")), actor(fixtures.truongPhongMkt()));
        assertThat(r.rows()).extracting(Row::title).containsExactly("Rule MKT");
        assertThat(r.scopeNote()).contains("ticket bạn được xem");
    }

    @Test
    void privilegedActorSeesAllHistory() {
        escalated("MKT ticket", fixtures.nhanVienMkt(), "Rule MKT");
        escalated("IT ticket", fixtures.nhanVienIT(), "Rule IT");
        QueryResult r = adapter.execute(list(d, filter(d, "ruleName", FilterOp.CONTAINS, "Rule ")), actor(fixtures.admin()));
        assertThat(r.rows()).extracting(Row::title).containsExactlyInAnyOrder("Rule MKT", "Rule IT");
        assertThat(r.scopeNote()).isNull();
    }

    @Test
    void historyOfOutOfScopeTicketIsDenied() {
        Ticket it = escalated("IT ticket", fixtures.nhanVienIT(), "Rule IT");
        QueryResult r = adapter.execute(plan(d, QueryAction.DETAIL, 20, 0, it.getTicketNumber()), actor(fixtures.nhanVienMkt()));
        assertThat(r.status()).isEqualTo(QueryResult.Status.DENIED);
    }

    @Test
    void listFilteredByOutOfScopeTicketCodeIsDenied() {
        Ticket it = escalated("IT ticket", fixtures.nhanVienIT(), "Rule IT");
        QueryResult r = adapter.execute(list(d, filter(d, "ticketNumber", FilterOp.CONTAINS, it.getTicketNumber())),
                actor(fixtures.nhanVienMkt()));
        assertThat(r.status()).isEqualTo(QueryResult.Status.DENIED);
    }

    @Test
    void countUsesTheSameTicketScope() {
        escalated("MKT ticket", fixtures.nhanVienMkt(), "Rule C");
        escalated("IT ticket", fixtures.nhanVienIT(), "Rule C");
        QueryResult r = adapter.execute(plan(d, QueryAction.COUNT, 20, 0, null,
                filter(d, "ruleName", FilterOp.CONTAINS, "Rule C")), actor(fixtures.truongPhongMkt()));
        assertThat(r.totalCount()).isEqualTo(1);
    }
}
