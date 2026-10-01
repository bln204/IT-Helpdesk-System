-- ============================================================
-- V60: Add resolution column to service_requests
-- Purpose: Match ServiceRequest entity with database schema
-- ============================================================

ALTER TABLE service_requests ADD COLUMN resolution TEXT;
