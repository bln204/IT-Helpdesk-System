package com.example.ticketing.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for Ollama API integration.
 */
@Component
@ConfigurationProperties(prefix = "ollama")
public class OllamaProperties {

    /**
     * Enable/disable Ollama integration.
     */
    private boolean enabled = true;

    /**
     * Ollama server base URL.
     */
    private String baseUrl = "http://localhost:11434";

    /**
     * Embedding model to use.
     */
    private String embeddingModel = "nomic-embed-text";

    /**
     * Timeout for API calls in seconds.
     */
    private int timeoutSeconds = 60;

    /**
     * Chat model to use for LLM generation.
     *
     * <p>Default is {@code gemma3:4b} after the Phase 6.3 migration. The
     * model is configurable via the {@code OLLAMA_CHAT_MODEL} environment
     * variable or {@code ollama.chat-model} property without code changes.
     */
    private String chatModel = "gemma3:4b";

    /**
     * Timeout for chat generation in seconds.
     * Should be longer than embedding timeout as LLM generation takes more time.
     */
    private int chatTimeoutSeconds = 120;

    /**
     * Temperature for LLM generation (0.0 = deterministic, 1.0 = creative).
     * Low values are used for factual, consistent responses.
     *
     * <p>Default 0.2 (Phase 6.3) is tuned for {@code gemma3:4b} on the
     * IT Helpdesk dataset. Lower than the previous 0.3 so the model sticks
     * to the retrieved context and produces stable ticket IDs and statuses.
     */
    private double chatTemperature = 0.2;

    /**
     * Maximum number of tokens to generate in chat responses.
     * Limits response length to prevent runaway generation.
     *
     * <p>Default is aligned with {@code ollama.chat-max-tokens=2048} in
     * {@code application.properties} so a missing environment override still
     * produces the intended runtime effective value (2048). On this dataset
     * {@code gemma3:4b} typically emits 30-60 tokens for short lookup answers
     * and up to 350-450 tokens for the self-service troubleshooting branch
     * (the assistant enumerates possible causes, lists first-line fix steps,
     * asks for 1-3 missing details, and closes with a ticket-escalation hint).
     * Phase 7.7 hotfix-4 raised the default from 1024 to 2048 because the
     * earlier 1024 ceiling was leaving the troubleshooting branch truncated
     * at {@code done_reason=length} even with the prompt-fitting work in
     * hotfix-3. Operators can still override via the
     * {@code OLLAMA_CHAT_MAX_TOKENS} env var.
     */
    private int chatMaxTokens = 2048;

    /**
     * Context window (num_ctx) sent with every chat request. Ollama's
     * built-in default (2048) silently truncates the ~8k-token canonical
     * system prompt, so the model only ever saw a fraction of its rules.
     * Must cover system prompt + retrieved data + history + num_predict.
     */
    private int chatNumCtx = 12288;

    /**
     * Optional Ollama {@code think} flag. Leave null for models without a
     * reasoning mode (e.g. qwen3:4b-instruct-2507); set false for hybrid
     * reasoning models (e.g. qwen3:4b) so they do not burn the token budget
     * on hidden reasoning.
     */
    private Boolean chatThink;

    /**
     * How long Ollama keeps the chat model loaded after a request
     * (Ollama duration string, e.g. "30m"). Avoids a cold reload on
     * every question on low-VRAM machines.
     */
    private String chatKeepAlive = "30m";

    // Getters and Setters

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getChatModel() {
        return chatModel;
    }

    public void setChatModel(String chatModel) {
        this.chatModel = chatModel;
    }

    public int getChatTimeoutSeconds() {
        return chatTimeoutSeconds;
    }

    public void setChatTimeoutSeconds(int chatTimeoutSeconds) {
        this.chatTimeoutSeconds = chatTimeoutSeconds;
    }

    public double getChatTemperature() {
        return chatTemperature;
    }

    public void setChatTemperature(double chatTemperature) {
        this.chatTemperature = chatTemperature;
    }

    public int getChatMaxTokens() {
        return chatMaxTokens;
    }

    public void setChatMaxTokens(int chatMaxTokens) {
        this.chatMaxTokens = chatMaxTokens;
    }

    public int getChatNumCtx() {
        return chatNumCtx;
    }

    public void setChatNumCtx(int chatNumCtx) {
        this.chatNumCtx = chatNumCtx;
    }

    public Boolean getChatThink() {
        return chatThink;
    }

    public void setChatThink(Boolean chatThink) {
        this.chatThink = chatThink;
    }

    public String getChatKeepAlive() {
        return chatKeepAlive;
    }

    public void setChatKeepAlive(String chatKeepAlive) {
        this.chatKeepAlive = chatKeepAlive;
    }
}
