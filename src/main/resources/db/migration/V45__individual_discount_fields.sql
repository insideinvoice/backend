-- Per-item (INDIVIDUAL) discount support — the counterpart to the discount
-- radio added on the invoice form.
--
--   invoices.discount_type       FULL | INDIVIDUAL | NULL
--   invoice_items.discount_percent  per-line percent (0-100), NULL unless INDIVIDUAL
--
-- Both nullable: legacy rows keep NULL and are rendered exactly as before
-- (NULL is treated as the original invoice-level FULL flow whenever
-- invoices.discount_percent > 0). Nothing is backfilled or recalculated.
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS discount_type VARCHAR(20);
ALTER TABLE invoice_items ADD COLUMN IF NOT EXISTS discount_percent NUMERIC(5,2);
