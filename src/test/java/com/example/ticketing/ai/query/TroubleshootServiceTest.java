package com.example.ticketing.ai.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ticketing.ai.KnownErrorRagRetrievalService;
import com.example.ticketing.ai.LlmGenerationException;
import com.example.ticketing.ai.OllamaChatService;
import com.example.ticketing.ai.OllamaChatService.ChatMessage;
import com.example.ticketing.ai.OllamaChatService.ChatOptions;
import com.example.ticketing.ai.RagContextDto;
import com.example.ticketing.ai.session.AiMessage;
import com.example.ticketing.ai.session.AiMessageRole;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TroubleshootServiceTest {

    private final OllamaChatService ollama = mock(OllamaChatService.class);
    private final KnownErrorRagRetrievalService knownErrors = mock(KnownErrorRagRetrievalService.class);
    private final TroubleshootService service = new TroubleshootService(ollama, knownErrors, new GroundingChecker(), 60, 0.65);

    private void knownError(String code, String title, String content) {
        RagContextDto.RagSource s = new RagContextDto.RagSource();
        s.setSourceRef(code);
        s.setTitle(title);
        s.setContent(content);
        RagContextDto.RetrievalResponse r = new RagContextDto.RetrievalResponse();
        r.setStatus("success");
        r.setSources(List.of(s));
        when(knownErrors.retrieveContext(any(), anyString())).thenReturn(r);
    }

    private void noKnownError() {
        RagContextDto.RetrievalResponse r = new RagContextDto.RetrievalResponse();
        r.setStatus("no_results");
        when(knownErrors.retrieveContext(any(), anyString())).thenReturn(r);
    }

    private static AiMessage msg(AiMessageRole role, String content) {
        AiMessage m = mock(AiMessage.class);
        when(m.getRole()).thenReturn(role);
        when(m.getContent()).thenReturn(content);
        return m;
    }

    @SuppressWarnings("unchecked")
    private List<ChatMessage> lastSent() {
        ArgumentCaptor<List<ChatMessage>> c = ArgumentCaptor.forClass(List.class);
        verify(ollama, org.mockito.Mockito.atLeastOnce()).generate(c.capture(), any(ChatOptions.class));
        return c.getValue();
    }

    @Test
    void knownErrorIsGivenToLlmAndFooterAdded() {
        knownError("KE-001", "Mất mạng sau khi restart", "Cách khắc phục tạm: tắt IPv6");
        when(ollama.generate(anyList(), any(ChatOptions.class)))
                .thenReturn("Cách khắc phục đã ghi nhận [KE-001]: tắt IPv6.");

        String a = service.advise("máy không vào mạng", "máy không vào mạng", List.of(), "nv");

        assertThat(a).contains("[KE-001]").endsWith(TroubleshootService.FOOTER);
        List<ChatMessage> sent = lastSent();
        assertThat(sent.get(sent.size() - 1).content()).contains("<KNOWN_ERRORS>").contains("tắt IPv6");
    }

    @Test
    void noKnownErrorStillAdvises() {
        noKnownError();
        when(ollama.generate(anyList(), any(ChatOptions.class))).thenReturn("1. Kiểm tra Wi-Fi.");
        String a = service.advise("máy không vào mạng", "máy không vào mạng", List.of(), "nv");
        assertThat(a).isEqualTo("1. Kiểm tra Wi-Fi.\n\n" + TroubleshootService.FOOTER);
        assertThat(lastSent().get(lastSent().size() - 1).content()).doesNotContain("<KNOWN_ERRORS>");
    }

    @Test
    void knownErrorRetrievalFailureStillAdvises() {
        when(knownErrors.retrieveContext(any(), anyString())).thenThrow(new IllegalStateException("embedding down"));
        when(ollama.generate(anyList(), any(ChatOptions.class))).thenReturn("1. Kiểm tra Wi-Fi.");
        assertThat(service.advise("máy không vào mạng", "máy không vào mạng", List.of(), "nv"))
                .startsWith("1. Kiểm tra Wi-Fi.");
    }

    @Test
    void historyIsSentInOrderWithoutSystemMessages() {
        noKnownError();
        when(ollama.generate(anyList(), any(ChatOptions.class))).thenReturn("Thử tắt IPv6.");
        service.advise("tôi dùng macOS", "máy không vào mạng | tôi dùng macOS", List.of(
                msg(AiMessageRole.USER, "máy không vào mạng"),
                msg(AiMessageRole.ASSISTANT, "Bạn dùng hệ điều hành gì?"),
                msg(AiMessageRole.SYSTEM, "Ticket TCK-1 đã được tạo.")), "nv");
        List<ChatMessage> sent = lastSent();
        assertThat(sent).extracting(ChatMessage::role).containsExactly("system", "user", "assistant", "user");
        assertThat(sent.get(1).content()).isEqualTo("máy không vào mạng");
        assertThat(sent.get(3).content()).endsWith("Người dùng: tôi dùng macOS");
    }

    @Test
    void inventedCodeIsRetriedOnce() {
        noKnownError();
        when(ollama.generate(anyList(), any(ChatOptions.class))).thenReturn("Xem TCK-2026-0099.", "Khởi động lại modem.");
        String a = service.advise("mất mạng", "mất mạng", List.of(), "nv");
        assertThat(a).startsWith("Khởi động lại modem.");
        verify(ollama, times(2)).generate(anyList(), any(ChatOptions.class));
    }

    @Test
    void inventedCodeTwiceFallsBackToKnownErrors() {
        knownError("KE-001", "Mất mạng sau khi restart", "Cách khắc phục tạm: tắt IPv6");
        when(ollama.generate(anyList(), any(ChatOptions.class))).thenReturn("Xem TCK-2026-0099.");
        String a = service.advise("mất mạng", "mất mạng", List.of(), "nv");
        assertThat(a).startsWith("Hệ thống đã ghi nhận cách khắc phục sau").contains("[KE-001]").doesNotContain("TCK-2026-0099");
    }

    @Test
    void inventedCodeTwiceWithoutKnownErrorsAsksForMoreDetail() {
        noKnownError();
        when(ollama.generate(anyList(), any(ChatOptions.class))).thenReturn("Xem TCK-2026-0099.");
        assertThat(service.advise("mất mạng", "mất mạng", List.of(), "nv")).isEqualTo(TroubleshootService.ASK_MORE);
    }

    @Test
    void ollamaFailurePropagates() {
        noKnownError();
        when(ollama.generate(anyList(), any(ChatOptions.class)))
                .thenThrow(LlmGenerationException.ollamaUnavailable(new java.net.ConnectException("down")));
        assertThatThrownBy(() -> service.advise("x", "x", List.of(), "nv")).isInstanceOf(LlmGenerationException.class);
    }

    @Test
    void codesAlreadyShownInConversationAreAllowed() {
        noKnownError();
        when(ollama.generate(anyList(), any(ChatOptions.class))).thenReturn("Như [KE-001] đã nêu, hãy tắt IPv6.");
        String a = service.advise("tôi dùng macOS", "mất mạng | tôi dùng macOS", List.of(
                msg(AiMessageRole.USER, "mất mạng"),
                msg(AiMessageRole.ASSISTANT, "Cách khắc phục đã ghi nhận [KE-001] DNS Resolution Failure")), "nv");
        assertThat(a).startsWith("Như [KE-001] đã nêu");
        verify(ollama, times(1)).generate(anyList(), any(ChatOptions.class));
    }
}
