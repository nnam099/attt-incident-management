-- ================================================================
-- V1: Khởi tạo schema cho Hệ thống tiếp nhận & xử lý sự cố ATTT
-- ================================================================

CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(30) NOT NULL UNIQUE,
    description VARCHAR(255)
);

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    full_name VARCHAR(150),
    department VARCHAR(100),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE incident_categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500),
    default_severity VARCHAR(20)
);

CREATE TABLE incidents (
    id BIGSERIAL PRIMARY KEY,
    incident_code VARCHAR(30) NOT NULL UNIQUE,
    title VARCHAR(255) NOT NULL,
    description TEXT,                         -- lưu dạng mã hóa (AES-GCM) ở tầng ứng dụng
    affected_system VARCHAR(255),
    category_id BIGINT REFERENCES incident_categories(id),
    severity VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'NEW',
    reported_by BIGINT REFERENCES users(id),
    assigned_to BIGINT REFERENCES users(id),
    detected_at TIMESTAMP,
    sla_due_at TIMESTAMP,
    closed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP
);

CREATE INDEX idx_incidents_status ON incidents(status);
CREATE INDEX idx_incidents_severity ON incidents(severity);
CREATE INDEX idx_incidents_assigned_to ON incidents(assigned_to);
CREATE INDEX idx_incidents_reported_by ON incidents(reported_by);

CREATE TABLE incident_logs (
    id BIGSERIAL PRIMARY KEY,
    incident_id BIGINT NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    performed_by BIGINT NOT NULL REFERENCES users(id),
    action_type VARCHAR(50) NOT NULL,
    old_value VARCHAR(255),
    new_value VARCHAR(255),
    note TEXT,
    record_hash VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_incident_logs_incident_id ON incident_logs(incident_id);

-- Ngăn UPDATE/DELETE trên nhật ký xử lý để đảm bảo tính toàn vẹn (append-only)
CREATE RULE incident_logs_no_update AS ON UPDATE TO incident_logs DO INSTEAD NOTHING;
CREATE RULE incident_logs_no_delete AS ON DELETE TO incident_logs DO INSTEAD NOTHING;

CREATE TABLE incident_attachments (
    id BIGSERIAL PRIMARY KEY,
    incident_id BIGINT NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    content_type VARCHAR(100),
    file_size BIGINT,
    uploaded_by BIGINT REFERENCES users(id),
    uploaded_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_incident_attachments_incident_id ON incident_attachments(incident_id);
