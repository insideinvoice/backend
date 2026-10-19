-- V32: Business logo for the Enterprise invoice template.
--
-- The logo is uploaded from Settings (POST /api/business/logo) and stored as a
-- Base64 data payload exactly like businesses.signature — no file storage, the
-- value is rendered inline as a data URI on invoice documents.
--
-- Nullable: a business can exist without a logo forever; the Enterprise
-- template renders a monogram fallback instead of reserving blank space.
ALTER TABLE businesses ADD COLUMN IF NOT EXISTS logo TEXT;

COMMENT ON COLUMN businesses.logo IS
    'Base64-encoded company logo uploaded from Settings. Rendered as a fixed-box image in the top-left corner of the Enterprise (template-32) invoice; null = monogram fallback.';
