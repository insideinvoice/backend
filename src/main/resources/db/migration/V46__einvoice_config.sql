-- GST e-Invoicing (IRN) configuration, scoped per business + GSTIN + environment.
-- Credentials are AES-GCM encrypted by the backend (EInvoiceCredentialService)
-- and NEVER stored or returned in plaintext.
CREATE TABLE einvoice_configs (
    id                       BIGSERIAL PRIMARY KEY,
    business_id              BIGINT NOT NULL REFERENCES businesses(id),
    gstin                    VARCHAR(15) NOT NULL,
    provider                 VARCHAR(20) NOT NULL,          -- IRIS_IRP | MOCK
    environment              VARCHAR(10) NOT NULL,          -- SANDBOX | PRODUCTION
    enabled                  BOOLEAN NOT NULL DEFAULT FALSE,
    auto_register            BOOLEAN NOT NULL DEFAULT FALSE,
    credentials_encrypted    TEXT,                          -- encrypted client id/secret JSON
    connection_status        VARCHAR(30) NOT NULL DEFAULT 'NOT_TESTED',
    last_connection_test_at  TIMESTAMPTZ,
    last_connection_error    TEXT,
    settings                 JSONB NOT NULL DEFAULT '{}'::jsonb, -- eligibility overrides etc.
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by               BIGINT,
    updated_by               BIGINT,
    CONSTRAINT einvoice_configs_env_chk CHECK (environment IN ('SANDBOX','PRODUCTION')),
    CONSTRAINT einvoice_configs_unique UNIQUE (business_id, gstin, environment)
);
CREATE INDEX idx_einvoice_configs_business ON einvoice_configs(business_id);
