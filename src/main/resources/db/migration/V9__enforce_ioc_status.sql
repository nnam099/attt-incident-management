-- Reject invalid IoC lifecycle values at the database boundary as well as in Java.
ALTER TABLE incident_iocs
    DROP CONSTRAINT IF EXISTS ck_incident_iocs_status;

ALTER TABLE incident_iocs
    ADD CONSTRAINT ck_incident_iocs_status
    CHECK (status IN ('ACTIVE', 'REMOVED'));
