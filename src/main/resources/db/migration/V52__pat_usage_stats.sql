-- V52: per-token API usage stats for the PAT screen.
--
--   hit_count        every API call authenticated with this token (exact,
--                    incremented on each request — this replaces V50's
--                    throttled last-used write, which existed only to avoid
--                    a DB hit per request and could never count calls)
--   last_used_ip     client address of the most recent call (first hop of
--                    X-Forwarded-For when behind the platform proxy)
--   last_used_agent  User-Agent of the most recent call
--
-- Existing rows start at 0; last_used_at keeps its V50 meaning.
ALTER TABLE personal_access_tokens ADD COLUMN IF NOT EXISTS hit_count      BIGINT      NOT NULL DEFAULT 0;
ALTER TABLE personal_access_tokens ADD COLUMN IF NOT EXISTS last_used_ip   VARCHAR(64);
ALTER TABLE personal_access_tokens ADD COLUMN IF NOT EXISTS last_used_agent VARCHAR(255);
