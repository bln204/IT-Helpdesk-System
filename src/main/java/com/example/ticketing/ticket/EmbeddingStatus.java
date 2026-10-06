package com.example.ticketing.ticket;

/**
 * Status values for ticket embedding workflow.
 *
 * Lifecycle:
 *   PENDING -> PROCESSING -> COMPLETED
 *                        \-> FAILED
 */
public enum EmbeddingStatus {
    /**
     * Ticket created, awaiting async embedding job.
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
