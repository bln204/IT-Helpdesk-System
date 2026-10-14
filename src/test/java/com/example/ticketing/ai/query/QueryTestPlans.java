package com.example.ticketing.ai.query;

import java.util.Arrays;
import java.util.List;

final class QueryTestPlans {

    private QueryTestPlans() { }

    static ValidatedPlan plan(DomainDef d, QueryAction action, int limit, int offset, String targetId,
                              ResolvedFilter... filters) {
        return new ValidatedPlan(action, d, List.of(filters), d.defaultSortPath(), d.defaultSortDesc(),
                limit, offset, targetId, null, null);
    }

    static ValidatedPlan list(DomainDef d, ResolvedFilter... filters) {
        return plan(d, QueryAction.LIST, 20, 0, null, filters);
    }

    static ResolvedFilter filter(DomainDef d, String field, FilterOp op, Object... values) {
        return new ResolvedFilter(d.field(field).orElseThrow(), op, Arrays.asList(values));
    }
}
