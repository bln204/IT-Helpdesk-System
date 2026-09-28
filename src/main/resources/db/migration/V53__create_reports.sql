-- ============================================================
-- V53: Create Reporting & Analytics
-- Purpose: Store report configurations and provide analytics API
-- ============================================================

-- ============================================================
-- Create report_configs table
-- ============================================================

CREATE TABLE report_configs (
    id BIGSERIAL PRIMARY KEY,
    
    name VARCHAR(200) NOT NULL,
    description TEXT,
    
    -- Report type
    report_type VARCHAR(50) NOT NULL,
    -- TICKET_VOLUME, RESPONSE_TIME, RESOLUTION_TIME, SLA_COMPLIANCE,
    -- ASSET_STATUS, CHANGE_ANALYSIS, USER_SATISFACTION, CUSTOM
    
    -- Report category
    category VARCHAR(50),
    -- OPERATIONS, SERVICE_DESK, INFRASTRUCTURE, FINANCIAL, CUSTOM
    
    -- Configuration
    config JSONB,
    -- {"dateRange": "last30days", "groupBy": "day", "filters": {...}}
    
    -- Schedule
    is_scheduled BOOLEAN DEFAULT FALSE,
    schedule_cron VARCHAR(100),
    -- 0 0 * * * = daily at midnight
    
    -- Output
    output_format VARCHAR(20) DEFAULT 'TABLE',
    -- TABLE, CHART, EXPORT
    
    -- Access
    is_public BOOLEAN DEFAULT FALSE,
    allowed_roles TEXT,
    -- JSON array of roles
    
    -- Metadata
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100)
);

CREATE INDEX idx_report_configs_type ON report_configs(report_type);
CREATE INDEX idx_report_configs_category ON report_configs(category);
CREATE INDEX idx_report_configs_public ON report_configs(is_public);

-- ============================================================
-- Create saved_reports table - User's saved reports
-- ============================================================

CREATE TABLE saved_reports (
    id BIGSERIAL PRIMARY KEY,
    
    name VARCHAR(200) NOT NULL,
    
    report_config_id BIGINT,
    -- Reference to report_configs if using a template
    
    -- Custom query
    query_name VARCHAR(100),
    query_params JSONB,
    
    -- Filters
    filters JSONB,
    -- {"dateFrom": "...", "dateTo": "...", "category": "..."}
    
    -- Output
    output_format VARCHAR(20) DEFAULT 'TABLE',
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100)
);

CREATE INDEX idx_saved_reports_user ON saved_reports(created_by);
CREATE INDEX idx_saved_reports_config ON saved_reports(report_config_id);

ALTER TABLE saved_reports
    ADD CONSTRAINT fk_saved_reports_config
    FOREIGN KEY (report_config_id)
    REFERENCES report_configs(id)
    ON DELETE SET NULL;

-- ============================================================
-- Create report_exports table - Track report exports
-- ============================================================

CREATE TABLE report_exports (
    id BIGSERIAL PRIMARY KEY,
    
    report_name VARCHAR(200),
    
    export_format VARCHAR(20) NOT NULL,
    -- PDF, EXCEL, CSV
    
    file_path VARCHAR(500),
    file_size INTEGER,
    
    parameters JSONB,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    expires_at TIMESTAMP
);

CREATE INDEX idx_report_exports_user ON report_exports(created_by);
CREATE INDEX idx_report_exports_created ON report_exports(created_at DESC);

-- ============================================================
-- Insert default report templates
-- ============================================================

