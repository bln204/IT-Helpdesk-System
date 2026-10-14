package com.example.ticketing.ai.query;

import java.util.List;

public record ValidatedPlan(
        QueryAction action,
        DomainDef domain,
        List<ResolvedFilter> filters,
        String sortPath,
        boolean sortDesc,
        int limit,
        int offset,
        String targetId,
        String clarifyQuestion,
        QueryPlan source) {

    public static ValidatedPlan clarify(String question, QueryPlan source) {
        return new ValidatedPlan(QueryAction.CLARIFY, null, List.of(), null, false, 0, 0, null, question, source);
    }

    /** Actions that never touch domain data: chitchat, create_ticket, troubleshoot. */
    public static ValidatedPlan noData(QueryAction action, QueryPlan source) {
        return new ValidatedPlan(action, null, List.of(), null, false, 0, 0, null, null, source);
    }
}
