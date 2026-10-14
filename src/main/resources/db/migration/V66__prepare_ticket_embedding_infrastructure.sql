-- ============================================================
-- V66: Prepare Ticket Embedding Infrastructure
-- Purpose: Database schema for ticket vector embeddings
--         to support AI/RAG functionality in Phase 5
-- Model: nomic-embed-text (768 dimensions)
-- ============================================================

-- 1. Create vector extension (idempotent - safe to run multiple times)
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. Create ticket_embeddings table
CREATE TABLE ticket_embeddings (
    id BIGSERIAL PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    embedding VECTOR(768),                    -- NULL when status = PENDING (async workflow)
    embedding_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    embedded_at TIMESTAMP,                    -- Set when embedding completed
    embedding_error TEXT,                     -- Error message if status = FAILED
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL,            -- Updated by JPA @PreUpdate

    CONSTRAINT uq_ticket_embeddings_ticket UNIQUE (ticket_id)
);

-- 3. Add foreign key constraint
-- Ticket deleted -> embeddings deleted (CASCADE)
ALTER TABLE ticket_embeddings
    ADD CONSTRAINT fk_ticket_embeddings_ticket
    FOREIGN KEY (ticket_id)
    REFERENCES tickets(id)
    ON DELETE CASCADE;

-- 4. Create status index for finding pending embeddings
CREATE INDEX idx_ticket_embeddings_status
ON ticket_embeddings(embedding_status);

-- 5. Add comments for documentation
COMMENT ON TABLE ticket_embeddings IS 'Vector embeddings for ticket semantic search (RAG infrastructure for Phase 5 AI)';
COMMENT ON COLUMN ticket_embeddings.ticket_id IS 'FK to tickets.id - 1:1 relationship';
COMMENT ON COLUMN ticket_embeddings.embedding IS 'Vector from nomic-embed-text model (768 dimensions). NULL when status=PENDING or FAILED';
COMMENT ON COLUMN ticket_embeddings.embedding_status IS 'PENDING = awaiting async job, PROCESSING = job running, COMPLETED = success, FAILED = error';
COMMENT ON COLUMN ticket_embeddings.embedded_at IS 'Timestamp when embedding was successfully generated';
COMMENT ON COLUMN ticket_embeddings.embedding_error IS 'Error message if embedding generation failed';
