-- ============================================================
-- V70: PHASE 6.6.4 - Known Error Embedding Infrastructure
-- Purpose: Add vector embeddings for Known Errors so the AI
--          self-service pipeline can retrieve reusable knowledge.
-- Model:   nomic-embed-text (768 dimensions, VECTOR(768))
-- Pattern: mirrors V66 + V68 for tickets, but lives in its own
--          table to keep the existing ticket_embeddings
--          abstraction untouched (the user explicitly asked
--          for backward compatibility with sourceType=TICKET).
-- ============================================================

-- 1. Create known_error_embeddings table
--    - One row per KnownError
--    - Vector column VECTOR(768) with the same dimension as
--      ticket_embeddings so the existing Ollama embedding
--      service can be reused without code changes.
--    - Status lifecycle mirrors ticket_embeddings
--      (PENDING -> PROCESSING -> COMPLETED / FAILED).
CREATE TABLE known_error_embeddings (
    id BIGSERIAL PRIMARY KEY,
    known_error_id BIGINT NOT NULL,
    embedding VECTOR(768),                    -- NULL when status = PENDING or FAILED
    embedding_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    embedded_at TIMESTAMP,                    -- Set when embedding completed
    embedding_error TEXT,                     -- Error message if status = FAILED
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL,

    CONSTRAINT uq_known_error_embeddings_known_error UNIQUE (known_error_id)
);

-- 2. Foreign key: KnownError deleted -> embeddings deleted (CASCADE)
ALTER TABLE known_error_embeddings
    ADD CONSTRAINT fk_known_error_embeddings_known_error
    FOREIGN KEY (known_error_id)
    REFERENCES known_errors(id)
    ON DELETE CASCADE;

-- 3. Index for the status lookup (the scheduler / pipeline
--    picks rows where embedding_status = 'PENDING').
CREATE INDEX idx_known_error_embeddings_status
ON known_error_embeddings(embedding_status);

-- 4. Documentation comments
COMMENT ON TABLE known_error_embeddings IS
    'Phase 6.6.4: vector embeddings for KnownError semantic search. Reusable operational knowledge for AI self-service.';

COMMENT ON COLUMN known_error_embeddings.known_error_id IS
    'FK to known_errors.id - 1:1 relationship. One embedding row per Known Error.';

COMMENT ON COLUMN known_error_embeddings.embedding IS
    'Vector from nomic-embed-text model (768 dimensions). NULL when status=PENDING or FAILED.';

COMMENT ON COLUMN known_error_embeddings.embedding_status IS
    'PENDING = awaiting async job, PROCESSING = job running, COMPLETED = success, FAILED = error';
