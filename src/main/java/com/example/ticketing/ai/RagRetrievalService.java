package com.example.ticketing.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ticketing.authorization.ActorContextService;
import com.example.ticketing.authorization.RagAuthorizationDecision;
import com.example.ticketing.authorization.RagAuthorizationPolicy;
import com.example.ticketing.department.Department;
import com.example.ticketing.department.DepartmentRepository;
import com.example.ticketing.ticket.Ticket;
import com.example.ticketing.ticket.TicketEmbeddingRepository;
import com.example.ticketing.ticket.TicketRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * RAG Context Retrieval Service.
 *
 * <p>PHASE 4.1 (C-6 fix). This service provides read-only context retrieval for future LLM
 * generation. It does NOT generate answers - it only retrieves and normalizes relevant context.
 *
 * <p>AUTHORIZATION (PHASE 4.1). The service delegates the actor-vs-scope decision to the
 * canonical {@link RagAuthorizationPolicy}. There is no longer a duplicated
 * {@code getAuthorizedDepartmentIds} method; both this service and {@link RagSearchService}
 * read from the same policy bean, so they cannot disagree.
 *
 * <p>SQL BOUNDARY (SAFE PATTERN). The verdict is materialized into one of three SQL shapes:
 * <ul>
 *   <li>{@link RagAuthorizationDecision.Kind#ALLOW_ALL} - the unfiltered vector search path
 *       is taken ({@code searchBySimilarity}). Used for IT operators and ADMIN/GIAM_DOC.</li>
 *   <li>{@link RagAuthorizationDecision.Kind#DEPARTMENT_SCOPED} - the SQL filter list contains
 *       exactly the actor's department id ({@code searchBySimilarityWithDepartmentFilter}).
 *       Used for non-IT TRUONG_PHONG / NHAN_VIEN.</li>
 *   <li>{@link RagAuthorizationDecision.Kind#DENY} - no vector search is invoked; the service
 *       returns an empty response with status {@code "denied"}. This is the explicit fix for
 *       the previous {@code null}-means-no-filter collision.</li>
 * </ul>
 *
 * <p>SECURITY. Authorization is enforced at the SQL/vector-search boundary. Unauthorized
 * tickets are never loaded into the application and never reach the LLM context.
 */
@Service
public class RagRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RagRetrievalService.class);

    private static final int MAX_CONTENT_LENGTH = 500;
    private static final int DESCRIPTION_TRUNCATE_LENGTH = 300;

    private final EmbeddingService embeddingService;
    private final VectorSearchRepository vectorSearchRepository;
    private final TicketRepository ticketRepository;
    private final TicketEmbeddingRepository embeddingRepository;
    private final OllamaProperties ollamaProperties;
    private final RagAuthorizationPolicy ragAuthorizationPolicy;
    private final ActorContextService actorContextService;
    private final DepartmentRepository departmentRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public RagRetrievalService(
            EmbeddingService embeddingService,
            VectorSearchRepository vectorSearchRepository,
            TicketRepository ticketRepository,
            TicketEmbeddingRepository embeddingRepository,
            OllamaProperties ollamaProperties,
            RagAuthorizationPolicy ragAuthorizationPolicy,
            ActorContextService actorContextService,
            DepartmentRepository departmentRepository) {
        this.embeddingService = embeddingService;
        this.vectorSearchRepository = vectorSearchRepository;
        this.ticketRepository = ticketRepository;
        this.embeddingRepository = embeddingRepository;
        this.ollamaProperties = ollamaProperties;
        this.ragAuthorizationPolicy = ragAuthorizationPolicy;
        this.actorContextService = actorContextService;
        this.departmentRepository = departmentRepository;
    }

    /**
     * Retrieve RAG context for a query, scoped to the authenticated actor.
     *
     * <p>This method is read-only and does not modify any data.
     *
     * @param request The retrieval request containing query and parameters
     * @param username The authenticated username (from {@code Authentication.getName()})
     * @return RAG context response with relevant sources, or an empty response if the actor is
     *         not authorized
     */
    @Transactional(readOnly = true)
    public RagContextDto.RetrievalResponse retrieveContext(
            RagContextDto.RetrievalRequest request,
            String username) {

        long startTime = System.currentTimeMillis();

        // Validate query
        if (request.getQuery() == null || request.getQuery().trim().isEmpty()) {
            return buildEmptyResponse(request.getQuery(), startTime, "empty_query", "Query must not be blank");
        }

        // Phase 4.1: ask the canonical policy FIRST. DENY is enforced by short-circuiting
        // BEFORE the vector search is ever invoked. This is the fix for the previous
        // null-username-means-no-filter sharp edge characterized in Phase 4.0.
        RagAuthorizationDecision decision = ragAuthorizationPolicy.decideByUsername(
            username, actorContextService);

        if (decision.kind() == RagAuthorizationDecision.Kind.DENY) {
            if (log.isInfoEnabled()) {
                log.info("RAG retrieval denied for username={} reason={}",
                    safeUsername(username), decision.reason());
            }
            return buildEmptyResponse(request.getQuery(), startTime, "denied", null);
        }

        try {
            // Generate query embedding
            float[] queryVector = embeddingService.generateEmbeddingInternal(request.getQuery());

            if (queryVector == null || queryVector.length == 0) {
                log.warn("Failed to generate embedding for query");
                return buildEmptyResponse(request.getQuery(), startTime, "embedding_failed",
                        "Failed to generate embedding for the query");
            }

            // Convert to JSON string for pgvector
            String queryVectorJson = vectorToJson(queryVector);

            // Materialize the verdict into a SQL-boundary shape.
            List<VectorSearchRepository.TicketSimilarity> similarities;
            String searchType;

            if (decision.kind() == RagAuthorizationDecision.Kind.ALLOW_ALL) {
                if (!vectorSearchRepository.isPgvectorAvailable()) {
                    log.warn("pgvector not available for context retrieval");
                    return buildEmptyResponse(request.getQuery(), startTime, "ollama_unavailable",
                            "Vector search is temporarily unavailable");
                }
                similarities = vectorSearchRepository.searchBySimilarity(
                        queryVectorJson,
                        request.getMinScore(),
                        request.getLimit());
                searchType = "all_departments";
            } else {
                // DEPARTMENT_SCOPED: resolve the actor's department code to an id and apply the
                // SQL filter. The policy has already verified the actor has a department.
                Long departmentId = resolveDepartmentId(decision.departmentCode());
                if (departmentId == null) {
                    // The configured department for this actor is missing or disabled. Treat as
                    // DENY rather than risk an empty/unsafe filter.
                    log.warn("RAG retrieval could not resolve department code={} for username={}",
                        decision.departmentCode(), safeUsername(username));
                    return buildEmptyResponse(request.getQuery(), startTime, "denied", null);
                }
                similarities = vectorSearchRepository.searchBySimilarityWithDepartmentFilter(
                        queryVectorJson,
                        request.getMinScore(),
                        request.getLimit(),
                        List.of(departmentId));
                searchType = "department_filtered";
            }

            if (similarities.isEmpty()) {
                return buildEmptyResponse(request.getQuery(), startTime, "no_results", null);
            }

            // Fetch tickets maintaining similarity order
            List<RagContextDto.RagSource> sources = buildSources(similarities);

            long processingTime = System.currentTimeMillis() - startTime;

            return RagContextDto.RetrievalResponse.builder()
                    .query(request.getQuery())
                    .totalResults(sources.size())
                    .sources(sources)
                    .status("success")
                    .errorMessage(null)
                    .metadata(buildMetadata(startTime, sources.size(), searchType))
                    .build();

        } catch (Exception e) {
            log.error("Context retrieval failed: {}", e.getMessage(), e);
            return buildEmptyResponse(request.getQuery(), startTime, "error",
                    "An error occurred while retrieving context");
        }
    }

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

    /**
     * Safe username for logging (avoids leaking PII if the principal is unexpectedly long).
     */
    private static String safeUsername(String username) {
        if (username == null) return "<null>";
        if (username.isBlank()) return "<blank>";
        return username;
    }

    /**
     * Build RAG sources from similarity results.
     * Maintains the similarity order from pgvector.
     */
    private List<RagContextDto.RagSource> buildSources(
            List<VectorSearchRepository.TicketSimilarity> similarities) {

        // Extract ticket IDs
        List<Long> ticketIds = similarities.stream()
                .map(VectorSearchRepository.TicketSimilarity::getTicketId)
                .collect(Collectors.toList());

        // Fetch tickets into a map for O(1) lookup
        Map<Long, Ticket> ticketMap = ticketRepository.findAllById(ticketIds).stream()
                .collect(Collectors.toMap(Ticket::getId, t -> t));

        // Build sources in similarity order (iterate original list)
        List<RagContextDto.RagSource> sources = new ArrayList<>();
        for (VectorSearchRepository.TicketSimilarity sim : similarities) {
            Ticket ticket = ticketMap.get(sim.getTicketId());
            if (ticket != null) {
                sources.add(toRagSource(ticket, sim.getSimilarity()));
            }
        }

        return sources;
    }

    /**
     * Convert Ticket entity to RAG source.
     * Excludes PII fields (requesterName, requesterUsername, requesterEmail).
     */
    private RagContextDto.RagSource toRagSource(Ticket ticket, double similarity) {
        String content = buildContent(ticket);

        return RagContextDto.RagSource.builder()
                .sourceType("TICKET")
                .sourceRef(ticket.getTicketNumber())
                .title(ticket.getTitle())
                .description(truncateDescription(ticket.getDescription(), DESCRIPTION_TRUNCATE_LENGTH))
                .category(ticket.getCategoryName())
                .subcategory(ticket.getSubcategoryName())
                .priority(ticket.getPriority() != null ? ticket.getPriority().name() : null)
                .status(ticket.getStatus() != null ? ticket.getStatus().name() : null)
                .resolvedAt(ticket.getResolvedAt())
                .similarityScore(Math.round(similarity * 100.0) / 100.0)
                .content(content)
                .build();
    }

    /**
     * Build normalized content for LLM context.
     * Combines ticket fields into a structured text format.
     */
    private String buildContent(Ticket ticket) {
        StringBuilder sb = new StringBuilder();

        sb.append("Title: ").append(ticket.getTitle()).append("\n\n");

        sb.append("Category: ");
        if (ticket.getCategoryName() != null) {
            sb.append(ticket.getCategoryName());
            if (ticket.getSubcategoryName() != null) {
                sb.append(" / ").append(ticket.getSubcategoryName());
            }
        }
        sb.append("\n");

        sb.append("Priority: ").append(
                ticket.getPriority() != null ? ticket.getPriority().name() : "N/A").append("\n");

        sb.append("Status: ").append(
                ticket.getStatus() != null ? ticket.getStatus().name() : "N/A").append("\n\n");

        sb.append("Description:\n").append(
                truncateDescription(ticket.getDescription(), 200)).append("\n\n");

        sb.append("Resolution: ");
        if (ticket.getStatus() != null &&
            (ticket.getStatus().name().equals("RESOLVED") ||
             ticket.getStatus().name().equals("CLOSED"))) {
            sb.append("Ticket has been resolved.");
        } else {
            sb.append("Not yet resolved.");
        }

        String content = sb.toString();

        // Truncate if exceeds max length
        if (content.length() > MAX_CONTENT_LENGTH) {
            content = content.substring(0, MAX_CONTENT_LENGTH - 3) + "...";
        }

        return content;
    }

    /**
     * Truncate description to specified length.
     */
    private String truncateDescription(String description, int maxLength) {
        if (description == null) return "";
        if (description.length() <= maxLength) return description;
        return description.substring(0, maxLength) + "...";
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
     * Build metadata for the response.
     */
    private RagContextDto.RetrievalMetadata buildMetadata(
            long startTime, int resultCount, String searchType) {

        long totalEmbeddings = embeddingRepository.count();

        return RagContextDto.RetrievalMetadata.builder()
                .processingTimeMs(System.currentTimeMillis() - startTime)
                .embeddingModel(ollamaProperties.getEmbeddingModel())
                .totalEmbeddingsAvailable((int) totalEmbeddings)
                .searchType(searchType)
                .build();
    }

    /**
     * Build an empty response for error cases.
     */
    private RagContextDto.RetrievalResponse buildEmptyResponse(
            String query, long startTime, String status, String errorMessage) {

        return RagContextDto.RetrievalResponse.builder()
                .query(query)
                .totalResults(0)
                .sources(List.of())
                .status(status)
                .errorMessage(errorMessage)
                .metadata(buildMetadata(startTime, 0, status))
                .build();
    }
}