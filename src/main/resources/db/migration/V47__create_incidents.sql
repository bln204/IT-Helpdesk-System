-- ============================================================
-- V47: Create Incidents Table
-- Purpose: Store incidents that group related tickets
-- ============================================================

-- Create incidents table
CREATE TABLE incidents (
    id BIGSERIAL PRIMARY KEY,
    incident_number VARCHAR(50) UNIQUE NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    
    -- Status tracking
    status VARCHAR(30) DEFAULT 'INVESTIGATING',
    -- INVESTIGATING - Đang điều tra nguyên nhân
    -- IDENTIFIED - Đã xác định nguyên nhân
    -- RESOLVED - Đã giải quyết
    -- CLOSED - Đã đóng
    
    priority VARCHAR(20),
    
    -- Assignment
    assigned_to_id BIGINT,
    team_id BIGINT,
    
    -- Reporting
    reported_by_id BIGINT,
    reported_by_username VARCHAR(100),
    
    -- Impact tracking
    impact_level VARCHAR(20) DEFAULT 'LOW',
    -- LOW - Ảnh hưởng một vài người
    -- MEDIUM - Ảnh hưởng một team/bộ phận
    -- HIGH - Ảnh hưởng toàn bộ công ty
    -- CRITICAL - Ảnh hưởng nghiêm trọng
    
    affected_users INTEGER DEFAULT 0,
    
    -- Root cause analysis
    root_cause TEXT,
    workaround TEXT,
    resolution TEXT,
    
    -- Timestamps
    identified_at TIMESTAMP,
    resolved_at TIMESTAMP,
    closed_at TIMESTAMP,
    
    -- Link count (denormalized for performance)
    linked_ticket_count INTEGER DEFAULT 0,
    
    -- Metadata
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_incidents_number ON incidents(incident_number);
CREATE INDEX IF NOT EXISTS idx_incidents_status ON incidents(status);
CREATE INDEX IF NOT EXISTS idx_incidents_priority ON incidents(priority);
CREATE INDEX IF NOT EXISTS idx_incidents_assigned ON incidents(assigned_to_id);
CREATE INDEX IF NOT EXISTS idx_incidents_team ON incidents(team_id);
CREATE INDEX IF NOT EXISTS idx_incidents_created ON incidents(created_at DESC);

-- ============================================================
-- Create ticket_incident_links table
-- ============================================================

CREATE TABLE ticket_incident_links (
    id BIGSERIAL PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    incident_id BIGINT NOT NULL,
    
    -- Why is this ticket linked?
    link_reason VARCHAR(200),
    
    -- Who linked
    linked_by VARCHAR(100),
    linked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Impact of this ticket to the incident
    impact VARCHAR(20) DEFAULT 'LOW',
    
    -- Metadata
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_ticket_incident_links_ticket ON ticket_incident_links(ticket_id);
CREATE INDEX IF NOT EXISTS idx_ticket_incident_links_incident ON ticket_incident_links(incident_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_ticket_incident_unique ON ticket_incident_links(ticket_id, incident_id);

-- Add foreign keys
ALTER TABLE ticket_incident_links
    ADD CONSTRAINT fk_ticket_incident_ticket
    FOREIGN KEY (ticket_id)
    REFERENCES tickets(id)
    ON DELETE CASCADE;

ALTER TABLE ticket_incident_links
    ADD CONSTRAINT fk_ticket_incident_incident
    FOREIGN KEY (incident_id)
    REFERENCES incidents(id)
    ON DELETE CASCADE;

-- ============================================================
-- Add incident_id column to tickets table
-- ============================================================

ALTER TABLE tickets
    ADD COLUMN incident_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_tickets_incident ON tickets(incident_id);

ALTER TABLE tickets
    ADD CONSTRAINT fk_tickets_incident
    FOREIGN KEY (incident_id)
    REFERENCES incidents(id)
    ON DELETE SET NULL;

-- ============================================================
-- Create incident_categories table (for common incident types)
-- ============================================================

CREATE TABLE incident_categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    
    -- Common resolution templates
    resolution_template TEXT,
    
    -- Is this a common/reusable category?
    is_common BOOLEAN DEFAULT TRUE,
    
    -- Priority suggestion
    suggested_priority VARCHAR(20),
    
    enabled BOOLEAN DEFAULT TRUE,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Insert common incident categories
INSERT INTO incident_categories (name, description, is_common, suggested_priority) VALUES
    ('Internet/VPN Down', 'Sự cố mất kết nối Internet hoặc VPN', TRUE, 'HIGH'),
    ('Email System Down', 'Hệ thống email không hoạt động', TRUE, 'HIGH'),
    ('Application Server Down', 'Server của ứng dụng không truy cập được', TRUE, 'CRITICAL'),
    ('Database Issue', 'Vấn đề về database', TRUE, 'HIGH'),
    ('Network Equipment Failure', 'Thiết bị mạng hỏng (router, switch)', TRUE, 'HIGH'),
    ('Power Outage', 'Mất điện ảnh hưởng đến IT', TRUE, 'CRITICAL'),
    ('Security Incident', 'Sự cố bảo mật', TRUE, 'CRITICAL'),
    ('Hardware Failure', 'Thiết bị phần cứng hỏng', TRUE, 'MEDIUM'),
    ('Software Bug', 'Lỗi phần mềm ảnh hưởng nhiều người', TRUE, 'MEDIUM'),
    ('Other', 'Các sự cố khác', TRUE, 'MEDIUM');

-- ============================================================
-- Create incident_timeline table (similar to ticket timeline)
-- ============================================================

CREATE TABLE incident_timeline (
    id BIGSERIAL PRIMARY KEY,
    incident_id BIGINT NOT NULL,
    
    event_type VARCHAR(50) NOT NULL,
    -- CREATED, STATUS_CHANGED, TICKET_LINKED, TICKET_UNLINKED,
    -- ASSIGNEE_CHANGED, RESOLVED, CLOSED, NOTE_ADDED
    
    title VARCHAR(200),
    description TEXT,
    
    -- Who did this
    actor_name VARCHAR(100),
    actor_role VARCHAR(50),
    
    -- Old/new values for tracking changes
    old_value VARCHAR(200),
    new_value VARCHAR(200),
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_incident_timeline_incident ON incident_timeline(incident_id);
CREATE INDEX IF NOT EXISTS idx_incident_timeline_date ON incident_timeline(created_at DESC);

ALTER TABLE incident_timeline
    ADD CONSTRAINT fk_incident_timeline_incident
    FOREIGN KEY (incident_id)
    REFERENCES incidents(id)
    ON DELETE CASCADE;
