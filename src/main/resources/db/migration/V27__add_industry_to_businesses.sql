-- V27: Persist the selected business industry (industry-based dynamic configuration).
--
-- The value is a stable identifier from the application-level Industry registry
-- (see com.insideinvoice.business.industry.Industry), never a display name.
-- DEFAULT 'OTHER' keeps every pre-existing business working unchanged: the
-- Other / General Business profile hides no fields and disables no documents,
-- so legacy rows behave exactly as before this migration.
ALTER TABLE businesses ADD COLUMN IF NOT EXISTS industry VARCHAR(40) NOT NULL DEFAULT 'OTHER';

COMMENT ON COLUMN businesses.industry IS
    'Stable industry profile id (e.g. TRADING, CONSTRUCTION, OTHER). Drives field/document configuration; not a tax determination.';
