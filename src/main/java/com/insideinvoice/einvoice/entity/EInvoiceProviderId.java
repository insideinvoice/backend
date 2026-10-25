package com.insideinvoice.einvoice.entity;

/**
 * Authorised integration providers. MOCK exists for automated tests and
 * local development ONLY — it can never be combined with PRODUCTION
 * (enforced in the service layer), and every response it produces is
 * stamped isMock=true so no fabricated IRN can masquerade as government
 * registration data.
 */
public enum EInvoiceProviderId {
    IRIS_IRP,
    MOCK
}
