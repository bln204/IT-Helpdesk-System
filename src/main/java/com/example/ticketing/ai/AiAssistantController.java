package com.example.ticketing.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.ticketing.ai.AiAssistantDto.AnswerResponse;

import jakarta.validation.Valid;

/**
 * REST Controller for AI Assistant (LLM/RAG Generation).
 * 
 * This controller exposes the AI assistant endpoint that generates answers
 * using authorized RAG context from Ollama/Qwen3.
 * 
 * Architecture:
 * Controller -> LlmGenerationService -> RagRetrievalService -> PromptBuilder -> OllamaChatService
 * 
 * Authorization:
 * - ADMIN, GIAM_DOC, TRUONG_PHONG, NHAN_VIEN can access this endpoint
 * - Department-level authorization is handled by RagRetrievalService
 */
@RestController
@RequestMapping("/api/ai")
@Validated
public class AiAssistantController {

    private static final Logger log = LoggerFactory.getLogger(AiAssistantController.class);

    private final LlmGenerationService llmGenerationService;

    public AiAssistantController(LlmGenerationService llmGenerationService) {
        this.llmGenerationService = llmGenerationService;
    }

    /**
     * Ask the AI assistant a question.
     * 
     * The assistant uses authorized RAG context (filtered by department access)
     * to answer questions about tickets and general IT topics.
     * 
     * Authorization:
     * - ADMIN: Full access to all departments' ticket context
     * - GIAM_DOC: Full access to all departments' ticket context
     * - TRUONG_PHONG: Access to own department's ticket context only
     * - NHAN_VIEN: Access to own department's ticket context only
     * 
     * @param request The question request with optional limit and minScore parameters
     * @param userDetails The authenticated user (for authorization)
     * @param authentication The authenticated principal, used to obtain the username for logging
     * @return AI assistant answer with context metadata
     */
    @PostMapping("/ask")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN')")
    public ResponseEntity<AnswerResponse> askQuestion(
            @Valid @RequestBody AiAssistantDto.AskRequest request,
            @AuthenticationPrincipal UserDetails userDetails,
            Authentication authentication) {

        // Username MUST come from the authenticated principal established by Spring Security.
        // JwtAuthenticationFilter stores the principal as a String, so @AuthenticationPrincipal
        // UserDetails resolves to null; Authentication#getName() is the project's established
        // idiom and always resolves. Never source this from the request DTO or client input.
        String username = (authentication != null) ? authentication.getName() : null;

        // Apply API defaults when client omits optional parameters
        // Jackson deserialization omits field initializers for absent JSON properties
        if (request.getLimit() == null) {
            request.setLimit(5);
        }
        if (request.getMinScore() == null) {
            request.setMinScore(0.5);
        }

        // Log safe request metadata only (no question content)
        if (log.isInfoEnabled()) {
            log.info("AI assistant request from user: {}, question length: {}, limit: {}",
                    username,
                    request.getQuestion() != null ? request.getQuestion().length() : 0,
                    request.getLimit());
        }

        // Delegate entirely to LlmGenerationService
        // Exception handling is handled by GlobalExceptionHandler in Step 5.6
        AnswerResponse response = llmGenerationService.generateAnswer(request, username);

        // Log safe response metadata
        if (log.isInfoEnabled()) {
            log.info("AI assistant response: contextCount={}, hasContext={}, user={}",
                    response.getContextCount(),
                    response.isHasContext(),
                    username);
        }

        return ResponseEntity.ok(response);
    }
}
