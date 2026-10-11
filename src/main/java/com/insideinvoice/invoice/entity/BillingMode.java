package com.insideinvoice.invoice.entity;

/**
 * How a construction invoice represents its charges. Stored as a plain string
 * column ({@code invoices.billing_mode}); NULL means a standard (non-construction
 * or legacy) invoice and is always treated as {@link #ITEMIZED} for rendering.
 *
 * <p>Both modes are ordinary line-item invoices — this enum only records which
 * form the contractor used, so re-opening an invoice restores the right editor.
 * Totals always come from the shared line-item engine; the project amount is
 * never added on top of itemized components.</p>
 */
public enum BillingMode {
    /** One line carrying the agreed project amount. */
    COMPLETE_PROJECT,
    /** Separate work/material/labour category lines. */
    ITEMIZED;

    /** Null-safe parse: unknown/blank values become {@code null} (standard invoice). */
    public static BillingMode fromRaw(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
