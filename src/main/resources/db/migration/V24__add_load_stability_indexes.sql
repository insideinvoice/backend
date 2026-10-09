-- Inside Invoice Database Schema
-- Flyway Migration V24: Load-stability indexes found during a full-backend scan.
--
-- 1. Label list screens filter business_id + deleted_at IS NULL and order by
--    created_at DESC. A partial index matching that exact predicate lets Postgres
--    serve both the filter and the sort from one index and ignores soft-delete
--    tombstones entirely.
-- 2. customers.phone and products.hsn are looked up on the create/upsert hot path
--    but had no index (their email/name twins did) → sequential scans.
-- 3. users/invoices get a global created_at index for the cross-tenant admin
--    list/analytics queries that ORDER BY created_at with no business_id filter.

CREATE INDEX IF NOT EXISTS idx_shipping_labels_business_live_created
    ON shipping_labels(business_id, created_at DESC) WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_hazmat_labels_business_live_created
    ON hazmat_labels(business_id, created_at DESC) WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_customers_business_phone
    ON customers(business_id, phone);

CREATE INDEX IF NOT EXISTS idx_products_business_hsn
    ON products(business_id, hsn);

CREATE INDEX IF NOT EXISTS idx_users_created_at
    ON users(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_invoices_created_at
    ON invoices(created_at DESC);
