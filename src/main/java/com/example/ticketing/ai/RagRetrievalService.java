package com.example.ticketing.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ticketing.auth.UserAccount;
import com.example.ticketing.auth.UserAccountRepository;
import com.example.ticketing.ticket.Ticket;
import com.example.ticketing.ticket.TicketEmbeddingRepository;
import com.example.ticketing.ticket.TicketRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * RAG Context Retrieval Service.
 * 
 * This service provides read-only context retrieval for future LLM generation.
 * It does NOT generate answers - it only retrieves and normalizes relevant context.
 * 
 * Authorization:
 * - ADMIN, GIAM_DOC: Access to all departments
 * - TRUONG_PHONG, NHAN_VIEN: Access to own department only
 * 
 * Security:
 * - Authorization is enforced at the SQL/vector-search boundary
 * - Unauthorized tickets are never loaded into the application
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
    private final UserAccountRepository userAccountRepository;
    private final OllamaProperties ollamaProperties;

    @PersistenceContext
    private EntityManager entityManager;

    public RagRetrievalService(
            EmbeddingService embeddingService,
            VectorSearchRepository vectorSearchRepository,
            TicketRepository ticketRepository,
            TicketEmbeddingRepository embeddingRepository,
            UserAccountRepository userAccountRepository,
            OllamaProperties ollamaProperties) {
        this.embeddingService = embeddingService;
        this.vectorSearchRepository = vectorSearchRepository;
        this.ticketRepository = ticketRepository;
        this.embeddingRepository = embeddingRepository;
        this.userAccountRepository = userAccountRepository;
        this.ollamaProperties = ollamaProperties;
    }

    /**
     * Retrieve RAG context for a query.
     * 
     * This method is read-only and does not modify any data.
     * 
     * @param request The retrieval request containing query and parameters
     * @param username The authenticated username
     * @return RAG context response with relevant sources
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
            
            // Get authorized department IDs
            List<Long> allowedDepartmentIds = getAuthorizedDepartmentIds(username);
            
            // Perform vector search with authorization
            List<VectorSearchRepository.TicketSimilarity> similarities;
            String searchType;
            
            if (allowedDepartmentIds != null && !allowedDepartmentIds.isEmpty()) {
                // Department-restricted search
                similarities = vectorSearchRepository.searchBySimilarityWithDepartmentFilter(
                        queryVectorJson,
                        request.getMinScore(),
                        request.getLimit(),
                        allowedDepartmentIds);
                searchType = "department_filtered";
            } else {
                // Admin/GiamDoc - no filter
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
