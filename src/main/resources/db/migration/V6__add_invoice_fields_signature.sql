ALTER TABLE invoices ADD COLUMN delivery_note_date DATE;

ALTER TABLE invoice_items ADD COLUMN sno INTEGER;

ALTER TABLE businesses ADD COLUMN signature TEXT;
