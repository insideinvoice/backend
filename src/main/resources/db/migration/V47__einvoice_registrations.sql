-- One row per IRP submission outcome for an invoice. Government-generated
-- data (IRN, Ack, signed QR) lands here verbatim from the provider response.
CREATE TABLE einvoice_registrations (
    id                    BIGSERIAL PRIMARY KEY,
    invoice_id            BIGINT NOT NULL REFERENCES invoices(id),
    business_id           BIGINT NOT NULL REFERENCES businesses(id),
    gstin                 VARCHAR(15) NOT NULL,
    provider              VARCHAR(20) NOT NULL,
    environment           VARCHAR(10) NOT NULL,
    doc_type              VARCHAR(20) NOT NULL,
    doc_number            VARCHAR(50) NOT NULL,
    doc_date              DATE NOT NULL,
    status                VARCHAR(30) NOT NULL,
    irn                   VARCHAR(64),
    ack_number            VARCHAR(20),
    ack_date              TIMESTAMPTZ,
    signed_invoice_json   TEXT,
    signed_qr_data        TEXT,
    provider_reference    VARCHAR(100),
    error_code            VARCHAR(30),
    error_message         TEXT,
    registered_at         TIMESTAMPTZ,
    cancelled_at          TIMESTAMPTZ,
    cancellation_reason   VARCHAR(200),
    is_mock               BOOLEAN NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT einvoice_registrations_status_chk CHECK (status IN
      ('REGISTRATION_PENDING','REGISTERED','REGISTRATION_FAILED','RECONCILIATION_REQUIRED',
       'CANCELLATION_PENDING','CANCELLED','CANCELLATION_FAILED'))
);
-- A document may hold only ONE active (non-cancelled) registration.
CREATE UNIQUE INDEX einvoice_registrations_active_uq
  ON einvoice_registrations(invoice_id) WHERE status <> 'CANCELLED';
CREATE INDEX idx_einvoice_registrations_irn ON einvoice_registrations(irn);
