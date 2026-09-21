-- ============================================================
-- V27: Add Pending User Requests Table
-- ============================================================
-- Purpose: Track requests from TRUONG_PHONG to create users

-- Create table for pending user requests
CREATE TABLE IF NOT EXISTS user_approval_requests (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(80) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    title VARCHAR(120),
    email VARCHAR(160),
    department_id BIGINT NOT NULL,
    requested_by VARCHAR(80) NOT NULL,
    requested_at TIMESTAMP NOT NULL DEFAULT NOW(),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewed_by VARCHAR(80),
    reviewed_at TIMESTAMP,
    rejection_reason VARCHAR(255),
    CONSTRAINT fk_user_approval_department FOREIGN KEY (department_id) REFERENCES departments(id),
    CONSTRAINT fk_user_approval_requested_by FOREIGN KEY (requested_by) REFERENCES users(username),
    CONSTRAINT fk_user_approval_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES users(username),
    CONSTRAINT chk_user_approval_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_user_approval_status ON user_approval_requests(status);
CREATE INDEX IF NOT EXISTS idx_user_approval_requested_by ON user_approval_requests(requested_by);
CREATE INDEX IF NOT EXISTS idx_user_approval_department ON user_approval_requests(department_id);

-- Comments for documentation
COMMENT ON TABLE user_approval_requests IS 'Tracks user creation requests from TRUONG_PHONG that require ADMIN approval';
COMMENT ON COLUMN user_approval_requests.status IS 'Request status: PENDING, APPROVED, or REJECTED';

-- ============================================================
-- V27.1: Add Enable/Disable Request Table
-- ============================================================
-- Purpose: Track enable/disable requests from TRUONG_PHONG

CREATE TABLE IF NOT EXISTS user_enable_requests (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    requested_action VARCHAR(20) NOT NULL,
    requested_by VARCHAR(80) NOT NULL,
    requested_at TIMESTAMP NOT NULL DEFAULT NOW(),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewed_by VARCHAR(80),
    reviewed_at TIMESTAMP,
    review_notes VARCHAR(255),
    CONSTRAINT fk_user_enable_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_user_enable_requested_by FOREIGN KEY (requested_by) REFERENCES users(username),
    CONSTRAINT fk_user_enable_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES users(username),
    CONSTRAINT chk_user_enable_action CHECK (requested_action IN ('ENABLE', 'DISABLE')),
    CONSTRAINT chk_user_enable_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_user_enable_status ON user_enable_requests(status);
CREATE INDEX IF NOT EXISTS idx_user_enable_user ON user_enable_requests(user_id);
CREATE INDEX IF NOT EXISTS idx_user_enable_requested_by ON user_enable_requests(requested_by);

-- Comments for documentation
COMMENT ON TABLE user_enable_requests IS 'Tracks enable/disable requests from TRUONG_PHONG that require ADMIN approval';
COMMENT ON COLUMN user_enable_requests.requested_action IS 'Action requested: ENABLE or DISABLE';
