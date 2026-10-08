-- Shared invoice links must render the template the business owner actually
-- selected, on any visitor's device. Template/print settings previously lived
-- only in the owner's browser localStorage, so recipients always fell back to
-- template-1. Persist the choice on the business so the public DTO can serve it.
ALTER TABLE businesses ADD COLUMN IF NOT EXISTS invoice_template VARCHAR(30);
ALTER TABLE businesses ADD COLUMN IF NOT EXISTS print_settings TEXT;
