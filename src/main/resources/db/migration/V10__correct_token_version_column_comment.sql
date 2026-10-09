-- Keep released migrations immutable. This migration updates the documentation
-- after token validation was tightened to require an exact version match.
COMMENT ON COLUMN users.token_version IS
    'Monotonically increasing version counter. Incrementing this value '
    'invalidates all JWTs issued before the increment. '
    'Stored in JWT claim "tv". Filter rejects tokens where claim differs from the current value.';
