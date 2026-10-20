package com.insideinvoice.invoice.entity;

/**
 * Lifecycle of the customer email for one invoice. Stored as VARCHAR
 * (same convention as {@link InvoiceStatus}): PENDING = queued on the async
 * pool, SENT = the provider accepted it, FAILED = the send failed (see
 * invoices.email_error). NULL in the database = never queued.
 */
public enum InvoiceEmailStatus {
    PENDING,
    SENT,
    FAILED
}