INSERT INTO report_configs (name, description, report_type, category, config, is_public, allowed_roles) VALUES
(
    'Ticket Volume Report',
    'Overview of ticket volume over time',
    'TICKET_VOLUME',
    'SERVICE_DESK',
    '{"chartType": "bar", "groupBy": "day", "metrics": ["count", "resolved"]}',
    TRUE,
    '["ROLE_ADMIN", "ROLE_GIAM_DOC", "ROLE_TRUONG_PHONG", "ROLE_IT_STAFF"]'
),
(
    'SLA Compliance Report',
    'Track SLA compliance rates',
    'SLA_COMPLIANCE',
    'OPERATIONS',
    '{"chartType": "gauge", "metrics": ["complianceRate", "breaches"]}',
    TRUE,
    '["ROLE_ADMIN", "ROLE_GIAM_DOC", "ROLE_TRUONG_PHONG", "ROLE_IT_STAFF"]'
),
(
    'Agent Performance',
    'Track response and resolution times by agent',
    'RESPONSE_TIME',
    'SERVICE_DESK',
    '{"chartType": "table", "metrics": ["avgFirstResponse", "avgResolution"]}',
    TRUE,
    '["ROLE_ADMIN", "ROLE_GIAM_DOC", "ROLE_TRUONG_PHONG"]'
),
(
    'Asset Health Overview',
    'Summary of asset health status',
    'ASSET_STATUS',
    'INFRASTRUCTURE',
    '{"chartType": "donut", "metrics": ["healthy", "warning", "critical"]}',
    TRUE,
    '["ROLE_ADMIN", "ROLE_GIAM_DOC", "ROLE_IT_STAFF"]'
),
(
    'Change Success Rate',
    'Track change implementation success',
    'CHANGE_ANALYSIS',
    'OPERATIONS',
    '{"chartType": "bar", "metrics": ["completed", "rolledBack", "cancelled"]}',
    TRUE,
    '["ROLE_ADMIN", "ROLE_GIAM_DOC", "ROLE_IT_STAFF"]'
);

-- ============================================================
-- Create analytics functions for real-time metrics
-- ============================================================

-- Note: Analytics will be computed via API endpoints
-- These are helper views for common queries

CREATE OR REPLACE VIEW v_ticket_metrics AS
SELECT 
    COUNT(*) as total_tickets,
    COUNT(*) FILTER (WHERE status = 'RESOLVED') as resolved_tickets,
    COUNT(*) FILTER (WHERE status = 'OPEN') as open_tickets,
    COUNT(*) FILTER (WHERE status = 'IN_PROGRESS') as in_progress_tickets,
    AVG(EXTRACT(EPOCH FROM (updated_at - created_at)) / 3600) FILTER (WHERE status = 'RESOLVED') as avg_resolution_hours
FROM tickets
WHERE created_at >= NOW() - INTERVAL '30 days';

CREATE OR REPLACE VIEW v_sla_metrics AS
SELECT 
    COUNT(*) as total_tickets,
    COUNT(*) FILTER (WHERE resolved_at IS NOT NULL AND resolved_at <= sla_resolution_at) as sla_met,
    COUNT(*) FILTER (WHERE resolved_at IS NOT NULL AND resolved_at > sla_resolution_at) as sla_breached,
    ROUND(100.0 * COUNT(*) FILTER (WHERE resolved_at IS NOT NULL AND resolved_at <= sla_resolution_at) / NULLIF(COUNT(*) FILTER (WHERE resolved_at IS NOT NULL), 0), 2) as compliance_rate
FROM tickets
WHERE sla_resolution_at IS NOT NULL;

CREATE OR REPLACE VIEW v_asset_health_metrics AS
SELECT 
    COUNT(*) as total_assets,
    COUNT(*) FILTER (WHERE health_status = 'HEALTHY') as healthy,
    COUNT(*) FILTER (WHERE health_status = 'WARNING') as warning,
    COUNT(*) FILTER (WHERE health_status = 'CRITICAL') as critical,
    COUNT(*) FILTER (WHERE health_status = 'UNKNOWN') as unknown
FROM assets;

CREATE OR REPLACE VIEW v_change_metrics AS
SELECT 
    COUNT(*) as total_changes,
    COUNT(*) FILTER (WHERE status = 'COMPLETED') as completed,
    COUNT(*) FILTER (WHERE status = 'ROLLED_BACK') as rolled_back,
    COUNT(*) FILTER (WHERE status = 'CANCELLED') as cancelled,
    COUNT(*) FILTER (WHERE status = 'IN_PROGRESS') as in_progress,
    ROUND(100.0 * COUNT(*) FILTER (WHERE status = 'COMPLETED') / NULLIF(COUNT(*), 0), 2) as success_rate
FROM change_requests
WHERE created_at >= NOW() - INTERVAL '30 days';
