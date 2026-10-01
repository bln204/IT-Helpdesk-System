-- ============================================================
-- V46: Create Escalation Rules Table
-- Purpose: Store escalation rules for automatic ticket escalation
-- ============================================================

-- Create escalation_rules table
CREATE TABLE escalation_rules (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    
    -- Trigger type
    trigger_type VARCHAR(50) NOT NULL,
    -- Values:
    -- SLA_RESPONSE_WARNING - 75% SLA response time used
    -- SLA_RESPONSE_BREACHED - SLA response breached
    -- SLA_RESOLUTION_WARNING - 75% SLA resolution time used
    -- SLA_RESOLUTION_BREACHED - SLA resolution breached
    -- CRITICAL_TICKET - New ticket with CRITICAL priority
    -- MANUAL - Manual escalation only
    
    -- Priority filter - only apply this rule for tickets with this priority or higher
    min_priority VARCHAR(20),
    
    -- Time-based trigger (minutes before/after breach)
    minutes_threshold INTEGER DEFAULT 0,
    -- positive = before breach, negative = after breach
    
    -- Escalation target - user or team
    escalate_to_user_id BIGINT,
    escalate_to_team_id BIGINT,
    
    -- Escalation level
    escalation_level VARCHAR(20) DEFAULT 'LEVEL_1',
    -- LEVEL_1 - Team Lead
    -- LEVEL_2 - IT Manager
    -- LEVEL_3 - Director
    
    -- Action to take
    action_type VARCHAR(50) DEFAULT 'NOTIFY',
    -- NOTIFY - Send notification only
    -- REASSIGN - Reassign to escalation target
    -- ESCALATE_STATUS - Change ticket status to ESCALATED
    
    -- Notification message template
    notification_message VARCHAR(500),
    
    -- Timing constraints
    start_time TIME,  -- Start time for when rule applies (e.g., 09:00)
    end_time TIME,    -- End time (e.g., 18:00)
    days_of_week VARCHAR(50), -- Comma-separated: MON,TUE,WED,THU,FRI
    
    -- Limits
    max_escalations INTEGER DEFAULT 3,  -- Max times this rule can trigger per ticket
    
    -- Status
    enabled BOOLEAN DEFAULT TRUE,
    priority INTEGER DEFAULT 0,  -- Rule evaluation order (higher = first)
    
    -- Metadata
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_escalation_rules_trigger ON escalation_rules(trigger_type);
CREATE INDEX IF NOT EXISTS idx_escalation_rules_priority ON escalation_rules(min_priority);
CREATE INDEX IF NOT EXISTS idx_escalation_rules_enabled ON escalation_rules(enabled);
CREATE INDEX IF NOT EXISTS idx_escalation_rules_user ON escalation_rules(escalate_to_user_id);
CREATE INDEX IF NOT EXISTS idx_escalation_rules_team ON escalation_rules(escalate_to_team_id);

-- ============================================================
-- Create escalation_history table to track escalations
-- ============================================================

CREATE TABLE escalation_history (
    id BIGSERIAL PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    ticket_number VARCHAR(50),
    
    -- Rule that triggered
    rule_id BIGINT,
    rule_name VARCHAR(100),
    
    -- Escalation details
    escalation_level VARCHAR(20),
    escalation_reason VARCHAR(200),
    
    -- What happened
    action_taken VARCHAR(50),
    notification_sent BOOLEAN DEFAULT FALSE,
    notification_recipients VARCHAR(500),
    
    -- Reassignment details
    previous_assignee VARCHAR(100),
    new_assignee VARCHAR(100),
    previous_team_id BIGINT,
    new_team_id BIGINT,
    
    -- Status change
    previous_status VARCHAR(30),
    new_status VARCHAR(30),
    
    -- Timestamps
    escalated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP,
    
    -- Notes
    notes TEXT,
    
    -- Metadata
    created_by VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_escalation_history_ticket ON escalation_history(ticket_id);
CREATE INDEX IF NOT EXISTS idx_escalation_history_rule ON escalation_history(rule_id);
CREATE INDEX IF NOT EXISTS idx_escalation_history_level ON escalation_history(escalation_level);
CREATE INDEX IF NOT EXISTS idx_escalation_history_date ON escalation_history(escalated_at);

-- Add foreign key constraints
ALTER TABLE escalation_rules 
    ADD CONSTRAINT fk_escalation_rules_user 
    FOREIGN KEY (escalate_to_user_id) 
    REFERENCES users(id) 
    ON DELETE SET NULL;

ALTER TABLE escalation_rules 
    ADD CONSTRAINT fk_escalation_rules_team 
    FOREIGN KEY (escalate_to_team_id) 
    REFERENCES teams(id) 
    ON DELETE SET NULL;

ALTER TABLE escalation_history 
    ADD CONSTRAINT fk_escalation_history_ticket 
    FOREIGN KEY (ticket_id) 
    REFERENCES tickets(id) 
    ON DELETE CASCADE;

ALTER TABLE escalation_history 
    ADD CONSTRAINT fk_escalation_history_rule 
    FOREIGN KEY (rule_id) 
    REFERENCES escalation_rules(id) 
    ON DELETE SET NULL;

-- ============================================================
-- Insert default escalation rules
-- ============================================================

-- Rule 1: CRITICAL tickets - notify Team Lead immediately
INSERT INTO escalation_rules (name, description, trigger_type, min_priority, minutes_threshold, escalation_level, action_type, enabled, priority)
VALUES (
    'Critical Ticket Alert',
    'Thông báo Team Lead ngay khi có ticket CRITICAL',
    'CRITICAL_TICKET',
    'CRITICAL',
    0,
    'LEVEL_1',
    'NOTIFY',
    TRUE,
    100
);

-- Rule 2: SLA Response Warning - notify current assignee
INSERT INTO escalation_rules (name, description, trigger_type, min_priority, minutes_threshold, escalation_level, action_type, notification_message, enabled, priority)
VALUES (
    'SLA Response Warning',
    'Cảnh báo khi SLA phản hồi sắp hết hạn',
    'SLA_RESPONSE_WARNING',
    'HIGH',
    0,
    'LEVEL_1',
    'NOTIFY',
    'SLA phản hồi cho ticket #{ticket_number} sắp hết hạn!',
    TRUE,
    50
);

-- Rule 3: SLA Response Breached - escalate to Team Lead
INSERT INTO escalation_rules (name, description, trigger_type, min_priority, minutes_threshold, escalation_level, action_type, enabled, priority)
VALUES (
    'SLA Response Breached',
    'Escalate lên Team Lead khi SLA phản hồi bị vi phạm',
    'SLA_RESPONSE_BREACHED',
    'HIGH',
    0,
    'LEVEL_1',
    'ESCALATE_STATUS',
    TRUE,
    80
);

-- Rule 4: SLA Resolution Warning - notify current assignee
INSERT INTO escalation_rules (name, description, trigger_type, min_priority, minutes_threshold, escalation_level, action_type, notification_message, enabled, priority)
VALUES (
    'SLA Resolution Warning',
    'Cảnh báo khi SLA giải quyết sắp hết hạn',
    'SLA_RESOLUTION_WARNING',
    'MEDIUM',
    0,
    'LEVEL_1',
    'NOTIFY',
    'SLA giải quyết cho ticket #{ticket_number} sắp hết hạn!',
    TRUE,
    40
);

-- Rule 5: SLA Resolution Breached - escalate to IT Manager
INSERT INTO escalation_rules (name, description, trigger_type, min_priority, minutes_threshold, escalation_level, action_type, enabled, priority)
VALUES (
    'SLA Resolution Breached',
    'Escalate lên IT Manager khi SLA giải quyết bị vi phạm',
    'SLA_RESOLUTION_BREACHED',
    'HIGH',
    0,
    'LEVEL_2',
    'ESCALATE_STATUS',
    TRUE,
    90
);

-- Rule 6: CRITICAL ticket not responded within 15 minutes - escalate to Manager
INSERT INTO escalation_rules (name, description, trigger_type, min_priority, minutes_threshold, escalation_level, action_type, enabled, priority)
VALUES (
    'Critical Ticket Response Timeout',
    'CRITICAL ticket không phản hồi sau 15 phút - escalate lên Manager',
    'SLA_RESPONSE_WARNING',
    'CRITICAL',
    0,
    'LEVEL_2',
    'ESCALATE_STATUS',
    TRUE,
    95
);
