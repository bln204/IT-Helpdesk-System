package com.example.ticketing.ai.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ticketing.ai.EmbeddingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Live accuracy check against the local Ollama. Run: AI_PLANNER_EVAL=true mvn test -Dtest=PlannerEvalTest */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "AI_PLANNER_EVAL", matches = "true")
class PlannerEvalTest {

    @MockitoBean private EmbeddingService embeddingService;
    @Autowired private QueryPlanner planner;
    @Autowired private PlanValidator validator;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void plannerAccuracyIsAtLeast85Percent() throws Exception {
        JsonNode cases;
        try (InputStream in = getClass().getResourceAsStream("/ai-planner-eval.json")) {
            cases = mapper.readTree(in);
        }
        int passed = 0;
        long totalMs = 0;
        List<String> failures = new ArrayList<>();
        for (JsonNode c : cases) {
            QueryPlan previous = c.has("previous") ? mapper.treeToValue(c.get("previous"), QueryPlan.class) : null;
            long t0 = System.currentTimeMillis();
            QueryPlan raw;
            try {
                raw = planner.plan(c.get("q").asText(), previous, c.path("flow").asText("none"));
            } catch (com.example.ticketing.ai.LlmGenerationException e) {
                // A timeout is a planner failure for this question, not a reason to abort the whole eval.
                failures.add(c.get("q").asText() + " -> lỗi LLM: " + e.getMessage()
                        + " (" + (System.currentTimeMillis() - t0) + " ms)");
                continue;
            }
            totalMs += System.currentTimeMillis() - t0;
            ValidatedPlan v = validator.validate(raw, c.get("q").asText());
            String why = mismatch(c, v);
            if (why == null) passed++;
            else failures.add(c.get("q").asText() + " -> " + why + " | raw=" + mapper.writeValueAsString(raw));
        }
        double accuracy = (double) passed / cases.size();
        System.out.printf("PLANNER EVAL: %d/%d = %.1f%%, trung bình %d ms/câu%n",
                passed, cases.size(), accuracy * 100, totalMs / cases.size());
        failures.forEach(f -> System.out.println("  FAIL " + f));
        assertThat(accuracy).as(String.join("\n", failures)).isGreaterThanOrEqualTo(0.85);
    }

    // detail and explain take the same code path (LLM over the rows); eq on one enum value is in.
    private static boolean sameAction(String got, String want) {
        return got.equals(want) || (Set.of("detail", "explain").contains(got) && Set.of("detail", "explain").contains(want));
    }

    // "after today" and "in_range today" select the same rows for creation dates.
    private static boolean sameOp(String got, String want) {
        return got.equals(want)
                || (Set.of("eq", "in").contains(got) && Set.of("eq", "in").contains(want))
                || (Set.of("in_range", "after").contains(got) && Set.of("in_range", "after").contains(want));
    }

    private static String mismatch(JsonNode c, ValidatedPlan v) {
        String action = c.get("action").asText();
        if (!sameAction(v.action().wire(), action)) return "action=" + v.action().wire() + " (cần " + action + ")";
        if (c.has("domain") && (v.domain() == null || !v.domain().name().equals(c.get("domain").asText()))) {
            return "domain=" + (v.domain() == null ? null : v.domain().name());
        }
        if (c.has("targetId") && (v.targetId() == null || !v.targetId().equalsIgnoreCase(c.get("targetId").asText()))) {
            return "targetId=" + v.targetId();
        }
        if (c.has("offset") && v.offset() != c.get("offset").asInt()) return "offset=" + v.offset();
        for (JsonNode ef : c.path("filters")) {
            String field = ef.get("field").asText();
            ResolvedFilter match = v.filters().stream()
                    .filter(f -> f.field().name().equals(field))
                    .filter(f -> !ef.has("op") || sameOp(f.op().wire(), ef.get("op").asText()))
                    .findFirst().orElse(null);
            if (match == null) return "thiếu filter " + field + (ef.has("op") ? ":" + ef.get("op").asText() : "");
            for (JsonNode ev : ef.path("values")) {
                String want = Text.fold(ev.asText()).toLowerCase(Locale.ROOT);
                boolean found = match.values().stream()
                        .map(x -> x instanceof Enum<?> e ? e.name().toLowerCase(Locale.ROOT)
                                : Text.fold(String.valueOf(x)).toLowerCase(Locale.ROOT))
                        .anyMatch(x -> x.contains(want));
                if (!found) return "filter " + field + " thiếu giá trị " + ev.asText() + " (có " + match.values() + ")";
            }
        }
        if (c.path("noOtherFilters").asBoolean(false) && v.filters().size() > c.path("filters").size()) {
            return "tự thêm điều kiện: " + v.filters().stream().map(f -> f.field().name()).toList();
        }
        return null;
    }
}
