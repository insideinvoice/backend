package com.insideinvoice.einvoice.entity;

/**
 * Application-side e-invoice lifecycle. Derived statuses (NOT_APPLICABLE,
 * DISABLED, NOT_REGISTERED, READY_TO_REGISTER) are computed from the config
 * + invoice and are never persisted; persisted rows use the submission
 * states only. An internal status must never be conflated with an official
 * government status.
 */
public enum EInvoiceStatus {
    // derived (not persisted)
    NOT_APPLICABLE, DISABLED, NOT_REGISTERED, READY_TO_REGISTER,
    // persisted
    REGISTRATION_PENDING, REGISTERED, REGISTRATION_FAILED, RECONCILIATION_REQUIRED,
    CANCELLATION_PENDING, CANCELLED, CANCELLATION_FAILED
}
