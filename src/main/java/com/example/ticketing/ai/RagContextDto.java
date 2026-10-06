package com.example.ticketing.ai;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTOs for RAG Context Retrieval functionality.
 * These DTOs represent the structured context prepared for future LLM generation.
 *
 * IMPORTANT: PII fields (requesterName, requesterUsername, requesterEmail) are
 * intentionally excluded from source data to protect user privacy.
 */
public class RagContextDto {

    /**
     * Request for RAG context retrieval.
     */
    public static class RetrievalRequest {

        /**
         * Natural language query to search for relevant context.
         */
        @NotBlank(message = "Query must not be blank")
        @Size(min = 1, max = 1000, message = "Query must be between 1 and 1000 characters")
        private String query;

        /**
         * Maximum number of context sources to return.
         * Default: 5
         * Maximum: 10
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

        public RetrievalRequest() {
        }

        public RetrievalRequest(String query, Integer limit, Double minScore) {
            this.query = query;
            this.limit = limit;
            this.minScore = minScore;
        }

        public String getQuery() {
            return query;
        }

        public void setQuery(String query) {
            this.query = query;
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

    /**
     * Response containing RAG context for LLM generation.
     */
    public static class RetrievalResponse {

        /**
         * The original query submitted by the user.
         */
        private String query;

        /**
         * Number of context sources returned.
         */
        private int totalResults;

        /**
         * List of context sources for LLM.
         */
        private List<RagSource> sources;

        /**
         * Processing status.
         */
        private String status;

        /**
         * Error message if status indicates failure.
         * Only populated when status is 'error'.
         */
        private String errorMessage;

        /**
         * Processing metadata.
         */
        private RetrievalMetadata metadata;

        public RetrievalResponse() {
        }

        public RetrievalResponse(String query, int totalResults, List<RagSource> sources,
                                 String status, String errorMessage, RetrievalMetadata metadata) {
            this.query = query;
            this.totalResults = totalResults;
            this.sources = sources;
            this.status = status;
            this.errorMessage = errorMessage;
            this.metadata = metadata;
        }

        public String getQuery() {
            return query;
        }

        public void setQuery(String query) {
            this.query = query;
        }

        public int getTotalResults() {
            return totalResults;
        }

        public void setTotalResults(int totalResults) {
            this.totalResults = totalResults;
        }

        public List<RagSource> getSources() {
            return sources;
        }

        public void setSources(List<RagSource> sources) {
            this.sources = sources;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public RetrievalMetadata getMetadata() {
            return metadata;
        }

        public void setMetadata(RetrievalMetadata metadata) {
            this.metadata = metadata;
        }

        // Builder pattern for convenience
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String query;
            private int totalResults;
            private List<RagSource> sources;
            private String status;
            private String errorMessage;
            private RetrievalMetadata metadata;

            public Builder query(String query) {
                this.query = query;
                return this;
            }

            public Builder totalResults(int totalResults) {
                this.totalResults = totalResults;
                return this;
            }

            public Builder sources(List<RagSource> sources) {
                this.sources = sources;
                return this;
            }

            public Builder status(String status) {
                this.status = status;
                return this;
            }

            public Builder errorMessage(String errorMessage) {
                this.errorMessage = errorMessage;
                return this;
            }

            public Builder metadata(RetrievalMetadata metadata) {
                this.metadata = metadata;
                return this;
            }

            public RetrievalResponse build() {
                return new RetrievalResponse(query, totalResults, sources, status, errorMessage, metadata);
            }
        }
    }

    /**
     * Single source in the RAG context.
     * Represents ticket information prepared for LLM consumption.
     */
    public static class RagSource {

        /**
         * Type of source (e.g., "TICKET").
         * Used to identify the source type for future processing.
         */
        private String sourceType;

        /**
         * Human-readable reference to the source (e.g., ticket number).
         * This is the business identifier, not the internal database ID.
         */
        private String sourceRef;

        /**
         * Ticket title.
         */
        private String title;

        /**
         * Truncated ticket description.
         * PII fields (requester info) are not included.
         */
        private String description;

        /**
         * Category name.
         */
        private String category;

        /**
         * Subcategory name.
         */
        private String subcategory;

        /**
         * Priority level.
         */
        private String priority;

        /**
         * Current status.
         */
        private String status;

        /**
         * Timestamp when ticket was resolved (if resolved).
         */
        private LocalDateTime resolvedAt;

        /**
         * Similarity score from vector search (0.0 - 1.0).
         */
        private double similarityScore;

        /**
         * Normalized content for LLM context.
         * Combines ticket fields into a structured text format.
         */
        private String content;

        public RagSource() {
        }

        public RagSource(String sourceType, String sourceRef, String title, String description,
                         String category, String subcategory, String priority, String status,
                         LocalDateTime resolvedAt, double similarityScore, String content) {
            this.sourceType = sourceType;
            this.sourceRef = sourceRef;
            this.title = title;
            this.description = description;
            this.category = category;
            this.subcategory = subcategory;
            this.priority = priority;
            this.status = status;
            this.resolvedAt = resolvedAt;
            this.similarityScore = similarityScore;
            this.content = content;
        }

