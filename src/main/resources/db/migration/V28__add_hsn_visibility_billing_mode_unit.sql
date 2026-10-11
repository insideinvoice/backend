-- V28: Industry invoice display + construction billing + item units.
-- All columns are additive and backward compatible:
--   * show_hsn_sac defaults TRUE, so every existing business keeps the
--     current behaviour (HSN/SAC shown) until the owner opts out.
--   * billing_mode is NULL for every pre-existing invoice; only Construction
--     invoices created from the new billing-mode selector set it.
--   * unit defaults to 'Piece' — the value every template already prints in
--     the "per" column, so existing rows render identically.

ALTER TABLE businesses ADD COLUMN IF NOT EXISTS show_hsn_sac BOOLEAN NOT NULL DEFAULT TRUE;
COMMENT ON COLUMN businesses.show_hsn_sac IS
    'Display-only switch for the HSN/SAC column on invoices. Stored codes are never deleted and GST calculations are never affected.';

ALTER TABLE invoices ADD COLUMN IF NOT EXISTS billing_mode VARCHAR(20);
COMMENT ON COLUMN invoices.billing_mode IS
    'Construction billing representation: COMPLETE_PROJECT (single agreed amount line) or ITEMIZED (category lines). NULL = standard invoice.';

ALTER TABLE invoice_items ADD COLUMN IF NOT EXISTS unit VARCHAR(50) NOT NULL DEFAULT 'Piece';
COMMENT ON COLUMN invoice_items.unit IS
    'Unit shown in the templates per column (Piece, Kg, Gram, Quintal, Ton, Litre, Box, Bag, ...).';
