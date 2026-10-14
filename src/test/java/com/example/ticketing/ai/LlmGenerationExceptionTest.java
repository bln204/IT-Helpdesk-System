package com.example.ticketing.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * PHASE 6.1 - Tests for {@link LlmGenerationException} factory methods.
 *
 * <p>The factory methods are the contract that the {@link com.example.ticketing.exception.GlobalExceptionHandler}
 * maps to HTTP status codes. They must therefore keep their message safe for clients
 * (no stack traces / hostnames / paths) and they must keep the structured error code
 * stable so the global handler can route the correct HTTP status.
 *
 * <p>This test only covers the Phase 6.1 additions (embedding-side errors). The chat-side
 * codes (LLM_TIMEOUT, LLM_UNAVAILABLE, LLM_ERROR_RESPONSE, LLM_MALFORMED_RESPONSE,
 * LLM_EMPTY_RESPONSE) are exercised end-to-end in {@link AiAskEndToEndAuthorizationTest}
 * and via the existing {@link OllamaChatServiceTest}.
 */
class LlmGenerationExceptionTest {

    @Test
    @DisplayName("embeddingTimeout() yields code EMBEDDING_TIMEOUT and a safe user message")
    void embeddingTimeoutHasStableCode() {
        Throwable cause = new RuntimeException("internal simulated cause");
        LlmGenerationException ex = LlmGenerationException.embeddingTimeout(cause);

        assertNotNull(ex);
        assertEquals("EMBEDDING_TIMEOUT", ex.getErrorCode());
        assertSame(cause, ex.getCause());
        // The message must be safe for clients.
        assertTrue(ex.getMessage().contains("AI service"),
            () -> "User message should be neutral and safe; was '" + ex.getMessage() + "'");
        // The message must NOT leak hostnames / paths / stack traces.
        assertTrue(!ex.getMessage().contains("localhost"), "no hostnames in message");
        assertTrue(!ex.getMessage().contains("C:\\"), "no Windows paths in message");
        assertTrue(!ex.getMessage().contains("/etc/"), "no unix paths in message");
        assertTrue(!ex.getMessage().toLowerCase().contains("stack"), "no stack-trace mention in message");
    }

    @Test
    @DisplayName("embeddingUnavailable() yields code EMBEDDING_UNAVAILABLE and a safe message")
    void embeddingUnavailableHasStableCode() {
        Throwable cause = new RuntimeException("simulated connection refused");
        LlmGenerationException ex = LlmGenerationException.embeddingUnavailable(cause);

        assertEquals("EMBEDDING_UNAVAILABLE", ex.getErrorCode());
        assertSame(cause, ex.getCause());
        assertTrue(ex.getMessage().contains("unavailable")
            || ex.getMessage().contains("AI service"));
    }

    @Test
    @DisplayName("embeddingMalformedResponse() yields code EMBEDDING_MALFORMED_RESPONSE and a safe message")
    void embeddingMalformedResponseHasStableCode() {
        Throwable cause = new RuntimeException("simulated malformed json");
        LlmGenerationException ex = LlmGenerationException.embeddingMalformedResponse(cause);

        assertEquals("EMBEDDING_MALFORMED_RESPONSE", ex.getErrorCode());
        assertSame(cause, ex.getCause());
        assertTrue(!ex.getMessage().toLowerCase().contains("json"),
            "User message must not mention implementation details like 'json'");
    }

    @Nested
    @DisplayName("Existing factory methods keep their codes (regression guard)")
    class ExistingFactories {

        @Test
        @DisplayName("timeout() keeps code LLM_TIMEOUT")
        void chatTimeoutKeepsCode() {
            LlmGenerationException ex = LlmGenerationException.timeout(new RuntimeException());
            assertEquals("LLM_TIMEOUT", ex.getErrorCode());
        }

        @Test
        @DisplayName("ollamaUnavailable() keeps code LLM_UNAVAILABLE")
        void chatUnavailableKeepsCode() {
            LlmGenerationException ex = LlmGenerationException.ollamaUnavailable(new RuntimeException());
            assertEquals("LLM_UNAVAILABLE", ex.getErrorCode());
        }

        @Test
        @DisplayName("emptyResponse() keeps code LLM_EMPTY_RESPONSE")
        void emptyResponseKeepsCode() {
            LlmGenerationException ex = LlmGenerationException.emptyResponse();
            assertEquals("LLM_EMPTY_RESPONSE", ex.getErrorCode());
        }
    }
}