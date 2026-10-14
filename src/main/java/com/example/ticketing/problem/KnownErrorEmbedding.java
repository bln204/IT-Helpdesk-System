package com.example.ticketing.problem;

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
 * Phase 6.6.4 - Vector embedding for a Known Error.
 *
 * <p>Mirrors the {@code ticket_embeddings} shape (one row per source entity,
 * {@code VECTOR(768)} dimension) so the existing Ollama embedding service can
 * be reused without code changes. The semantic text is built by
 * {@code KnownErrorEmbeddingTextBuilder} from the {@link KnownError} fields
 * the user explicitly listed in Phase 6.6.4 section 6-7
 * (title/description/symptoms/rootCause/rootCauseCategory/workaround/resolution/fixSteps/impactDescription/affectedSystems).
 *
 * <p>The {@code knownErrorId} field is NOT persistent - it is a derived accessor
 * backed by the {@link #knownError} association. The repository uses explicit
 * JPQL on that association for the same reason as
 * {@code TicketEmbeddingRepository}: derived "ByKnownErrorId" methods cannot
 * resolve the path without a persistent id field.
 */
@Entity
@Table(name = "known_error_embeddings")
public class KnownErrorEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "known_error_id", nullable = false, unique = true)
    private KnownError knownError;

    /**
     * Vector embedding (768 dimensions, nomic-embed-text). Stored as TEXT in
     * Java; PostgreSQL casts to vector when needed for similarity search.
     */
    @Column(name = "embedding", columnDefinition = "TEXT")
    private String embedding;

    @Enumerated(EnumType.STRING)
    @Column(name = "embedding_status", nullable = false, length = 20)
    private KnownErrorEmbeddingStatus embeddingStatus = KnownErrorEmbeddingStatus.PENDING;

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
            embeddingStatus = KnownErrorEmbeddingStatus.PENDING;
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

    public KnownError getKnownError() {
        return knownError;
    }

    public void setKnownError(KnownError knownError) {
        this.knownError = knownError;
    }

    /**
     * Convenience accessor for the {@code known_error_id} FK without exposing
     * a persistent id field. Used by the repository and services.
     */
    public Long getKnownErrorId() {
        return knownError != null ? knownError.getId() : null;
    }

    public String getEmbedding() {
        return embedding;
    }

    public void setEmbedding(String embedding) {
        this.embedding = embedding;
    }

    public KnownErrorEmbeddingStatus getEmbeddingStatus() {
        return embeddingStatus;
    }

    public void setEmbeddingStatus(KnownErrorEmbeddingStatus embeddingStatus) {
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
        return embeddingStatus == KnownErrorEmbeddingStatus.COMPLETED && embedding != null;
    }
}
