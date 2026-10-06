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
     */
    private String chatModel = "qwen3:4b";

    /**
     * Timeout for chat generation in seconds.
     * Should be longer than embedding timeout as LLM generation takes more time.
     */
    private int chatTimeoutSeconds = 120;

    /**
     * Temperature for LLM generation (0.0 = deterministic, 1.0 = creative).
     * Lower values for factual, consistent responses.
     */
    private double chatTemperature = 0.3;

    /**
     * Maximum number of tokens to generate in chat responses.
     * Limits response length to prevent runaway generation.
     */
    private int chatMaxTokens = 512;

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
}
