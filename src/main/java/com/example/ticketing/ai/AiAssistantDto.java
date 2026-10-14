package com.example.ticketing.ai;

import java.util.List;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTOs for AI Assistant (Phase 5 - Gemma3/RAG LLM Integration).
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

        /**
         * Optional. If present, the question is appended to this
         * existing conversation thread (creating it if it does not
         * exist or belongs to a different user). If null/empty, a
         * new thread is created and its id is returned in the
         * response.
         */
        private String sessionId;

        public AskRequest() {
        }

        public AskRequest(String question, Integer limit, Double minScore) {
            this.question = question;
            this.limit = limit;
            this.minScore = minScore;
        }

        public AskRequest(String question, Integer limit, Double minScore, String sessionId) {
            this.question = question;
            this.limit = limit;
            this.minScore = minScore;
            this.sessionId = sessionId;
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

        public String getSessionId() {
            return sessionId;
        }

        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
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

        /**
         * Conversation thread id. Present on every response so the
         * client can resume the same thread on the next ask.
         */
        private String sessionId;

        /**
         * Optional structured action. Currently one of:
         *   - CONFIRM_TICKET_DRAFT: server produced a TicketDraft
         *     and is asking the user to confirm before creation.
         *     The draft JSON lives in actionPayload.
         *   - ASK_MISSING_FIELD: server is asking the user to
         *     supply a specific ticket field (title, description,
         *     priority, category, etc.). The missing field name and
         *     a question live in actionPayload.
         *   - TICKET_CREATED: server successfully created a ticket
         *     from a previously-confirmed draft. actionPayload
         *     contains the new ticket number.
         *   - null/absent: normal text answer.
         */
        private String action;

        /**
         * JSON object carrying data for {@link #action}. Shape
         * depends on action (see above).
         */
        private String actionPayload;

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

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }

        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }

        public String getActionPayload() { return actionPayload; }
        public void setActionPayload(String actionPayload) { this.actionPayload = actionPayload; }

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
            private String sessionId;
            private String action;
            private String actionPayload;

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

            public Builder sessionId(String sessionId) {
                this.sessionId = sessionId;
                return this;
            }

            public Builder action(String action) {
                this.action = action;
                return this;
            }

            public Builder actionPayload(String actionPayload) {
                this.actionPayload = actionPayload;
                return this;
            }

            public AnswerResponse build() {
                AnswerResponse r = new AnswerResponse(answer, contextCount, sources,
                        hasContext, metadata);
                r.setSessionId(sessionId);
                r.setAction(action);
                r.setActionPayload(actionPayload);
                return r;
            }
        }
    }

    // ============================================================
    // SOURCE REFERENCE (for API response only, NOT for LLM prompt)
    // ============================================================

    /**
     * Reference to a source (Ticket or Known Error) used for context.
     * Contains similarity metadata for frontend display.
     *
     * <p>PHASE 6.6.4 - extended to distinguish KNOWN_ERROR sources
     * from TICKET sources. For a KNOWN_ERROR source:
     * <ul>
     *   <li>{@link #sourceType} = "KNOWN_ERROR"</li>
     *   <li>{@link #knownErrorCode} is the business code (e.g. "KE-001")</li>
     *   <li>{@link #ticketNumber} is {@code null} - it is reserved
     *       for TICKET sources only, so a frontend can safely treat
     *       it as the discriminator</li>
     * </ul>
     * Backward compatibility: existing clients reading
     * {@code ticketNumber} on TICKET sources continue to work unchanged.
     *
     * <p>PHASE: AI System-Wide Access - extended again to carry
     * {@code ASSET}, {@code USER}, {@code TEAM}, {@code DEPARTMENT},
     * {@code CHANGE_REQUEST}, and {@code PROBLEM} references. For each
     * new domain the corresponding business-code field is populated
     * and the legacy fields remain {@code null} so a frontend can use
     * {@link #sourceType} as the single discriminator.
     */
    public static class SourceReference {

        /**
         * Source discriminator. One of:
         * {@code "TICKET"}, {@code "KNOWN_ERROR"}, {@code "ASSET"},
         * {@code "USER"}, {@code "TEAM"}, {@code "DEPARTMENT"},
         * {@code "CHANGE_REQUEST"}, {@code "PROBLEM"},
         * {@code "RELATIONAL"}.
         */
        private String sourceType;

        /**
         * CP-5: Database primary key. Populated for ASSET sources
         * by the CP-5 retriever path. Null for legacy ASSET sources
         * and for all non-ASSET source types.
         *
         * <p>Used by the continuation path to construct the
         * {@code includedInResponseIds} manifest field. The
         * manifest never relies on the JSON-serialized
         * {@link #assetNumber} for exclusion.
         */
        private Long id;

        /**
         * Ticket number (business identifier). Only populated for
         * TICKET sources. {@code null} for all other source types.
         */
        private String ticketNumber;

        /**
         * Known Error code (business identifier, e.g. KE-001).
         * Only populated for KNOWN_ERROR sources. {@code null} for
         * all other source types.
         */
        private String knownErrorCode;

        /**
         * Asset number (business identifier, e.g. AST-1A2B3C4D).
         * Only populated for ASSET sources. {@code null} for all
         * other source types. PHASE: AI System-Wide Access.
         */
        private String assetNumber;

        /**
         * Username (business identifier). Only populated for USER
         * sources. {@code null} for all other source types.
         * PHASE: AI System-Wide Access.
         */
        private String username;

        /**
         * Team code (business identifier). Only populated for TEAM
         * sources. {@code null} for all other source types.
         * PHASE: AI System-Wide Access.
         */
        private String teamCode;

        /**
         * Department code (business identifier). Only populated for
         * DEPARTMENT sources. {@code null} for all other source
         * types. PHASE: AI System-Wide Access.
         */
        private String departmentCode;

        /**
         * Change Request number (business identifier, e.g. CR-1).
         * Only populated for CHANGE_REQUEST sources. {@code null}
         * for all other source types. PHASE: AI System-Wide Access.
         */
        private String changeNumber;

        /**
         * Problem number (business identifier, e.g. PRB-1).
         * Only populated for PROBLEM sources. {@code null} for all
         * other source types. PHASE: AI System-Wide Access.
         */
        private String problemNumber;

        /**
         * Source title.
         */
        private String title;

        /**
         * Similarity score from vector search (0.0 - 1.0).
         * This is retrieval metadata, NOT LLM context.
         */
        private double similarityScore;

        public SourceReference() {
        }

        /**
         * Legacy TICKET source constructor (backward compatibility).
         */
        public SourceReference(String ticketNumber, String title, double similarityScore) {
            this.sourceType = "TICKET";
            this.ticketNumber = ticketNumber;
            this.title = title;
            this.similarityScore = similarityScore;
        }

        /**
         * PHASE 6.6.4 - explicit constructor that sets the source type
         * and (optionally) the Known Error code.
         */
        public SourceReference(String sourceType, String ticketNumber, String knownErrorCode,
                               String title, double similarityScore) {
            this.sourceType = sourceType;
            this.ticketNumber = ticketNumber;
            this.knownErrorCode = knownErrorCode;
            this.title = title;
            this.similarityScore = similarityScore;
        }

        public String getSourceType() {
            return sourceType;
        }

        public void setSourceType(String sourceType) {
            this.sourceType = sourceType;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getTicketNumber() {
            return ticketNumber;
        }

        public void setTicketNumber(String ticketNumber) {
            this.ticketNumber = ticketNumber;
        }

        public String getKnownErrorCode() {
            return knownErrorCode;
        }

        public void setKnownErrorCode(String knownErrorCode) {
            this.knownErrorCode = knownErrorCode;
        }

        public String getAssetNumber() {
            return assetNumber;
        }

        public void setAssetNumber(String assetNumber) {
            this.assetNumber = assetNumber;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getTeamCode() {
            return teamCode;
        }

        public void setTeamCode(String teamCode) {
            this.teamCode = teamCode;
        }

        public String getDepartmentCode() {
            return departmentCode;
        }

        public void setDepartmentCode(String departmentCode) {
            this.departmentCode = departmentCode;
        }

        public String getChangeNumber() {
            return changeNumber;
        }

        public void setChangeNumber(String changeNumber) {
            this.changeNumber = changeNumber;
        }

        public String getProblemNumber() {
            return problemNumber;
        }

        public void setProblemNumber(String problemNumber) {
            this.problemNumber = problemNumber;
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
