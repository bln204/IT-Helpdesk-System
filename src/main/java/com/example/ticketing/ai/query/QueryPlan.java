package com.example.ticketing.ai.query;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** Raw Pass-1 output exactly as the LLM produced it. Never trusted: PlanValidator turns it into a ValidatedPlan. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QueryPlan(
        String action,
        String domain,
        List<PlanFilter> filters,
        PlanSort sort,
        Integer limit,
        Integer offset,
        String targetId,
        String clarifyQuestion) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlanFilter(String field, String op, List<String> values) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlanSort(String field, String dir) { }

    public static QueryPlan clarify(String question) {
        return new QueryPlan("clarify", "none", List.of(), null, null, null, null, question);
    }
}
