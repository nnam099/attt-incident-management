-- ================================================================
-- V6: Harden default admin account and add token versioning
-- ================================================================
-- Goals:
--   1. Disable the seed admin account created in V2 so it cannot log in.
--   2. Add token_version to users – incrementing it invalidates all
--      existing JWTs for that user immediately.
--   3. Add password_changed_at to users – used for audit and
--      future password-age policy enforcement.
--   4. Revoke all existing refresh tokens for the disabled admin.
--
-- Safety contract:
--   • All ADD COLUMN statements are additive (no existing column removed).
--   • The admin account disable is idempotent (UPDATE WHERE username = 'admin').
--   • Refresh token deletion is scoped to the admin user only.
--   • Script is wrapped in a DO block that validates pre-conditions and
--     raises an exception on schema drift before any DML runs.
-- ================================================================

DO $$
DECLARE
    v_col_exists BOOLEAN;
BEGIN
    -- ── Pre-condition guard: verify users table exists ──────────────
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = current_schema()
          AND table_name = 'users'
    ) THEN
        RAISE EXCEPTION 'V6 pre-condition failed: table "users" not found in schema "%"',
            current_schema();
    END IF;

    -- ── Pre-condition guard: verify refresh_tokens table exists ─────
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = current_schema()
          AND table_name = 'refresh_tokens'
    ) THEN
        RAISE EXCEPTION 'V6 pre-condition failed: table "refresh_tokens" not found in schema "%"',
            current_schema();
    END IF;

    -- ── Add token_version column if missing ─────────────────────────
    SELECT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name  = 'users'
          AND column_name = 'token_version'
    ) INTO v_col_exists;

    IF NOT v_col_exists THEN
        ALTER TABLE users
            ADD COLUMN token_version BIGINT NOT NULL DEFAULT 0;
    ELSE
        -- Verify existing column has compatible type
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = current_schema()
              AND table_name   = 'users'
              AND column_name  = 'token_version'
              AND data_type    IN ('bigint', 'integer', 'smallint')
        ) THEN
            RAISE EXCEPTION 'V6 schema conflict: column users.token_version exists but has unexpected type.';
        END IF;
    END IF;

    -- ── Add password_changed_at column if missing ───────────────────
    SELECT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name  = 'users'
          AND column_name = 'password_changed_at'
    ) INTO v_col_exists;

    IF NOT v_col_exists THEN
        ALTER TABLE users
            ADD COLUMN password_changed_at TIMESTAMP;
    END IF;

    -- ── Disable the default seed admin ──────────────────────────────
    -- We do NOT delete the account; we disable it and bump token_version
    -- so any previously issued JWT becomes invalid immediately.
    UPDATE users
    SET enabled            = FALSE,
        token_version      = COALESCE(token_version, 0) + 1,
        password_changed_at = NOW()
    WHERE username = 'admin'
      AND enabled  = TRUE;   -- idempotent: no-op if already disabled

    -- Log to stderr for visibility in Flyway output
    RAISE NOTICE 'V6: seed admin account disabled (if it was enabled). token_version incremented.';

    -- ── Revoke all refresh tokens for the admin account ─────────────
    DELETE FROM refresh_tokens
    WHERE user_id IN (
        SELECT id FROM users WHERE username = 'admin'
    );

    RAISE NOTICE 'V6: refresh tokens for admin account revoked.';
END;
$$;

-- ── Create index on token_version for fast JWT validation ───────────
-- Partial index: only index rows where token_version > 0
-- (accounts that have had at least one token invalidation)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = current_schema()
          AND tablename  = 'users'
          AND indexname  = 'idx_users_token_version'
    ) THEN
        CREATE INDEX idx_users_token_version ON users (id, token_version);
    END IF;
END;
$$;

COMMENT ON COLUMN users.token_version IS
    'Monotonically increasing version counter. Incrementing this value '
    'invalidates all JWTs issued before the increment. '
    'Stored in JWT claim "tv". Filter rejects tokens where claim < current value.';

COMMENT ON COLUMN users.password_changed_at IS
    'Timestamp of the last password change. Used for password-age policy '
    'enforcement and security audit. NULL = never changed since account creation.';
