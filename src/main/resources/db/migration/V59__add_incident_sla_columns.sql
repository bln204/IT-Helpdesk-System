-- ============================================================
-- V59: Add SLA columns to incidents table
-- Purpose: Match Incident entity with database schema
-- ============================================================

-- SLA configuration
ALTER TABLE incidents ADD COLUMN sla_policy_name VARCHAR(100);
ALTER TABLE incidents ADD COLUMN sla_response_minutes INTEGER;
ALTER TABLE incidents ADD COLUMN sla_resolution_minutes INTEGER;

-- SLA tracking (deadlines)
ALTER TABLE incidents ADD COLUMN response_deadline TIMESTAMP;
ALTER TABLE incidents ADD COLUMN resolution_deadline TIMESTAMP;

-- SLA status
ALTER TABLE incidents ADD COLUMN response_sla_breached BOOLEAN DEFAULT FALSE;
ALTER TABLE incidents ADD COLUMN resolution_sla_breached BOOLEAN DEFAULT FALSE;
