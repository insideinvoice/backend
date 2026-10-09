-- Inside Invoice Database Schema
-- Flyway Migration V23: Composite indexes for tenant list screens.
-- Each list endpoint filters by business_id and orders by created_at DESC;
-- a composite index lets Postgres serve both in a single index scan instead of
-- filtering then sorting.

CREATE INDEX IF NOT EXISTS idx_customers_business_created_at
    ON customers(business_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_payments_business_created_at
    ON payments(business_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_products_business_created_at
    ON products(business_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_delivery_challans_business_created_at
    ON delivery_challans(business_id, created_at DESC);
