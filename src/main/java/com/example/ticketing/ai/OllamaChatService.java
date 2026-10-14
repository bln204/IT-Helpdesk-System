package com.example.ticketing.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Service for communicating with Ollama's chat API.
 *
 * <p>Uses JDK HttpClient to call the local Ollama daemon for LLM
 * generation. The service is independent of prompt construction: it
 * accepts a prepared list of messages and returns the generated response.
 *
 * <p>Production default chat model is {@code gemma3:4b} (Phase 6.3). The
 * model is configurable via {@code OLLAMA_CHAT_MODEL}.
 *
 * <p>Security:
 * <ul>
 *   <li>Does NOT log raw prompts, responses, or sensitive data.</li>
 *   <li>Maps errors to user-friendly messages.</li>
 *   <li>Uses configured timeouts to prevent runaway requests.</li>
 * </ul>
 */
@Service
public class OllamaChatService {

    private static final Logger log = LoggerFactory.getLogger(OllamaChatService.class);

    private final OllamaProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OllamaChatService(OllamaProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Send a chat request to Ollama and return the generated response.
     *
     * <p>Implements a single-shot retry when the model returns an empty
     * {@code content} field (the failure shape produced when a reasoning-
     * heavy model spends the entire token budget on internal reasoning).
     * The retry sends the exact same prompt; further failures propagate
     * as {@link LlmGenerationException#emptyResponse()}.
     *
     * @param messages List of chat messages (system + user) prepared by the caller
     * @return The assistant's response content
     * @throws LlmGenerationException if Ollama is unavailable, times out, or returns an error
     */
    public String generate(List<ChatMessage> messages) {
        return generate(messages, null);
    }

    /** Same as {@link #generate(List)} with per-call overrides (JSON Schema format, temperature, timeout). */
    public String generate(List<ChatMessage> messages, ChatOptions options) {
        long startTime = System.currentTimeMillis();
        String endpoint = properties.getBaseUrl() + "/api/chat";

        try {
            // Build Ollama request body
            Map<String, Object> requestBody = buildRequestBody(messages, options);

            // Create HTTP request with configured timeout
            String requestJson = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(options != null && options.timeoutSeconds() != null
                            ? options.timeoutSeconds() : properties.getChatTimeoutSeconds()))
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            // Log safe technical info only. The num_predict log
            // line is critical for Phase 7.7 hotfix-4
            // diagnostics: a done_reason=length result could be
            // caused by the budget being too small, the context
            // window being full, or Ollama deciding to stop
            // early. The num_predict value logged here is the
            // actual budget sent in this request, so a future
            // operator can correlate the eval_count vs num_predict
            // to find the cause.
            log.info(
                "Ollama chat request: model={}, num_predict={}, message_count={}, "
                    + "approx_prompt_chars={}",
                properties.getChatModel(),
                properties.getChatMaxTokens(),
                messages.size(),
                messages.stream().mapToInt(m -> m.content() == null ? 0 : m.content().length()).sum()
            );

            // Send request
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            long duration = System.currentTimeMillis() - startTime;
            log.debug("Ollama chat response: status={}, duration={}ms", response.statusCode(), duration);

            // Handle non-2xx responses
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Ollama returned non-success status: {}", response.statusCode());
                throw LlmGenerationException.nonSuccessfulResponse(response.statusCode());
            }

            // Parse response
            OllamaChatReply firstReply = parseResponse(response.body());

            // Single-shot retry on empty content. Small open-weight models can
            // occasionally emit a done_reason="length" with no visible answer;
            // a second attempt with the same prompt usually succeeds.
            if (firstReply.content() == null || firstReply.content().isBlank()) {
                log.warn("Ollama returned empty response; retrying once with the same prompt");
                HttpResponse<String> retryResponse = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());
                if (retryResponse.statusCode() < 200 || retryResponse.statusCode() >= 300) {
                    log.warn("Ollama retry returned non-success status: {}", retryResponse.statusCode());
                    throw LlmGenerationException.nonSuccessfulResponse(retryResponse.statusCode());
                }
                OllamaChatReply retried = parseResponse(retryResponse.body());
                if (retried.content() == null || retried.content().isBlank()) {
                    throw LlmGenerationException.emptyResponse();
                }
                return forOptions(cleanAnswer(retried.content()), options);
            }

            return forOptions(cleanAnswer(firstReply.content()), options);

        } catch (LlmGenerationException e) {
            throw e;
        } catch (java.net.ConnectException e) {
            log.warn("Cannot connect to Ollama: {}", e.getMessage());
            throw LlmGenerationException.ollamaUnavailable(e);
        } catch (java.net.http.HttpConnectTimeoutException e) {
            log.warn("Connection to Ollama timed out");
            throw LlmGenerationException.timeout(e);
        } catch (java.net.http.HttpTimeoutException e) {
            log.warn("Ollama response timed out");
            throw LlmGenerationException.timeout(e);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize request to JSON");
            throw new LlmGenerationException(
                "Failed to process request. Please try again.",
                "LLM_REQUEST_ERROR",
                e
            );
        } catch (Exception e) {
            log.error("Unexpected error during Ollama chat: {}", e.getClass().getSimpleName());
            throw new LlmGenerationException(
                "An unexpected error occurred. Please try again.",
                "LLM_UNEXPECTED_ERROR",
                e
            );
        }
    }

    /**
     * Lightweight post-processing of the model output. Gemma3 occasionally
     * emits a leading artifact (for example a stray code fence) before the
     * actual answer. We trim common wrappers and never alter the visible
     * content beyond that. Empty / whitespace-only inputs are left as-is
     * so the caller can detect them.
     */
    private String cleanAnswer(String raw) {
        if (raw == null) return "";
        String trimmed = raw.strip();
        // Strip a single leading "```" / "```json" / "```text" code fence
        // sometimes emitted by small models, and a matching trailing fence.
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
            trimmed = trimmed.strip();
        }
        return trimmed;
    }

    /**
     * Build the Ollama chat request body.
     */
    private Map<String, Object> buildRequestBody(List<ChatMessage> messages, ChatOptions options) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("model", properties.getChatModel());
        body.put("messages", messages.stream()
                .map(m -> Map.of(
                    "role", m.role(),
                    "content", m.content()
                ))
                .toList());
        body.put("stream", false);
        double temperature = options != null && options.temperature() != null
                ? options.temperature() : properties.getChatTemperature();
        // num_ctx is mandatory: without it Ollama falls back to a 2048-token
        // window and silently drops most of the system prompt.
        body.put("options", Map.of(
                "temperature", temperature,
                "num_predict", options != null && options.maxTokens() != null
                        ? options.maxTokens() : properties.getChatMaxTokens(),
                "num_ctx", properties.getChatNumCtx()
        ));
        if (options != null && options.format() != null) {
            body.put("format", options.format());
        }
        if (properties.getChatThink() != null) {
            body.put("think", properties.getChatThink());
        }
        if (properties.getChatKeepAlive() != null && !properties.getChatKeepAlive().isBlank()) {
            body.put("keep_alive", properties.getChatKeepAlive());
        }
        return body;
    }

    /**
     * Parse Ollama's response and return the assistant's message content.
     *
     * <p>Expected response format:
     * <pre>
     * {
     *   "model": "gemma3:4b",
     *   "message": {
     *     "role": "assistant",
     *     "content": "..."
     *   },
     *   "done": true,
     *   ...
     * }
     * </pre>
     *
     * <p>The raw body is parsed and returned as an {@link OllamaChatReply}
     * record. The {@code content} field is the only value consumed by the
     * caller; the record leaves room to surface additional fields (for
     * example {@code done_reason}) without changing the signature.
     */
    private OllamaChatReply parseResponse(String responseBody) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);

            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) response.get("message");

            if (message == null) {
                log.warn("Ollama response missing 'message' field");
                throw LlmGenerationException.malformedResponse(null);
            }

            Object content = message.get("content");
            if (content == null) {
                log.warn("Ollama response missing 'content' field in message");
                throw LlmGenerationException.malformedResponse(null);
            }

            logGenerationMetadata(response, message, content.toString());

            // PHASE 7.7 hotfix-3: when the model hits the
            // num_predict ceiling, Ollama returns done_reason
            // "length" and the content is truncated mid-sentence.
            // Returning the truncated content silently would
            // confuse the user (the assistant's self-service
            // answer would just stop halfway through a fix step).
            // We detect this case and append a short Vietnamese
            // notice so the user knows the answer was cut off
            // and can ask the assistant to continue, or open a
            // ticket. The notice is a single, neutral sentence
            // — not an apology, not a "khong tim thay" fallback,
            // and not an auto-ticket-creation.
            String finalContent = content.toString();
            Object doneReason = response.get("done_reason");
            if ("length".equals(doneReason) && !finalContent.isBlank()) {
                log.warn(
                    "Ollama response truncated by num_predict (chat_max_tokens={}). "
                        + "Appending continuation hint to the user-visible answer.",
                    properties.getChatMaxTokens());
                finalContent = finalContent + TRUNCATION_HINT;
            }

            return new OllamaChatReply(finalContent);

        } catch (LlmGenerationException e) {
            throw e;
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse Ollama response as JSON");
            throw LlmGenerationException.malformedResponse(e);
        } catch (ClassCastException e) {
            log.warn("Failed to extract content from Ollama response");
            throw LlmGenerationException.malformedResponse(e);
        }
    }

    /**
     * Log safe generation metadata for diagnosing empty or truncated responses.
     *
     * SECURITY: This method logs ONLY numeric/boolean metadata. It never logs
     * prompt text, response content, thinking/reasoning text, ticket data, or
     * credentials. Lengths are reported instead of the underlying strings so
     * that no sensitive content can leak via the logs.
     *
     * The 'done_reason' field is the key diagnostic signal:
     * - "length" means the generation budget (num_predict) was exhausted,
     *   typically because reasoning/thinking consumed the budget before
     *   any visible answer was produced.
     * - "stop" means the model finished on its own.
     */
    private void logGenerationMetadata(Map<String, Object> response,
                                       Map<String, Object> message,
                                       String content) {
        Object doneReason = response.get("done_reason");
        Object evalCount = response.get("eval_count");
        Object promptEvalCount = response.get("prompt_eval_count");

        Object thinking = message.get("thinking");
        int thinkingLength = (thinking == null) ? 0 : thinking.toString().length();

        int contentLength = (content == null) ? 0 : content.length();
        boolean emptyContent = contentLength == 0;

        log.info(
            "Ollama generation metadata: done_reason={}, eval_count={}, prompt_eval_count={}, "
                + "content_length={}, thinking_length={}, empty_content={}",
            doneReason, evalCount, promptEvalCount,
            contentLength, thinkingLength, emptyContent);

        // Ollama truncates silently when the prompt does not fit num_ctx.
        if (promptEvalCount instanceof Number n
                && n.intValue() + properties.getChatMaxTokens() >= properties.getChatNumCtx()) {
            log.warn(
                "Ollama prompt is close to / over the context window (prompt_eval_count={}, "
                    + "num_predict={}, num_ctx={}). The prompt may be truncated; "
                    + "consider increasing ollama.chat-num-ctx or shrinking the prompt.",
                n, properties.getChatMaxTokens(), properties.getChatNumCtx());
        }

        // Surface budget exhaustion explicitly - this is the failure mode that
        // produced LLM_EMPTY_RESPONSE in production.
        if (emptyContent) {
            log.warn(
                "Ollama returned no visible answer (done_reason={}, thinking_length={}, "
                    + "eval_count={}, chat_max_tokens={}). If thinking consumed the budget, "
                    + "consider increasing ollama.chat-max-tokens.",
                doneReason, thinkingLength, evalCount, properties.getChatMaxTokens());
        }
    }

    /**
     * Record representing a chat message.
     * 
     * @param role The role (e.g., "system", "user", "assistant")
     * @param content The message content
     */
    static final String TRUNCATION_HINT = "\n\n[Phần còn lại của câu trả lời đã bị giới hạn bởi số token tối đa. "
            + "Bạn có thể nhắn 'tiếp tục' để nhận phần còn lại, hoặc nhờ tôi tạo ticket hỗ trợ cho đội IT.]";

    /** A JSON-Schema reply is parsed, not read: a prose hint appended to it would only break the parse. */
    static String forOptions(String content, ChatOptions options) {
        if (options != null && options.format() != null && content != null && content.endsWith(TRUNCATION_HINT)) {
            return content.substring(0, content.length() - TRUNCATION_HINT.length());
        }
        return content;
    }

    /** Per-call overrides; null fields fall back to OllamaProperties. format = JSON Schema for structured output. */
    public record ChatOptions(Map<String, Object> format, Double temperature, Integer timeoutSeconds, Integer maxTokens) {
        public ChatOptions(Map<String, Object> format, Double temperature, Integer timeoutSeconds) {
            this(format, temperature, timeoutSeconds, null);
        }
    }

    public record ChatMessage(String role, String content) {

        /**
         * Create a system message.
         */
        public static ChatMessage system(String content) {
            return new ChatMessage("system", content);
        }

        /**
         * Create a user message.
         */
        public static ChatMessage user(String content) {
            return new ChatMessage("user", content);
        }

        /**
         * Create an assistant message.
         */
        public static ChatMessage assistant(String content) {
            return new ChatMessage("assistant", content);
        }
    }

    /**
     * Internal record representing a parsed Ollama chat reply. Currently
     * only {@code content} is consumed by the caller, but the record leaves
     * room for additional fields (for example {@code done_reason}) without
     * changing the {@link #parseResponse(String)} signature.
     */
    public record OllamaChatReply(String content) { }
}
