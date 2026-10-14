package com.example.ticketing.ai.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ticketing.ai.AiAssistantDto;
import com.example.ticketing.ai.LlmGenerationException;
import com.example.ticketing.ai.OllamaProperties;
import com.example.ticketing.ai.TicketDraft;
import com.example.ticketing.ai.session.AiMessage;
import com.example.ticketing.ai.session.AiMessageRole;
import com.example.ticketing.ai.session.AiSessionService;
import com.example.ticketing.auth.UserRole.Role;
import com.example.ticketing.authorization.ActorContext;
import com.example.ticketing.authorization.ActorContextService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AiAssistantPipelineTest {

    private final AiSessionService sessions = mock(AiSessionService.class);
    private final ActorContextService actors = mock(ActorContextService.class);
    private final QueryPlanner planner = mock(QueryPlanner.class);
    private final DomainDef d = StubAdapter.ticketLike();
    private final PlanValidator validator =
            new PlanValidator(new DomainCatalog(List.of(new StubAdapter(d))), java.time.Clock.systemDefaultZone(), 20, 50);
    private final QueryExecutor executor = mock(QueryExecutor.class);
    private final AnswerComposer composer = mock(AnswerComposer.class);
    private final TroubleshootService troubleshoot = mock(TroubleshootService.class);
    private final TicketDraftService drafts = mock(TicketDraftService.class);
    private final OllamaProperties props = mock(OllamaProperties.class);
    private final AiAssistantPipeline pipeline = new AiAssistantPipeline(
            sessions, actors, planner, validator, executor, composer, troubleshoot, drafts, props);

    private final ObjectMapper mapper = new ObjectMapper();
    private final UUID sid = UUID.randomUUID();
    private final ActorContext actor = ActorContext.of("nv", Role.NHAN_VIEN, "MKT", false);
    private final List<AiMessage> recent = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(sessions.getOrCreate(eq("nv"), any(), anyString())).thenReturn(sid);
        when(sessions.recentMessages(eq(sid), anyInt())).thenReturn(recent);
        when(actors.fromUsername("nv")).thenReturn(actor);
        when(props.getChatModel()).thenReturn("qwen3:4b");
        when(composer.compose(anyString(), any(), any())).thenReturn(new AnswerComposer.Composed("dữ liệu", false, false));
    }

    private static AiAssistantDto.AskRequest ask(String q) {
        return new AiAssistantDto.AskRequest(q, 5, 0.65);
    }

    private static AiMessage msg(AiMessageRole role, String content, String meta) {
        AiMessage m = mock(AiMessage.class);
        when(m.getRole()).thenReturn(role);
        when(m.getContent()).thenReturn(content);
        when(m.getMetadata()).thenReturn(meta);
        return m;
    }

    private void lastAssistantFlow(ConversationFlow flow) throws Exception {
        recent.add(msg(AiMessageRole.USER, "câu trước", null));
        recent.add(msg(AiMessageRole.ASSISTANT, "trả lời trước",
                "{\"pipeline\":\"v2\",\"flow\":" + mapper.writeValueAsString(flow) + "}"));
    }

    private void plannerReturns(String action) {
        when(planner.plan(anyString(), any(), anyString()))
                .thenReturn(new QueryPlan(action, "none", List.of(), null, null, null, null, null));
    }

    private String savedAssistantMeta() {
        ArgumentCaptor<String> meta = ArgumentCaptor.forClass(String.class);
        verify(sessions).append(eq(sid), eq("nv"), eq(AiMessageRole.ASSISTANT), anyString(), meta.capture());
        return meta.getValue();
    }

    @Test
    void countQuestionIsAnsweredAndPersistedWithPlan() {
        when(planner.plan(eq("có bao nhiêu ticket chưa gán"), isNull(), eq("none"))).thenReturn(new QueryPlan("count", "ticket",
                List.of(new QueryPlan.PlanFilter("assignee", "is_null", List.of())), null, null, null, null, null));
        when(executor.execute(any(), eq(actor))).thenReturn(QueryResult.count(8, null));

        AiAssistantDto.AnswerResponse r = pipeline.answer(ask("có bao nhiêu ticket chưa gán"), "nv");

        assertThat(r.getAnswer()).isEqualTo("dữ liệu");
        assertThat(r.getMetadata().getSearchType()).isEqualTo("query-plan");
        verify(sessions).append(eq(sid), eq("nv"), eq(AiMessageRole.USER), eq("có bao nhiêu ticket chưa gán"), isNull());
        assertThat(savedAssistantMeta()).contains("\"queryPlan\"").contains("\"limit\":20").doesNotContain("\"flow\"");
    }

    @Test
    void listRowsBecomeRelationalSources() {
        when(planner.plan(anyString(), any(), anyString()))
                .thenReturn(new QueryPlan("list", "ticket", List.of(), null, null, null, null, null));
        when(executor.execute(any(), any())).thenReturn(QueryResult.of(List.of(new Row("TCK-1", "Máy in", Map.of(), null)), 1, 0, null));

        AiAssistantDto.AnswerResponse r = pipeline.answer(ask("ticket"), "nv");

        assertThat(r.getSources()).singleElement().satisfies(s -> {
            assertThat(s.getSourceType()).isEqualTo("RELATIONAL");
            assertThat(s.getTicketNumber()).isEqualTo("TCK-1");
        });
    }

    @Test
    void previousPlanIsReadFromLastAssistantMessage() {
        recent.add(msg(AiMessageRole.ASSISTANT, "x",
                "{\"pipeline\":\"v2\",\"queryPlan\":{\"action\":\"list\",\"domain\":\"ticket\",\"filters\":[],\"limit\":20,\"offset\":0}}"));
        plannerReturns("chitchat");

        pipeline.answer(ask("xem tiếp"), "nv");

        ArgumentCaptor<QueryPlan> prev = ArgumentCaptor.forClass(QueryPlan.class);
        verify(planner).plan(eq("xem tiếp"), prev.capture(), eq("none"));
        assertThat(prev.getValue().domain()).isEqualTo("ticket");
    }

    @Test
    void troubleshootAdvisesAndOpensFlow() {
        plannerReturns("troubleshoot");
        when(troubleshoot.advise(anyString(), anyString(), anyList(), eq("nv"))).thenReturn("Bước 1: kiểm tra Wi-Fi.");

        AiAssistantDto.AnswerResponse r = pipeline.answer(ask("máy không vào mạng"), "nv");

        assertThat(r.getAnswer()).isEqualTo("Bước 1: kiểm tra Wi-Fi.");
        verify(troubleshoot).advise("máy không vào mạng", "máy không vào mạng", recent, "nv");
        assertThat(savedAssistantMeta()).contains("\"kind\":\"TROUBLESHOOT\"").contains("\"problem\":\"máy không vào mạng\"");
    }

    @Test
    void troubleshootFollowUpAccumulatesProblem() throws Exception {
        lastAssistantFlow(ConversationFlow.troubleshoot("máy không vào mạng"));
        plannerReturns("troubleshoot");
        when(troubleshoot.advise(anyString(), anyString(), anyList(), anyString())).thenReturn("Thử tắt IPv6.");

        pipeline.answer(ask("tôi dùng macOS"), "nv");

        verify(planner).plan(eq("tôi dùng macOS"), isNull(), eq("troubleshoot"));
        verify(troubleshoot).advise("tôi dùng macOS", "máy không vào mạng | tôi dùng macOS", recent, "nv");
    }

    @Test
    void problemIsCapped() throws Exception {
        lastAssistantFlow(ConversationFlow.troubleshoot("x".repeat(600)));
        plannerReturns("troubleshoot");
        when(troubleshoot.advise(anyString(), anyString(), anyList(), anyString())).thenReturn("ok");

        pipeline.answer(ask("thêm chi tiết"), "nv");

        ArgumentCaptor<String> problem = ArgumentCaptor.forClass(String.class);
        verify(troubleshoot).advise(anyString(), problem.capture(), anyList(), anyString());
        assertThat(problem.getValue()).hasSizeLessThanOrEqualTo(500);
    }

    @Test
    void resolvedClosesFlowWithoutDraft() throws Exception {
        lastAssistantFlow(ConversationFlow.troubleshoot("máy không vào mạng"));
        plannerReturns("resolved");

        assertThat(pipeline.answer(ask("được rồi cảm ơn"), "nv").getAnswer()).isEqualTo(AiAssistantPipeline.RESOLVED);
        verify(drafts, never()).start(anyList(), anyString(), anyString(), any());
        assertThat(savedAssistantMeta()).doesNotContain("\"flow\"");
    }

    @Test
    void escalateAsksBeforeDrafting() throws Exception {
        lastAssistantFlow(ConversationFlow.troubleshoot("máy không vào mạng"));
        plannerReturns("escalate");

        assertThat(pipeline.answer(ask("vẫn không được"), "nv").getAnswer()).isEqualTo(AiAssistantPipeline.OFFER);
        verify(drafts, never()).start(anyList(), anyString(), anyString(), any());
        assertThat(savedAssistantMeta()).contains("\"kind\":\"OFFER_TICKET\"").contains("máy không vào mạng");
    }

    @Test
    void yesAfterOfferStartsDraft() throws Exception {
        lastAssistantFlow(ConversationFlow.offerTicket("máy không vào mạng"));
        plannerReturns("create_ticket");
        TicketDraft draft = new TicketDraft("Mất mạng", "macOS", "MEDIUM", null);
        when(drafts.start(anyList(), anyString(), anyString(), any())).thenReturn(new TicketDraftService.Result(
                "Vấn đề thuộc loại nào?", "ASK_MISSING_FIELD", "{\"field\":\"category\"}",
                ConversationFlow.drafting("máy không vào mạng", draft, "category")));

        AiAssistantDto.AnswerResponse r = pipeline.answer(ask("có"), "nv");

        verify(drafts).start(recent, "máy không vào mạng", "có", null);
        assertThat(r.getAction()).isEqualTo("ASK_MISSING_FIELD");
        assertThat(r.getActionPayload()).isEqualTo("{\"field\":\"category\"}");
        assertThat(savedAssistantMeta()).contains("\"kind\":\"DRAFTING\"").contains("\"action\":\"ASK_MISSING_FIELD\"");
    }

    @Test
    void noAfterOfferCloses() throws Exception {
        lastAssistantFlow(ConversationFlow.offerTicket("máy không vào mạng"));
        plannerReturns("resolved");
        assertThat(pipeline.answer(ask("không cần"), "nv").getAnswer()).isEqualTo(AiAssistantPipeline.RESOLVED);
    }

    @Test
    void draftingAnswerFillsField() throws Exception {
        ConversationFlow drafting = ConversationFlow.drafting("máy chậm", new TicketDraft("Máy chậm", "Excel", "MEDIUM", null), "category");
        lastAssistantFlow(drafting);
        plannerReturns("chitchat");
        when(drafts.answerField(any(), eq("phần mềm"))).thenReturn(new TicketDraftService.Result(
                TicketDraftService.CONFIRM_TEXT, "CONFIRM_TICKET_DRAFT", "{\"title\":\"Máy chậm\"}",
                ConversationFlow.confirm("máy chậm", new TicketDraft("Máy chậm", "Excel", "MEDIUM", "SOFTWARE"))));

        AiAssistantDto.AnswerResponse r = pipeline.answer(ask("phần mềm"), "nv");

        assertThat(r.getAction()).isEqualTo("CONFIRM_TICKET_DRAFT");
        ArgumentCaptor<ConversationFlow> f = ArgumentCaptor.forClass(ConversationFlow.class);
        verify(drafts).answerField(f.capture(), eq("phần mềm"));
        assertThat(f.getValue().missingField()).isEqualTo("category");
    }

    @Test
    void draftingDataQuestionKeepsDraft() throws Exception {
        lastAssistantFlow(ConversationFlow.drafting("máy chậm", new TicketDraft("Máy chậm", "Excel", "MEDIUM", null), "category"));
        when(planner.plan(anyString(), any(), anyString()))
                .thenReturn(new QueryPlan("count", "ticket", List.of(), null, null, null, null, null));
        when(executor.execute(any(), any())).thenReturn(QueryResult.count(3, null));

        AiAssistantDto.AnswerResponse r = pipeline.answer(ask("có bao nhiêu ticket"), "nv");
        assertThat(r.getAnswer()).startsWith("dữ liệu").endsWith(TicketDraftService.QUESTIONS.get("category"));
        assertThat(r.getAction()).isEqualTo("ASK_MISSING_FIELD");
        assertThat(r.getActionPayload()).isEqualTo("{\"field\":\"category\"}");
        verify(drafts, never()).answerField(any(), anyString());
        assertThat(savedAssistantMeta()).contains("\"kind\":\"DRAFTING\"").contains("\"missingField\":\"category\"");
    }

    @Test
    void ticketCreatedSystemMessageResetsFlow() throws Exception {
        lastAssistantFlow(ConversationFlow.confirm("máy chậm", new TicketDraft("Máy chậm", "Excel", "MEDIUM", "SOFTWARE")));
        recent.add(msg(AiMessageRole.SYSTEM, "Ticket TCK-9 đã được tạo.", "{\"action\":\"TICKET_CREATED\",\"ticketNumber\":\"TCK-9\"}"));
        plannerReturns("chitchat");

        pipeline.answer(ask("cảm ơn"), "nv");

        verify(planner).plan(eq("cảm ơn"), isNull(), eq("none"));
    }

    @Test
    void ollamaDownGivesUnavailable() {
        when(planner.plan(anyString(), any(), anyString()))
                .thenThrow(LlmGenerationException.ollamaUnavailable(new java.net.ConnectException("x")));
        assertThat(pipeline.answer(ask("ticket"), "nv").getAnswer()).isEqualTo(AiAssistantPipeline.UNAVAILABLE);
    }

    @Test
    void ollamaDownWhileDraftingStillFillsField() throws Exception {
        lastAssistantFlow(ConversationFlow.drafting("máy chậm", new TicketDraft("Máy chậm", "Excel", "MEDIUM", null), "category"));
        when(planner.plan(anyString(), any(), anyString()))
                .thenThrow(LlmGenerationException.ollamaUnavailable(new java.net.ConnectException("x")));
        when(drafts.answerField(any(), eq("mạng"))).thenReturn(new TicketDraftService.Result(
                TicketDraftService.CONFIRM_TEXT, "CONFIRM_TICKET_DRAFT", "{}", ConversationFlow.NONE));

        assertThat(pipeline.answer(ask("mạng"), "nv").getAction()).isEqualTo("CONFIRM_TICKET_DRAFT");
    }

    @Test
    void troubleshootOllamaFailureGivesUnavailableAndKeepsFlow() throws Exception {
        lastAssistantFlow(ConversationFlow.troubleshoot("máy không vào mạng"));
        plannerReturns("troubleshoot");
        when(troubleshoot.advise(anyString(), anyString(), anyList(), anyString()))
                .thenThrow(LlmGenerationException.timeout(new RuntimeException("slow")));

        assertThat(pipeline.answer(ask("tôi dùng macOS"), "nv").getAnswer()).isEqualTo(AiAssistantPipeline.UNAVAILABLE);
        assertThat(savedAssistantMeta()).contains("\"kind\":\"TROUBLESHOOT\"");
    }

    @Test
    void blankUserIsRefusedWithoutSession() {
        assertThat(pipeline.answer(ask("ticket"), " ").getAnswer()).contains("đăng nhập");
        verify(sessions, never()).getOrCreate(anyString(), any(), anyString());
    }

    @Test
    void cancellingWhileDraftingDropsTheDraft() throws Exception {
        lastAssistantFlow(ConversationFlow.drafting("máy chậm", new TicketDraft("Máy chậm", "Excel", null, null), "priority"));
        plannerReturns("resolved");

        AiAssistantDto.AnswerResponse r = pipeline.answer(ask("thôi, không cần tạo ticket nữa"), "nv");

        assertThat(r.getAnswer()).isEqualTo(AiAssistantPipeline.DRAFT_CANCELLED);
        verify(drafts, never()).answerField(any(), anyString());
        assertThat(savedAssistantMeta()).doesNotContain("\"flow\"");
    }

    @Test
    void createTicketWhileConfirmingReusesPreviousDraft() throws Exception {
        TicketDraft previous = new TicketDraft("Mất mạng", "macOS", "MEDIUM", "NETWORK");
        lastAssistantFlow(ConversationFlow.confirm("máy không vào mạng", previous));
        plannerReturns("create_ticket");
        when(drafts.start(anyList(), anyString(), anyString(), any())).thenReturn(new TicketDraftService.Result(
                TicketDraftService.CONFIRM_TEXT, "CONFIRM_TICKET_DRAFT", "{}", ConversationFlow.confirm("x", previous)));

        pipeline.answer(ask("tạo lại giúp, mức độ cao"), "nv");

        ArgumentCaptor<TicketDraft> d = ArgumentCaptor.forClass(TicketDraft.class);
        verify(drafts).start(eq(recent), eq("máy không vào mạng"), eq("tạo lại giúp, mức độ cao"), d.capture());
        assertThat(d.getValue().getCategory()).isEqualTo("NETWORK");
    }

    @Test
    void resolvedOrEscalateWithoutOpenFlowIsJustChat() {
        plannerReturns("resolved");
        assertThat(pipeline.answer(ask("ổn rồi cảm ơn"), "nv").getAnswer()).isEqualTo("dữ liệu");
        plannerReturns("escalate");
        assertThat(pipeline.answer(ask("vẫn không được"), "nv").getAnswer()).isEqualTo("dữ liệu");
        verify(drafts, never()).start(anyList(), anyString(), anyString(), any());
    }
}
