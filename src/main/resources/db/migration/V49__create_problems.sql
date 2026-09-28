-- ============================================================
-- V49: Create Problem Management
-- Purpose: Track root cause of recurring incidents
-- ============================================================

-- ============================================================
-- Create problems table - Main problem records
-- ============================================================

CREATE TABLE problems (
    id BIGSERIAL PRIMARY KEY,
    problem_number VARCHAR(50) UNIQUE NOT NULL,
    
    -- Problem details
    title VARCHAR(300) NOT NULL,
    description TEXT,
    
    -- Categorization
    category VARCHAR(100),
    -- NETWORK, HARDWARE, SOFTWARE, SECURITY, DATABASE, APPLICATION, OTHER
    
    impact_level VARCHAR(20) DEFAULT 'MEDIUM',
    -- LOW, MEDIUM, HIGH, CRITICAL
    
    priority VARCHAR(20) DEFAULT 'MEDIUM',
    -- LOW, MEDIUM, HIGH, CRITICAL
    
    status VARCHAR(30) DEFAULT 'NEW',
    -- NEW - Mới tạo
    -- INVESTIGATING - Đang điều tra
    -- IDENTIFIED - Đã xác định nguyên nhân
    -- SOLVING - Đang giải quyết
    -- RESOLVED - Đã giải quyết (temporary)
    -- CLOSED - Đã đóng vĩnh viễn
    
    -- Root cause analysis
    root_cause TEXT,
    root_cause_category VARCHAR(100),
    -- INFRASTRUCTURE, CONFIGURATION, PROCESS, VENDOR, HUMAN_ERROR, DESIGN_FLAW
    
    root_cause_confidence VARCHAR(20) DEFAULT 'MEDIUM',
    -- LOW, MEDIUM, HIGH, CONFIRMED
    
    -- Resolution
    workaround TEXT,
    resolution TEXT,
    resolution_date TIMESTAMP,
    
    -- Known Error association
    known_error_id BIGINT,
    
    -- Impact metrics
    total_incidents_linked INTEGER DEFAULT 0,
    total_downtime_minutes INTEGER DEFAULT 0,
    estimated_cost DECIMAL(10, 2),
    
    -- Assignment
    assigned_to VARCHAR(100),
    team_id BIGINT,
    
    -- Closure info
    closed_by VARCHAR(100),
    closed_at TIMESTAMP,
    closure_notes TEXT,
    
    -- Metadata
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_problems_number ON problems(problem_number);
CREATE INDEX IF NOT EXISTS idx_problems_status ON problems(status);
CREATE INDEX IF NOT EXISTS idx_problems_category ON problems(category);
CREATE INDEX IF NOT EXISTS idx_problems_impact ON problems(impact_level);
CREATE INDEX IF NOT EXISTS idx_problems_created ON problems(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_problems_assigned ON problems(assigned_to);

-- ============================================================
-- Create known_errors table - Known Error Database (KEDB)
-- ============================================================

CREATE TABLE known_errors (
    id BIGSERIAL PRIMARY KEY,
    error_code VARCHAR(50) UNIQUE NOT NULL,
    
    -- Error details
    title VARCHAR(300) NOT NULL,
    description TEXT,
    
    -- Classification
    category VARCHAR(100),
    symptoms TEXT,
    -- JSON array of common symptoms
    
    -- Root cause
    root_cause TEXT,
    root_cause_category VARCHAR(100),
    
    -- Solution
    workaround TEXT,
    resolution TEXT,
    fix_steps TEXT,
    -- Detailed fix instructions
    
    -- Known Error Status
    status VARCHAR(30) DEFAULT 'ACTIVE',
    -- ACTIVE - Đang hoạt động (still occurring)
    -- RESOLVED - Đã được sửa (permanent fix applied)
    -- OBSOLETE - Không còn áp dụng
    
    -- Impact
    impact_description TEXT,
    affected_systems TEXT,
    -- JSON array of affected systems/applications
    
    -- Related Problems count
    related_problems_count INTEGER DEFAULT 0,
    
    -- Knowledge Base
    knowledge_article_id BIGINT,
    
    -- Assignment
    managed_by VARCHAR(100),
    team_id BIGINT,
    
    -- Usage metrics
    occurrence_count INTEGER DEFAULT 0,
    last_occurrence_at TIMESTAMP,
    
    -- Validity
    valid_from TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    valid_until TIMESTAMP,
    
    -- Metadata
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_known_errors_code ON known_errors(error_code);
CREATE INDEX IF NOT EXISTS idx_known_errors_category ON known_errors(category);
CREATE INDEX IF NOT EXISTS idx_known_errors_status ON known_errors(status);
CREATE INDEX IF NOT EXISTS idx_known_errors_occurrence ON known_errors(occurrence_count DESC);

-- ============================================================
-- Create problem_incident_links table
-- ============================================================

CREATE TABLE problem_incident_links (
    id BIGSERIAL PRIMARY KEY,
    problem_id BIGINT NOT NULL,
    incident_id BIGINT NOT NULL,
    
    -- Link details
    link_type VARCHAR(30) DEFAULT 'CAUSED_BY',
    -- CAUSED_BY - Incident caused by this problem
    -- RELATED_TO - Related to this problem
    -- DUPLICATE_OF - Duplicate of this problem
    
    linked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    linked_by VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_problem_incident_links_problem ON problem_incident_links(problem_id);
CREATE INDEX IF NOT EXISTS idx_problem_incident_links_incident ON problem_incident_links(incident_id);

ALTER TABLE problem_incident_links
    ADD CONSTRAINT fk_problem_incident_links_problem
    FOREIGN KEY (problem_id)
    REFERENCES problems(id)
    ON DELETE CASCADE;

ALTER TABLE problem_incident_links
    ADD CONSTRAINT fk_problem_incident_links_incident
    FOREIGN KEY (incident_id)
    REFERENCES incidents(id)
    ON DELETE CASCADE;

-- ============================================================
-- Create problem_ticket_links table (for regular tickets)
-- ============================================================

CREATE TABLE problem_ticket_links (
    id BIGSERIAL PRIMARY KEY,
    problem_id BIGINT NOT NULL,
    ticket_id BIGINT NOT NULL,
    
    link_type VARCHAR(30) DEFAULT 'CAUSED_BY',
    linked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    linked_by VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_problem_ticket_links_problem ON problem_ticket_links(problem_id);
CREATE INDEX IF NOT EXISTS idx_problem_ticket_links_ticket ON problem_ticket_links(ticket_id);

ALTER TABLE problem_ticket_links
    ADD CONSTRAINT fk_problem_ticket_links_problem
    FOREIGN KEY (problem_id)
    REFERENCES problems(id)
    ON DELETE CASCADE;

ALTER TABLE problem_ticket_links
    ADD CONSTRAINT fk_problem_ticket_links_ticket
    FOREIGN KEY (ticket_id)
    REFERENCES tickets(id)
    ON DELETE CASCADE;

-- ============================================================
-- Create problem_change_links table
-- ============================================================

CREATE TABLE problem_change_links (
    id BIGSERIAL PRIMARY KEY,
    problem_id BIGINT NOT NULL,
    change_id BIGINT NOT NULL,
    
    link_reason TEXT,
    linked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    linked_by VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_problem_change_links_problem ON problem_change_links(problem_id);
CREATE INDEX IF NOT EXISTS idx_problem_change_links_change ON problem_change_links(change_id);

ALTER TABLE problem_change_links
    ADD CONSTRAINT fk_problem_change_links_problem
    FOREIGN KEY (problem_id)
    REFERENCES problems(id)
    ON DELETE CASCADE;

-- ============================================================
-- Create problem_workaround_notes table
-- ============================================================

CREATE TABLE problem_workaround_notes (
    id BIGSERIAL PRIMARY KEY,
    problem_id BIGINT NOT NULL,
    
    author_name VARCHAR(100),
    author_username VARCHAR(100),
    author_role VARCHAR(50),
    
    note_type VARCHAR(30) DEFAULT 'WORKAROUND',
    -- WORKAROUND, RESOLUTION_STEP, INVESTIGATION_NOTE, LESSON_LEARNED
    
    body TEXT NOT NULL,
    
    effectiveness_rating INTEGER,
    -- 1-5 rating
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_problem_workaround_notes_problem ON problem_workaround_notes(problem_id);
CREATE INDEX IF NOT EXISTS idx_problem_workaround_notes_author ON problem_workaround_notes(author_username);

ALTER TABLE problem_workaround_notes
    ADD CONSTRAINT fk_problem_workaround_notes_problem
    FOREIGN KEY (problem_id)
    REFERENCES problems(id)
    ON DELETE CASCADE;

-- ============================================================
-- Insert sample problem categories
-- ============================================================

-- Add foreign key for known_error reference in problems
ALTER TABLE problems
    ADD CONSTRAINT fk_problems_known_error
    FOREIGN KEY (known_error_id)
    REFERENCES known_errors(id)
    ON DELETE SET NULL;

-- Insert sample known errors
INSERT INTO known_errors (error_code, title, description, category, symptoms, status) VALUES
('KE-001', 'DNS Resolution Failure', 'Server không thể resolve DNS trong giờ cao điểm', 'NETWORK', 
 '["Không truy cập được website", "Timeout khi connect", "Lỗi 503"]', 'ACTIVE'),

('KE-002', 'Database Connection Pool Exhausted', 'Connection pool bị exhaustion do connection leaks', 'DATABASE',
 '["Ứng dụng treo", "Lỗi timeout", "CPU spike"]', 'ACTIVE'),

('KE-003', 'SSL Certificate Expiry', 'SSL certificate hết hạn gây lỗi HTTPS', 'SECURITY',
 '["Lỗi SSL handshake", "Website không load", "Mixed content warnings"]', 'RESOLVED');
