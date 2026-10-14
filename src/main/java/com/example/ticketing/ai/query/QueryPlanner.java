package com.example.ticketing.ai.query;

import com.example.ticketing.ai.OllamaChatService;
import com.example.ticketing.ai.OllamaChatService.ChatMessage;
import com.example.ticketing.ai.OllamaChatService.ChatOptions;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Pass 1: question (+ previous plan) -> QueryPlan. Throws LlmGenerationException when Ollama is down. */
@Service
public class QueryPlanner {

    private static final Logger log = LoggerFactory.getLogger(QueryPlanner.class);

    private final OllamaChatService ollama;
    private final DomainCatalog catalog;
    /** A plan is a few hundred tokens; the cap stops a looping generation from running to the timeout. */
    static final int MAX_PLAN_TOKENS = 256;

    private final int timeoutSeconds;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setDefaultPropertyInclusion(JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.USE_DEFAULTS));

    public QueryPlanner(OllamaChatService ollama, DomainCatalog catalog,
                        @Value("${ai.planner.timeout-seconds:30}") int timeoutSeconds) {
        this.ollama = ollama;
        this.catalog = catalog;
        this.timeoutSeconds = timeoutSeconds;
    }

    public QueryPlan plan(String question, QueryPlan previous) {
        return plan(question, previous, "none");
    }

    public QueryPlan plan(String question, QueryPlan previous, String flowLabel) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(PlannerPrompt.system(catalog.promptText())));
        for (PlannerPrompt.Example ex : PlannerPrompt.EXAMPLES) {
            messages.add(ChatMessage.user(ex.input()));
            messages.add(ChatMessage.assistant(ex.output()));
        }
        messages.add(ChatMessage.user(PlannerPrompt.userTurn(question, toJson(previous), flowLabel)));

        Map<String, Object> schema = QueryPlanSchema.build(catalog.domainNames(), catalog.fieldNames());
        ChatOptions options = new ChatOptions(schema, 0.0, timeoutSeconds, MAX_PLAN_TOKENS);
        String raw = ollama.generate(messages, options);
        try {
            return mapper.readValue(raw, QueryPlan.class);
        } catch (JsonProcessingException first) {
            log.warn("Planner trả JSON không hợp lệ, thử lại 1 lần: {}", first.getOriginalMessage());
            messages.add(ChatMessage.assistant(raw));
            messages.add(ChatMessage.user("JSON vừa rồi không hợp lệ (" + first.getOriginalMessage()
                    + "). Trả lại đúng một object JSON theo schema."));
            String retry = ollama.generate(messages, options);
            try {
                return mapper.readValue(retry, QueryPlan.class);
            } catch (JsonProcessingException second) {
                log.warn("Planner vẫn trả JSON không hợp lệ, chuyển sang clarify");
                return QueryPlan.clarify(null);
            }
        }
    }

    String toJson(QueryPlan plan) {
        if (plan == null) return null;
        try {
            return mapper.writeValueAsString(plan);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
