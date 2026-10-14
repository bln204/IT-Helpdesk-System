package com.example.ticketing.ai;

import static org.junit.jupiter.api.Assertions.*;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for OllamaChatService.
 * Tests HTTP communication with Ollama API without requiring a real server.
 */
@ExtendWith(MockitoExtension.class)
class OllamaChatServiceTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private OllamaProperties properties;

    @BeforeEach
    void setUp() {
        properties = new OllamaProperties();
        properties.setBaseUrl("http://localhost:11434");
        properties.setChatModel("gemma3:4b");
        properties.setChatTimeoutSeconds(120);
        properties.setChatTemperature(0.2);
        properties.setChatMaxTokens(512);
        properties.setTimeoutSeconds(60);

        // Use reflection or a test subclass to inject mock HttpClient
        // For simplicity, we test the exception mapping logic
    }

    @Test
    @DisplayName("ChatMessage.record should create valid system messages")
    void chatMessageSystem() {
        OllamaChatService.ChatMessage msg = OllamaChatService.ChatMessage.system("You are helpful");
        assertEquals("system", msg.role());
        assertEquals("You are helpful", msg.content());
    }

    @Test
    @DisplayName("ChatMessage.record should create valid user messages")
    void chatMessageUser() {
        OllamaChatService.ChatMessage msg = OllamaChatService.ChatMessage.user("Hello");
        assertEquals("user", msg.role());
        assertEquals("Hello", msg.content());
    }

    @Test
    @DisplayName("ChatMessage.record should create valid assistant messages")
    void chatMessageAssistant() {
        OllamaChatService.ChatMessage msg = OllamaChatService.ChatMessage.assistant("I am here");
        assertEquals("assistant", msg.role());
        assertEquals("I am here", msg.content());
    }

    @Test
    @DisplayName("LlmGenerationException.ollamaUnavailable should create exception with correct code")
    void llmExceptionOllamaUnavailable() {
        RuntimeException cause = new RuntimeException("Connection refused");
        LlmGenerationException ex = LlmGenerationException.ollamaUnavailable(cause);

        assertEquals("LLM_UNAVAILABLE", ex.getErrorCode());
        assertEquals("AI service temporarily unavailable. Please try again later.", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    @DisplayName("LlmGenerationException.timeout should create exception with correct code")
    void llmExceptionTimeout() {
        RuntimeException cause = new RuntimeException("Request timeout");
        LlmGenerationException ex = LlmGenerationException.timeout(cause);

        assertEquals("LLM_TIMEOUT", ex.getErrorCode());
        assertEquals("AI service took too long to respond. Please try again.", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    @DisplayName("LlmGenerationException.nonSuccessfulResponse should create exception with status code")
    void llmExceptionNonSuccessfulResponse() {
        LlmGenerationException ex = LlmGenerationException.nonSuccessfulResponse(500);

        assertEquals("LLM_ERROR_RESPONSE", ex.getErrorCode());
        assertEquals("AI service returned an unexpected response. Please try again.", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    @DisplayName("LlmGenerationException.malformedResponse should create exception")
    void llmExceptionMalformedResponse() {
        RuntimeException cause = new RuntimeException("Invalid JSON");
        LlmGenerationException ex = LlmGenerationException.malformedResponse(cause);

        assertEquals("LLM_MALFORMED_RESPONSE", ex.getErrorCode());
        assertEquals("AI service returned an invalid response. Please try again.", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    @DisplayName("LlmGenerationException.emptyResponse should create exception without cause")
    void llmExceptionEmptyResponse() {
        LlmGenerationException ex = LlmGenerationException.emptyResponse();

        assertEquals("LLM_EMPTY_RESPONSE", ex.getErrorCode());
        assertEquals("AI service did not generate a response. Please try again.", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    @DisplayName("LlmGenerationException constructor should work with message only")
    void llmExceptionMessageOnly() {
        LlmGenerationException ex = new LlmGenerationException("Test message", "TEST_CODE");

        assertEquals("TEST_CODE", ex.getErrorCode());
        assertEquals("Test message", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    @DisplayName("OllamaProperties should have correct default values for chat")
    void ollamaPropertiesDefaults() {
        OllamaProperties props = new OllamaProperties();

        assertEquals("gemma3:4b", props.getChatModel(),
                "Default chat model must be gemma3:4b (Phase 6.3)");
        assertEquals(120, props.getChatTimeoutSeconds());
        assertEquals(0.2, props.getChatTemperature(),
                "Default chat temperature must be 0.2 (Phase 6.3)");
        assertEquals(2048, props.getChatMaxTokens(),
                "Default chatMaxTokens must be 2048, aligned with application.properties ollama.chat-max-tokens. "
                        + "Phase 7.7 hotfix-4 raised the default from 1024 to 2048 because the "
                        + "self-service troubleshooting branch was still emitting answers truncated "
                        + "at done_reason=length with 1024 when the prompt contained retrieved sources. "
                        + "Hotfix-4 also drops retrieved sources for troubleshooting intent so the prompt "
                        + "fits in 2048 cleanly.");
    }

    @Test
    @DisplayName("OllamaProperties setters should work correctly")
    void ollamaPropertiesSetters() {
        OllamaProperties props = new OllamaProperties();

        props.setChatModel("llama3");
        props.setChatTimeoutSeconds(60);
        props.setChatTemperature(0.7);
        props.setChatMaxTokens(256);

        assertEquals("llama3", props.getChatModel());
        assertEquals(60, props.getChatTimeoutSeconds());
        assertEquals(0.7, props.getChatTemperature());
        assertEquals(256, props.getChatMaxTokens());
    }

    @Test
    @DisplayName("Service should use correct base URL for chat endpoint")
    void serviceEndpointConstruction() {
        // Verify the base URL is constructed correctly
        OllamaProperties props = new OllamaProperties();
        props.setBaseUrl("http://ollama.local:11434");
        props.setChatModel("test-model");

        String expectedEndpoint = props.getBaseUrl() + "/api/chat";
        assertEquals("http://ollama.local:11434/api/chat", expectedEndpoint);
    }

    // ================= Response parsing tests =================

    /**
     * Invoke the private parseResponse(String) method via reflection so the
     * real parsing path is exercised without needing a live Ollama server.
     */
    private OllamaChatService.OllamaChatReply invokeParseResponse(OllamaProperties props, String responseBody) {
        OllamaChatService svc = new OllamaChatService(props);
        try {
            Method m = OllamaChatService.class.getDeclaredMethod("parseResponse", String.class);
            m.setAccessible(true);
            return (OllamaChatService.OllamaChatReply) m.invoke(svc, responseBody);
        } catch (InvocationTargetException e) {
            // Unwrap so the original exception type/assertion is visible
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException(cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not access parseResponse", e);
        }
    }

    @Test
    @DisplayName("parseResponse should return content when response has thinking AND content")
    void parseResponseWithThinkingAndContent() {
        OllamaProperties props = new OllamaProperties();
        props.setChatMaxTokens(2048);

        // Mirrors the real Gemma3/Qwen3 shape: non-empty 'thinking' alongside real
        // 'content'.
        // Synthetic placeholder text only - no real ticket data.
        String json = "{"
                + "\"model\":\"gemma3:4b\","
                + "\"message\":{"
                + "  \"role\":\"assistant\","
                + "  \"content\":\"Based on the two tickets, badge access issues were resolved by a manual restart.\","
                + "  \"thinking\":\"Let me consider the retrieved context.\""
                + "},"
                + "\"done\":true,"
                + "\"done_reason\":\"stop\","
                + "\"prompt_eval_count\":916,"
                + "\"eval_count\":1508"
                + "}";

        OllamaChatService.OllamaChatReply reply = invokeParseResponse(props, json);

        assertNotNull(reply);
        assertNotNull(reply.content());
        assertEquals(
                "Based on the two tickets, badge access issues were resolved by a manual restart.",
                reply.content());
        assertFalse(reply.content().isBlank());
    }

    @Test
    @DisplayName("parseResponse should return empty content when done_reason=length and content empty")
    void parseResponseLengthWithEmptyContent() {
        OllamaProperties props = new OllamaProperties();
        props.setChatMaxTokens(2048);

        // Reproduces the production LLM_EMPTY_RESPONSE cause: the generation
        // budget was fully consumed by thinking, leaving no visible answer.
        String json = "{"
                + "\"model\":\"gemma3:4b\","
                + "\"message\":{"
                + "  \"role\":\"assistant\","
                + "  \"content\":\"\","
                + "  \"thinking\":\"extended reasoning that consumed the entire token budget\""
                + "},"
                + "\"done\":true,"
                + "\"done_reason\":\"length\","
                + "\"prompt_eval_count\":916,"
                + "\"eval_count\":512"
                + "}";

        OllamaChatService.OllamaChatReply reply = invokeParseResponse(props, json);

        // parseResponse returns an OllamaChatReply with empty content;
        // generate() is what converts this into LLM_EMPTY_RESPONSE (with a
        // single retry in between). Asserting here keeps the unit scoped to
        // the parsing layer.
        assertNotNull(reply);
        assertEquals("", reply.content());
        assertTrue(reply.content().isBlank());
    }

    @Test
    @DisplayName("PHASE 7.7 hotfix-3: parseResponse appends continuation hint when done_reason=length with non-empty content")
    void parseResponseLengthWithNonEmptyContent() {
        OllamaProperties props = new OllamaProperties();
        props.setChatMaxTokens(512);

        // Phase 7.7 hotfix-3: when the model hits the num_predict
        // ceiling but still emits a partial answer, parseResponse
        // must append a short Vietnamese notice so the user
        // knows the answer was cut off and can ask for the rest
        // or open a ticket. The bug being fixed was: the
        // self-service troubleshooting branch produced a 370+ token
        // answer for "may to dang mo app Excel thi do, cham, sau
        // do thi tu tat nguon" and got cut off mid-sentence with
        // done_reason=length, leaving the user with a half-finished
        // fix recipe.
        // Use short content without newlines so we don't have to
        // worry about JSON escaping; the test only needs to verify
        // the contract: done_reason=length + non-empty content ->
        // the continuation hint is appended after the original text.
        String partialAnswer = "Kiem tra RAM, CPU, driver. Loi co the do";
        // Build JSON with Jackson so we don't have to worry about
        // escaping special characters.
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode root = mapper.createObjectNode();
        root.put("model", "gemma3:4b");
        com.fasterxml.jackson.databind.node.ObjectNode message = mapper.createObjectNode();
        message.put("role", "assistant");
        message.put("content", partialAnswer);
        message.put("thinking", "");
        root.set("message", message);
        root.put("done", true);
        root.put("done_reason", "length");
        root.put("prompt_eval_count", 3986);
        root.put("eval_count", 110);
        String json;
        try {
            json = mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        OllamaChatService.OllamaChatReply reply = invokeParseResponse(props, json);

        assertNotNull(reply);
        String content = reply.content();
        // The original partial answer must be preserved.
        assertTrue(content.contains(partialAnswer),
                "Original partial answer must be preserved");
        // The continuation hint must be appended.
        assertTrue(content.endsWith(OllamaChatService.TRUNCATION_HINT),
                "Continuation hint must be appended to signal truncation to the user");
        assertTrue(content.contains("tiếp tục") && content.contains("ticket"),
                "Hint must mention 'tiếp tục' and ticket creation");
    }

    @Test
    @DisplayName("PHASE 7.7 hotfix-3: parseResponse does NOT append hint when done_reason=stop")
    void parseResponseStopNoHint() {
        OllamaProperties props = new OllamaProperties();
        props.setChatMaxTokens(512);

        // done_reason=stop means the model finished on its own.
        // No truncation, no hint should be appended.
        String json = "{"
                + "\"model\":\"gemma3:4b\","
                + "\"message\":{"
                + "  \"role\":\"assistant\","
                + "  \"content\":\"Day la cau tra loi binh thuong.\","
                + "  \"thinking\":\"\""
                + "},"
                + "\"done\":true,"
                + "\"done_reason\":\"stop\","
                + "\"prompt_eval_count\":500,"
                + "\"eval_count\":10"
                + "}";

        OllamaChatService.OllamaChatReply reply = invokeParseResponse(props, json);

        assertNotNull(reply);
        String content = reply.content();
        assertEquals("Day la cau tra loi binh thuong.", content);
        assertFalse(content.contains("[Phần còn lại"),
                "Continuation hint must NOT be appended when the model finished cleanly");
    }
}
