package com.example.ticketing.problem;

/**
 * Status values for Known Error embedding workflow.
 *
 * <p>Mirrors the lifecycle used for ticket embeddings
 * (PENDING -> PROCESSING -> COMPLETED / FAILED) so the same scheduler
 * and FOR UPDATE SKIP LOCKED claim pattern can be reused without
 * divergent lifecycle semantics.
 */
public enum KnownErrorEmbeddingStatus {
    /**
     * KnownError created, awaiting async embedding job.
     */
    PENDING,

    /**
     * Async job is currently calling Ollama API.
     */
    PROCESSING,

    /**
     * Embedding generated successfully, vector stored.
     */
    COMPLETED,

    /**
     * Embedding generation failed (API error, timeout, etc).
     */
    FAILED
}