        public String getSourceType() {
            return sourceType;
        }

        public void setSourceType(String sourceType) {
            this.sourceType = sourceType;
        }

        public String getSourceRef() {
            return sourceRef;
        }

        public void setSourceRef(String sourceRef) {
            this.sourceRef = sourceRef;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getSubcategory() {
            return subcategory;
        }

        public void setSubcategory(String subcategory) {
            this.subcategory = subcategory;
        }

        public String getPriority() {
            return priority;
        }

        public void setPriority(String priority) {
            this.priority = priority;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public LocalDateTime getResolvedAt() {
            return resolvedAt;
        }

        public void setResolvedAt(LocalDateTime resolvedAt) {
            this.resolvedAt = resolvedAt;
        }

        public double getSimilarityScore() {
            return similarityScore;
        }

        public void setSimilarityScore(double similarityScore) {
            this.similarityScore = similarityScore;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        // Builder pattern for convenience
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String sourceType;
            private String sourceRef;
            private String title;
            private String description;
            private String category;
            private String subcategory;
            private String priority;
            private String status;
            private LocalDateTime resolvedAt;
            private double similarityScore;
            private String content;

            public Builder sourceType(String sourceType) {
                this.sourceType = sourceType;
                return this;
            }

            public Builder sourceRef(String sourceRef) {
                this.sourceRef = sourceRef;
                return this;
            }

            public Builder title(String title) {
                this.title = title;
                return this;
            }

            public Builder description(String description) {
                this.description = description;
                return this;
            }

            public Builder category(String category) {
                this.category = category;
                return this;
            }

            public Builder subcategory(String subcategory) {
                this.subcategory = subcategory;
                return this;
            }

            public Builder priority(String priority) {
                this.priority = priority;
                return this;
            }

            public Builder status(String status) {
                this.status = status;
                return this;
            }

            public Builder resolvedAt(LocalDateTime resolvedAt) {
                this.resolvedAt = resolvedAt;
                return this;
            }

            public Builder similarityScore(double similarityScore) {
                this.similarityScore = similarityScore;
                return this;
            }

            public Builder content(String content) {
                this.content = content;
                return this;
            }

            public RagSource build() {
                return new RagSource(sourceType, sourceRef, title, description, category,
                        subcategory, priority, status, resolvedAt, similarityScore, content);
            }
        }
    }

    /**
     * Metadata about the retrieval process.
     */
    public static class RetrievalMetadata {

        /**
         * Processing time in milliseconds.
         */
        private long processingTimeMs;

        /**
         * Embedding model used.
         */
        private String embeddingModel;

        /**
         * Number of available embeddings in the system.
         */
        private int totalEmbeddingsAvailable;

        /**
         * Search type/filter applied.
         */
        private String searchType;

        public RetrievalMetadata() {
        }

        public RetrievalMetadata(long processingTimeMs, String embeddingModel,
                                 int totalEmbeddingsAvailable, String searchType) {
            this.processingTimeMs = processingTimeMs;
            this.embeddingModel = embeddingModel;
            this.totalEmbeddingsAvailable = totalEmbeddingsAvailable;
            this.searchType = searchType;
        }

        public long getProcessingTimeMs() {
            return processingTimeMs;
        }

        public void setProcessingTimeMs(long processingTimeMs) {
            this.processingTimeMs = processingTimeMs;
        }

        public String getEmbeddingModel() {
            return embeddingModel;
        }

        public void setEmbeddingModel(String embeddingModel) {
            this.embeddingModel = embeddingModel;
        }

        public int getTotalEmbeddingsAvailable() {
            return totalEmbeddingsAvailable;
        }

        public void setTotalEmbeddingsAvailable(int totalEmbeddingsAvailable) {
            this.totalEmbeddingsAvailable = totalEmbeddingsAvailable;
        }

        public String getSearchType() {
            return searchType;
        }

        public void setSearchType(String searchType) {
            this.searchType = searchType;
        }

        // Builder pattern for convenience
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private long processingTimeMs;
            private String embeddingModel;
            private int totalEmbeddingsAvailable;
            private String searchType;

            public Builder processingTimeMs(long processingTimeMs) {
                this.processingTimeMs = processingTimeMs;
                return this;
            }

            public Builder embeddingModel(String embeddingModel) {
                this.embeddingModel = embeddingModel;
                return this;
            }

            public Builder totalEmbeddingsAvailable(int totalEmbeddingsAvailable) {
                this.totalEmbeddingsAvailable = totalEmbeddingsAvailable;
                return this;
            }

            public Builder searchType(String searchType) {
                this.searchType = searchType;
                return this;
            }

            public RetrievalMetadata build() {
                return new RetrievalMetadata(processingTimeMs, embeddingModel,
                        totalEmbeddingsAvailable, searchType);
            }
        }
    }
}
