-- Persist the discount the user applies in the editor so shared links, shared PDFs and emails can reproduce it (previously it was browser-local only).
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS discount_percent NUMERIC(5,2);
