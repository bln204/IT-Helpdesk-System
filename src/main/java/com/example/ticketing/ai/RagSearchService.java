package com.example.ticketing.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.ticketing.authorization.ActorContextService;
import com.example.ticketing.authorization.RagAuthorizationDecision;
import com.example.ticketing.authorization.RagAuthorizationPolicy;
import com.example.ticketing.department.Department;
import com.example.ticketing.department.DepartmentRepository;
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
 *
 * <p>PHASE 4.1 (C-6 fix). Authorization now goes through the canonical
 * {@link RagAuthorizationPolicy}, which mirrors the same policy used by
 * {@link RagRetrievalService}. The two services cannot disagree on the scope of a
 * retrieval because they share the same policy bean and the same SQL-boundary branches.
 *
 * <p>SQL BOUNDARY. The verdict is materialized into one of three SQL shapes:
 * <ul>
 *   <li>{@link RagAuthorizationDecision.Kind#ALLOW_ALL} - the unfiltered vector search path
 *       ({@code searchBySimilarity}). Used for IT operators and ADMIN/GIAM_DOC.</li>
 *   <li>{@link RagAuthorizationDecision.Kind#DEPARTMENT_SCOPED} - the SQL filter list contains
 *       exactly the actor's department id. Used for non-IT TRUONG_PHONG / NHAN_VIEN.</li>
 *   <li>{@link RagAuthorizationDecision.Kind#DENY} - the service returns an empty response
 *       WITHOUT invoking any vector search.</li>
 * </ul>
 */
@Service
public class RagSearchService {

    private static final Logger log = LoggerFactory.getLogger(RagSearchService.class);

    private final EmbeddingService embeddingService;
    private final TicketEmbeddingRepository embeddingRepository;
    private final TicketRepository ticketRepository;
    private final VectorSearchRepository vectorSearchRepository;
    private final OllamaProperties ollamaProperties;
    private final RagAuthorizationPolicy ragAuthorizationPolicy;
    private final ActorContextService actorContextService;
    private final DepartmentRepository departmentRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public RagSearchService(
            EmbeddingService embeddingService,
            TicketEmbeddingRepository embeddingRepository,
            TicketRepository ticketRepository,
            VectorSearchRepository vectorSearchRepository,
            OllamaProperties ollamaProperties,
            RagAuthorizationPolicy ragAuthorizationPolicy,
            ActorContextService actorContextService,
            DepartmentRepository departmentRepository) {
        this.embeddingService = embeddingService;
        this.embeddingRepository = embeddingRepository;
        this.ticketRepository = ticketRepository;
        this.vectorSearchRepository = vectorSearchRepository;
        this.ollamaProperties = ollamaProperties;
        this.ragAuthorizationPolicy = ragAuthorizationPolicy;
        this.actorContextService = actorContextService;
        this.departmentRepository = departmentRepository;
    }

    // ========================================================================
    // Public entry points
    // ========================================================================

    /**
     * Search tickets semantically using natural language query.
     * Results are filtered by the actor's authorization (per the canonical RAG policy).
     *
     * @param request the search request
     * @param username the authenticated username (from {@code Authentication.getName()}); may
     *        be null / blank / unknown, in which case the request is DENY
     */
    public RagSearchDto.SearchResponse search(RagSearchDto.SearchRequest request, String username) {
        long startTime = System.currentTimeMillis();

        // Validate query
        if (request.getQuery() == null || request.getQuery().trim().isEmpty()) {
            return buildEmptyResponse(request.getQuery(), startTime, "empty_query");
        }

        // Phase 4.1: ask the canonical policy FIRST. DENY short-circuits before any vector
        // search is invoked.
        RagAuthorizationDecision decision = ragAuthorizationPolicy.decideByUsername(
            username, actorContextService);

        if (decision.kind() == RagAuthorizationDecision.Kind.DENY) {
            if (log.isInfoEnabled()) {
                log.info("RAG search denied for username={} reason={}",
                    safeUsername(username), decision.reason());
            }
            return buildEmptyResponse(request.getQuery(), startTime, "denied");
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

            // Search using pgvector with authorization filter
            List<VectorSearchRepository.TicketSimilarity> similarities;

            if (decision.kind() == RagAuthorizationDecision.Kind.ALLOW_ALL) {
                if (!vectorSearchRepository.isPgvectorAvailable()) {
                    log.warn("pgvector not available, falling back to Java calculation");
                    return searchWithJavaFallback(request, startTime, decision);
                }
                similarities = vectorSearchRepository.searchBySimilarity(
                        queryVectorJson,
                        request.getMinScore(),
                        request.getLimit());
            } else {
                // DEPARTMENT_SCOPED
                Long departmentId = resolveDepartmentId(decision.departmentCode());
                if (departmentId == null) {
                    log.warn("RAG search could not resolve department code={}",
                        decision.departmentCode());
                    return buildEmptyResponse(request.getQuery(), startTime, "denied");
                }
                similarities = vectorSearchRepository.searchBySimilarityWithDepartmentFilter(
                        queryVectorJson,
                        request.getMinScore(),
                        request.getLimit(),
                        List.of(departmentId));
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
     * Search without authorization filter (for the ADMIN-only {@code /api/ai/search/admin}
     * endpoint).
     *
     * <p>This is the only entry point that is allowed to bypass the per-actor policy. The
     * authorization to reach it is enforced at the controller layer
     * ({@code @PreAuthorize("hasRole('ADMIN')")} on {@link RagSearchController#searchAdmin}).
     * The service itself does NOT perform any actor resolution, which means a null / blank
     * username MUST NOT reach this method; the controller passes no username.
     */
    public RagSearchDto.SearchResponse searchAdmin(RagSearchDto.SearchRequest request) {
        return searchAll(request);
    }

    /**
     * Search across all departments without consulting the per-actor policy.
     *
     * <p>This is the privileged path for {@code /api/ai/search/admin}. The controller layer
     * guarantees the caller has the ADMIN role; the service therefore does not resolve any
     * actor. No department filter is applied at the SQL boundary.
     */
    public RagSearchDto.SearchResponse searchAll(RagSearchDto.SearchRequest request) {
        long startTime = System.currentTimeMillis();

        if (request.getQuery() == null || request.getQuery().trim().isEmpty()) {
            return buildEmptyResponse(request.getQuery(), startTime, "empty_query");
        }

        try {
            float[] queryVector = embeddingService.generateEmbeddingInternal(request.getQuery());
            if (queryVector == null || queryVector.length == 0) {
                log.warn("Failed to generate embedding for query: {}", request.getQuery());
                return buildEmptyResponse(request.getQuery(), startTime, "embedding_failed");
            }

            String queryVectorJson = vectorToJson(queryVector);

            if (!vectorSearchRepository.isPgvectorAvailable()) {
                log.warn("pgvector not available, falling back to Java calculation");
                return searchAllJavaFallback(request, startTime);
            }

            List<VectorSearchRepository.TicketSimilarity> similarities = vectorSearchRepository.searchBySimilarity(
                    queryVectorJson,
                    request.getMinScore(),
                    request.getLimit());

            if (similarities.isEmpty()) {
                return buildEmptyResponse(request.getQuery(), startTime, "no_results");
            }

            List<Long> ticketIds = similarities.stream()
                    .map(VectorSearchRepository.TicketSimilarity::getTicketId)
                    .collect(Collectors.toList());

            Map<Long, Ticket> ticketMap = ticketRepository.findAllById(ticketIds).stream()
                    .collect(Collectors.toMap(Ticket::getId, t -> t));

            List<RagSearchDto.SearchResult> results = new ArrayList<>();
            for (VectorSearchRepository.TicketSimilarity sim : similarities) {
                Ticket ticket = ticketMap.get(sim.getTicketId());
                if (ticket != null) {
                    results.add(toSearchResult(ticket, sim.getSimilarity()));
                }
            }

            return RagSearchDto.SearchResponse.builder()
                    .query(request.getQuery())
                    .totalResults(results.size())
                    .results(results)
                    .metadata(buildMetadata(startTime, results.size(), "pgvector_similarity"))
                    .build();
        } catch (Exception e) {
            log.error("Admin semantic search failed: {}", e.getMessage(), e);
            return buildEmptyResponse(request.getQuery(), startTime, "error: " + e.getMessage());
        }
    }

    // ========================================================================
    // Fallback paths (Java calculation when pgvector is not available)
    // ========================================================================

    /**
     * Java fallback for the per-actor search path when pgvector is unavailable.
     *
     * <p>The department filter is materialized from the verdict directly, NOT from a list of
     * ids carried into the method. This keeps the fallback in sync with the SQL-boundary
     * logic in {@link #search(RagSearchDto.SearchRequest, String)}.
     */
    private RagSearchDto.SearchResponse searchWithJavaFallback(
            RagSearchDto.SearchRequest request,
            long startTime,
            RagAuthorizationDecision decision) {

        List<TicketEmbedding> completedEmbeddings = embeddingRepository
                .findByEmbeddingStatus(EmbeddingStatus.COMPLETED);

        if (completedEmbeddings.isEmpty()) {
            return buildEmptyResponse(request.getQuery(), startTime, "no_embeddings");
        }

        float[] queryVector;
        try {
            queryVector = embeddingService.generateEmbeddingInternal(request.getQuery());
        } catch (Exception e) {
            return buildEmptyResponse(request.getQuery(), startTime, "embedding_failed");
        }

        Long allowedDepartmentId = null;
        if (decision.kind() == RagAuthorizationDecision.Kind.DEPARTMENT_SCOPED) {
            allowedDepartmentId = resolveDepartmentId(decision.departmentCode());
            if (allowedDepartmentId == null) {
                log.warn("RAG Java fallback could not resolve department code={}",
                    decision.departmentCode());
                return buildEmptyResponse(request.getQuery(), startTime, "denied");
            }
        }
        // ALLOW_ALL => allowedDepartmentId=null (no department filter in the fallback loop)

        List<ScoredTicket> scoredTickets = new ArrayList<>();
        for (TicketEmbedding embedding : completedEmbeddings) {
            try {
                Ticket ticket = ticketRepository.findById(embedding.getTicketId()).orElse(null);
                if (ticket == null) continue;

                if (allowedDepartmentId != null) {
                    if (ticket.getDepartmentId() == null
                        || !allowedDepartmentId.equals(ticket.getDepartmentId())) {
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

        scoredTickets.sort((a, b) -> Double.compare(b.similarity, a.similarity));
        int limit = Math.min(request.getLimit(), scoredTickets.size());

        List<RagSearchDto.SearchResult> results = scoredTickets.stream()
                .limit(limit)
                .map(s -> toSearchResult(s.ticket, s.similarity))
                .toList();

        return RagSearchDto.SearchResponse.builder()
                .query(request.getQuery())
                .totalResults(results.size())
                .results(results)
                .metadata(buildMetadata(startTime, results.size(), "java_fallback"))
                .build();
    }

    /**
     * Java fallback for the unfiltered (ADMIN) search path when pgvector is unavailable.
     * No department filter is applied.
     */
    private RagSearchDto.SearchResponse searchAllJavaFallback(
            RagSearchDto.SearchRequest request, long startTime) {
        List<TicketEmbedding> completedEmbeddings = embeddingRepository
                .findByEmbeddingStatus(EmbeddingStatus.COMPLETED);

        if (completedEmbeddings.isEmpty()) {
            return buildEmptyResponse(request.getQuery(), startTime, "no_embeddings");
        }

        float[] queryVector;
        try {
            queryVector = embeddingService.generateEmbeddingInternal(request.getQuery());
        } catch (Exception e) {
            return buildEmptyResponse(request.getQuery(), startTime, "embedding_failed");
        }

        List<ScoredTicket> scoredTickets = new ArrayList<>();
        for (TicketEmbedding embedding : completedEmbeddings) {
            try {
                Ticket ticket = ticketRepository.findById(embedding.getTicketId()).orElse(null);
                if (ticket == null) continue;

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

        scoredTickets.sort((a, b) -> Double.compare(b.similarity, a.similarity));
        int limit = Math.min(request.getLimit(), scoredTickets.size());

        List<RagSearchDto.SearchResult> results = scoredTickets.stream()
                .limit(limit)
                .map(s -> toSearchResult(s.ticket, s.similarity))
                .toList();

        return RagSearchDto.SearchResponse.builder()
                .query(request.getQuery())
                .totalResults(results.size())
                .results(results)
                .metadata(buildMetadata(startTime, results.size(), "java_fallback"))
                .build();
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /**
     * Resolve a configured department code to its internal id, or {@code null} if no enabled
     * department matches.
     */
    private Long resolveDepartmentId(String departmentCode) {
        if (departmentCode == null) {
            return null;
        }
        return departmentRepository.findByCode(departmentCode)
            .filter(Department::isEnabled)
            .map(Department::getId)
            .orElse(null);
    }

    private static String safeUsername(String username) {
        if (username == null) return "<null>";
        if (username.isBlank()) return "<blank>";
        return username;
    }

    private String vectorToJson(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }

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