-- ============================================================
-- V69: PHASE 6.6.3 - Add index on problems.known_error_id
-- Purpose: speed up "find all problems that share a known_error"
--          and the AI/RAG relational walk
--          Ticket -> Incident -> Problem -> KnownError.
--
-- Schema truth is unchanged: the FK was already created in V49
-- (fk_problems_known_error, ON DELETE SET NULL, nullable).
-- This migration is additive and idempotent.
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_problems_known_error_id
ON problems(known_error_id);