-- V40: Per-business default Terms & Conditions.
--
-- The owner writes up to five short lines once in Settings; every invoice
-- template is meant to print them in the footer block (template rendering
-- itself is a later change — this stores the data now).
--
-- Stored as a JSON array of strings so order is preserved and no separate
-- child table is needed:
--   ["Goods once sold will not be taken back.",
--    "Interest @18% p.a. applies on overdue payments."]
-- NULL means "no default terms" — renderers simply omit the block.
ALTER TABLE businesses ADD COLUMN IF NOT EXISTS terms_conditions TEXT;

COMMENT ON COLUMN businesses.terms_conditions IS
    'JSON array of up to five default T&C lines; NULL = none.';
