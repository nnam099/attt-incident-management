-- ================================================================
-- V4.1: Tạo bảng nền cho IoC và Task gắn liền với Incident (Idempotent)
-- Ownership & Delete Behavior:
--   - incident_id: ON DELETE CASCADE vì Incident là Aggregate Root sở hữu
--     vòng đời của IoC và Task (khớp CascadeType.ALL & orphanRemoval=true trong JPA).
--   - completed_by: ON DELETE SET NULL để bảo toàn lịch sử xử lý playbook khi user bị xóa.
-- ================================================================

CREATE TABLE IF NOT EXISTS incident_iocs (
    id BIGSERIAL PRIMARY KEY,
    incident_id BIGINT NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    type VARCHAR(30) NOT NULL,
    value VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_incident_iocs_incident_id ON incident_iocs(incident_id);
CREATE INDEX IF NOT EXISTS idx_incident_iocs_type ON incident_iocs(type);
CREATE INDEX IF NOT EXISTS idx_incident_iocs_value ON incident_iocs(value);

CREATE TABLE IF NOT EXISTS incident_tasks (
    id BIGSERIAL PRIMARY KEY,
    incident_id BIGINT NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    task_name VARCHAR(255) NOT NULL,
    is_completed BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMP,
    completed_by BIGINT REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_incident_tasks_incident_id ON incident_tasks(incident_id);
CREATE INDEX IF NOT EXISTS idx_incident_tasks_is_completed ON incident_tasks(is_completed);
