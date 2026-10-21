-- Revert of V36 (async invoice email): email sending is synchronous again, so
-- the status-tracking columns are dead weight. DROP IF EXISTS keeps this safe
-- in both states — V36 already applied (columns dropped) or not yet applied
-- (V36 + V37 run back-to-back on the same startup, net zero columns).
ALTER TABLE invoices DROP COLUMN IF EXISTS email_status;
ALTER TABLE invoices DROP COLUMN IF EXISTS email_error;
ALTER TABLE invoices DROP COLUMN IF EXISTS emailed_at;
