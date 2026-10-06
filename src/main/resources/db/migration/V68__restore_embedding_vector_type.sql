-- ============================================================
-- V68: Restore embedding to VECTOR(768) type
--
-- Purpose: Enable pgvector <=> operator for cosine similarity
--          while preserving existing embedding data
--
-- Migration strategy:
-- 1. ALTER COLUMN TYPE with USING clause
-- 2. PostgreSQL casts TEXT → VECTOR using ::vector
-- 3. Existing data (Ticket #12) preserved automatically
-- ============================================================

-- Restore to VECTOR(768) with data preservation
-- USING embedding::vector converts existing TEXT to VECTOR
ALTER TABLE ticket_embeddings
ALTER COLUMN embedding TYPE VECTOR(768)
USING embedding::vector;

-- Add descriptive comment
COMMENT ON COLUMN ticket_embeddings.embedding IS
    'Vector from nomic-embed-text model (768 dimensions). NULL when status=PENDING or FAILED. Type restored to VECTOR(768) in V68.';
