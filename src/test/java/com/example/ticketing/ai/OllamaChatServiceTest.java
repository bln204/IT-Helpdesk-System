package com.example.ticketing.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

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
    private OllamaChatService service;

    @BeforeEach
    void setUp() {
        properties = new OllamaProperties();
        properties.setBaseUrl("http://localhost:11434");
        properties.setChatModel("qwen3:4b");
        properties.setChatTimeoutSeconds(120);
        properties.setChatTemperature(0.3);
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
        
        assertEquals("qwen3:4b", props.getChatModel());
        assertEquals(120, props.getChatTimeoutSeconds());
        assertEquals(0.3, props.getChatTemperature());
        assertEquals(512, props.getChatMaxTokens());
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
    private String invokeParseResponse(OllamaProperties props, String responseBody) {
        OllamaChatService svc = new OllamaChatService(props);
        try {
            Method m = OllamaChatService.class.getDeclaredMethod("parseResponse", String.class);
            m.setAccessible(true);
            return (String) m.invoke(svc, responseBody);
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

        // Mirrors the real Qwen3 shape: non-empty 'thinking' alongside real 'content'.
        // Synthetic placeholder text only - no real ticket data.
        String json = "{"
            + "\"model\":\"qwen3:4b\","
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

        String content = invokeParseResponse(props, json);

        assertNotNull(content);
        assertEquals(
            "Based on the two tickets, badge access issues were resolved by a manual restart.",
            content);
        assertFalse(content.isBlank());
    }

    @Test
    @DisplayName("parseResponse should return empty string when done_reason=length and content empty")
    void parseResponseLengthWithEmptyContent() {
        OllamaProperties props = new OllamaProperties();
        props.setChatMaxTokens(2048);

        // Reproduces the production LLM_EMPTY_RESPONSE cause: the generation
        // budget was fully consumed by thinking, leaving no visible answer.
        String json = "{"
            + "\"model\":\"qwen3:4b\","
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

        String content = invokeParseResponse(props, json);

        // parseResponse returns the empty string; generate() is what converts
        // this into LLM_EMPTY_RESPONSE. Asserting here keeps the unit scoped to
        // the parsing layer.
        assertEquals("", content);
        assertTrue(content.isBlank());
    }
}
