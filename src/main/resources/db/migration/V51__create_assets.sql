-- ============================================================
-- V51: Create Asset Management
-- Purpose: Track IT hardware/software assets and their lifecycle
-- ============================================================

-- ============================================================
-- Create assets table
-- ============================================================

CREATE TABLE assets (
    id BIGSERIAL PRIMARY KEY,
    asset_number VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    asset_type VARCHAR(50) NOT NULL,
    category VARCHAR(100),
    manufacturer VARCHAR(100),
    model VARCHAR(100),
    serial_number VARCHAR(100),
    part_number VARCHAR(100),
    location VARCHAR(200),
    building VARCHAR(100),
    floor VARCHAR(50),
    room VARCHAR(100),
    rack_position VARCHAR(50),
    assigned_to VARCHAR(100),
    assigned_department VARCHAR(100),
    assigned_location VARCHAR(200),
    status VARCHAR(30) DEFAULT 'ACTIVE',
    health_status VARCHAR(20) DEFAULT 'HEALTHY',
    health_notes TEXT,
    purchase_date DATE,
    purchase_cost DECIMAL(12, 2),
    warranty_expiry_date DATE,
    lease_expiry_date DATE,
    depreciation_rate DECIMAL(5, 2),
    current_value DECIMAL(12, 2),
    specifications JSONB,
    ip_address VARCHAR(45),
    mac_address VARCHAR(17),
    hostname VARCHAR(100),
    network_segment VARCHAR(50),
    vlan VARCHAR(20),
    software_name VARCHAR(200),
    software_version VARCHAR(50),
    license_key VARCHAR(200),
    license_type VARCHAR(50),
    license_seats_total INTEGER DEFAULT 0,
    license_seats_used INTEGER DEFAULT 0,
    cloud_provider VARCHAR(50),
    cloud_region VARCHAR(50),
    cloud_resource_id VARCHAR(200),
    resource_type VARCHAR(100),
    parent_asset_id BIGINT,
    vendor_name VARCHAR(200),
    vendor_contact VARCHAR(100),
    vendor_contract_number VARCHAR(100),
    last_maintenance_date DATE,
    next_maintenance_date DATE,
    tags TEXT,
    notes TEXT,
    photo_url VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_assets_number ON assets(asset_number);
CREATE INDEX IF NOT EXISTS idx_assets_type ON assets(asset_type);
CREATE INDEX IF NOT EXISTS idx_assets_status ON assets(status);
CREATE INDEX IF NOT EXISTS idx_assets_health ON assets(health_status);
CREATE INDEX IF NOT EXISTS idx_assets_location ON assets(location);
CREATE INDEX IF NOT EXISTS idx_assets_assigned ON assets(assigned_to);
CREATE INDEX IF NOT EXISTS idx_assets_category ON assets(category);
CREATE INDEX IF NOT EXISTS idx_assets_warranty ON assets(warranty_expiry_date);
CREATE INDEX IF NOT EXISTS idx_assets_created ON assets(created_at DESC);

-- ============================================================
-- Create asset_maintenance_records table
-- ============================================================

CREATE TABLE asset_maintenance_records (
    id BIGSERIAL PRIMARY KEY,
    asset_id BIGINT NOT NULL,
    maintenance_type VARCHAR(50),
    description TEXT,
    performed_by VARCHAR(100),
    performed_at DATE,
    cost DECIMAL(10, 2),
    outcome VARCHAR(50),
    next_maintenance_date DATE,
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_asset_maintenance_asset ON asset_maintenance_records(asset_id);
CREATE INDEX IF NOT EXISTS idx_asset_maintenance_performed ON asset_maintenance_records(performed_at DESC);

ALTER TABLE asset_maintenance_records ADD CONSTRAINT fk_asset_maintenance_asset FOREIGN KEY (asset_id) REFERENCES assets(id) ON DELETE CASCADE;

-- ============================================================
-- Create asset_assignments table
-- ============================================================

CREATE TABLE asset_assignments (
    id BIGSERIAL PRIMARY KEY,
    asset_id BIGINT NOT NULL,
    assigned_to VARCHAR(100),
    assigned_to_name VARCHAR(200),
    assigned_department VARCHAR(100),
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    returned_at TIMESTAMP,
    purpose VARCHAR(200),
    notes TEXT,
    status VARCHAR(20) DEFAULT 'ACTIVE'
);

CREATE INDEX IF NOT EXISTS idx_asset_assignments_asset ON asset_assignments(asset_id);
CREATE INDEX IF NOT EXISTS idx_asset_assignments_assigned ON asset_assignments(assigned_to);
CREATE INDEX IF NOT EXISTS idx_asset_assignments_status ON asset_assignments(status);

ALTER TABLE asset_assignments ADD CONSTRAINT fk_asset_assignments_asset FOREIGN KEY (asset_id) REFERENCES assets(id) ON DELETE CASCADE;

-- ============================================================
-- Create asset_software table - Software installed on assets
-- ============================================================

CREATE TABLE asset_software (
    id BIGSERIAL PRIMARY KEY,
    asset_id BIGINT NOT NULL,
    software_name VARCHAR(200) NOT NULL,
    software_version VARCHAR(50),
    publisher VARCHAR(200),
    install_date DATE,
    license_key VARCHAR(200),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_asset_software_asset ON asset_software(asset_id);
CREATE INDEX IF NOT EXISTS idx_asset_software_name ON asset_software(software_name);

ALTER TABLE asset_software ADD CONSTRAINT fk_asset_software_asset FOREIGN KEY (asset_id) REFERENCES assets(id) ON DELETE CASCADE;

-- ============================================================
-- Create asset_locations table
-- ============================================================

CREATE TABLE asset_locations (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    address TEXT,
    parent_location_id BIGINT,
    asset_count INTEGER DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_asset_locations_name ON asset_locations(name);
CREATE INDEX IF NOT EXISTS idx_asset_locations_parent ON asset_locations(parent_location_id);

-- ============================================================
-- Create software_catalog table - Software inventory
-- ============================================================

CREATE TABLE software_catalog (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    publisher VARCHAR(200),
    category VARCHAR(100),
    version VARCHAR(50),
    license_type VARCHAR(50),
    total_licenses INTEGER DEFAULT 0,
    used_licenses INTEGER DEFAULT 0,
    available_licenses INTEGER DEFAULT 0,
    purchase_date DATE,
    expiry_date DATE,
    cost DECIMAL(10, 2),
    compliance_status VARCHAR(20) DEFAULT 'COMPLIANT',
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_software_catalog_name ON software_catalog(name);
CREATE INDEX IF NOT EXISTS idx_software_catalog_compliance ON software_catalog(compliance_status);

-- ============================================================
-- Insert sample data
-- ============================================================

INSERT INTO asset_locations (name, description, asset_count) VALUES
('HQ Building', 'Trụ sở chính', 150),
('Data Center A', 'Data center tầng 3', 45),
('Branch Office 1', 'Chi nhánh TP.HCM', 30);

INSERT INTO assets (asset_number, name, description, asset_type, category, manufacturer, model, serial_number, status, health_status, location, purchase_date, warranty_expiry_date, purchase_cost, ip_address) VALUES
('AST-001', 'Dell PowerEdge R740', 'Production database server', 'SERVER', 'Hardware', 'Dell', 'PowerEdge R740', 'DL-R740-001', 'ACTIVE', 'HEALTHY', 'Data Center A', '2024-01-15', '2027-01-15', 45000000, '192.168.1.10'),
('AST-002', 'HP ProLiant DL380', 'Application server', 'SERVER', 'Hardware', 'HP', 'ProLiant DL380', 'HP-DL380-002', 'ACTIVE', 'WARNING', 'Data Center A', '2023-06-01', '2026-06-01', 38000000, '192.168.1.11'),
('AST-003', 'MacBook Pro 16', 'Laptop for development', 'LAPTOP', 'Hardware', 'Apple', 'MacBook Pro 16', 'C02X1234ABCD', 'ACTIVE', 'HEALTHY', 'HQ Building', '2024-03-10', '2026-03-10', 65000000, NULL),
('AST-004', 'Cisco Catalyst 9300', 'Core network switch', 'NETWORK_DEVICE', 'Hardware', 'Cisco', 'Catalyst 9300', 'CC-9300-004', 'ACTIVE', 'HEALTHY', 'Data Center A', '2023-11-20', '2026-11-20', 28000000, '192.168.1.1'),
('AST-005', 'Microsoft Office 365', 'Office productivity suite', 'SOFTWARE_LICENSE', 'Software', 'Microsoft', 'Office 365 Business', 'MS-O365-005', 'ACTIVE', 'HEALTHY', 'Cloud', '2024-01-01', '2025-01-01', 12000000, NULL),
('AST-006', 'Dell OptiPlex 7090', 'Workstation for accounting', 'WORKSTATION', 'Hardware', 'Dell', 'OptiPlex 7090', 'DL-OPT-006', 'ACTIVE', 'CRITICAL', 'HQ Building', '2022-05-15', '2025-05-15', 25000000, '192.168.1.50'),
('AST-007', 'Synology RS1221+', 'NAS backup storage', 'STORAGE', 'Hardware', 'Synology', 'RS1221+', 'SN-RS1221-007', 'ACTIVE', 'HEALTHY', 'Data Center A', '2024-02-28', '2027-02-28', 18000000, '192.168.1.20'),
('AST-008', 'Ubuntu Server 22.04', 'Linux server OS', 'SOFTWARE_LICENSE', 'Software', 'Canonical', 'Ubuntu Server 22.04', 'UB-22-008', 'ACTIVE', 'HEALTHY', 'Cloud', NULL, NULL, 0, NULL);

UPDATE asset_locations SET asset_count = 4 WHERE name = 'Data Center A';
UPDATE asset_locations SET asset_count = 2 WHERE name = 'HQ Building';
