-- Per-user invoice convention (e.g. INV-RSHWE, INV-2026, INV-00001) is longer than 10 chars
ALTER TABLE businesses ALTER COLUMN invoice_prefix TYPE VARCHAR(50);
