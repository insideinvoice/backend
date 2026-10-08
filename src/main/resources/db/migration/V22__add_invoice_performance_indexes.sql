-- Inside Invoice Database Schema
-- Flyway Migration V22: Additional Performance Indexes for Invoices & Customers

CREATE INDEX IF NOT EXISTS idx_invoices_business_invoice_date ON invoices(business_id, invoice_date DESC);
CREATE INDEX IF NOT EXISTS idx_invoices_business_customer_id ON invoices(business_id, customer_id);
CREATE INDEX IF NOT EXISTS idx_invoices_business_invoice_num ON invoices(business_id, invoice_number);
CREATE INDEX IF NOT EXISTS idx_invoice_items_invoice_id ON invoice_items(invoice_id);
CREATE INDEX IF NOT EXISTS idx_customers_business_id_id ON customers(business_id, id);
