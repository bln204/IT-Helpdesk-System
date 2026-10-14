package com.example.ticketing.ai.query;

import com.example.ticketing.authorization.ActorContext;
import com.example.ticketing.escalation.EscalationHistory;
import com.example.ticketing.escalation.EscalationHistoryRepository;
import com.example.ticketing.ticket.Ticket;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.List;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class EscalationHistoryQueryAdapter extends JpaQueryAdapter<EscalationHistory> {

    /** EscalationController#getTicketHistory: any user, but only for tickets they may read (enforced in scope). */
    static final AccessRule ACCESS = AccessRule.allRoles();

    static final DomainDef DEF = new DomainDef("escalation_history", "lịch sử escalation",
            "lịch sử các lần escalate: quy tắc nào kích hoạt, khi nào, mức nào. Hỏi danh sách ticket đang escalate thì dùng domain ticket",
            List.of(
                    FieldDef.text("ticketNumber", "ticketNumber", "mã ticket"),
                    FieldDef.text("ruleName", "ruleName", "tên quy tắc"),
                    FieldDef.text("escalationLevel", "escalationLevel", "mức escalation"),
                    FieldDef.date("escalatedAt", "escalatedAt", "ngày escalate")),
            "escalatedAt", true);

    private final EscalationHistoryRepository repository;
    private final TicketQueryAdapter tickets;

    public EscalationHistoryQueryAdapter(EscalationHistoryRepository repository, TicketQueryAdapter tickets) {
        this.repository = repository;
        this.tickets = tickets;
    }

    @Override public DomainDef definition() { return DEF; }
    @Override public AccessRule accessRule() { return ACCESS; }
    @Override protected JpaSpecificationExecutor<EscalationHistory> repository() { return repository; }
    @Override protected String idPath() { return "ticketNumber"; }

    /** Naming an unreadable ticket must say "no permission", like the UI's 403, not "no history". */
    @Override
    @Transactional(readOnly = true)
    public QueryResult execute(ValidatedPlan plan, ActorContext actor) {
        for (ResolvedFilter f : plan.filters()) {
            if (!"ticketNumber".equals(f.field().name())) continue;
            for (Object code : f.values()) {
                QueryResult probe = tickets.execute(new ValidatedPlan(QueryAction.DETAIL, TicketQueryAdapter.DEF,
                        List.of(), "createdAt", true, 1, 0, code.toString(), null, null), actor);
                if (probe.status() == QueryResult.Status.DENIED) return probe;
            }
        }
        return super.execute(plan, actor);
    }

    @Override
    protected Scope<EscalationHistory> scope(ActorContext actor) {
        Scope<Ticket> ticketScope = tickets.ticketScope(actor);
        if (ticketScope.deniedMessage() != null) return Scope.deny(ticketScope.deniedMessage());
        if (ticketScope.note() == null) return Scope.all();
        // History is ticket-derived data: same rule as the UI, which reads the parent ticket first.
        return Scope.of((root, q, cb) -> {
            Subquery<Long> sub = q.subquery(Long.class);
            Root<Ticket> t = sub.from(Ticket.class);
            sub.select(t.get("id")).where(ticketScope.spec().toPredicate(t, q, cb));
            return root.get("ticketId").in(sub);
        }, "Kết quả chỉ gồm lịch sử escalation của các ticket bạn được xem.");
    }

    @Override
    protected Row toRow(EscalationHistory h) {
        return new Row(h.getTicketNumber(), fmt(h.getRuleName()), fields(
                "mức", h.getEscalationLevel(),
                "lý do", h.getEscalationReason(),
                "ngày escalate", h.getEscalatedAt()),
                h.getNotes());
    }
}
