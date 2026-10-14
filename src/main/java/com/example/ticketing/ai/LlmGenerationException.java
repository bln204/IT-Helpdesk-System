package com.example.ticketing.ai;

/**
 * Exception for LLM generation failures.
 * 
 * This exception is thrown when:
 * - Ollama is unavailable
 * - Connection fails
 * - Timeout occurs
 * - Invalid response received
 * 
 * The message is user-friendly and does NOT expose internal technical details.
 */
public class LlmGenerationException extends RuntimeException {

    /**
     * Error code for API error responses.
     */
    private final String errorCode;

    /**
     * Create a new exception with a user-friendly message.
     * 
     * @param message User-friendly message (safe to display to API users)
     * @param errorCode Internal error code for logging/debugging
     */
    public LlmGenerationException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Create a new exception with a user-friendly message and cause.
     * 
     * @param message User-friendly message
     * @param errorCode Internal error code
     * @param cause The underlying cause (not exposed to API users)
     */
    public LlmGenerationException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * Get the internal error code.
     * Used for logging and debugging, not exposed to API users.
     */
    public String getErrorCode() {
        return errorCode;
    }

    // Factory methods for common error scenarios

    /**
     * Ollama server is not responding.
     */
    public static LlmGenerationException ollamaUnavailable(Throwable cause) {
        return new LlmGenerationException(
            "AI service temporarily unavailable. Please try again later.",
            "LLM_UNAVAILABLE",
            cause
        );
    }

    /**
     * Request timed out waiting for Ollama response.
     */
    public static LlmGenerationException timeout(Throwable cause) {
        return new LlmGenerationException(
            "AI service took too long to respond. Please try again.",
            "LLM_TIMEOUT",
            cause
        );
    }

    /**
     * Ollama returned a non-2xx HTTP status.
     */
    public static LlmGenerationException nonSuccessfulResponse(int statusCode) {
        return new LlmGenerationException(
            "AI service returned an unexpected response. Please try again.",
            "LLM_ERROR_RESPONSE",
            null
        );
    }

    /**
     * Ollama returned a malformed or unparseable response.
     */
    public static LlmGenerationException malformedResponse(Throwable cause) {
        return new LlmGenerationException(
            "AI service returned an invalid response. Please try again.",
            "LLM_MALFORMED_RESPONSE",
            cause
        );
    }

    /**
     * Ollama returned an empty (no content generated) response. The model may have
     * spent the entire token budget on internal "thinking" / reasoning rather than a
     * visible answer. The user-facing message is identical to {@link #malformedResponse(Throwable)}
     * because both signal "the AI service produced no usable answer".
     */
    public static LlmGenerationException emptyResponse() {
        return new LlmGenerationException(
            "AI service did not generate a response. Please try again.",
            "LLM_EMPTY_RESPONSE",
            null
        );
    }

    // ==================== Embedding errors ====================
    //
    // The embedding path runs BEFORE RAG and BEFORE the LLM call. It has its own
    // structured codes so the existing /api/ai/ask GlobalExceptionHandler mapping
    // (LLM_TIMEOUT -> 504, LLM_UNAVAILABLE -> 503) does not silently reuse chat-timeout
    // codes for what is in fact an embedding-timeout. The two halves of the AI pipeline
    // must be diagnosable separately in logs.

    /**
     * Embedding generation timed out waiting for Ollama /api/embeddings.
     */
    public static LlmGenerationException embeddingTimeout(Throwable cause) {
        return new LlmGenerationException(
            "AI service took too long to prepare the context. Please try again.",
            "EMBEDDING_TIMEOUT",
            cause
        );
    }

    /**
     * Ollama /api/embeddings is unavailable (connection refused, DNS failure, etc.).
     */
    public static LlmGenerationException embeddingUnavailable(Throwable cause) {
        return new LlmGenerationException(
            "AI service is temporarily unavailable. Please try again later.",
            "EMBEDDING_UNAVAILABLE",
            cause
        );
    }

    /**
     * Embedding call returned a non-2xx status or a malformed response body. The
     * user-facing message is sanitized; the cause is logged but never exposed.
     */
    public static LlmGenerationException embeddingMalformedResponse(Throwable cause) {
        return new LlmGenerationException(
            "AI service returned an invalid response while preparing the context. Please try again.",
            "EMBEDDING_MALFORMED_RESPONSE",
            cause
        );
    }
}
