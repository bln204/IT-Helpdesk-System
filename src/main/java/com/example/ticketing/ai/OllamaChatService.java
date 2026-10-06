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
 * Uses JDK HttpClient to call local Ollama for LLM generation.
 * This service is independent of prompt construction - it accepts
 * a prepared list of messages and returns the generated response.
 * 
 * Security:
 * - Does NOT log raw prompts, responses, or sensitive data
 * - Maps errors to user-friendly messages
 * - Uses configured timeouts to prevent runaway requests
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
     * @param messages List of chat messages (system + user) prepared by the caller
     * @return The assistant's response content
     * @throws LlmGenerationException if Ollama is unavailable, times out, or returns an error
     */
    public String generate(List<ChatMessage> messages) {
        long startTime = System.currentTimeMillis();
        String endpoint = properties.getBaseUrl() + "/api/chat";

        try {
            // Build Ollama request body
            Map<String, Object> requestBody = buildRequestBody(messages);

            // Create HTTP request with configured timeout
            String requestJson = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(properties.getChatTimeoutSeconds()))
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            // Log safe technical info only
            log.debug("Sending chat request to Ollama: model={}", properties.getChatModel());

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
            String responseContent = parseResponse(response.body());

            // Handle empty response
            if (responseContent == null || responseContent.isBlank()) {
                log.warn("Ollama returned empty response");
                throw LlmGenerationException.emptyResponse();
            }

            return responseContent;

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
     * Build the Ollama chat request body.
     */
    private Map<String, Object> buildRequestBody(List<ChatMessage> messages) {
        return Map.of(
            "model", properties.getChatModel(),
            "messages", messages.stream()
                .map(m -> Map.of(
                    "role", m.role(),
                    "content", m.content()
                ))
                .toList(),
            "stream", false,
            "options", Map.of(
                "temperature", properties.getChatTemperature(),
                "num_predict", properties.getChatMaxTokens()
            )
        );
    }

    /**
     * Parse Ollama's response to extract the assistant's message content.
     * 
     * Expected response format:
     * {
     *   "model": "qwen3:4b",
     *   "message": {
     *     "role": "assistant",
     *     "content": "..."
     *   },
     *   "done": true,
     *   ...
     * }
     */
    private String parseResponse(String responseBody) {
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

            return content.toString();

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
}
