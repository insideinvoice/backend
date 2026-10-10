-- V25: Seed the two default accounts so a fresh Postgres + this jar is immediately usable.
-- Idempotent: every INSERT is guarded, so re-running (or running against the existing
-- production database) is a no-op for rows that already exist. Guards are keyed on
-- business_name / email (not ids) so the migration works on both empty and live schemas.

-- ---------------------------------------------------------------------------
-- 1) rshardware (primary USER account) + business profile
-- ---------------------------------------------------------------------------
INSERT INTO businesses (
    business_name, owner_name, gst_in, phone, email,
    address_line1, address_line2, city, state, country, pincode,
    invoice_prefix, next_invoice_sequence,
    bank_name, account_no, branch, ifsc, upi_id, invoice_template
)
SELECT
    'RS Hardware Glass & Electrical', 'Fahad Pasha', '29FKLPP1223G1Z0', '8147465517', 'rshardware2210@gmail.com',
    'Building No-3/7, Shop No-6 Ground Floor', 'Gowri Shankar Complex Arekere Main Road', 'Bangalore', 'Karnataka', 'India', '560076',
    'RS', 1,
    'HDFC BANK', '50200093163651', 'Vijaya Bank Layout', 'HDFC0002841', 'paytm.s1wtxj6@pty', 'template-31'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'rshardware2210@gmail.com');

INSERT INTO users (name, email, password, role, business_id, business_setup_completed, raw_password, username, must_change_password)
SELECT 'rshardware', 'rshardware2210@gmail.com', '$2a$10$iiUZE63gPpFwuxtBIvyFAuVNFxX.83H.XkhqMiBAaFoMRENtieK6u', 'USER', b.id, true, 'RSHARDWARE', 'rshardware', false
FROM businesses b
WHERE b.gst_in = '29FKLPP1223G1Z0'
  AND NOT EXISTS (SELECT 1 FROM users WHERE email = 'rshardware2210@gmail.com')
ORDER BY b.id
LIMIT 1;

-- ---------------------------------------------------------------------------
-- 2) insideinvoice (break-glass ADMIN account) + business profile
-- ---------------------------------------------------------------------------
INSERT INTO businesses (business_name, owner_name, invoice_prefix, next_invoice_sequence)
SELECT 'Inside Invoice Admin', 'Admin', 'ADMIN', 1
WHERE NOT EXISTS (SELECT 1 FROM businesses WHERE business_name = 'Inside Invoice Admin');

INSERT INTO users (name, email, password, role, business_id, business_setup_completed, raw_password, username, must_change_password)
SELECT 'InsideInvoice Admin', 'insideinvoice', '$2y$10$gLpIm9yoGn6DMmWLck8IBuOh2VsPO0vLnKErMduICgjZ26LO7t7de', 'ADMIN', b.id, true, 'invoice6688inside', 'insideinvoice', false
FROM businesses b
WHERE b.business_name = 'Inside Invoice Admin'
  AND NOT EXISTS (SELECT 1 FROM users WHERE email = 'insideinvoice')
ORDER BY b.id
LIMIT 1;
