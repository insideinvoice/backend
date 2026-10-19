-- V31: Configurable "Subject to ... Jurisdiction" footer per business.
--
-- The jurisdiction line on invoices was previously hardcoded to
-- "SUBJECT TO BENGALURU JURISDICTION" in the frontend renderers, so no
-- business outside Bengaluru could print a legally correct footer.
--
-- Defaults preserve today's behaviour exactly: the line is SHOWN and reads
-- BENGALURU until the business overrides it in Settings. The region is
-- nullable because no existing business has ever been asked to pick one —
-- writing a guess into historical rows would fabricate legal data, so it
-- stays blank until the owner chooses it.
ALTER TABLE businesses ADD COLUMN IF NOT EXISTS jurisdiction_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE businesses ADD COLUMN IF NOT EXISTS jurisdiction_region VARCHAR(120);
ALTER TABLE businesses ADD COLUMN IF NOT EXISTS jurisdiction_city VARCHAR(120) NOT NULL DEFAULT 'Bengaluru';

COMMENT ON COLUMN businesses.jurisdiction_enabled IS
    'Whether the "SUBJECT TO {CITY} JURISDICTION" footer line is rendered on invoice documents and public share pages.';
COMMENT ON COLUMN businesses.jurisdiction_region IS
    'Selected Indian state or Union Territory (validated against the IndianRegions reference list). Presentation/admin only - never rendered on the invoice.';
COMMENT ON COLUMN businesses.jurisdiction_city IS
    'City/location printed in the jurisdiction footer, e.g. Bengaluru. Backfilled with the historical hardcoded value so existing businesses keep their current footer until they change it.';
