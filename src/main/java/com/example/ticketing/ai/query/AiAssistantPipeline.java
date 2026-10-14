package com.example.ticketing.ai.query;

import com.example.ticketing.ai.AiAssistantDto;
import com.example.ticketing.ai.AiAssistantDto.AnswerResponse;
import com.example.ticketing.ai.LlmGenerationException;
import com.example.ticketing.ai.OllamaProperties;
import com.example.ticketing.ai.session.AiMessage;
import com.example.ticketing.ai.session.AiMessageRole;
import com.example.ticketing.ai.session.AiSessionService;
import com.example.ticketing.authorization.ActorContextService;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The only /ask path: plan with the conversation flow, then data lookup,
 * self-help, or ticket drafting.
 */
@Service
public class AiAssistantPipeline {

    private static final Logger log = LoggerFactory.getLogger(AiAssistantPipeline.class);

    public static final String UNAVAILABLE = "Trợ lý AI tạm thời không khả dụng, vui lòng thử lại.";
    static final String LOGIN = "Bạn cần đăng nhập để sử dụng trợ lý AI.";
    static final String OFFER = "Rất tiếc cách trên chưa giải quyết được. Bạn có muốn tôi tạo ticket gửi đội IT không?";
    static final String RESOLVED = "Rất vui vì sự cố đã được giải quyết. Nếu cần hỗ trợ thêm, cứ hỏi tôi nhé.";
    static final String DRAFT_CANCELLED = "Đã hủy bản nháp ticket. Nếu cần hỗ trợ thêm, cứ hỏi tôi nhé.";
    private static final int HISTORY = 12;
    private static final int MAX_PROBLEM = 500;
    private static final Set<QueryAction> DATA_ACTIONS = Set.of(QueryAction.LIST, QueryAction.COUNT, QueryAction.DETAIL,
            QueryAction.EXPLAIN);

