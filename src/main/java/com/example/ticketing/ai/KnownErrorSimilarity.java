package com.example.ticketing.ai;

import java.util.List;

import com.example.ticketing.problem.KnownError;

/**
 * Phase 6.6.4 - Result of a Known Error semantic search.
 *
 * <p>Carries the database id (used to fetch the {@link KnownError} row), the
 * similarity score from pgvector, and the canonical error code so the
 * retrieval layer can build a {@code sourceType=KNOWN_ERROR} RagSource
 * without re-querying the database for the code.
 */
public class KnownErrorSimilarity {

    private final Long knownErrorId;
    private final String errorCode;
    private final double similarity;
    private final double distance;

    public KnownErrorSimilarity(Long knownErrorId, String errorCode, double similarity, double distance) {
        this.knownErrorId = knownErrorId;
        this.errorCode = errorCode;
        this.similarity = similarity;
        this.distance = distance;
    }

    public Long getKnownErrorId() {
        return knownErrorId;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public double getSimilarity() {
        return similarity;
    }

    public double getDistance() {
        return distance;
    }

    /**
     * Convenience batch container - the repository methods return
     * {@code List<KnownErrorSimilarity>} directly.
     */
    public static class Batch {
        private final List<KnownErrorSimilarity> items;

        public Batch(List<KnownErrorSimilarity> items) {
            this.items = items;
        }

        public List<KnownErrorSimilarity> items() {
            return items;
        }

        public boolean isEmpty() {
            return items == null || items.isEmpty();
        }

        public int size() {
            return items == null ? 0 : items.size();
        }
    }
}
