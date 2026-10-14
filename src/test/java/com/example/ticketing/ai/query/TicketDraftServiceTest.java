package com.example.ticketing.ai.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ticketing.ai.OllamaChatService;
import com.example.ticketing.ai.OllamaChatService.ChatMessage;
import com.example.ticketing.ai.OllamaChatService.ChatOptions;
import com.example.ticketing.ai.TicketDraft;
import com.example.ticketing.ai.session.AiMessage;
import com.example.ticketing.ai.session.AiMessageRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TicketDraftServiceTest {

    private final OllamaChatService ollama = mock(OllamaChatService.class);
    private final TicketDraftService service = new TicketDraftService(ollama, 60);
    private final ObjectMapper mapper = new ObjectMapper();

    private static AiMessage msg(AiMessageRole role, String content) {
        AiMessage m = mock(AiMessage.class);
        when(m.getRole()).thenReturn(role);
        when(m.getContent()).thenReturn(content);
        return m;
    }

    @Test
    void completeDraftGivesConfirmPayload() throws Exception {
        when(ollama.generate(anyList(), any(ChatOptions.class))).thenReturn(
                "{\"title\":\"Máy không truy cập được internet\",\"description\":\"macOS, lỗi sau khi restart\","
                        + "\"priority\":\"medium\",\"category\":\"NETWORK\"}");

        TicketDraftService.Result r = service.start(List.of(
                msg(AiMessageRole.USER, "máy không truy cập được internet"),
                msg(AiMessageRole.ASSISTANT, "Bạn dùng hệ điều hành gì?")), "máy không truy cập được internet", "có", null);

        assertThat(r.action()).isEqualTo("CONFIRM_TICKET_DRAFT");
        assertThat(r.text()).isEqualTo(TicketDraftService.CONFIRM_TEXT);
        JsonNode p = mapper.readTree(r.payloadJson());
        assertThat(p.get("priority").asText()).isEqualTo("MEDIUM");
        assertThat(p.get("category").asText()).isEqualTo("NETWORK");
        assertThat(p.get("title").asText()).isEqualTo("Máy không truy cập được internet");
        assertThat(r.flow().kind()).isEqualTo(ConversationFlow.Kind.CONFIRM);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> msgs = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<ChatOptions> opts = ArgumentCaptor.forClass(ChatOptions.class);
        verify(ollama).generate(msgs.capture(), opts.capture());
        assertThat(opts.getValue().format()).isNotNull();
        assertThat(msgs.getValue().get(1).content()).contains("Sự cố: máy không truy cập được internet")
                .contains("Trợ lý: Bạn dùng hệ điều hành gì?").endsWith("Người dùng: có");
    }

    @Test
    void missingCategoryAsksForIt() throws Exception {
        when(ollama.generate(anyList(), any(ChatOptions.class))).thenReturn(
                "{\"title\":\"Máy chậm\",\"description\":\"Máy chậm khi mở Excel\",\"priority\":\"MEDIUM\",\"category\":\"\"}");
        TicketDraftService.Result r = service.start(List.of(), "máy chậm", "tạo ticket máy chậm", null);
        assertThat(r.action()).isEqualTo("ASK_MISSING_FIELD");
        assertThat(mapper.readTree(r.payloadJson()).get("field").asText()).isEqualTo("category");
        assertThat(r.text()).isEqualTo(TicketDraftService.QUESTIONS.get("category"));
        assertThat(r.flow().kind()).isEqualTo(ConversationFlow.Kind.DRAFTING);
        assertThat(r.flow().missingField()).isEqualTo("category");
    }

    @Test
    void unparseableLlmOutputAsksForTitle() {
        when(ollama.generate(anyList(), any(ChatOptions.class))).thenReturn("không phải json");
        TicketDraftService.Result r = service.start(List.of(), "x", "tạo ticket", null);
        assertThat(r.flow().missingField()).isEqualTo("title");
    }

    @Test
    void vietnameseCategoryAnswerCompletesDraft() throws Exception {
        TicketDraft d = new TicketDraft("Máy chậm", "Chậm khi mở Excel", "MEDIUM", null);
        TicketDraftService.Result r = service.answerField(ConversationFlow.drafting("máy chậm", d, "category"), "lỗi mạng");
        assertThat(r.action()).isEqualTo("CONFIRM_TICKET_DRAFT");
        assertThat(mapper.readTree(r.payloadJson()).get("category").asText()).isEqualTo("NETWORK");
    }

    @Test
    void vietnamesePriorityAnswerIsMapped() throws Exception {
        TicketDraft d = new TicketDraft("Máy chậm", "Chậm khi mở Excel", null, "SOFTWARE");
        TicketDraftService.Result r = service.answerField(ConversationFlow.drafting("máy chậm", d, "priority"), "khẩn cấp");
        assertThat(mapper.readTree(r.payloadJson()).get("priority").asText()).isEqualTo("URGENT");
    }

    @Test
    void invalidAnswerReasksSameField() {
        TicketDraft d = new TicketDraft("Máy chậm", "Chậm khi mở Excel", null, "SOFTWARE");
        TicketDraftService.Result r = service.answerField(ConversationFlow.drafting("máy chậm", d, "priority"), "abc");
        assertThat(r.action()).isEqualTo("ASK_MISSING_FIELD");
        assertThat(r.flow().missingField()).isEqualTo("priority");
    }

    @Test
    void titleAnswerKeepsOtherFields() throws Exception {
        TicketDraft d = new TicketDraft(null, "Chậm khi mở Excel", "LOW", "SOFTWARE");
        TicketDraftService.Result r = service.answerField(ConversationFlow.drafting("máy chậm", d, "title"), "Excel rất chậm");
        JsonNode p = mapper.readTree(r.payloadJson());
        assertThat(p.get("title").asText()).isEqualTo("Excel rất chậm");
        assertThat(p.get("priority").asText()).isEqualTo("LOW");
    }

    private String priorityFor(String answer) throws Exception {
        TicketDraft d = new TicketDraft("Máy chậm", "Chậm khi mở Excel", null, "SOFTWARE");
        TicketDraftService.Result r = service.answerField(ConversationFlow.drafting("máy chậm", d, "priority"), answer);
        return "ASK_MISSING_FIELD".equals(r.action()) ? "ASK" : mapper.readTree(r.payloadJson()).get("priority").asText();
    }

    @Test
    void negatedPriorityWordsAreNotUpgraded() throws Exception {
        assertThat(priorityFor("trung bình thôi, không gấp")).isEqualTo("MEDIUM");
        assertThat(priorityFor("thấp, không khẩn")).isEqualTo("LOW");
        assertThat(priorityFor("không gấp lắm")).isEqualTo("ASK");
        assertThat(priorityFor("không cao")).isEqualTo("ASK");
        assertThat(priorityFor("rất gấp")).isEqualTo("CRITICAL");
        assertThat(priorityFor("cao")).isEqualTo("HIGH");
    }

    @Test
    void previousDraftFillsFieldsTheNewExtractionMissed() throws Exception {
        when(ollama.generate(anyList(), any(ChatOptions.class))).thenReturn(
                "{\"title\":\"Mất mạng\",\"description\":\"macOS sau restart\",\"priority\":\"HIGH\",\"category\":\"\"}");
        TicketDraft previous = new TicketDraft("Cũ", "Mô tả cũ", "LOW", "NETWORK");

        TicketDraftService.Result r = service.start(List.of(), "mất mạng", "tạo lại, mức độ cao", previous);

        JsonNode p = mapper.readTree(r.payloadJson());
        assertThat(p.get("category").asText()).isEqualTo("NETWORK");
        assertThat(p.get("priority").asText()).isEqualTo("HIGH");
        assertThat(p.get("title").asText()).isEqualTo("Mất mạng");
    }
}
