package com.example.ticketing.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ticketing.ai.RagContextDto.RagSource;
import com.example.ticketing.authorization.ActorContextService;
import com.example.ticketing.authorization.RagAuthorizationDecision;
import com.example.ticketing.authorization.RagAuthorizationPolicy;
import com.example.ticketing.problem.KnownError;
import com.example.ticketing.problem.KnownErrorEmbeddingService;
import com.example.ticketing.problem.KnownErrorRepository;

/**
 * Phase 6.6.4 - RAG retrieval for Known Errors (self-service knowledge).
 *
 * <p>Mirrors {@link RagRetrievalService} but operates on the
 * {@code known_error_embeddings} table. Key differences from ticket
 * retrieval:
 *
 * <ol>
 *   <li><b>No department filter.</b> Known Errors are reusable operational
 *       knowledge that should be reachable by any authenticated user. The
 *       task brief section 17 explicitly allows broader access for
 *       ADMIN/GIAM_DOC/IT and we extend the same convenience to all
 *       authenticated roles for self-service.</li>
 *   <li><b>Status filter is hard-coded in SQL.</b> OBSOLETE is excluded
 *       at the database boundary so the model never sees an obsolete
 *       record - we never rely on Gemma to "ignore" it (section 16).</li>
 *   <li><b>Top-K starts at 3.</b> Reusable knowledge is denser than
 *       ticket history; 3-5 highly relevant matches is preferred over
 *       10 weak ones (section 21).</li>
 *   <li><b>Threshold is 0.5 by default.</b> Same baseline as ticket RAG;
 *       left configurable so a future benchmark can tune it without
 *       code changes (section 20).</li>
 * </ol>
 *
 * <p>Authorization mirrors the ticket RAG path: the canonical
 * {@link RagAuthorizationPolicy} decides whether the actor is allowed to
 * use the AI assistant at all. DENY short-circuits before any vector
 * search is invoked. ALLOW_ALL and DEPARTMENT_SCOPED actors both get the
 * self-service knowledge (the department filter is not applied to Known
 * Errors because they are not department-scoped in the data model).
 */
