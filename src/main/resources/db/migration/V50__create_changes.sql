-- ============================================================
-- V50: Create Change Management
-- Purpose: Manage IT infrastructure changes with approval workflow
-- ============================================================

-- ============================================================
-- Create change_requests table
-- ============================================================

CREATE TABLE change_requests (
    id BIGSERIAL PRIMARY KEY,
    change_number VARCHAR(50) UNIQUE NOT NULL,
    
    -- Change details
    title VARCHAR(300) NOT NULL,
    description TEXT,
    
    -- Change type
    change_type VARCHAR(30) DEFAULT 'STANDARD',
    -- STANDARD - Thay đổi standard, ít rủi ro
    -- NORMAL - Thay đổi normal, cần approve
    -- EMERGENCY - Thay đổi khẩn cấp
    
    -- Category
    category VARCHAR(100),
    -- HARDWARE, SOFTWARE, NETWORK, SECURITY, DATABASE, APPLICATION, INFRASTRUCTURE, PROCESS
    
    -- Risk assessment (HERO ELEMENT)
    risk_level VARCHAR(20) DEFAULT 'MEDIUM',
    -- LOW - Ít ảnh hưởng
    -- MEDIUM - Có ảnh hưởng
    -- HIGH - Nghiêm trọng
    -- CRITICAL - Rất nghiêm trọng
    
    risk_score INTEGER DEFAULT 0,
    -- 1-100 risk score
    
    risk_factors JSONB,
    -- [{"factor":"user_impact","score":30}, {"factor":"system_availability","score":40}]
    
    risk_mitigation TEXT,
    
    -- Impact assessment
    impact_level VARCHAR(20) DEFAULT 'MODERATE',
    -- MINIMAL, MODERATE, SIGNIFICANT, SEVERE, CATASTROPHIC
    
    affected_systems TEXT,
    -- JSON array of affected systems
    
    affected_users_count INTEGER DEFAULT 0,
    estimated_downtime_minutes INTEGER DEFAULT 0,
    
    -- Status workflow
    status VARCHAR(30) DEFAULT 'DRAFT',
    -- DRAFT - Nháp
    -- SUBMITTED - Đã gửi chờ review
    -- PENDING_APPROVAL - Chờ phê duyệt
    -- APPROVED - Đã phê duyệt
    -- REJECTED - Từ chối
    -- SCHEDULED - Đã lên lịch
    -- IN_PROGRESS - Đang thực hiện
    -- COMPLETED - Hoàn thành
    -- ROLLED_BACK - Đã rollback
    -- CANCELLED - Hủy bỏ
    
    -- Implementation plan
    implementation_plan TEXT,
    -- Step-by-step implementation instructions
    
    rollback_plan TEXT,
    -- Rollback procedure if needed
    
    -- Schedule
    scheduled_start_date TIMESTAMP,
    scheduled_end_date TIMESTAMP,
    actual_start_date TIMESTAMP,
    actual_end_date TIMESTAMP,
    
    -- Assignment
    requester_name VARCHAR(100),
    requester_username VARCHAR(100),
    requester_email VARCHAR(160),
    requester_department VARCHAR(100),
    
    assigned_to VARCHAR(100),
    team_id BIGINT,
    cab_board VARCHAR(50),
    -- CAB (Change Advisory Board) assignment
    
    -- Approval info
    approved_by VARCHAR(100),
    approved_at TIMESTAMP,
    approval_notes TEXT,
    
    rejection_reason TEXT,
    rejected_by VARCHAR(100),
    rejected_at TIMESTAMP,
    
    -- Change owner
    change_owner VARCHAR(100),
    
    -- Completion info
    completion_notes TEXT,
    completed_by VARCHAR(100),
    completed_at TIMESTAMP,
    
    -- Review & Lessons learned
    review_notes TEXT,
    lessons_learned TEXT,
    
    -- Backout confirmation
    backout_confirmed BOOLEAN DEFAULT FALSE,
    backout_performed BOOLEAN DEFAULT FALSE,
    
    -- Links
    linked_tickets TEXT,
    -- JSON array of linked ticket IDs
    
    linked_problems TEXT,
    -- JSON array of linked problem IDs
    
    linked_incidents TEXT,
    -- JSON array of linked incident IDs
    
    -- Priority
    priority VARCHAR(20) DEFAULT 'MEDIUM',
    -- LOW, MEDIUM, HIGH, CRITICAL
    
    -- Urgency
    urgency VARCHAR(20) DEFAULT 'NORMAL',
    -- LOW, NORMAL, HIGH, CRITICAL
    
    -- Metadata
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_change_requests_number ON change_requests(change_number);
CREATE INDEX IF NOT EXISTS idx_change_requests_status ON change_requests(status);
CREATE INDEX IF NOT EXISTS idx_change_requests_type ON change_requests(change_type);
CREATE INDEX IF NOT EXISTS idx_change_requests_risk ON change_requests(risk_level);
CREATE INDEX IF NOT EXISTS idx_change_requests_schedule ON change_requests(scheduled_start_date);
CREATE INDEX IF NOT EXISTS idx_change_requests_requester ON change_requests(requester_username);
CREATE INDEX IF NOT EXISTS idx_change_requests_created ON change_requests(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_change_requests_owner ON change_requests(change_owner);

-- ============================================================
-- Create change_approvals table - Approval workflow
-- ============================================================

CREATE TABLE change_approvals (
    id BIGSERIAL PRIMARY KEY,
    change_request_id BIGINT NOT NULL,
    
    approver_name VARCHAR(100),
    approver_username VARCHAR(100),
    approver_role VARCHAR(50),
    
    -- Approval level/sequence
    approval_level INTEGER DEFAULT 1,
    -- Level 1: IT Manager
    -- Level 2: Change Manager
    -- Level 3: CAB Board
    
    -- Decision
    status VARCHAR(20) DEFAULT 'PENDING',
    -- PENDING - Chờ duyệt
    -- APPROVED - Đã duyệt
    -- REJECTED - Từ chối
    -- SKIPPED - Bỏ qua
    
    decision_at TIMESTAMP,
    comments TEXT,
    
    -- Notification
    notified_at TIMESTAMP,
    reminded_at TIMESTAMP,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_change_approvals_request ON change_approvals(change_request_id);
CREATE INDEX IF NOT EXISTS idx_change_approvals_approver ON change_approvals(approver_username);
CREATE INDEX IF NOT EXISTS idx_change_approvals_status ON change_approvals(status);

ALTER TABLE change_approvals
    ADD CONSTRAINT fk_change_approvals_request
    FOREIGN KEY (change_request_id)
    REFERENCES change_requests(id)
    ON DELETE CASCADE;

-- ============================================================
-- Create change_timeline table - Audit trail
-- ============================================================

CREATE TABLE change_timeline (
    id BIGSERIAL PRIMARY KEY,
    change_request_id BIGINT NOT NULL,
    
    event_type VARCHAR(50) NOT NULL,
    -- CREATED, SUBMITTED, APPROVED, REJECTED, SCHEDULED, STARTED, 
    -- COMPLETED, ROLLED_BACK, CANCELLED, COMMENT_ADDED, FILE_ATTACHED
    
    actor_name VARCHAR(100),
    actor_username VARCHAR(100),
    actor_role VARCHAR(50),
    
    event_data JSONB,
    -- Additional event-specific data
    
    notes TEXT,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_change_timeline_request ON change_timeline(change_request_id);
CREATE INDEX IF NOT EXISTS idx_change_timeline_event ON change_timeline(event_type);
CREATE INDEX IF NOT EXISTS idx_change_timeline_created ON change_timeline(created_at DESC);

ALTER TABLE change_timeline
    ADD CONSTRAINT fk_change_timeline_request
    FOREIGN KEY (change_request_id)
    REFERENCES change_requests(id)
    ON DELETE CASCADE;

-- ============================================================
-- Create change_tasks table - Implementation tasks
-- ============================================================

CREATE TABLE change_tasks (
    id BIGSERIAL PRIMARY KEY,
    change_request_id BIGINT NOT NULL,
    
    task_order INTEGER DEFAULT 1,
    
    title VARCHAR(300) NOT NULL,
    description TEXT,
    
    status VARCHAR(20) DEFAULT 'PENDING',
    -- PENDING, IN_PROGRESS, COMPLETED, FAILED, SKIPPED
    
    assigned_to VARCHAR(100),
    
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    
    output TEXT,
    -- Task output/results
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_change_tasks_request ON change_tasks(change_request_id);
CREATE INDEX idx_change_tasks_status ON change_tasks(status);

ALTER TABLE change_tasks
    ADD CONSTRAINT fk_change_tasks_request
    FOREIGN KEY (change_request_id)
    REFERENCES change_requests(id)
    ON DELETE CASCADE;

-- ============================================================
-- Create change_risk_matrix table - Risk assessment rules
-- ============================================================

CREATE TABLE change_risk_matrix (
    id BIGSERIAL PRIMARY KEY,
    
    name VARCHAR(100) NOT NULL,
    description TEXT,
    
    -- Risk factors
    user_impact_score INTEGER DEFAULT 0,
    system_availability_score INTEGER DEFAULT 0,
    data_integrity_score INTEGER DEFAULT 0,
    security_impact_score INTEGER DEFAULT 0,
    compliance_impact_score INTEGER DEFAULT 0,
    
    -- Thresholds
    low_threshold INTEGER DEFAULT 20,
    medium_threshold INTEGER DEFAULT 50,
    high_threshold INTEGER DEFAULT 75,
    
    -- Result
    calculated_risk_level VARCHAR(20),
    
    enabled BOOLEAN DEFAULT TRUE,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Insert default risk matrix
INSERT INTO change_risk_matrix (name, description, user_impact_score, system_availability_score, data_integrity_score, security_impact_score, compliance_impact_score) VALUES
('Default IT Risk Matrix', 'Standard risk assessment for IT changes', 25, 30, 25, 15, 5);

-- ============================================================
-- Insert sample change categories
-- ============================================================

-- Sample change requests
INSERT INTO change_requests (change_number, title, description, change_type, category, risk_level, impact_level, status, priority) VALUES
('CHG-001', 'Nâng cấp firmware switch core', 'Nâng cấp firmware lên phiên bản mới nhất cho switch core datacenter', 'NORMAL', 'NETWORK', 'MEDIUM', 'SIGNIFICANT', 'SCHEDULED', 'HIGH'),
('CHG-002', 'Thêm memory cho production server', 'Bổ sung 32GB RAM cho server ERP production', 'STANDARD', 'HARDWARE', 'LOW', 'MODERATE', 'COMPLETED', 'MEDIUM'),
('CHG-003', 'Update SSL certificates', 'Renew SSL certificates cho các subdomain', 'STANDARD', 'SECURITY', 'LOW', 'MINIMAL', 'APPROVED', 'LOW');
