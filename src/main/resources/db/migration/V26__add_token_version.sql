-- V26: per-session revocation for bearer tokens.
-- Bumping users.token_version invalidates every JWT already issued for that user,
-- because JwtAuthenticationFilter rejects any token whose `tv` claim does not match
-- the stored value. POST /api/auth/logout increments it.
ALTER TABLE users ADD COLUMN IF NOT EXISTS token_version INTEGER NOT NULL DEFAULT 0;
