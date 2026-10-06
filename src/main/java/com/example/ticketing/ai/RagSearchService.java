package com.example.ticketing.ai;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.ticketing.auth.UserAccount;
import com.example.ticketing.auth.UserAccountRepository;
import com.example.ticketing.ticket.EmbeddingStatus;
import com.example.ticketing.ticket.Ticket;
import com.example.ticketing.ticket.TicketEmbedding;
import com.example.ticketing.ticket.TicketEmbeddingRepository;
import com.example.ticketing.ticket.TicketRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * RAG (Retrieval-Augmented Generation) service for semantic ticket search.
 * Uses pgvector for efficient similarity search with authorization filtering.
 */
@Service
public class RagSearchService {

    private static final Logger log = LoggerFactory.getLogger(RagSearchService.class);

    private final EmbeddingService embeddingService;
    private final TicketEmbeddingRepository embeddingRepository;
    private final TicketRepository ticketRepository;
    private final UserAccountRepository userAccountRepository;
    private final VectorSearchRepository vectorSearchRepository;
    private final OllamaProperties ollamaProperties;

    @PersistenceContext
    private EntityManager entityManager;

    public RagSearchService(
            EmbeddingService embeddingService,
            TicketEmbeddingRepository embeddingRepository,
            TicketRepository ticketRepository,
            UserAccountRepository userAccountRepository,
            VectorSearchRepository vectorSearchRepository,
            OllamaProperties ollamaProperties) {
        this.embeddingService = embeddingService;
        this.embeddingRepository = embeddingRepository;
        this.ticketRepository = ticketRepository;
        this.userAccountRepository = userAccountRepository;
        this.vectorSearchRepository = vectorSearchRepository;
        this.ollamaProperties = ollamaProperties;
    }

    /**
     * Search tickets semantically using natural language query.
     * Results are filtered by user's authorization (department access).
     */
    public RagSearchDto.SearchResponse search(RagSearchDto.SearchRequest request, String username) {
        long startTime = System.currentTimeMillis();

        // Validate query
        if (request.getQuery() == null || request.getQuery().trim().isEmpty()) {
            return buildEmptyResponse(request.getQuery(), startTime, "empty_query");
        }

        try {
            // Generate embedding for query
            float[] queryVector = embeddingService.generateEmbeddingInternal(request.getQuery());

            if (queryVector == null || queryVector.length == 0) {
                log.warn("Failed to generate embedding for query: {}", request.getQuery());
                return buildEmptyResponse(request.getQuery(), startTime, "embedding_failed");
            }

            // Convert to JSON string for pgvector
            String queryVectorJson = vectorToJson(queryVector);

            // Get authorized department IDs for the user
            List<Long> allowedDepartmentIds = getAuthorizedDepartmentIds(username);

            // Search using pgvector with authorization filter
            List<VectorSearchRepository.TicketSimilarity> similarities;
            
            if (allowedDepartmentIds != null && !allowedDepartmentIds.isEmpty()) {
                // Filter by department
                similarities = vectorSearchRepository.searchBySimilarityWithDepartmentFilter(
                        queryVectorJson,
                        request.getMinScore(),
                        request.getLimit(),
                        allowedDepartmentIds);
            } else {
                // Admin - no filter (but check if pgvector is available)
                if (!vectorSearchRepository.isPgvectorAvailable()) {
                    log.warn("pgvector not available, falling back to Java calculation");
                    return searchWithJavaFallback(request, startTime, username);
                }
                
                similarities = vectorSearchRepository.searchBySimilarity(
                        queryVectorJson,
                        request.getMinScore(),
                        request.getLimit());
            }

            if (similarities.isEmpty()) {
                return buildEmptyResponse(request.getQuery(), startTime, "no_results");
            }

            // Fetch tickets by IDs
            List<Long> ticketIds = similarities.stream()
                    .map(VectorSearchRepository.TicketSimilarity::getTicketId)
                    .collect(Collectors.toList());

            Map<Long, Ticket> ticketMap = ticketRepository.findAllById(ticketIds).stream()
                    .collect(Collectors.toMap(Ticket::getId, t -> t));

            // Build results maintaining order from similarity search
            List<RagSearchDto.SearchResult> results = new ArrayList<>();
            for (VectorSearchRepository.TicketSimilarity sim : similarities) {
                Ticket ticket = ticketMap.get(sim.getTicketId());
                if (ticket != null) {
                    results.add(toSearchResult(ticket, sim.getSimilarity()));
                }
            }

            long processingTime = System.currentTimeMillis() - startTime;

            return RagSearchDto.SearchResponse.builder()
                    .query(request.getQuery())
                    .totalResults(results.size())
                    .results(results)
                    .metadata(buildMetadata(startTime, results.size(), "pgvector_similarity"))
                    .build();

        } catch (Exception e) {
            log.error("Semantic search failed: {}", e.getMessage(), e);
            return buildEmptyResponse(request.getQuery(), startTime, "error: " + e.getMessage());
        }
    }

