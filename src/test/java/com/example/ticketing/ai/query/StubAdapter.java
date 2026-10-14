package com.example.ticketing.ai.query;

import com.example.ticketing.authorization.ActorContext;
import java.util.List;
import java.util.Map;
import com.example.ticketing.ticket.TicketTypes.TicketStatus;
import com.example.ticketing.ticket.TicketTypes.TicketPriority;

/** Test double: a domain definition with no data access. */
record StubAdapter(DomainDef definition, AccessRule accessRule) implements DomainQueryAdapter {

    StubAdapter(DomainDef definition) {
        this(definition, AccessRule.allRoles());
    }

    @Override
    public QueryResult execute(ValidatedPlan plan, ActorContext actor) {
        throw new UnsupportedOperationException("stub");
    }

    static DomainDef ticketLike() {
        return new DomainDef("ticket", "ticket", "phiếu hỗ trợ IT", List.of(
                FieldDef.text("title", "title", "tiêu đề"),
                FieldDef.enumeration("status", "status", TicketStatus.class, "trạng thái")
                        .withGroups(Map.of(
                                "OPEN", List.of("NEW", "ASSIGNED", "IN_PROGRESS"),
                                "DANG_MO", List.of("NEW", "ASSIGNED", "IN_PROGRESS"))),
                FieldDef.enumeration("priority", "priority", TicketPriority.class, "độ ưu tiên"),
                FieldDef.text("assignee", "assigneeName", "người xử lý"),
                FieldDef.date("createdAt", "createdAt", "ngày tạo"),
                FieldDef.semantic("mô tả sự cố")),
                "createdAt", true);
    }
}
