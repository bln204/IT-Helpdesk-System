package com.example.ticketing.ticket;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Entity for storing vector embeddings of ticket content.
 * Supports AI/RAG functionality for semantic search.
 *
 * Workflow:
 * 1. Ticket created -> embedding record created with PENDING status
 * 2. Async job picks up PENDING records
 * 3. Job calls Ollama API to generate embedding
 * 4. Job updates record with COMPLETED/FAILED status
 */
@Entity
@Table(name = "ticket_embeddings")
public class TicketEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false, unique = true)
    private Ticket ticket;

    /**
     * Vector embedding from nomic-embed-text model (768 dimensions).
     * NULL when status is PENDING or FAILED.
     * Stored as TEXT in Java; PostgreSQL casts to vector when needed for similarity search.
     */
    @Column(name = "embedding", columnDefinition = "TEXT")
    private String embedding;

    @Enumerated(EnumType.STRING)
    @Column(name = "embedding_status", nullable = false, length = 20)
    private EmbeddingStatus embeddingStatus = EmbeddingStatus.PENDING;

    @Column(name = "embedded_at")
    private LocalDateTime embeddedAt;

    @Column(name = "embedding_error", columnDefinition = "TEXT")
    private String embeddingError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (embeddingStatus == null) {
            embeddingStatus = EmbeddingStatus.PENDING;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ==================== Getters & Setters ====================

    public Long getId() {
        return id;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public void setTicket(Ticket ticket) {
        this.ticket = ticket;
    }

    public Long getTicketId() {
        return ticket != null ? ticket.getId() : null;
    }

    public String getEmbedding() {
        return embedding;
    }

    public void setEmbedding(String embedding) {
        this.embedding = embedding;
    }

    public EmbeddingStatus getEmbeddingStatus() {
        return embeddingStatus;
    }

    public void setEmbeddingStatus(EmbeddingStatus embeddingStatus) {
        this.embeddingStatus = embeddingStatus;
    }

    public LocalDateTime getEmbeddedAt() {
        return embeddedAt;
    }

    public void setEmbeddedAt(LocalDateTime embeddedAt) {
        this.embeddedAt = embeddedAt;
    }

    public String getEmbeddingError() {
        return embeddingError;
    }

    public void setEmbeddingError(String embeddingError) {
        this.embeddingError = embeddingError;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Check if embedding is ready for search.
     */
    public boolean isReady() {
        return embeddingStatus == EmbeddingStatus.COMPLETED && embedding != null;
    }
}