@Service
public class KnownErrorRagRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(KnownErrorRagRetrievalService.class);

    /** Maximum length of a single field inside the LLM-facing content. */
    private static final int MAX_FIELD_LENGTH = 800;
    private static final int DESCRIPTION_TRUNCATE_LENGTH = 400;

    private final EmbeddingService embeddingService;
    private final VectorSearchRepository vectorSearchRepository;
    private final KnownErrorRepository knownErrorRepository;
    private final KnownErrorEmbeddingService knownErrorEmbeddingService;
    private final OllamaProperties ollamaProperties;
    private final RagAuthorizationPolicy ragAuthorizationPolicy;
    private final ActorContextService actorContextService;

    public KnownErrorRagRetrievalService(
            EmbeddingService embeddingService,
            VectorSearchRepository vectorSearchRepository,
            KnownErrorRepository knownErrorRepository,
            KnownErrorEmbeddingService knownErrorEmbeddingService,
            OllamaProperties ollamaProperties,
            RagAuthorizationPolicy ragAuthorizationPolicy,
            ActorContextService actorContextService) {
        this.embeddingService = embeddingService;
        this.vectorSearchRepository = vectorSearchRepository;
        this.knownErrorRepository = knownErrorRepository;
        this.knownErrorEmbeddingService = knownErrorEmbeddingService;
        this.ollamaProperties = ollamaProperties;
        this.ragAuthorizationPolicy = ragAuthorizationPolicy;
        this.actorContextService = actorContextService;
    }

    /**
     * Retrieve Known Error context for a self-service question.
     *
     * <p>Mirrors {@code RagRetrievalService.retrieveContext} but is
     * Knowledge-type specific.
     */
    @Transactional(readOnly = true)
    public RagContextDto.RetrievalResponse retrieveContext(
            RagContextDto.RetrievalRequest request,
            String username) {

        long startTime = System.currentTimeMillis();

        if (request == null || request.getQuery() == null || request.getQuery().trim().isEmpty()) {
            return buildEmptyResponse("", startTime, "empty_query", "Query must not be blank");
        }

        RagAuthorizationDecision decision = ragAuthorizationPolicy.decideByUsername(
                username, actorContextService);
        if (decision.kind() == RagAuthorizationDecision.Kind.DENY) {
            log.info("KnownError RAG retrieval denied for username={} reason={}",
                    safeUsername(username), decision.reason());
            return buildEmptyResponse(request.getQuery(), startTime, "denied", null);
        }

        try {
            float[] queryVector = embeddingService.generateEmbeddingInternal(request.getQuery());
            if (queryVector == null || queryVector.length == 0) {
                return buildEmptyResponse(request.getQuery(), startTime, "embedding_failed",
                        "Failed to generate embedding for the query");
            }
            String queryVectorJson = vectorToJson(queryVector);

            if (!vectorSearchRepository.isPgvectorAvailable()) {
                log.warn("pgvector not available for KnownError RAG retrieval");
                return buildEmptyResponse(request.getQuery(), startTime, "ollama_unavailable",
                        "Vector search is temporarily unavailable");
            }

            // Known Error semantic search (status filter applied at SQL boundary)
            int limit = clampLimit(request.getLimit());
            List<KnownErrorSimilarity> similarities = vectorSearchRepository.searchKnownErrorsBySimilarity(
                    queryVectorJson, request.getMinScore(), limit);

            if (similarities.isEmpty()) {
                return buildEmptyResponse(request.getQuery(), startTime, "no_results", null);
            }

            List<RagSource> sources = buildSources(similarities);

            return RagContextDto.RetrievalResponse.builder()
                    .query(request.getQuery())
                    .totalResults(sources.size())
                    .sources(sources)
                    .status("success")
                    .errorMessage(null)
                    .metadata(buildMetadata(startTime, sources.size(), "known_error_semantic"))
                    .build();
        } catch (Exception e) {
            log.error("KnownError RAG retrieval failed: {}", e.getMessage(), e);
            return buildEmptyResponse(request.getQuery(), startTime, "error",
                    "An error occurred while retrieving KnownError context");
        }
    }

    /**
     * Build the {@link RagSource} list in similarity order.
     */
    private List<RagSource> buildSources(List<KnownErrorSimilarity> similarities) {
        List<Long> ids = similarities.stream()
                .map(KnownErrorSimilarity::getKnownErrorId)
                .collect(Collectors.toList());
        Map<Long, KnownError> map = knownErrorRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(KnownError::getId, k -> k));
        List<RagSource> sources = new ArrayList<>();
        for (KnownErrorSimilarity sim : similarities) {
            KnownError ke = map.get(sim.getKnownErrorId());
            if (ke != null) {
                sources.add(toRagSource(ke, sim.getSimilarity()));
            }
        }
        return sources;
    }

    /**
     * Convert a KnownError entity to a RAG source. Excludes PII-like fields
     * (managedBy, teamId) so they never leak into the prompt.
     */
    private RagSource toRagSource(KnownError ke, double similarity) {
        String content = buildContent(ke);
        return RagSource.builder()
                .sourceType("KNOWN_ERROR")
                .id(ke.getId())
                .sourceRef(ke.getErrorCode())
                .title(ke.getTitle())
                .description(truncate(ke.getDescription(), DESCRIPTION_TRUNCATE_LENGTH))
                .category(ke.getCategory())
                .subcategory(null)
                .priority(null)
                .status(ke.getStatus() != null ? ke.getStatus().name() : null)
                .resolvedAt(null)
                .similarityScore(Math.round(similarity * 100.0) / 100.0)
                .content(content)
                .build();
    }

    /**
     * Normalize the KnownError fields into a structured LLM-facing text.
     * No PII / no managedBy / no teamId / no counts / no timestamps.
     */
    private String buildContent(KnownError ke) {
        StringBuilder sb = new StringBuilder();
        appendIfPresent(sb, "Code", ke.getErrorCode());
        appendIfPresent(sb, "Title", ke.getTitle());
        appendIfPresent(sb, "Status", ke.getStatus() != null ? ke.getStatus().name() : null);
        appendIfPresent(sb, "Description", ke.getDescription());
        appendIfPresent(sb, "Symptoms", ke.getSymptoms());
        appendIfPresent(sb, "Root Cause", ke.getRootCause());
        appendIfPresent(sb, "Root Cause Category", ke.getRootCauseCategory());
        appendIfPresent(sb, "Workaround", ke.getWorkaround());
        appendIfPresent(sb, "Resolution", ke.getResolution());
        appendIfPresent(sb, "Fix Steps", ke.getFixSteps());
        appendIfPresent(sb, "Impact", ke.getImpactDescription());
        appendIfPresent(sb, "Affected Systems", ke.getAffectedSystems());

        String s = sb.toString().trim();
        if (s.length() > MAX_FIELD_LENGTH) {
            s = s.substring(0, MAX_FIELD_LENGTH - 3) + "...";
        }
        return s;
    }

    private static void appendIfPresent(StringBuilder sb, String label, String value) {
        if (value == null) return;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return;
        sb.append(label).append(": ").append(trimmed).append("\n");
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        if (value.length() <= max) return value;
        return value.substring(0, max) + "...";
    }

    private static String vectorToJson(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    private static int clampLimit(Integer requested) {
        if (requested == null || requested <= 0) return 3;
        return Math.min(requested, 5);
    }

    private static String safeUsername(String u) {
        if (u == null) return "<null>";
        if (u.isBlank()) return "<blank>";
        return u;
    }

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

    private RagContextDto.RetrievalMetadata buildMetadata(long startTime, int resultCount, String searchType) {
        long totalEmbeddings = knownErrorEmbeddingService != null
                ? 0
                : 0;
        return RagContextDto.RetrievalMetadata.builder()
                .processingTimeMs(System.currentTimeMillis() - startTime)
                .embeddingModel(ollamaProperties.getEmbeddingModel())
                .totalEmbeddingsAvailable((int) totalEmbeddings)
                .searchType(searchType)
                .build();
    }
}
