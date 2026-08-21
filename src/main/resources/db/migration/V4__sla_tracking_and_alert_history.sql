-- Tách SLA tiếp nhận (MTTA) và SLA xử lý (MTTR), đồng thời lưu lịch sử alert.
ALTER TABLE incidents
    ADD COLUMN IF NOT EXISTS ack_due_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS acknowledged_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS resolve_due_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS resolution_type VARCHAR(30);

-- Dữ liệu cũ chỉ có một hạn SLA: dùng nó làm hạn xử lý để không mất lịch sử.
UPDATE incidents
SET resolve_due_at = sla_due_at
WHERE resolve_due_at IS NULL AND sla_due_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_incidents_ack_due_at ON incidents(ack_due_at);
CREATE INDEX IF NOT EXISTS idx_incidents_resolve_due_at ON incidents(resolve_due_at);

CREATE TABLE IF NOT EXISTS sla_alert_history (
    id BIGSERIAL PRIMARY KEY,
    incident_id BIGINT NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    alert_type VARCHAR(30) NOT NULL,
    recipient_scope VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_sla_alert_history_incident_type UNIQUE (incident_id, alert_type)
);

CREATE INDEX IF NOT EXISTS idx_sla_alert_history_incident ON sla_alert_history(incident_id);
