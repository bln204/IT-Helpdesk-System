-- ============================================================
-- V61: Verify and add missing FK constraints for incidents
-- Purpose: Ensure referential integrity
-- ============================================================

-- Check if FK exists, if not add it
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_incident_timeline_incident'
    ) THEN
        ALTER TABLE incident_timeline
            ADD CONSTRAINT fk_incident_timeline_incident
            FOREIGN KEY (incident_id)
            REFERENCES incidents(id)
            ON DELETE CASCADE;
    END IF;
END $$;

-- Check ticket_incident_links FK
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_ticket_incident_incident'
    ) THEN
        ALTER TABLE ticket_incident_links
            ADD CONSTRAINT fk_ticket_incident_incident
            FOREIGN KEY (incident_id)
            REFERENCES incidents(id)
            ON DELETE CASCADE;
    END IF;
END $$;
