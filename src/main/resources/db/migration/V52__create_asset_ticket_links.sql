-- ============================================================
-- V52: Create Asset Ticket Links
-- Purpose: Link assets with tickets, incidents, and changes
-- ============================================================

-- ============================================================
-- Create asset_ticket_links table
-- ============================================================

CREATE TABLE asset_ticket_links (
    id BIGSERIAL PRIMARY KEY,
    asset_id BIGINT NOT NULL,
    ticket_id BIGINT,
    incident_id BIGINT,
    change_id BIGINT,
    link_type VARCHAR(50) NOT NULL DEFAULT 'AFFECTED',
    impact_assessment VARCHAR(50) DEFAULT 'MODERATE',
    notes TEXT,
    identified_by VARCHAR(100),
    identified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    resolved BOOLEAN DEFAULT FALSE,
    resolved_at TIMESTAMP,
    resolution_notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_asset_ticket_links_asset ON asset_ticket_links(asset_id);
CREATE INDEX IF NOT EXISTS idx_asset_ticket_links_ticket ON asset_ticket_links(ticket_id);
CREATE INDEX IF NOT EXISTS idx_asset_ticket_links_incident ON asset_ticket_links(incident_id);
CREATE INDEX IF NOT EXISTS idx_asset_ticket_links_change ON asset_ticket_links(change_id);
CREATE INDEX IF NOT EXISTS idx_asset_ticket_links_type ON asset_ticket_links(link_type);

ALTER TABLE asset_ticket_links ADD CONSTRAINT fk_asset_ticket_links_asset FOREIGN KEY (asset_id) REFERENCES assets(id) ON DELETE CASCADE;

-- ============================================================
-- Create asset_incident_links table
-- ============================================================

CREATE TABLE asset_incident_links (
    id BIGSERIAL PRIMARY KEY,
    asset_id BIGINT NOT NULL,
    incident_id BIGINT NOT NULL,
    link_role VARCHAR(50) DEFAULT 'AFFECTED',
    confidence_level VARCHAR(20) DEFAULT 'MEDIUM',
    symptoms TEXT,
    impact_description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_asset_incident_asset ON asset_incident_links(asset_id);
CREATE INDEX IF NOT EXISTS idx_asset_incident_incident ON asset_incident_links(incident_id);

ALTER TABLE asset_incident_links ADD CONSTRAINT fk_ail_asset FOREIGN KEY (asset_id) REFERENCES assets(id) ON DELETE CASCADE;
ALTER TABLE asset_incident_links ADD CONSTRAINT fk_ail_incident FOREIGN KEY (incident_id) REFERENCES incidents(id) ON DELETE CASCADE;

-- ============================================================
-- Create asset_dependency_links table
-- ============================================================

CREATE TABLE asset_dependency_links (
    id BIGSERIAL PRIMARY KEY,
    parent_asset_id BIGINT NOT NULL,
    child_asset_id BIGINT NOT NULL,
    dependency_type VARCHAR(50) DEFAULT 'REQUIRES',
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_dep_parent ON asset_dependency_links(parent_asset_id);
CREATE INDEX IF NOT EXISTS idx_dep_child ON asset_dependency_links(child_asset_id);

ALTER TABLE asset_dependency_links ADD CONSTRAINT fk_dep_parent FOREIGN KEY (parent_asset_id) REFERENCES assets(id) ON DELETE CASCADE;
ALTER TABLE asset_dependency_links ADD CONSTRAINT fk_dep_child FOREIGN KEY (child_asset_id) REFERENCES assets(id) ON DELETE CASCADE;

-- ============================================================
-- Create incident_affected_assets table
-- ============================================================

CREATE TABLE incident_affected_assets (
    id BIGSERIAL PRIMARY KEY,
    incident_id BIGINT NOT NULL,
    asset_id BIGINT NOT NULL,
    affected_component VARCHAR(100),
    outage_duration_minutes INTEGER DEFAULT 0,
    services_impacted TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_incident_affected_incident ON incident_affected_assets(incident_id);
CREATE INDEX IF NOT EXISTS idx_incident_affected_asset ON incident_affected_assets(asset_id);

ALTER TABLE incident_affected_assets ADD CONSTRAINT fk_iaa_incident FOREIGN KEY (incident_id) REFERENCES incidents(id) ON DELETE CASCADE;
ALTER TABLE incident_affected_assets ADD CONSTRAINT fk_iaa_asset FOREIGN KEY (asset_id) REFERENCES assets(id) ON DELETE CASCADE;
