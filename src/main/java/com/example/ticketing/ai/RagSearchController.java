package com.example.ticketing.ai;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.ticketing.auth.UserRole;

import jakarta.validation.Valid;

/**
 * REST Controller for RAG (Retrieval-Augmented Generation) semantic search.
 * Provides endpoints for searching tickets using natural language queries.
 * 
 * Authorization: Results are filtered based on user's department access.
 */
@RestController
@RequestMapping("/api/ai")
public class RagSearchController {

    private final RagSearchService ragSearchService;
    private final EmbeddingSchedulerService embeddingSchedulerService;

    public RagSearchController(
            RagSearchService ragSearchService,
            EmbeddingSchedulerService embeddingSchedulerService) {
        this.ragSearchService = ragSearchService;
        this.embeddingSchedulerService = embeddingSchedulerService;
    }

    /**
     * Search tickets semantically using natural language query.
     * Results are filtered based on user's authorization (department access).
     * 
     * - ADMIN: Can search all tickets
     * - Others: Only tickets from their department
     * 
     * @param query Natural language query
     * @param limit Maximum number of results (default 5)
     * @param minScore Minimum similarity score 0.0-1.0 (default 0.5)
     * @param authentication The authenticated principal, used to obtain the username
     * @return List of semantically similar tickets (filtered by authorization)
     */
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN')")
    public ResponseEntity<RagSearchDto.SearchResponse> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int limit,
            @RequestParam(defaultValue = "0.5") double minScore,
            Authentication authentication) {

        // Username MUST come from the authenticated principal established by Spring Security.
        // JwtAuthenticationFilter stores the principal as a String, so @AuthenticationPrincipal
        // UserDetails resolves to null and department authorization would be bypassed.
        String username = authentication != null ? authentication.getName() : null;

        RagSearchDto.SearchRequest request = RagSearchDto.SearchRequest.builder()
                .query(query)
                .limit(limit)
                .minScore(minScore)
                .build();

        return ResponseEntity.ok(ragSearchService.search(request, username));
    }

    /**
     * Search tickets semantically using POST with request body.
     */
    @PostMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN')")
    public ResponseEntity<RagSearchDto.SearchResponse> searchPost(
            @Valid @RequestBody RagSearchDto.SearchRequest request,
            Authentication authentication) {

        // See GET /search: principal is a String, so Authentication#getName() is required.
        String username = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(ragSearchService.search(request, username));
    }

    /**
     * Admin-only: Search all tickets without department filter.
     */
    @GetMapping("/search/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RagSearchDto.SearchResponse> searchAdmin(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int limit,
            @RequestParam(defaultValue = "0.5") double minScore) {

        RagSearchDto.SearchRequest request = RagSearchDto.SearchRequest.builder()
                .query(query)
                .limit(limit)
                .minScore(minScore)
                .build();

        return ResponseEntity.ok(ragSearchService.searchAdmin(request));
    }

    /**
     * Get embedding status and statistics.
     * Shows how many tickets have embeddings generated.
     */
    @GetMapping("/embeddings/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'TRUONG_PHONG')")
    public ResponseEntity<RagSearchDto.EmbeddingStatusResponse> getEmbeddingStatus() {
        return ResponseEntity.ok(ragSearchService.getEmbeddingStatus());
    }

    /**
     * Trigger manual embedding job.
     * Admin can manually trigger processing of pending embeddings.
     */
    @PostMapping("/embeddings/process")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> triggerEmbeddingJob() {
        embeddingSchedulerService.triggerEmbeddingJob();
        return ResponseEntity.ok("Embedding job triggered");
    }
}
