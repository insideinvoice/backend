-- Customers are unique PER BUSINESS (multi-tenant), never globally.
-- business_id is part of every index, so user1 and user2 may each hold the
-- SAME phone/email (the same person can be a customer of two businesses) --
-- only a repeat INSIDE one business is rejected.
--
-- Prod databases already hold duplicate (business_id, phone) rows created
-- before this guard existed, so a bare CREATE UNIQUE INDEX aborts the whole
-- migration and the app never boots. Neutralize the duplicates FIRST:
-- the OLDEST row (smallest id) of each group keeps the contact value; every
-- later copy has just that column blanked. No row is ever deleted and no
-- invoice link is touched -- the duplicate customer records stay queryable,
-- they simply stop carrying the phone/email that collided.

-- Phone: blank the phone on every later duplicate inside one business.
UPDATE customers AS c
SET phone = ''
FROM (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY business_id, phone
               ORDER BY id ASC
           ) AS rn
    FROM customers
    WHERE phone IS NOT NULL AND phone <> ''
) AS d
WHERE c.id = d.id
  AND d.rn > 1;

-- Email: same treatment, case-insensitive (matches the LOWER() index below).
UPDATE customers AS c
SET email = ''
FROM (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY business_id, LOWER(email)
               ORDER BY id ASC
           ) AS rn
    FROM customers
    WHERE email IS NOT NULL AND email <> ''
) AS d
WHERE c.id = d.id
  AND d.rn > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uq_customers_business_id_phone
    ON customers (business_id, phone)
    WHERE phone IS NOT NULL AND phone <> '';

-- LOWER(email) so "Acme@x.com" and "acme@x.com" collide inside the same
-- business while staying distinct across businesses (mirrors V39 for users).
CREATE UNIQUE INDEX IF NOT EXISTS uq_customers_business_id_email_lower
    ON customers (business_id, LOWER(email))
    WHERE email IS NOT NULL AND email <> '';
