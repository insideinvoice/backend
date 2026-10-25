-- IRP registration results printed on the invoice (e-Invoice template Zone A).
-- Values are ONLY ever written by the backend from an actual provider response
-- (or a clearly mock-stamped sandbox one); users never type them.
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS irn VARCHAR(64);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS ack_no VARCHAR(30);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS ack_date DATE;
-- NOT_REGISTERED | REGISTERED | REGISTERED_MOCK | FAILED
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS einvoice_status VARCHAR(20);
