-- ============================================================
-- V48: Create Service Requests and Service Catalog
-- Purpose: Support Service Request management with catalog
-- ============================================================

-- ============================================================
-- Create service_catalog table - Define available services
-- ============================================================

CREATE TABLE service_catalog (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    
    -- Category grouping
    category VARCHAR(100),
    
    -- Icon and visual
    icon VARCHAR(50),
    color VARCHAR(20),
    
    -- SLA for this service
    response_sla_minutes INTEGER DEFAULT 480,  -- 8 hours default
    resolution_sla_minutes INTEGER DEFAULT 2880, -- 48 hours default
    
    -- Pricing/Cost
    cost DECIMAL(10, 2) DEFAULT 0,
    billable BOOLEAN DEFAULT FALSE,
    
    -- Approval workflow
    requires_approval BOOLEAN DEFAULT FALSE,
    approver_role VARCHAR(50),
    auto_approve BOOLEAN DEFAULT FALSE,
    
    -- Form fields configuration (JSON)
    form_fields JSONB,
    -- Example: [{"name":"server_name","type":"text","required":true},
    --          {"name":"access_level","type":"select","options":["Read","Write","Admin"]}]
    
    -- Status
    enabled BOOLEAN DEFAULT TRUE,
    is_public BOOLEAN DEFAULT TRUE,
    
    -- Metadata
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100)
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_service_catalog_category ON service_catalog(category);
CREATE INDEX IF NOT EXISTS idx_service_catalog_enabled ON service_catalog(enabled);

-- Insert default service catalog items
INSERT INTO service_catalog (name, description, category, icon, color, response_sla_minutes, resolution_sla_minutes, is_public) VALUES
('Yêu cầu tài khoản mới', 'Tạo tài khoản người dùng mới trong hệ thống', 'Tài khoản', '👤', '#3498db', 480, 1440, TRUE),
('Reset mật khẩu', 'Yêu cầu reset mật khẩu AD/Email', 'Tài khoản', '🔑', '#e74c3c', 60, 240, TRUE),
('Phân quyền thư mục', 'Yêu cầu phân quyền truy cập thư mục/folder', 'Tài khoản', '📁', '#9b59b6', 480, 1440, TRUE),
('Cấp quyền VPN', 'Cấp quyền truy cập VPN từ xa', 'Mạng', '🌐', '#2ecc71', 120, 480, TRUE),
('Cấp quyền Email', 'Phân quyền hộp thư, distribution list', 'Email', '📧', '#3498db', 240, 720, TRUE),
('Yêu cầu phần mềm', 'Cài đặt phần mềm mới theo yêu cầu', 'Phần mềm', '💿', '#f39c12', 480, 1440, TRUE),
('Yêu cầu thiết bị', 'Cấp phát thiết bị mới (laptop, mouse, keyboard)', 'Thiết bị', '💻', '#1abc9c', 1440, 4320, TRUE),
('Yêu cầu license', 'Cấp phát license phần mềm', 'Phần mềm', '📜', '#e67e22', 480, 1440, TRUE),
('Yêu cầu mailbox mới', 'Tạo mailbox Exchange mới', 'Email', '📬', '#9b59b6', 480, 1440, TRUE),
('Cấu hình email forwarding', 'Thiết lập email forwarding tự động', 'Email', '↪️', '#3498db', 120, 480, TRUE),
('Yêu cầu API key', 'Cấp phát API key cho developer', 'Developer', '🔑', '#34495e', 240, 720, TRUE),
('Truy cập Git repository', 'Cấp quyền truy cập Git repo', 'Developer', '📦', '#2c3e50', 240, 720, TRUE);

-- ============================================================
-- Create service_requests table
-- ============================================================

