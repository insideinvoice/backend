-- V51: per-invoice seal choice ("ROUND" / "STAMP" / "NONE").
-- NULL keeps the historical behaviour: the mark comes from the business's
-- Settings (seal_enabled + seal_type + seal_config). The invoices API accepts
-- an optional sealType on create/update so third-party (PAT) callers can pin
-- a specific mark — or no mark at all — on a single invoice.
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS seal_type VARCHAR(20);
