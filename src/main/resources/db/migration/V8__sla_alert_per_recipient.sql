-- Track successful SLA deliveries per recipient so a partial SMTP failure does
-- not resend messages to recipients that already received the alert.
ALTER TABLE sla_alert_history
    ADD COLUMN IF NOT EXISTS recipient_key VARCHAR(200);

UPDATE sla_alert_history
SET recipient_key = 'LEGACY_SCOPE:' || recipient_scope
WHERE recipient_key IS NULL;

ALTER TABLE sla_alert_history
    ALTER COLUMN recipient_key SET NOT NULL;

ALTER TABLE sla_alert_history
    DROP CONSTRAINT IF EXISTS uq_sla_alert_history_incident_type;

ALTER TABLE sla_alert_history
    ADD CONSTRAINT uq_sla_alert_history_incident_type_recipient
    UNIQUE (incident_id, alert_type, recipient_key);