    private final AiSessionService sessions;
    private final ActorContextService actors;
    private final QueryPlanner planner;
    private final PlanValidator validator;
    private final QueryExecutor executor;
    private final AnswerComposer composer;
    private final TroubleshootService troubleshoot;
    private final TicketDraftService drafts;
    private final OllamaProperties ollamaProperties;
    private final ObjectMapper mapper = new ObjectMapper().setDefaultPropertyInclusion(JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.USE_DEFAULTS));

    public AiAssistantPipeline(AiSessionService sessions, ActorContextService actors, QueryPlanner planner,
            PlanValidator validator, QueryExecutor executor, AnswerComposer composer,
            TroubleshootService troubleshoot, TicketDraftService drafts,
            OllamaProperties ollamaProperties) {
        this.sessions = sessions;
        this.actors = actors;
        this.planner = planner;
        this.validator = validator;
        this.executor = executor;
        this.composer = composer;
        this.troubleshoot = troubleshoot;
        this.drafts = drafts;
        this.ollamaProperties = ollamaProperties;
    }

    public AnswerResponse answer(AiAssistantDto.AskRequest request, String username) {
        long start = System.currentTimeMillis();
        if (username == null || username.isBlank()) {
            return response(LOGIN, List.of(), null, null, start, null);
        }
        String question = request.getQuestion();
        UUID sessionId = sessions.getOrCreate(username, parseUuid(request.getSessionId()), title(question));
        request.setSessionId(sessionId.toString());

        List<AiMessage> recent = sessions.recentMessages(sessionId, HISTORY);
        JsonNode lastMeta = lastMeta(recent);
        ConversationFlow flow = flowOf(lastMeta);
        QueryPlan previous = previousPlan(lastMeta);

        ValidatedPlan plan;
        try {
            plan = validator.validate(planner.plan(question, previous, flow.label()), question, previous);
        } catch (LlmGenerationException e) {
            log.warn("Planner không gọi được Ollama: {}", e.getMessage());
            // Filling a draft field needs no LLM: keep the user moving even when Ollama is
            // down.
            if (flow.kind() == ConversationFlow.Kind.DRAFTING) {
                return draftTurn(drafts.answerField(flow, question), sessionId, username, question, start);
            }
            return persist(sessionId, username, question,
                    response(UNAVAILABLE, List.of(), null, null, start, sessionId),
                    null, flow);
        }

        try {
            if (flow.kind() == ConversationFlow.Kind.DRAFTING && plan.action() == QueryAction.RESOLVED) {
                return persist(sessionId, username, question,
                        response(DRAFT_CANCELLED, List.of(), null, null, start, sessionId), plan,
                        ConversationFlow.NONE);
            }
            if (flow.kind() == ConversationFlow.Kind.DRAFTING && !isDataQuestion(plan)) {
                return draftTurn(drafts.answerField(flow, question), sessionId, username, question, start);
            }
            switch (plan.action()) {
                case TROUBLESHOOT: {
                    String problem = accumulate(flow.problem(), question);
                    String text = troubleshoot.advise(question, problem, recent, username);
                    return persist(sessionId, username, question,
                            response(text, List.of(), null, null, start, sessionId),
                            plan, ConversationFlow.troubleshoot(problem));
                }
                case ESCALATE:
                    if (flow.kind() == ConversationFlow.Kind.NONE)
                        break; // nothing was suggested yet: just chat
                    return persist(sessionId, username, question,
                            response(OFFER, List.of(), null, null, start, sessionId),
                            plan,
                            ConversationFlow.offerTicket(flow.problem() != null ? flow.problem() : cap(question)));
                case RESOLVED:
                    if (flow.kind() == ConversationFlow.Kind.NONE)
                        break;
                    return persist(sessionId, username, question,
                            response(RESOLVED, List.of(), null, null, start, sessionId),
                            plan, ConversationFlow.NONE);
                case CREATE_TICKET:
                    return draftTurn(
                            drafts.start(recent, flow.problem() != null ? flow.problem() : cap(question), question,
                                    flow.draft()),
                            sessionId, username, question, start);
                default:
                    break;
            }
        } catch (LlmGenerationException e) {
            log.warn("Ollama lỗi khi xử lý luồng {}: {}", flow.label(), e.getMessage());
            return persist(sessionId, username, question,
                    response(UNAVAILABLE, List.of(), null, null, start, sessionId),
                    null, flow);
        }

        QueryResult result = plan.domain() == null ? null : executor.execute(plan, actors.fromUsername(username));
        AnswerComposer.Composed composed = composer.compose(question, plan, result);
        if (composed.groundingFallback())
            log.info("AI grounding fallback session={}", sessionId);
        List<AiAssistantDto.SourceReference> sources = result == null ? List.of() : sources(result);
        // A data question asked mid-flow answers the question and leaves the flow open.
        if (flow.kind() == ConversationFlow.Kind.DRAFTING && flow.missingField() != null) {
            // Re-ask the open field, otherwise the user's next unrelated reply would
            // silently fill it.
            String field = flow.missingField();
            return persist(sessionId, username, question, response(
                    composed.text() + "\n\n" + TicketDraftService.QUESTIONS.get(field), sources,
                    "ASK_MISSING_FIELD", "{\"field\":\"" + field + "\"}", start, sessionId), plan, flow);
        }
        return persist(sessionId, username, question, response(composed.text(), sources, null, null, start, sessionId),
                plan, flow);
    }

    private AnswerResponse draftTurn(TicketDraftService.Result r, UUID sessionId, String username, String question,
            long start) {
        return persist(sessionId, username, question,
                response(r.text(), List.of(), r.action(), r.payloadJson(), start, sessionId), null, r.flow());
    }

    private static boolean isDataQuestion(ValidatedPlan plan) {
        return plan.domain() != null && DATA_ACTIONS.contains(plan.action());
    }

    private static String accumulate(String problem, String question) {
        return cap(problem == null ? question : problem + " | " + question);
    }

    private static String cap(String s) {
        if (s == null)
            return null;
        return s.length() <= MAX_PROBLEM ? s : s.substring(s.length() - MAX_PROBLEM);
    }

    private AnswerResponse persist(UUID sessionId, String username, String question, AnswerResponse resp,
            ValidatedPlan plan, ConversationFlow flow) {
        try {
            sessions.append(sessionId, username, AiMessageRole.USER, question, null);
            sessions.append(sessionId, username, AiMessageRole.ASSISTANT, resp.getAnswer(), meta(plan, flow, resp));
        } catch (RuntimeException e) {
            log.warn("Không lưu được lượt hội thoại {}: {}", sessionId, e.getMessage());
        }
        return resp;
    }

    private String meta(ValidatedPlan plan, ConversationFlow flow, AnswerResponse resp) {
        ObjectNode node = mapper.createObjectNode().put("pipeline", "v2");
        if (plan != null && plan.source() != null && plan.domain() != null) {
            QueryPlan s = plan.source();
            node.set("queryPlan", mapper.valueToTree(new QueryPlan(s.action(), s.domain(), s.filters(), s.sort(),
                    plan.limit(), plan.offset(), s.targetId(), null)));
        }
        if (flow != null && flow.kind() != ConversationFlow.Kind.NONE)
            node.set("flow", mapper.valueToTree(flow));
        if (resp.getAction() != null) {
            node.put("action", resp.getAction());
            node.put("actionPayload", resp.getActionPayload());
        }
        return node.toString();
    }

    /**
     * Metadata of the latest ASSISTANT or SYSTEM message (SYSTEM = ticket confirmed
     * by the button).
     */
    private JsonNode lastMeta(List<AiMessage> recent) {
        for (int i = recent.size() - 1; i >= 0; i--) {
            AiMessage m = recent.get(i);
            if (m.getRole() != AiMessageRole.ASSISTANT && m.getRole() != AiMessageRole.SYSTEM)
                continue;
            if (m.getMetadata() == null || m.getMetadata().isBlank())
                return mapper.createObjectNode();
            try {
                return mapper.readTree(m.getMetadata());
            } catch (Exception e) {
                return mapper.createObjectNode();
            }
        }
        return mapper.createObjectNode();
    }

    private ConversationFlow flowOf(JsonNode meta) {
        if ("TICKET_CREATED".equals(meta.path("action").asText()))
            return ConversationFlow.NONE;
        JsonNode f = meta.path("flow");
        if (f.isMissingNode() || f.isNull())
            return ConversationFlow.NONE;
        try {
            ConversationFlow flow = mapper.treeToValue(f, ConversationFlow.class);
            return flow.kind() == null ? ConversationFlow.NONE : flow;
        } catch (Exception e) {
            return ConversationFlow.NONE;
        }
    }

    private QueryPlan previousPlan(JsonNode meta) {
        JsonNode p = meta.path("queryPlan");
        if (p.isMissingNode() || p.isNull())
            return null;
        try {
            return mapper.treeToValue(p, QueryPlan.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static List<AiAssistantDto.SourceReference> sources(QueryResult result) {
        return result.rows().stream().map(row -> {
            AiAssistantDto.SourceReference s = new AiAssistantDto.SourceReference();
            s.setSourceType("RELATIONAL");
            s.setTicketNumber(row.id());
            s.setTitle(row.title());
            return s;
        }).toList();
    }

    private AnswerResponse response(String text, List<AiAssistantDto.SourceReference> sources, String action,
            String actionPayload, long start, UUID sessionId) {
        return AnswerResponse.builder()
                .answer(text)
                .sources(sources)
                .contextCount(sources.size())
                .hasContext(!sources.isEmpty())
                .action(action)
                .actionPayload(actionPayload)
                .metadata(new AiAssistantDto.ResponseMetadata(System.currentTimeMillis() - start,
                        ollamaProperties.getChatModel(), "query-plan"))
                .sessionId(sessionId == null ? null : sessionId.toString())
                .build();
    }

    private static UUID parseUuid(String s) {
        if (s == null || s.isBlank())
            return null;
        try {
            return UUID.fromString(s.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String title(String question) {
        if (question == null)
            return "Cuộc trò chuyện mới";
        String q = question.strip();
        return q.length() <= 60 ? q : q.substring(0, 60) + "…";
    }
}