CREATE TABLE service_requests (
    id BIGSERIAL PRIMARY KEY,
    request_number VARCHAR(50) UNIQUE NOT NULL,
    
    -- Service reference
    service_id BIGINT,
    service_name VARCHAR(200),
    
    -- Request details
    title VARCHAR(300) NOT NULL,
    description TEXT,
    
    -- Requester info
    requester_name VARCHAR(100),
    requester_username VARCHAR(100),
    requester_email VARCHAR(160),
    requester_department VARCHAR(100),
    
    -- Status tracking
    status VARCHAR(30) DEFAULT 'SUBMITTED',
    -- SUBMITTED - Đã gửi
    -- PENDING_APPROVAL - Chờ phê duyệt
    -- APPROVED - Đã phê duyệt
    -- IN_PROGRESS - Đang xử lý
    -- COMPLETED - Hoàn thành
    -- REJECTED - Từ chối
    -- CANCELLED - Hủy bỏ
    
    -- Assignment
    assigned_to VARCHAR(100),
    team_id BIGINT,
    
    -- SLA
    sla_response_at TIMESTAMP,
    sla_resolution_at TIMESTAMP,
    first_response_at TIMESTAMP,
    completed_at TIMESTAMP,
    
    -- Approval info
    approval_required BOOLEAN DEFAULT FALSE,
    approved_by VARCHAR(100),
    approved_at TIMESTAMP,
    approval_notes TEXT,
    
    -- Form data (JSON)
    form_data JSONB,
    
    -- Cost tracking
    estimated_cost DECIMAL(10, 2),
    actual_cost DECIMAL(10, 2),
    billable BOOLEAN DEFAULT FALSE,
    invoiced BOOLEAN DEFAULT FALSE,
    
    -- Priority (can be different from ticket priority)
    priority VARCHAR(20) DEFAULT 'MEDIUM',
    
    -- Linked ticket (if needed)
    linked_ticket_id BIGINT,
    
    -- Feedback
    rating INTEGER,
    feedback TEXT,
    feedback_at TIMESTAMP,
    
    -- Metadata
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_service_requests_number ON service_requests(request_number);
CREATE INDEX IF NOT EXISTS idx_service_requests_status ON service_requests(status);
CREATE INDEX IF NOT EXISTS idx_service_requests_requester ON service_requests(requester_username);
CREATE INDEX IF NOT EXISTS idx_service_requests_service ON service_requests(service_id);
CREATE INDEX IF NOT EXISTS idx_service_requests_created ON service_requests(created_at DESC);

-- Add foreign key to service catalog
ALTER TABLE service_requests
    ADD CONSTRAINT fk_service_requests_service
    FOREIGN KEY (service_id)
    REFERENCES service_catalog(id)
    ON DELETE SET NULL;

-- ============================================================
-- Create service_request_comments table
-- ============================================================

CREATE TABLE service_request_comments (
    id BIGSERIAL PRIMARY KEY,
    service_request_id BIGINT NOT NULL,
    
    author_name VARCHAR(100),
    author_username VARCHAR(100),
    author_role VARCHAR(50),
    
    -- Comment visibility
    visibility VARCHAR(20) DEFAULT 'PUBLIC',
    -- PUBLIC - Visible to requester
    -- INTERNAL - IT staff only
    
    body TEXT NOT NULL,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_service_request_comments_request ON service_request_comments(service_request_id);
CREATE INDEX IF NOT EXISTS idx_service_request_comments_author ON service_request_comments(author_username);

ALTER TABLE service_request_comments
    ADD CONSTRAINT fk_service_request_comments_request
    FOREIGN KEY (service_request_id)
    REFERENCES service_requests(id)
    ON DELETE CASCADE;

-- ============================================================
-- Create service_approval_workflow table
-- ============================================================

CREATE TABLE service_approval_workflow (
    id BIGSERIAL PRIMARY KEY,
    service_request_id BIGINT NOT NULL,
    
    approver_name VARCHAR(100),
    approver_username VARCHAR(100),
    approver_role VARCHAR(50),
    
    -- Approval status
    status VARCHAR(20) DEFAULT 'PENDING',
    -- PENDING - Chờ duyệt
    -- APPROVED - Đã duyệt
    -- REJECTED - Từ chối
    
    decision_at TIMESTAMP,
    notes TEXT,
    
    -- Order in workflow
    step_order INTEGER DEFAULT 1,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_service_approval_workflow_request ON service_approval_workflow(service_request_id);
CREATE INDEX IF NOT EXISTS idx_service_approval_workflow_approver ON service_approval_workflow(approver_username);

ALTER TABLE service_approval_workflow
    ADD CONSTRAINT fk_service_approval_workflow_request
    FOREIGN KEY (service_request_id)
    REFERENCES service_requests(id)
    ON DELETE CASCADE;
