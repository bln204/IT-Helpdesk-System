package com.example.ticketing.ai;

import java.util.List;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTOs for AI Assistant (Phase 5 - Qwen3/RAG LLM Integration).
 * 
 * These DTOs represent the request/response structure for the AI assistant
 * endpoint that generates answers using authorized RAG context.
 */
public class AiAssistantDto {

    private AiAssistantDto() {
        // Utility class
    }

    // ============================================================
    // REQUEST DTO
    // ============================================================

    /**
     * Request DTO for AI assistant question.
     */
    public static class AskRequest {

        /**
         * The user's question in natural language.
         * Can be about specific tickets or general IT knowledge.
         */
        @NotBlank(message = "Question must not be blank")
        @Size(min = 5, max = 500, message = "Question must be between 5 and 500 characters")
        private String question;

        /**
         * Maximum number of context sources (tickets) to retrieve.
         * Default: 5
         * Range: 1-10
         */
        @Min(value = 1, message = "Limit must be at least 1")
        @Max(value = 10, message = "Limit must not exceed 10")
        private Integer limit = 5;

        /**
         * Minimum similarity score threshold (0.0 - 1.0).
         * Only results with similarity >= minScore will be included.
         * Default: 0.5
         */
        @DecimalMin(value = "0.0", message = "MinScore must be at least 0.0")
        @DecimalMax(value = "1.0", message = "MinScore must not exceed 1.0")
        private Double minScore = 0.5;

        public AskRequest() {
        }

        public AskRequest(String question, Integer limit, Double minScore) {
            this.question = question;
            this.limit = limit;
            this.minScore = minScore;
        }

        public String getQuestion() {
            return question;
        }

        public void setQuestion(String question) {
            this.question = question;
        }

        public Integer getLimit() {
            return limit;
        }

        public void setLimit(Integer limit) {
            this.limit = limit;
        }

        public Double getMinScore() {
            return minScore;
        }

        public void setMinScore(Double minScore) {
            this.minScore = minScore;
        }
    }

    // ============================================================
    // RESPONSE DTO
    // ============================================================

    /**
     * Response DTO for AI assistant answer.
     */
    public static class AnswerResponse {

        /**
         * The generated answer from the LLM.
         * May include information from retrieved tickets and/or general IT knowledge.
         */
        private String answer;

        /**
         * Number of context sources (tickets) used in generation.
         */
        private int contextCount;

        /**
         * List of source tickets used for context.
         * Contains retrieval metadata (similarityScore) for frontend display.
         */
        private List<SourceReference> sources;

        /**
         * Indicates whether any ticket context was available.
         * Useful for frontend to show different messages.
         */
        private boolean hasContext;

        /**
         * Processing metadata.
         */
        private ResponseMetadata metadata;

        public AnswerResponse() {
        }

        public AnswerResponse(String answer, int contextCount, List<SourceReference> sources,
                             boolean hasContext, ResponseMetadata metadata) {
            this.answer = answer;
            this.contextCount = contextCount;
            this.sources = sources;
            this.hasContext = hasContext;
            this.metadata = metadata;
        }

        public String getAnswer() {
            return answer;
        }

        public void setAnswer(String answer) {
            this.answer = answer;
        }

        public int getContextCount() {
            return contextCount;
        }

        public void setContextCount(int contextCount) {
            this.contextCount = contextCount;
        }

        public List<SourceReference> getSources() {
            return sources;
        }

        public void setSources(List<SourceReference> sources) {
            this.sources = sources;
        }

        public boolean isHasContext() {
            return hasContext;
        }

        public void setHasContext(boolean hasContext) {
            this.hasContext = hasContext;
        }

        public ResponseMetadata getMetadata() {
            return metadata;
        }

        public void setMetadata(ResponseMetadata metadata) {
            this.metadata = metadata;
        }

        // Builder pattern
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String answer;
            private int contextCount;
            private List<SourceReference> sources;
            private boolean hasContext;
            private ResponseMetadata metadata;

            public Builder answer(String answer) {
                this.answer = answer;
                return this;
            }

            public Builder contextCount(int contextCount) {
                this.contextCount = contextCount;
                return this;
            }

            public Builder sources(List<SourceReference> sources) {
                this.sources = sources;
                return this;
            }

            public Builder hasContext(boolean hasContext) {
                this.hasContext = hasContext;
                return this;
            }

            public Builder metadata(ResponseMetadata metadata) {
                this.metadata = metadata;
                return this;
            }

            public AnswerResponse build() {
                return new AnswerResponse(answer, contextCount, sources, hasContext, metadata);
            }
        }
    }

    // ============================================================
    // SOURCE REFERENCE (for API response only, NOT for LLM prompt)
    // ============================================================

    /**
     * Reference to a source ticket used for context.
     * Contains similarity metadata for frontend display.
     * 
     * NOTE: similarityScore is included here for frontend display but
     * is NOT sent to the LLM prompt (handled by PromptBuilder).
     */
    public static class SourceReference {

        /**
         * Ticket number (business identifier).
         */
        private String ticketNumber;

        /**
         * Ticket title.
         */
        private String title;

        /**
         * Similarity score from vector search (0.0 - 1.0).
         * This is retrieval metadata, NOT LLM context.
         */
        private double similarityScore;

        public SourceReference() {
        }

        public SourceReference(String ticketNumber, String title, double similarityScore) {
            this.ticketNumber = ticketNumber;
            this.title = title;
            this.similarityScore = similarityScore;
        }

        public String getTicketNumber() {
            return ticketNumber;
        }

        public void setTicketNumber(String ticketNumber) {
            this.ticketNumber = ticketNumber;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public double getSimilarityScore() {
            return similarityScore;
        }

        public void setSimilarityScore(double similarityScore) {
            this.similarityScore = similarityScore;
        }
    }

    // ============================================================
    // RESPONSE METADATA
    // ============================================================

    /**
     * Metadata about the response generation.
     */
    public static class ResponseMetadata {

        /**
         * Total processing time in milliseconds.
         */
        private long processingTimeMs;

        /**
         * LLM model used for generation.
         */
        private String model;

        /**
         * Search type applied.
         * e.g., "department_filtered", "all_departments", "no_results"
         */
        private String searchType;

        public ResponseMetadata() {
        }

        public ResponseMetadata(long processingTimeMs, String model, String searchType) {
            this.processingTimeMs = processingTimeMs;
            this.model = model;
            this.searchType = searchType;
        }

        public long getProcessingTimeMs() {
            return processingTimeMs;
        }

        public void setProcessingTimeMs(long processingTimeMs) {
            this.processingTimeMs = processingTimeMs;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getSearchType() {
            return searchType;
        }

        public void setSearchType(String searchType) {
            this.searchType = searchType;
        }
    }
}
