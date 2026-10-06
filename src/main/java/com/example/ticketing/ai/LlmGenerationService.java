package com.example.ticketing.ai;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.ticketing.ai.AiAssistantDto.AnswerResponse;
import com.example.ticketing.ai.AiAssistantDto.ResponseMetadata;
import com.example.ticketing.ai.AiAssistantDto.SourceReference;
import com.example.ticketing.ai.RagContextDto.RagSource;

/**
 * LLM Generation Service - Orchestration layer for AI assistant.
 * 
 * This service orchestrates the flow between:
 * - RagRetrievalService (authorized context retrieval)
 * - PromptBuilder (prompt construction)
 * - OllamaChatService (LLM generation)
 * 
 * Security:
 * - Authorization is handled by RagRetrievalService before this service
 * - similarityScore is NOT passed to PromptBuilder
 * - Only safe metadata is exposed in the API response
 * - No raw prompts, answers, or PII are logged
 * 
 * This service does NOT perform authorization. It trusts the existing
 * authorization already applied by RagRetrievalService.
 */
@Service
public class LlmGenerationService {

    private static final Logger log = LoggerFactory.getLogger(LlmGenerationService.class);

    private final RagRetrievalService ragRetrievalService;
    private final PromptBuilder promptBuilder;
    private final OllamaChatService ollamaChatService;
    private final OllamaProperties ollamaProperties;

    public LlmGenerationService(
            RagRetrievalService ragRetrievalService,
            PromptBuilder promptBuilder,
            OllamaChatService ollamaChatService,
            OllamaProperties ollamaProperties) {
        this.ragRetrievalService = ragRetrievalService;
        this.promptBuilder = promptBuilder;
        this.ollamaChatService = ollamaChatService;
        this.ollamaProperties = ollamaProperties;
    }

    /**
     * Generate an AI assistant answer.
     * 
     * Flow:
     * 1. Retrieve authorized RAG context
     * 2. Build prompt using PromptBuilder
     * 3. Send to OllamaChatService
     * 4. Build and return response
     * 
     * @param request The validated request with question and parameters
     * @param username The authenticated username (for authorization)
     * @return AnswerResponse with generated answer and metadata
     * @throws LlmGenerationException if Ollama fails
     */
    public AnswerResponse generateAnswer(AiAssistantDto.AskRequest request, String username) {
        long startTime = System.currentTimeMillis();
        
        // Step 1: Retrieve authorized RAG context
        // Authorization is handled by RagRetrievalService
        RagContextDto.RetrievalRequest retrievalRequest = new RagContextDto.RetrievalRequest(
                request.getQuestion(),
                request.getLimit(),
                request.getMinScore()
        );
        
        RagContextDto.RetrievalResponse ragResponse = ragRetrievalService.retrieveContext(
                retrievalRequest, username);
        
        // Extract sources for prompt (this does NOT include similarityScore in the prompt)
        List<RagSource> sourcesForPrompt = ragResponse.getSources();
        boolean hasContext = sourcesForPrompt != null && !sourcesForPrompt.isEmpty();
        
        // Log safe metadata only
        log.info("RAG retrieval: queryLength={}, contextCount={}", 
                request.getQuestion().length(), 
                ragResponse.getTotalResults());
        
        // Step 2: Build prompt
        // PromptBuilder handles trust boundary, ticket context, and action boundary
        List<OllamaChatService.ChatMessage> messages = promptBuilder.build(
                sourcesForPrompt, 
                request.getQuestion()
        );
        
        // Step 3: Send to Ollama
        // OllamaChatService handles /api/chat, timeout, temperature, and error handling
        String answer = ollamaChatService.generate(messages);
        
        long processingTimeMs = System.currentTimeMillis() - startTime;
        
        // Step 4: Build response
        // similarityScore is included in source metadata for API response
        List<SourceReference> sourceReferences = buildSourceReferences(sourcesForPrompt);
        
        ResponseMetadata metadata = new ResponseMetadata(
                processingTimeMs,
                ollamaProperties.getChatModel(),
                ragResponse.getMetadata() != null ? ragResponse.getMetadata().getSearchType() : "unknown"
        );
        
        AnswerResponse response = AnswerResponse.builder()
                .answer(answer)
                .contextCount(ragResponse.getTotalResults())
                .sources(sourceReferences)
                .hasContext(hasContext)
                .metadata(metadata)
                .build();
        
        // Log safe completion info
        log.info("LLM generation completed: contextCount={}, model={}, duration={}ms",
                ragResponse.getTotalResults(),
                ollamaProperties.getChatModel(),
                processingTimeMs);
        
        return response;
    }

    /**
     * Build source references for API response.
     * 
     * Note: similarityScore is intentionally included here for frontend display.
     * It was NOT passed to PromptBuilder.
     */
    private List<SourceReference> buildSourceReferences(List<RagSource> sources) {
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }
        
        List<SourceReference> references = new ArrayList<>();
        for (RagSource source : sources) {
            SourceReference ref = new SourceReference(
                    source.getSourceRef(),
                    source.getTitle(),
                    source.getSimilarityScore()  // safe for API response
            );
            references.add(ref);
        }
        
        return references;
    }
}
