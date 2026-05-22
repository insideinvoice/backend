-- Inside Invoice Database Schema
-- Flyway Migration V2: Additional Indexes for Performance

CREATE INDEX IF NOT EXISTS idx_invoices_business_id_status ON invoices(business_id, status);
CREATE INDEX IF NOT EXISTS idx_invoices_business_id_created_at ON invoices(business_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_invoice_items_product_id ON invoice_items(product_id);
CREATE INDEX IF NOT EXISTS idx_customers_business_id_name ON customers(business_id, name);
CREATE INDEX IF NOT EXISTS idx_products_business_id_name ON products(business_id, name);

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'update_businesses_updated_at') THEN
        CREATE TRIGGER update_businesses_updated_at
            BEFORE UPDATE ON businesses
            FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'update_users_updated_at') THEN
        CREATE TRIGGER update_users_updated_at
            BEFORE UPDATE ON users
            FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'update_customers_updated_at') THEN
        CREATE TRIGGER update_customers_updated_at
            BEFORE UPDATE ON customers
            FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'update_products_updated_at') THEN
        CREATE TRIGGER update_products_updated_at
            BEFORE UPDATE ON products
            FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'update_invoices_updated_at') THEN
        CREATE TRIGGER update_invoices_updated_at
            BEFORE UPDATE ON invoices
            FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
    END IF;
END $$;
