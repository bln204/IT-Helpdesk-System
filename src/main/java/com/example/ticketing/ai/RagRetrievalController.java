package com.example.ticketing.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * REST Controller for RAG Context Retrieval.
 * 
 * This controller provides an endpoint for retrieving structured RAG context
 * for future LLM generation. It does NOT generate answers - it only retrieves
 * and normalizes relevant context.
 * 
 * Authorization:
 * - ADMIN, GIAM_DOC, TRUONG_PHONG, NHAN_VIEN can access this endpoint
 * - Results are filtered by user's department access
 */
@RestController
@RequestMapping("/api/ai")
@Validated
public class RagRetrievalController {

    private static final Logger log = LoggerFactory.getLogger(RagRetrievalController.class);

    private final RagRetrievalService ragRetrievalService;

    public RagRetrievalController(RagRetrievalService ragRetrievalService) {
        this.ragRetrievalService = ragRetrievalService;
    }

    /**
     * Retrieve RAG context for a query.
     * 
     * This endpoint performs semantic search and returns structured context
     * suitable for LLM generation. Results are filtered based on the user's
     * authorization (department access).
     * 
     * Authorization:
     * - ADMIN: Can retrieve context from all departments
     * - GIAM_DOC: Can retrieve context from all departments
     * - TRUONG_PHONG: Can retrieve context from own department only
     * - NHAN_VIEN: Can retrieve context from own department only
     * 
     * @param request The retrieval request containing query and parameters
     * @param authentication The authenticated principal, used to obtain the username
     * @return RAG context response with relevant sources
     */
    @PostMapping("/context")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN')")
    public ResponseEntity<RagContextDto.RetrievalResponse> getContext(
            @Valid @RequestBody RagContextDto.RetrievalRequest request,
            Authentication authentication) {

        // Username MUST come from the authenticated principal established by Spring Security.
        // JwtAuthenticationFilter stores the principal as a String, so @AuthenticationPrincipal
        // UserDetails resolves to null and department authorization would be bypassed.
        String username = authentication != null ? authentication.getName() : null;
        
        // Log retrieval request (without query content for security)
        if (log.isInfoEnabled()) {
            log.info("RAG context request from user: {}, query length: {}, limit: {}", 
                    username, 
                    request.getQuery() != null ? request.getQuery().length() : 0,
                    request.getLimit());
        }
        
        RagContextDto.RetrievalResponse response = ragRetrievalService.retrieveContext(request, username);
        
        // Log response summary (without ticket content)
        if (log.isInfoEnabled()) {
            log.info("RAG context response: status={}, results={}, user={}", 
                    response.getStatus(), 
                    response.getTotalResults(),
                    username);
        }
        
        return ResponseEntity.ok(response);
    }
}
