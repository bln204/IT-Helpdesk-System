package com.example.ticketing.ai;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTOs for RAG semantic search functionality.
 */
public class RagSearchDto {

    /**
     * Request for semantic search.
     */
    public static class SearchRequest {

        /**
         * Natural language query to search.
         */
        private String query;

        /**
         * Maximum number of results to return.
         */
        private int limit = 5;

        /**
         * Minimum similarity score (0.0 - 1.0).
         */
        private Double minScore = 0.5;

        public SearchRequest() {
        }

        public SearchRequest(String query, int limit, Double minScore) {
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

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }

        public Double getMinScore() {
            return minScore;
        }

        public void setMinScore(Double minScore) {
            this.minScore = minScore;
        }

        // Builder pattern for convenience
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String query;
            private int limit = 5;
            private Double minScore = 0.5;

            public Builder query(String query) {
                this.query = query;
                return this;
            }

            public Builder limit(int limit) {
                this.limit = limit;
                return this;
            }

            public Builder minScore(Double minScore) {
                this.minScore = minScore;
                return this;
            }

            public SearchRequest build() {
                return new SearchRequest(query, limit, minScore);
            }
        }
    }

    /**
     * Response containing search results.
     */
    public static class SearchResponse {
        private String query;
        private int totalResults;
        private List<SearchResult> results;
        private SearchMetadata metadata;

        public SearchResponse() {
        }

        public SearchResponse(String query, int totalResults, List<SearchResult> results, SearchMetadata metadata) {
            this.query = query;
            this.totalResults = totalResults;
            this.results = results;
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

        public List<SearchResult> getResults() {
            return results;
        }

        public void setResults(List<SearchResult> results) {
            this.results = results;
        }

        public SearchMetadata getMetadata() {
            return metadata;
        }

        public void setMetadata(SearchMetadata metadata) {
            this.metadata = metadata;
        }

        // Builder pattern for convenience
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String query;
            private int totalResults;
            private List<SearchResult> results;
            private SearchMetadata metadata;

            public Builder query(String query) {
                this.query = query;
                return this;
            }

            public Builder totalResults(int totalResults) {
                this.totalResults = totalResults;
                return this;
            }

            public Builder results(List<SearchResult> results) {
                this.results = results;
                return this;
            }

            public Builder metadata(SearchMetadata metadata) {
                this.metadata = metadata;
                return this;
            }

            public SearchResponse build() {
                return new SearchResponse(query, totalResults, results, metadata);
            }
        }
    }

    /**
     * Single search result.
     */
    public static class SearchResult {
        private Long ticketId;
        private String ticketNumber;
        private String title;
        private String description;
        private String categoryName;
        private String priority;
        private String status;
        private String requesterUsername;
        private String assigneeName;
        private LocalDateTime createdAt;
        private LocalDateTime resolvedAt;
        private double similarityScore;
        private String matchedContent;

        public SearchResult() {
        }

        public SearchResult(Long ticketId, String ticketNumber, String title, String description,
                            String categoryName, String priority, String status, String requesterUsername,
                            String assigneeName, LocalDateTime createdAt, LocalDateTime resolvedAt,
                            double similarityScore, String matchedContent) {
            this.ticketId = ticketId;
            this.ticketNumber = ticketNumber;
            this.title = title;
            this.description = description;
            this.categoryName = categoryName;
            this.priority = priority;
            this.status = status;
            this.requesterUsername = requesterUsername;
            this.assigneeName = assigneeName;
            this.createdAt = createdAt;
            this.resolvedAt = resolvedAt;
            this.similarityScore = similarityScore;
            this.matchedContent = matchedContent;
        }

        public Long getTicketId() {
            return ticketId;
        }

        public void setTicketId(Long ticketId) {
            this.ticketId = ticketId;
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

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getCategoryName() {
            return categoryName;
        }

        public void setCategoryName(String categoryName) {
            this.categoryName = categoryName;
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

        public String getRequesterUsername() {
            return requesterUsername;
        }

        public void setRequesterUsername(String requesterUsername) {
            this.requesterUsername = requesterUsername;
        }

        public String getAssigneeName() {
            return assigneeName;
        }

        public void setAssigneeName(String assigneeName) {
            this.assigneeName = assigneeName;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
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

        public String getMatchedContent() {
            return matchedContent;
        }

        public void setMatchedContent(String matchedContent) {
            this.matchedContent = matchedContent;
        }

        // Builder pattern for convenience
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private Long ticketId;
            private String ticketNumber;
            private String title;
            private String description;
            private String categoryName;
            private String priority;
            private String status;
            private String requesterUsername;
            private String assigneeName;
            private LocalDateTime createdAt;
            private LocalDateTime resolvedAt;
            private double similarityScore;
            private String matchedContent;

            public Builder ticketId(Long ticketId) {
                this.ticketId = ticketId;
                return this;
            }

            public Builder ticketNumber(String ticketNumber) {
                this.ticketNumber = ticketNumber;
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

            public Builder categoryName(String categoryName) {
                this.categoryName = categoryName;
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

            public Builder requesterUsername(String requesterUsername) {
                this.requesterUsername = requesterUsername;
                return this;
            }

            public Builder assigneeName(String assigneeName) {
                this.assigneeName = assigneeName;
                return this;
            }

            public Builder createdAt(LocalDateTime createdAt) {
                this.createdAt = createdAt;
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

            public Builder matchedContent(String matchedContent) {
                this.matchedContent = matchedContent;
                return this;
            }

            public SearchResult build() {
                return new SearchResult(ticketId, ticketNumber, title, description, categoryName,
                        priority, status, requesterUsername, assigneeName, createdAt, resolvedAt,
                        similarityScore, matchedContent);
            }
        }
    }

    /**
     * Search metadata.
     */
    public static class SearchMetadata {
        private long processingTimeMs;
        private String model;
        private int totalEmbeddingsAvailable;
        private String searchType;

        public SearchMetadata() {
        }

        public SearchMetadata(long processingTimeMs, String model, int totalEmbeddingsAvailable, String searchType) {
            this.processingTimeMs = processingTimeMs;
            this.model = model;
            this.totalEmbeddingsAvailable = totalEmbeddingsAvailable;
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
            private String model;
            private int totalEmbeddingsAvailable;
            private String searchType;

            public Builder processingTimeMs(long processingTimeMs) {
                this.processingTimeMs = processingTimeMs;
                return this;
            }

            public Builder model(String model) {
                this.model = model;
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

            public SearchMetadata build() {
                return new SearchMetadata(processingTimeMs, model, totalEmbeddingsAvailable, searchType);
            }
        }
    }

    /**
     * Embedding status response.
     */
    public static class EmbeddingStatusResponse {
        private long totalEmbeddings;
        private long completed;
        private long pending;
        private long processing;
        private long failed;
        private boolean ollamaAvailable;
        private boolean pgvectorAvailable;

        public EmbeddingStatusResponse() {
        }

        public EmbeddingStatusResponse(long totalEmbeddings, long completed, long pending,
                                       long processing, long failed, boolean ollamaAvailable,
                                       boolean pgvectorAvailable) {
            this.totalEmbeddings = totalEmbeddings;
            this.completed = completed;
            this.pending = pending;
            this.processing = processing;
            this.failed = failed;
            this.ollamaAvailable = ollamaAvailable;
            this.pgvectorAvailable = pgvectorAvailable;
        }

        public long getTotalEmbeddings() {
            return totalEmbeddings;
        }

        public void setTotalEmbeddings(long totalEmbeddings) {
            this.totalEmbeddings = totalEmbeddings;
        }

        public long getCompleted() {
            return completed;
        }

        public void setCompleted(long completed) {
            this.completed = completed;
        }

        public long getPending() {
            return pending;
        }

        public void setPending(long pending) {
            this.pending = pending;
        }

        public long getProcessing() {
            return processing;
        }

        public void setProcessing(long processing) {
            this.processing = processing;
        }

        public long getFailed() {
            return failed;
        }

        public void setFailed(long failed) {
            this.failed = failed;
        }

        public boolean isOllamaAvailable() {
            return ollamaAvailable;
        }

        public void setOllamaAvailable(boolean ollamaAvailable) {
            this.ollamaAvailable = ollamaAvailable;
        }

        public boolean isPgvectorAvailable() {
            return pgvectorAvailable;
        }

        public void setPgvectorAvailable(boolean pgvectorAvailable) {
            this.pgvectorAvailable = pgvectorAvailable;
        }

        // Builder pattern for convenience
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private long totalEmbeddings;
            private long completed;
            private long pending;
            private long processing;
            private long failed;
            private boolean ollamaAvailable;
            private boolean pgvectorAvailable;

            public Builder totalEmbeddings(long totalEmbeddings) {
                this.totalEmbeddings = totalEmbeddings;
                return this;
            }

            public Builder completed(long completed) {
                this.completed = completed;
                return this;
            }

            public Builder pending(long pending) {
                this.pending = pending;
                return this;
            }

            public Builder processing(long processing) {
                this.processing = processing;
                return this;
            }

            public Builder failed(long failed) {
                this.failed = failed;
                return this;
            }

            public Builder ollamaAvailable(boolean ollamaAvailable) {
                this.ollamaAvailable = ollamaAvailable;
                return this;
            }

            public Builder pgvectorAvailable(boolean pgvectorAvailable) {
                this.pgvectorAvailable = pgvectorAvailable;
                return this;
            }

            public EmbeddingStatusResponse build() {
                return new EmbeddingStatusResponse(totalEmbeddings, completed, pending,
                        processing, failed, ollamaAvailable, pgvectorAvailable);
            }
        }
    }
}
