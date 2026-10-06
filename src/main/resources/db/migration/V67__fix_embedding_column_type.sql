-- ============================================================
-- V67: Fix Embedding Column Type
-- Problem: Hibernate cannot handle PostgreSQL vector type directly
-- Solution: Store embedding as TEXT in database, cast to vector in queries
-- ============================================================

-- Change embedding column from vector(768) to TEXT
-- This allows Hibernate to insert/update without type mismatch errors
ALTER TABLE ticket_embeddings 
ALTER COLUMN embedding TYPE TEXT;

-- Add comment for future reference
COMMENT ON COLUMN ticket_embeddings.embedding IS 'Stored as TEXT. PostgreSQL casts to vector(768) when used in similarity searches';
