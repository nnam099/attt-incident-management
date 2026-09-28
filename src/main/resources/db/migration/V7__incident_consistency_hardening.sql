-- Concurrency-safe incident updates and code generation.
ALTER TABLE incidents
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMP;

ALTER TABLE incident_logs
    ADD COLUMN IF NOT EXISTS previous_hash VARCHAR(128);

UPDATE incidents
SET resolved_at = COALESCE(closed_at, updated_at, created_at)
WHERE status IN ('RESOLVED', 'CLOSED')
  AND resolved_at IS NULL;

CREATE SEQUENCE IF NOT EXISTS incident_code_seq START WITH 1 INCREMENT BY 1;

-- Continue above every numeric suffix already present so upgrading an existing
-- database cannot generate duplicate incident codes.
SELECT setval(
    'incident_code_seq',
    COALESCE((
        SELECT MAX(substring(incident_code FROM '^INC-[0-9]{4}-([0-9]+)$')::BIGINT)
        FROM incidents
        WHERE incident_code ~ '^INC-[0-9]{4}-[0-9]+$'
    ), 0) + 1,
    false
);

-- Existing plaintext refresh tokens are intentionally revoked. New tokens are
-- stored as SHA-256 hashes and cannot be migrated without knowing the raw value.
DELETE FROM refresh_tokens;
