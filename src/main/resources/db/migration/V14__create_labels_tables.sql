CREATE TABLE shipping_labels (
    id BIGSERIAL PRIMARY KEY,
    business_id BIGINT NOT NULL,
    invoice_id BIGINT,
    label_number VARCHAR(40) NOT NULL,
    preset VARCHAR(40) NOT NULL,
    label_size VARCHAR(20) NOT NULL,
    dpi INT NOT NULL DEFAULT 203,
    carrier VARCHAR(60),
    service_level VARCHAR(80),
    tracking_number VARCHAR(80),
    ship_date DATE,
    from_json JSONB,
    to_json JSONB,
    package_weight_kg DECIMAL(8, 3),
    dims_cm TEXT,
    carton_index INT NOT NULL DEFAULT 1,
    carton_total INT NOT NULL DEFAULT 1,
    reference_fields JSONB,
    barcode_payloads JSONB,
    thermal_mode BOOLEAN NOT NULL DEFAULT TRUE,
    service_code VARCHAR(40),
    generated_by VARCHAR(80),
    fnsku_copies INT,
    fba_json JSONB,
    gs1_json JSONB,
    fnsku_items JSONB,
    print_border BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    pdf_sha256 CHAR(64),
    created_by BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_shipping_labels_number UNIQUE (business_id, label_number),
    CONSTRAINT fk_shipping_labels_business FOREIGN KEY (business_id) REFERENCES businesses(id),
    CONSTRAINT fk_shipping_labels_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id)
);

CREATE INDEX idx_shipping_labels_business_id ON shipping_labels(business_id);
CREATE INDEX idx_shipping_labels_invoice_id ON shipping_labels(invoice_id);
CREATE INDEX idx_shipping_labels_created_at ON shipping_labels(created_at DESC);
CREATE INDEX idx_shipping_labels_status ON shipping_labels(status);
CREATE INDEX idx_shipping_labels_carrier ON shipping_labels(carrier);

CREATE TABLE hazmat_labels (
    id BIGSERIAL PRIMARY KEY,
    business_id BIGINT NOT NULL,
    invoice_id BIGINT,
    label_number VARCHAR(40) NOT NULL,
    un_number VARCHAR(10),
    proper_shipping_name VARCHAR(200) NOT NULL,
    technical_name VARCHAR(200),
    hazard_class VARCHAR(10) NOT NULL,
    division VARCHAR(4),
    compat_group VARCHAR(4),
    packing_group VARCHAR(4),
    subsidiary_risks VARCHAR(400),
    net_quantity VARCHAR(40),
    package_count INT NOT NULL DEFAULT 1,
    transport_mode VARCHAR(10) NOT NULL DEFAULT 'ROAD',
    label_type VARCHAR(24) NOT NULL DEFAULT 'CLASS_DIAMOND',
    consignor_json JSONB,
    consignee_json JSONB,
    emergency_phone VARCHAR(40),
    gra_code VARCHAR(40),
    erg_guide VARCHAR(10),
    lithium_wh DECIMAL(8, 2),
    notes VARCHAR(1000),
    radiation_category VARCHAR(8),
    limited_quantity_code VARCHAR(40),
    lithium_un_list VARCHAR(120),
    lithium_packing_instruction VARCHAR(40),
    generated_by VARCHAR(80),
    overpack BOOLEAN NOT NULL DEFAULT FALSE,
    marine_pollutant BOOLEAN NOT NULL DEFAULT FALSE,
    label_size VARCHAR(20) NOT NULL,
    color_mode VARCHAR(12) NOT NULL DEFAULT 'THERMAL_BW',
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    pdf_sha256 CHAR(64),
    created_by BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_hazmat_labels_number UNIQUE (business_id, label_number),
    CONSTRAINT fk_hazmat_labels_business FOREIGN KEY (business_id) REFERENCES businesses(id),
    CONSTRAINT fk_hazmat_labels_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id)
);

CREATE INDEX idx_hazmat_labels_business_id ON hazmat_labels(business_id);
CREATE INDEX idx_hazmat_labels_un_number ON hazmat_labels(un_number);
CREATE INDEX idx_hazmat_labels_created_at ON hazmat_labels(created_at DESC);

CREATE TABLE label_files (
    label_id BIGINT NOT NULL,
    label_type VARCHAR(16) NOT NULL,
    pdf_bytes BYTEA NOT NULL,
    pdf_sha256 CHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (label_id, label_type)
);

CREATE TABLE un_numbers_reference (
    un_number VARCHAR(10) PRIMARY KEY,
    proper_shipping_name VARCHAR(200) NOT NULL,
    hazard_class VARCHAR(10) NOT NULL,
    division VARCHAR(4),
    compat_group VARCHAR(4),
    packing_group VARCHAR(4),
    subsidiary_risks VARCHAR(120),
    erg_guide VARCHAR(10),
    ltd_qty VARCHAR(40),
    pax_allowed BOOLEAN NOT NULL DEFAULT TRUE,
    cao_allowed BOOLEAN NOT NULL DEFAULT TRUE,
    special_provisions VARCHAR(200)
);

CREATE TABLE label_audit (
    id BIGSERIAL PRIMARY KEY,
    business_id BIGINT NOT NULL,
    label_id BIGINT NOT NULL,
    label_type VARCHAR(16) NOT NULL,
    action VARCHAR(24) NOT NULL,
    user_id BIGINT,
    ip VARCHAR(45),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_label_audit_label ON label_audit(label_id, label_type);
CREATE INDEX idx_label_audit_business ON label_audit(business_id);

CREATE OR REPLACE FUNCTION update_shipping_labels_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION update_hazmat_labels_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION update_label_files_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'update_shipping_labels_updated_at') THEN
        CREATE TRIGGER update_shipping_labels_updated_at
        BEFORE UPDATE ON shipping_labels
        FOR EACH ROW EXECUTE FUNCTION update_shipping_labels_updated_at();
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'update_hazmat_labels_updated_at') THEN
        CREATE TRIGGER update_hazmat_labels_updated_at
        BEFORE UPDATE ON hazmat_labels
        FOR EACH ROW EXECUTE FUNCTION update_hazmat_labels_updated_at();
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'update_label_files_updated_at') THEN
        CREATE TRIGGER update_label_files_updated_at
        BEFORE UPDATE ON label_files
        FOR EACH ROW EXECUTE FUNCTION update_label_files_updated_at();
    END IF;
END $$;
