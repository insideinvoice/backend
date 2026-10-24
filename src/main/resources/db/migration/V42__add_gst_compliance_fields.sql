-- GST Rule 46 compliance fields (Ship-To, reverse charge, cess, e-way bill).
-- All nullable/defaulted: legacy invoices keep their exact prior rendering.
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS shipping_address TEXT;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS reverse_charge BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS cess_percentage NUMERIC(5,2) DEFAULT 0;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS cess_amount NUMERIC(12,2) DEFAULT 0;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS eway_bill_number VARCHAR(50);
