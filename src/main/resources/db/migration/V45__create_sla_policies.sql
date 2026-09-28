-- ============================================================
-- V45: Create SLA Policies Table
-- Purpose: Store configurable SLA policies instead of hardcoded values
-- ============================================================

-- Create SLA Policies table
CREATE TABLE sla_policies (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    
    -- Priority mapping (maps to TicketTypes.TicketPriority)
    priority VARCHAR(20) NOT NULL,
    
    -- Response time in MINUTES (not hours for precision)
    response_minutes INTEGER NOT NULL,
    
    -- Resolution time in MINUTES
    resolution_minutes INTEGER NOT NULL,
    
    -- Business hours only - if true, SLA only counts during business hours
    business_hours_only BOOLEAN DEFAULT FALSE,
    
    -- Warning threshold percentage (default 75% = warning when 75% time used)
    warning_threshold INTEGER DEFAULT 75,
    
    -- Second response time (optional - for multi-stage SLA)
    second_response_minutes INTEGER,
    
    -- Is this the default policy for this priority?
    is_default BOOLEAN DEFAULT FALSE,
    
    -- Enable/disable this policy
    enabled BOOLEAN DEFAULT TRUE,
    
    -- Metadata
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

-- Create indexes for common queries
CREATE INDEX IF NOT EXISTS idx_sla_policies_priority ON sla_policies(priority);
CREATE INDEX IF NOT EXISTS idx_sla_policies_enabled ON sla_policies(enabled);
CREATE INDEX IF NOT EXISTS idx_sla_policies_default ON sla_policies(priority, is_default) WHERE is_default = TRUE;

CREATE UNIQUE INDEX IF NOT EXISTS idx_sla_policies_unique_default_per_priority 
    ON sla_policies(priority) WHERE is_default = TRUE;

-- ============================================================
-- Insert default SLA policies based on the ITSM requirements
-- ============================================================

-- Critical: Response ≤ 15 phút, Resolution ≤ 2 giờ (120 phút)
INSERT INTO sla_policies (name, description, priority, response_minutes, resolution_minutes, warning_threshold, is_default, enabled)
VALUES (
    'Critical SLA',
    'SLA cho ticket Critical - Phản hồi trong 15 phút, giải quyết trong 2 giờ',
    'CRITICAL',
    15,    -- 15 minutes
    120,   -- 2 hours = 120 minutes
    75,
    TRUE,
    TRUE
);

-- High: Response ≤ 30 phút, Resolution ≤ 4 giờ (240 phút)
INSERT INTO sla_policies (name, description, priority, response_minutes, resolution_minutes, warning_threshold, is_default, enabled)
VALUES (
    'High SLA',
    'SLA cho ticket High - Phản hồi trong 30 phút, giải quyết trong 4 giờ',
    'HIGH',
    30,    -- 30 minutes
    240,   -- 4 hours = 240 minutes
    75,
    TRUE,
    TRUE
);

-- Medium: Response ≤ 2 giờ (120 phút), Resolution ≤ 8 giờ (480 phút)
INSERT INTO sla_policies (name, description, priority, response_minutes, resolution_minutes, warning_threshold, is_default, enabled)
VALUES (
    'Medium SLA',
    'SLA cho ticket Medium - Phản hồi trong 2 giờ, giải quyết trong 8 giờ',
    'MEDIUM',
    120,   -- 2 hours = 120 minutes
    480,   -- 8 hours = 480 minutes
    75,
    TRUE,
    TRUE
);

-- Low: Response ≤ 8 giờ (480 phút), Resolution ≤ 3 ngày (4320 phút)
INSERT INTO sla_policies (name, description, priority, response_minutes, resolution_minutes, warning_threshold, is_default, enabled)
VALUES (
    'Low SLA',
    'SLA cho ticket Low - Phản hồi trong 8 giờ, giải quyết trong 3 ngày',
    'LOW',
    480,   -- 8 hours = 480 minutes
    4320,  -- 3 days = 4320 minutes
    75,
    TRUE,
    TRUE
);

-- Urgent: Response ≤ 1 giờ (60 phút), Resolution ≤ 6 giờ (360 phút)
INSERT INTO sla_policies (name, description, priority, response_minutes, resolution_minutes, warning_threshold, is_default, enabled)
VALUES (
    'Urgent SLA',
    'SLA cho ticket Urgent - Phản hồi trong 1 giờ, giải quyết trong 6 giờ',
    'URGENT',
    60,    -- 1 hour = 60 minutes
    360,   -- 6 hours = 360 minutes
    75,
    TRUE,
    TRUE
);
