-- Inside Invoice Database Schema
-- Flyway Migration V35: indexes for the paginated/s-searchable admin list
-- endpoints and the SQL-side sales analytics added alongside them.

-- 1) Cross-tenant admin lists order by created_at DESC with NO business_id
--    filter (the tenant composites from V22/V23 can't serve these). Scanning
--    the plain created_at index lets Postgres emit rows in order and stop at
--    OFFSET+LIMIT instead of sorting every row of the table.
CREATE INDEX IF NOT EXISTS idx_businesses_created_at
    ON businesses(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_customers_created_at
    ON customers(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_products_created_at
    ON products(created_at DESC);

-- 2) GET /admin/users now runs a page-scoped "last activity" GROUP BY and the
--    ?activeOnly=true EXISTS filter — both probe invoices by creator + time.
CREATE INDEX IF NOT EXISTS idx_invoices_created_by_created_at
    ON invoices(created_by, created_at DESC);

-- 3) Trigram indexes for the q search on each list (lower(col) LIKE '%q%').
--    Plain btree indexes cannot serve infix matches; gin_trgm_ops can.
--    Guarded so the migration cannot fail on a database where the pg_trgm
--    extension is unavailable (managed Postgres allowlists, restricted roles):
--    the extension is attempted, then each CREATE INDEX only runs when the
--    extension actually exists.
DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS pg_trgm;
EXCEPTION
    WHEN OTHERS THEN
        RAISE NOTICE 'pg_trgm not created (skipping trigram indexes): %', SQLERRM;
END $$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_trgm') THEN
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_customers_name_trgm ON customers USING gin (lower(name) gin_trgm_ops)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_customers_email_trgm ON customers USING gin (lower(email) gin_trgm_ops)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_customers_phone_trgm ON customers USING gin (lower(phone) gin_trgm_ops)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_customers_gstin_trgm ON customers USING gin (lower(gst_in) gin_trgm_ops)';

        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_products_name_trgm ON products USING gin (lower(name) gin_trgm_ops)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_products_hsn_trgm ON products USING gin (lower(hsn) gin_trgm_ops)';

        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_users_name_trgm ON users USING gin (lower(name) gin_trgm_ops)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_users_email_trgm ON users USING gin (lower(email) gin_trgm_ops)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_users_username_trgm ON users USING gin (lower(username) gin_trgm_ops)';

        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_businesses_business_name_trgm ON businesses USING gin (lower(business_name) gin_trgm_ops)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_businesses_owner_name_trgm ON businesses USING gin (lower(owner_name) gin_trgm_ops)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_businesses_email_trgm ON businesses USING gin (lower(email) gin_trgm_ops)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_businesses_gstin_trgm ON businesses USING gin (lower(gst_in) gin_trgm_ops)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_businesses_phone_trgm ON businesses USING gin (lower(phone) gin_trgm_ops)';

        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_invoices_invoice_number_trgm ON invoices USING gin (lower(invoice_number) gin_trgm_ops)';

        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_delivery_challans_challan_number_trgm ON delivery_challans USING gin (lower(challan_number) gin_trgm_ops)';
    ELSE
        RAISE NOTICE 'pg_trgm unavailable — q search falls back to sequential scans (correct, just slower).';
    END IF;
END $$;
