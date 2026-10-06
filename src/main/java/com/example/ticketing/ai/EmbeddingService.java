package com.example.ticketing.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.ticketing.ticket.EmbeddingStatus;
import com.example.ticketing.ticket.Ticket;
import com.example.ticketing.ticket.TicketEmbedding;
import com.example.ticketing.ticket.TicketEmbeddingRepository;
import com.example.ticketing.ticket.TicketRepository;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

/**
 * Service for generating and managing ticket embeddings.
 * Integrates with Ollama API for vector embedding generation.
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final TicketEmbeddingRepository embeddingRepository;
    private final TicketRepository ticketRepository;
    private final OllamaProperties ollamaProperties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;

    public EmbeddingService(
            TicketEmbeddingRepository embeddingRepository,
            TicketRepository ticketRepository,
            OllamaProperties ollamaProperties,
            EntityManager entityManager) {
        this.embeddingRepository = embeddingRepository;
        this.ticketRepository = ticketRepository;
        this.ollamaProperties = ollamaProperties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(ollamaProperties.getTimeoutSeconds()))
                .build();
        this.objectMapper = new ObjectMapper();
        this.entityManager = entityManager;
    }

    /**
     * Create embedding record for a ticket.
     * Called synchronously after ticket is saved.
     *
     * Declared REQUIRES_NEW because the record must be committed
     * independently: TicketService swallows failures from this call so that
     * ticket creation is never blocked by embedding problems.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TicketEmbedding createEmbedding(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + ticketId));

        log.info("Creating embedding record for ticket: {}", ticketId);
        return insertPendingEmbedding(ticket);
    }

    /**
     * Insert a new PENDING embedding row using native SQL.
     *
     * Required because V68 restored the embedding column to VECTOR(768) while
     * the entity maps it as a String. Hibernate would bind the column as a
     * varchar and PostgreSQL rejects it with
     * "column embedding is of type vector but expression is of type
     * character varying". This mirrors the explicit CAST approach already used
     * for the vector update in processEmbedding.
     *
     * The vector is left as an untyped NULL so PostgreSQL applies the correct
     * vector type, and the row is identical to what a PENDING record requires:
     * embedding NULL, embedded_at NULL, embedding_error NULL.
     *
     * The returned instance mirrors the persisted row. The ID is not populated
     * because the entity is IDENTITY-generated and exposes no ID setter; callers
     * only consume the ticket and status.
     *
     * @param ticket ticket to create the embedding record for
     * @return an in-memory representation of the created PENDING record
     */
    private TicketEmbedding insertPendingEmbedding(Ticket ticket) {
        Query query = entityManager.createNativeQuery("""
            INSERT INTO ticket_embeddings
                (ticket_id, embedding, embedding_status, embedded_at, embedding_error, created_at, updated_at)
            VALUES
                (:ticketId, NULL, 'PENDING', NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """);
        query.setParameter("ticketId", ticket.getId());
        query.executeUpdate();

        TicketEmbedding embedding = new TicketEmbedding();
        embedding.setTicket(ticket);
        embedding.setEmbeddingStatus(EmbeddingStatus.PENDING);
        return embedding;
    }

    /**
     * Re-generate embedding for a ticket.
     * Used when ticket content is updated.
     */
    public TicketEmbedding reembedTicket(Long ticketId) {
        TicketEmbedding embedding = findEmbeddingForTicket(ticketId)
                .orElseGet(() -> {
                    // Create new embedding record if doesn't exist
                    Ticket ticket = ticketRepository.findById(ticketId)
                            .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + ticketId));
                    return insertPendingEmbedding(ticket);
                });

        // Reset status to PENDING
        embedding.setEmbeddingStatus(EmbeddingStatus.PENDING);
        embedding.setEmbedding(null);
        embedding.setEmbeddingError(null);
        embedding.setEmbeddedAt(null);

        log.info("Re-embedding ticket: {}", ticketId);
        return embeddingRepository.save(embedding);
    }

    /**
     * Look up the embedding record for a ticket.
     *
     * Uses explicit JPQL on the ticket association: TicketEmbedding has no
     * persistent ticketId field, so the derived "ByTicketId" method cannot
     * resolve its query path.
     *
     * @param ticketId ticket ID
     * @return the embedding record, if one exists
     */
    public java.util.Optional<TicketEmbedding> findEmbeddingForTicket(Long ticketId) {
        return entityManager.createQuery(
                        "SELECT e FROM TicketEmbedding e WHERE e.ticket.id = :ticketId", TicketEmbedding.class)
                .setParameter("ticketId", ticketId)
                .getResultStream()
                .findFirst();
    }

    /**
     * Process pending embeddings (to be called by scheduler).
     * This method is transactional to ensure consistency.
     */
    public void processPendingEmbeddings() {
        if (!ollamaProperties.isEnabled()) {
            log.debug("Ollama integration is disabled");
            return;
        }

        List<TicketEmbedding> pending = embeddingRepository.findAllPending();
        log.info("Found {} pending embeddings to process", pending.size());

        for (TicketEmbedding embedding : pending) {
            try {
                processEmbeddingDirect(embedding.getId());
            } catch (Exception e) {
                log.error("Failed to process embedding for ticket: {}",
                        embedding.getTicketId(), e);
            }
        }
    }

    /**
     * Process a single embedding by ID.
     * Called by scheduler with claim locking.
     */
    public void processEmbeddingDirect(Long embeddingId) {
        TicketEmbedding embedding = embeddingRepository.findById(embeddingId)
                .orElseThrow(() -> new IllegalArgumentException("Embedding not found: " + embeddingId));
        processEmbedding(embedding);
    }

    /**
     * Process a single embedding.
     * Status must be PROCESSING when called.
     * 
     * Uses EntityManager with native SQL for vector persistence (V68 fix).
     * This bypasses JPA's type handling to avoid VARCHAR->vector cast issues.
     */
    private void processEmbedding(TicketEmbedding embedding) {
        Long ticketId = embedding.getTicketId();

        try {
            // Get ticket content
            Ticket ticket = ticketRepository.findById(ticketId).orElse(null);
            if (ticket == null) {
                markEmbeddingFailed(embedding.getId(), "Ticket not found");
                return;
            }

            // Build text for embedding
            String text = buildEmbeddingText(ticket);

            // Call Ollama API
            float[] vector = generateEmbedding(text);

            if (vector == null || vector.length == 0) {
                markEmbeddingFailed(embedding.getId(), "Empty embedding returned from Ollama");
                return;
            }

            // Convert to string format for storage
            String embeddingString = vectorToString(vector);

            // Store using native SQL with explicit CAST to vector
            // This avoids JPA's VARCHAR->vector type mismatch error
            LocalDateTime now = LocalDateTime.now();
            Query query = entityManager.createNativeQuery("""
                UPDATE ticket_embeddings
                SET embedding = CAST(:embedding AS vector),
                    embedding_status = 'COMPLETED',
                    embedded_at = :embeddedAt,
                    embedding_error = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """);
            query.setParameter("embedding", embeddingString);
            query.setParameter("embeddedAt", now);
            query.setParameter("id", embedding.getId());
            query.executeUpdate();

            log.info("Embedding completed for ticket: {}", ticketId);

        } catch (Exception e) {
            markEmbeddingFailed(embedding.getId(), e.getMessage());
        }
    }

    /**
     * Mark embedding as failed using native SQL.
     * 
     * @param embeddingId ID of embedding record
     * @param errorMessage error description
     */
    private void markEmbeddingFailed(Long embeddingId, String errorMessage) {
        Query query = entityManager.createNativeQuery("""
            UPDATE ticket_embeddings
            SET embedding = NULL,
                embedding_status = 'FAILED',
                embedded_at = NULL,
                embedding_error = :errorMessage,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = :id
            """);
        query.setParameter("errorMessage", errorMessage);
        query.setParameter("id", embeddingId);
        query.executeUpdate();

        log.warn("Embedding failed for ID {}: {}", embeddingId, errorMessage);
    }

    /**
     * Build text from ticket for embedding.
     */
    private String buildEmbeddingText(Ticket ticket) {
        StringBuilder sb = new StringBuilder();

        // Title
        if (ticket.getTitle() != null) {
            sb.append("Title: ").append(ticket.getTitle()).append("\n");
        }

        // Description
        if (ticket.getDescription() != null) {
            sb.append("Description: ").append(ticket.getDescription()).append("\n");
        }

        // Category
        if (ticket.getCategoryName() != null) {
            sb.append("Category: ").append(ticket.getCategoryName()).append("\n");
        }

        // Subcategory
        if (ticket.getSubcategoryName() != null) {
            sb.append("Subcategory: ").append(ticket.getSubcategoryName()).append("\n");
        }

        // Priority
        if (ticket.getPriority() != null) {
            sb.append("Priority: ").append(ticket.getPriority().name()).append("\n");
        }

        return sb.toString().trim();
    }

    /**
     * Call Ollama API to generate embedding.
     */
    private float[] generateEmbedding(String text) {
        return generateEmbeddingInternal(text);
    }

    /**
     * Generate embedding for search queries.
     * Public method for use by RagSearchService.
     */
    public float[] generateEmbeddingInternal(String text) {
        String url = ollamaProperties.getBaseUrl() + "/api/embeddings";

        OllamaRequest request = new OllamaRequest();
        request.setModel(ollamaProperties.getEmbeddingModel());
        request.setPrompt(text);

        try {
            String jsonBody = objectMapper.writeValueAsString(request);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(ollamaProperties.getTimeoutSeconds()))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            log.debug("Calling Ollama API: {}", url);

            HttpResponse<String> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Ollama API returned status " + response.statusCode() + ": " + response.body());
            }

            OllamaResponse ollamaResponse = objectMapper.readValue(response.body(), OllamaResponse.class);

            if (ollamaResponse == null || ollamaResponse.getEmbedding() == null) {
                throw new RuntimeException("Ollama API returned null response");
            }

            return ollamaResponse.getEmbedding();

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to call Ollama API: " + e.getMessage(), e);
        }
    }

    /**
     * Convert float array to PostgreSQL vector string format.
     */
    private String vectorToString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Check if Ollama is available.
     */
    public boolean isOllamaAvailable() {
        if (!ollamaProperties.isEnabled()) {
            return false;
        }
        try {
            String url = ollamaProperties.getBaseUrl() + "/api/tags";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(ollamaProperties.getTimeoutSeconds()))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.warn("Ollama is not available: {}", e.getMessage());
            return false;
        }
    }

    // ==================== Inner classes for Ollama API ====================

    public static class OllamaRequest {
        private String model;
        private String prompt;

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getPrompt() {
            return prompt;
        }

        public void setPrompt(String prompt) {
            this.prompt = prompt;
        }
    }

    public static class OllamaResponse {
        private String model;
        private float[] embedding;
        private long duration;

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public float[] getEmbedding() {
            return embedding;
        }

        public void setEmbedding(float[] embedding) {
            this.embedding = embedding;
        }

        public long getDuration() {
            return duration;
        }

        public void setDuration(long duration) {
            this.duration = duration;
        }
    }
}
