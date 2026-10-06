package com.example.ticketing.ai;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.ticketing.ticket.EmbeddingStatus;
import com.example.ticketing.ticket.TicketEmbedding;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

/**
 * Repository for vector similarity search using pgvector.
 * Uses native SQL for efficient similarity calculations.
 */
@Service
public class VectorSearchRepository {

    private static final Logger log = LoggerFactory.getLogger(VectorSearchRepository.class);

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Search tickets by vector similarity using pgvector.
     * 
     * @param queryVector JSON string representation of query vector
     * @param minScore Minimum similarity score (0.0 - 1.0)
     * @param limit Maximum number of results
     * @return List of ticket IDs with their similarity scores
     */
    public List<TicketSimilarity> searchBySimilarity(String queryVector, double minScore, int limit) {
        String sql = """
            SELECT 
                e.ticket_id,
                e.embedding <=> CAST(:queryVector AS vector) AS distance,
                1 - (e.embedding <=> CAST(:queryVector AS vector)) AS similarity
            FROM ticket_embeddings e
            WHERE e.embedding_status = 'COMPLETED'
              AND e.embedding IS NOT NULL
              AND (1 - (e.embedding <=> CAST(:queryVector AS vector))) >= :minScore
            ORDER BY e.embedding <=> CAST(:queryVector AS vector) ASC
            LIMIT :limit
            """;

        @SuppressWarnings("unchecked")
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("queryVector", queryVector);
        query.setParameter("minScore", (float) minScore);
        query.setParameter("limit", limit);

        List<Object[]> results = query.getResultList();
        List<TicketSimilarity> similarities = new ArrayList<>();

        for (Object[] row : results) {
            Long ticketId = ((Number) row[0]).longValue();
            double distance = ((Number) row[1]).doubleValue();
            double similarity = ((Number) row[2]).doubleValue();
            similarities.add(new TicketSimilarity(ticketId, similarity, distance));
        }

        return similarities;
    }

    /**
     * Search tickets with department filter for authorization.
     */
    public List<TicketSimilarity> searchBySimilarityWithDepartmentFilter(
            String queryVector, 
            double minScore, 
            int limit,
            List<Long> allowedDepartmentIds) {
        
        if (allowedDepartmentIds == null || allowedDepartmentIds.isEmpty()) {
            return List.of();
        }

        // Build IN clause for department IDs
        StringBuilder deptInClause = new StringBuilder();
        for (int i = 0; i < allowedDepartmentIds.size(); i++) {
            if (i > 0) deptInClause.append(",");
            deptInClause.append(allowedDepartmentIds.get(i));
        }

        String sql = String.format("""
            SELECT 
                e.ticket_id,
                e.embedding <=> CAST(:queryVector AS vector) AS distance,
                1 - (e.embedding <=> CAST(:queryVector AS vector)) AS similarity
            FROM ticket_embeddings e
            INNER JOIN tickets t ON e.ticket_id = t.id
            WHERE e.embedding_status = 'COMPLETED'
              AND e.embedding IS NOT NULL
              AND t.department_id IN (%s)
              AND (1 - (e.embedding <=> CAST(:queryVector AS vector))) >= :minScore
            ORDER BY e.embedding <=> CAST(:queryVector AS vector) ASC
            LIMIT :limit
            """, deptInClause.toString());

        @SuppressWarnings("unchecked")
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("queryVector", queryVector);
        query.setParameter("minScore", (float) minScore);
        query.setParameter("limit", limit);

        List<Object[]> results = query.getResultList();
        List<TicketSimilarity> similarities = new ArrayList<>();

        for (Object[] row : results) {
            Long ticketId = ((Number) row[0]).longValue();
            double distance = ((Number) row[1]).doubleValue();
            double similarity = ((Number) row[2]).doubleValue();
            similarities.add(new TicketSimilarity(ticketId, similarity, distance));
        }

        return similarities;
    }

    /**
     * Check if pgvector extension is available.
     */
    public boolean isPgvectorAvailable() {
        try {
            Query query = entityManager.createNativeQuery("SELECT 1 FROM pg_extension WHERE extname = 'vector'");
            query.getSingleResult();
            return true;
        } catch (NoResultException e) {
            return false;
        } catch (Exception e) {
            log.warn("Error checking pgvector availability: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Result class for similarity search.
     */
    public static class TicketSimilarity {
        private final Long ticketId;
        private final double similarity;
        private final double distance;

        public TicketSimilarity(Long ticketId, double similarity, double distance) {
            this.ticketId = ticketId;
            this.similarity = similarity;
            this.distance = distance;
        }

        public Long getTicketId() {
            return ticketId;
        }

        public double getSimilarity() {
            return similarity;
        }

        public double getDistance() {
            return distance;
        }
    }
}
