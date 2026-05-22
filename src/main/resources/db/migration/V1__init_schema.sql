-- Inside Invoice Database Schema
-- Flyway Migration V1: Initial Schema

CREATE TABLE IF NOT EXISTS businesses (
    id BIGSERIAL PRIMARY KEY,
    business_name VARCHAR(255) NOT NULL,
    owner_name VARCHAR(255),
    gst_in VARCHAR(20),
    phone VARCHAR(20),
    email VARCHAR(255),
    website VARCHAR(255),
    address_line1 VARCHAR(255),
    address_line2 VARCHAR(255),
    city VARCHAR(100),
    state VARCHAR(100),
    country VARCHAR(100),
    pincode VARCHAR(10),
    invoice_prefix VARCHAR(10),
    next_invoice_sequence BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    business_id BIGINT NOT NULL,
    business_setup_completed BOOLEAN NOT NULL DEFAULT FALSE,
    reset_password_token VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_users_business FOREIGN KEY (business_id) REFERENCES businesses(id)
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_business_id ON users(business_id);

CREATE TABLE IF NOT EXISTS customers (
    id BIGSERIAL PRIMARY KEY,
    business_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    phone VARCHAR(20),
    billing_address TEXT,
    shipping_address TEXT,
    gst_in VARCHAR(20),
    city VARCHAR(100),
    state VARCHAR(100),
    country VARCHAR(100),
    pincode VARCHAR(10),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_customers_business FOREIGN KEY (business_id) REFERENCES businesses(id)
);

CREATE INDEX idx_customers_business_id ON customers(business_id);
CREATE INDEX idx_customers_email_business_id ON customers(email, business_id);

CREATE TABLE IF NOT EXISTS products (
    id BIGSERIAL PRIMARY KEY,
    business_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    hsn VARCHAR(20),
    unit VARCHAR(50),
    rate DECIMAL(12, 2) NOT NULL,
    gst_percentage DECIMAL(5, 2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_products_business FOREIGN KEY (business_id) REFERENCES businesses(id)
);

CREATE INDEX idx_products_business_id ON products(business_id);

CREATE TABLE IF NOT EXISTS invoices (
    id BIGSERIAL PRIMARY KEY,
    business_id BIGINT NOT NULL,
    invoice_number VARCHAR(50) NOT NULL,
    invoice_type VARCHAR(20) NOT NULL,
    customer_id BIGINT NOT NULL,
    invoice_date DATE NOT NULL,
    due_date DATE NOT NULL,
    subtotal DECIMAL(14, 2) NOT NULL,
    tax_amount DECIMAL(14, 2) NOT NULL,
    grand_total DECIMAL(14, 2) NOT NULL,
    payment_terms VARCHAR(100),
    notes TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    place_of_supply VARCHAR(100),
    delivery_note VARCHAR(255),
    reference_number VARCHAR(100),
    buyer_order_number VARCHAR(100),
    dispatch_doc_number VARCHAR(100),
    dispatched_through VARCHAR(100),
    terms_of_delivery TEXT,
    other_references TEXT,
    destination VARCHAR(255),
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_invoices_business FOREIGN KEY (business_id) REFERENCES businesses(id),
    CONSTRAINT fk_invoices_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_invoices_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT uq_invoice_number_per_business UNIQUE (business_id, invoice_number)
);

CREATE INDEX idx_invoices_business_id ON invoices(business_id);
CREATE INDEX idx_invoices_customer_id ON invoices(customer_id);
CREATE INDEX idx_invoices_status ON invoices(status);
CREATE INDEX idx_invoices_invoice_date ON invoices(invoice_date);

CREATE TABLE IF NOT EXISTS invoice_items (
    id BIGSERIAL PRIMARY KEY,
    invoice_id BIGINT NOT NULL,
    product_id BIGINT,
    item_name VARCHAR(255) NOT NULL,
    hsn VARCHAR(20),
    qty DECIMAL(10, 2) NOT NULL,
    rate DECIMAL(12, 2) NOT NULL,
    gst_percentage DECIMAL(5, 2) NOT NULL,
    taxable_value DECIMAL(14, 2) NOT NULL,
    tax_amount DECIMAL(14, 2) NOT NULL,
    total DECIMAL(14, 2) NOT NULL,
    CONSTRAINT fk_invoice_items_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE CASCADE
);

CREATE INDEX idx_invoice_items_invoice_id ON invoice_items(invoice_id);
