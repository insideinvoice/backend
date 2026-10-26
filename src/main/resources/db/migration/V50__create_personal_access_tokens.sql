-- V50: Personal Access Tokens (PATs) for third-party API integration.
-- A PAT authenticates as the owning user/business; the raw token is shown
-- exactly once at creation time and only its SHA-256 hash is persisted.

CREATE TABLE IF NOT EXISTS personal_access_tokens (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    business_id   BIGINT       NOT NULL,
    name          VARCHAR(120) NOT NULL,
    token_hash    VARCHAR(128) NOT NULL UNIQUE,
    token_prefix  VARCHAR(16)  NOT NULL,
    scopes        VARCHAR(255) NOT NULL DEFAULT 'invoices:read,invoices:write',
    expires_at    TIMESTAMP,
    last_used_at  TIMESTAMP,
    revoked_at    TIMESTAMP,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pat_user_id      ON personal_access_tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_pat_business_id  ON personal_access_tokens (business_id);
CREATE INDEX IF NOT EXISTS idx_pat_token_hash   ON personal_access_tokens (token_hash);

-- Backfill updated_at trigger parity with BaseEntity (handled by JPA @PreUpdate).