    /**
     * Get authorized department IDs for a user.
     * - ADMIN, GIAM_DOC: Access to all departments (no filter)
     * - TRUONG_PHONG, NHAN_VIEN: Only their own department
     */
    private List<Long> getAuthorizedDepartmentIds(String username) {
        if (username == null || username.isBlank()) {
            return List.of();
        }

        UserAccount user = userAccountRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return List.of();
        }

        // ADMIN and GIAM_DOC can see all departments (return null to skip filter)
        if (user.isAdmin() || user.isGiamDoc()) {
            return null; // Signal for no filter
        }

        // TRUONG_PHONG and NHAN_VIEN: only their department
        if (user.getDepartment() != null) {
            return List.of(user.getDepartment().getId());
        }

        // If no department, return empty list (no access)
        return List.of();
    }

    /**
     * Fallback search using Java calculation when pgvector is not available.
     */
    private RagSearchDto.SearchResponse searchWithJavaFallback(
            RagSearchDto.SearchRequest request, 
            long startTime,
            String username) {
        
        log.warn("Using Java fallback for similarity calculation");

        List<TicketEmbedding> completedEmbeddings = embeddingRepository
                .findByEmbeddingStatus(EmbeddingStatus.COMPLETED);

        if (completedEmbeddings.isEmpty()) {
            return buildEmptyResponse(request.getQuery(), startTime, "no_embeddings");
        }

        // Get query vector
        float[] queryVector;
        try {
            queryVector = embeddingService.generateEmbeddingInternal(request.getQuery());
        } catch (Exception e) {
            return buildEmptyResponse(request.getQuery(), startTime, "embedding_failed");
        }

        // Get authorized department IDs
        List<Long> allowedDeptIds = getAuthorizedDepartmentIds(username);

        List<ScoredTicket> scoredTickets = new ArrayList<>();

        for (TicketEmbedding embedding : completedEmbeddings) {
            try {
                Ticket ticket = ticketRepository.findById(embedding.getTicketId()).orElse(null);
                if (ticket == null) continue;

                // Authorization filter
                if (allowedDeptIds != null && !allowedDeptIds.isEmpty()) {
                    if (ticket.getDepartmentId() == null || 
                        !allowedDeptIds.contains(ticket.getDepartmentId())) {
                        continue;
                    }
                }

                float[] ticketVector = parseVector(embedding.getEmbedding());
                if (ticketVector == null) continue;

                double similarity = cosineSimilarity(queryVector, ticketVector);

                if (similarity >= request.getMinScore()) {
                    scoredTickets.add(new ScoredTicket(ticket, similarity));
                }
            } catch (Exception e) {
                log.warn("Error processing embedding for ticket {}: {}",
                        embedding.getTicketId(), e.getMessage());
            }
        }

        // Sort and limit
        scoredTickets.sort((a, b) -> Double.compare(b.similarity, a.similarity));
        int limit = Math.min(request.getLimit(), scoredTickets.size());

        List<RagSearchDto.SearchResult> results = scoredTickets.stream()
                .limit(limit)
                .map(s -> toSearchResult(s.ticket, s.similarity))
                .toList();

        long processingTime = System.currentTimeMillis() - startTime;

        return RagSearchDto.SearchResponse.builder()
                .query(request.getQuery())
                .totalResults(results.size())
                .results(results)
                .metadata(buildMetadata(startTime, results.size(), "java_fallback"))
                .build();
    }

    /**
     * Search without authorization filter (for admin dashboard).
     */
    public RagSearchDto.SearchResponse searchAdmin(RagSearchDto.SearchRequest request) {
        return search(request, null);
    }

    /**
     * Convert float array to JSON vector string for pgvector.
     */
    private String vectorToJson(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Parse PostgreSQL vector string to float array.
     */
    private float[] parseVector(String vectorString) {
        if (vectorString == null || vectorString.isEmpty()) return null;

        try {
            String cleaned = vectorString.trim()
                    .replaceFirst("^\\[", "")
                    .replaceFirst("\\]$", "");

            String[] parts = cleaned.split(",");
            float[] vector = new float[parts.length];

            for (int i = 0; i < parts.length; i++) {
                vector[i] = Float.parseFloat(parts[i].trim());
            }

            return vector;
        } catch (Exception e) {
            log.warn("Failed to parse vector: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Calculate cosine similarity.
     */
    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) return 0.0;

        double dotProduct = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0 || normB == 0) return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private RagSearchDto.SearchResult toSearchResult(Ticket ticket, double similarity) {
        return RagSearchDto.SearchResult.builder()
                .ticketId(ticket.getId())
                .ticketNumber(ticket.getTicketNumber())
                .title(ticket.getTitle())
                .description(truncateDescription(ticket.getDescription(), 300))
                .categoryName(ticket.getCategoryName())
                .priority(ticket.getPriority() != null ? ticket.getPriority().name() : null)
                .status(ticket.getStatus() != null ? ticket.getStatus().name() : null)
                .requesterUsername(ticket.getRequesterUsername())
                .assigneeName(ticket.getAssigneeName())
                .createdAt(ticket.getCreatedAt())
                .resolvedAt(ticket.getResolvedAt())
                .similarityScore(Math.round(similarity * 100.0) / 100.0)
                .build();
    }

    private String truncateDescription(String description, int maxLength) {
        if (description == null) return null;
        if (description.length() <= maxLength) return description;
        return description.substring(0, maxLength) + "...";
    }

    private RagSearchDto.SearchMetadata buildMetadata(long startTime, int resultCount, String searchType) {
        long processingTime = System.currentTimeMillis() - startTime;
        long totalEmbeddings = embeddingRepository.count();

        return RagSearchDto.SearchMetadata.builder()
                .processingTimeMs(processingTime)
                .model(ollamaProperties.getEmbeddingModel())
                .totalEmbeddingsAvailable((int) totalEmbeddings)
                .searchType(searchType)
                .build();
    }

    private RagSearchDto.SearchResponse buildEmptyResponse(String query, long startTime, String reason) {
        return RagSearchDto.SearchResponse.builder()
                .query(query)
                .totalResults(0)
                .results(List.of())
                .metadata(buildMetadata(startTime, 0, reason))
                .build();
    }

    public RagSearchDto.EmbeddingStatusResponse getEmbeddingStatus() {
        long total = embeddingRepository.count();
        long completed = embeddingRepository.countByEmbeddingStatus(EmbeddingStatus.COMPLETED);
        long pending = embeddingRepository.countByEmbeddingStatus(EmbeddingStatus.PENDING);
        long processing = embeddingRepository.countByEmbeddingStatus(EmbeddingStatus.PROCESSING);
        long failed = embeddingRepository.countByEmbeddingStatus(EmbeddingStatus.FAILED);

        return RagSearchDto.EmbeddingStatusResponse.builder()
                .totalEmbeddings(total)
                .completed(completed)
                .pending(pending)
                .processing(processing)
                .failed(failed)
                .ollamaAvailable(embeddingService.isOllamaAvailable())
                .pgvectorAvailable(vectorSearchRepository.isPgvectorAvailable())
                .build();
    }

    private static class ScoredTicket {
        final Ticket ticket;
        final double similarity;

        ScoredTicket(Ticket ticket, double similarity) {
            this.ticket = ticket;
            this.similarity = similarity;
        }
    }
}
