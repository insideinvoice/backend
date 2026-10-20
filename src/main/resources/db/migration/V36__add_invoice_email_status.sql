-- Async invoice email tracking (V36):
--   email_status NULL  = never queued (every legacy row stays NULL, no backfill)
--   email_status PENDING = queued, the provider call runs on the async pool
--   email_status SENT    = provider accepted; emailed_at records when
--   email_status FAILED  = provider/reason failed; email_error carries a short reason
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS email_status VARCHAR(20);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS email_error VARCHAR(500);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS emailed_at TIMESTAMP;
