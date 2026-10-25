-- Customers are unique PER BUSINESS (multi-tenant), never globally.
-- business_id is part of every index, so user1 and user2 may each hold the
-- SAME phone/email (the same person can be a customer of two businesses) --
-- only a repeat INSIDE one business is rejected.

-- Pre-check for existing duplicates (run before applying; returns 0 rows if safe):
--   SELECT business_id, phone, COUNT(*) FROM customers
--     WHERE phone IS NOT NULL AND phone <> ''
--     GROUP BY 1, 2 HAVING COUNT(*) > 1;
--   SELECT business_id, LOWER(email), COUNT(*) FROM customers
--     WHERE email IS NOT NULL AND email <> ''
--     GROUP BY 1, 2 HAVING COUNT(*) > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uq_customers_business_id_phone
    ON customers (business_id, phone)
    WHERE phone IS NOT NULL AND phone <> '';

-- LOWER(email) so "Acme@x.com" and "acme@x.com" collide inside the same
-- business while staying distinct across businesses (mirrors V39 for users).
CREATE UNIQUE INDEX IF NOT EXISTS uq_customers_business_id_email_lower
    ON customers (business_id, LOWER(email))
    WHERE email IS NOT NULL AND email <> '';
