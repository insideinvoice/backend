CREATE TABLE delivery_challans (
    id BIGSERIAL PRIMARY KEY,
    business_id BIGINT NOT NULL,
    challan_number VARCHAR(50) NOT NULL,
    customer_id BIGINT NOT NULL,
    challan_date DATE NOT NULL,
    po_number VARCHAR(100),
    po_date DATE,
    created_by BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_delivery_challans_number UNIQUE (business_id, challan_number),
    CONSTRAINT fk_delivery_challans_business FOREIGN KEY (business_id) REFERENCES businesses(id)
);

CREATE INDEX idx_delivery_challans_business_id ON delivery_challans(business_id);
CREATE INDEX idx_delivery_challans_customer_id ON delivery_challans(customer_id);
CREATE INDEX idx_delivery_challans_created_at ON delivery_challans(created_at DESC);

CREATE TABLE delivery_challan_items (
    id BIGSERIAL PRIMARY KEY,
    challan_id BIGINT NOT NULL REFERENCES delivery_challans(id) ON DELETE CASCADE,
    sno INT NOT NULL,
    description VARCHAR(500) NOT NULL,
    quantity DECIMAL(12, 3) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_delivery_challan_items_challan_id ON delivery_challan_items(challan_id);
