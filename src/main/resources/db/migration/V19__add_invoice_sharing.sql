-- Public invoice sharing (opaque bearer tokens).
-- A share token is a 256-bit SecureRandom value, base64url-encoded (43 chars).
-- share_token is NULL when the invoice has never been shared.
-- Multiple invoices may hold NULL tokens (PostgreSQL treats NULLs as distinct in unique indexes).

ALTER TABLE invoices ADD COLUMN IF NOT EXISTS share_token VARCHAR(64);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS share_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS share_created_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS share_revoked_at TIMESTAMP WITH TIME ZONE;

CREATE UNIQUE INDEX IF NOT EXISTS uq_invoices_share_token ON invoices(share_token);
