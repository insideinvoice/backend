-- Idempotency + retry tracking: one request row per invoice (unique), so
-- repeated clicks, refreshes or concurrent submits can never double-register.
CREATE TABLE einvoice_registration_requests (
    id                       BIGSERIAL PRIMARY KEY,
    invoice_id               BIGINT NOT NULL UNIQUE REFERENCES invoices(id),
    idempotency_key          VARCHAR(64) NOT NULL UNIQUE,
    state                    VARCHAR(20) NOT NULL,   -- PENDING|SUBMITTED|COMPLETED|FAILED|TIMEOUT|RECONCILE_REQUIRED
    payload_hash             VARCHAR(64) NOT NULL,
    attempts                 INT NOT NULL DEFAULT 0,
    last_attempt_at          TIMESTAMPTZ,
    last_response_at         TIMESTAMPTZ,
    provider_correlation_id  VARCHAR(100),
    error_code               VARCHAR(30),
    error_message            TEXT,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Append-only audit trail (no update endpoints; altered rows detectable via
-- updated_at staying equal to created_at).
CREATE TABLE einvoice_audit_events (
    id               BIGSERIAL PRIMARY KEY,
    event_type       VARCHAR(40) NOT NULL,
    invoice_id       BIGINT,
    registration_id  BIGINT,
    business_id      BIGINT NOT NULL,
    actor_user_id    BIGINT,
    actor_type       VARCHAR(10) NOT NULL DEFAULT 'USER',  -- USER | SYSTEM
    from_status      VARCHAR(30),
    to_status        VARCHAR(30),
    operation        VARCHAR(40),
    provider_result  VARCHAR(20),
    reason           TEXT,
    correlation_id   VARCHAR(100),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_einvoice_audit_invoice ON einvoice_audit_events(invoice_id);
