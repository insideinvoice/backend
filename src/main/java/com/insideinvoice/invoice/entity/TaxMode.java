package com.insideinvoice.invoice.entity;

/**
 * Invoice-level Indian GST tax mode. Stored as a plain string column
 * ({@code invoices.tax_mode}, V34); NULL means "not recorded" (every legacy
 * row) and is resolved at render time — historical invoices are NEVER
 * force-assigned or recalculated by the migration.
 *
 * <p>The total tax is mode-independent (GST applies to the taxable value
 * either way) — this enum only records WHICH structure the invoice presents:</p>
 * <ul>
 *   <li>{@link #CGST_SGST} — intra-state supply: the rate splits into a central
 *       leg and a state/UT leg (the state leg prints as SGST or UTGST).</li>
 *   <li>{@link #IGST} — inter-state supply: the full rate as one IGST leg.</li>
 * </ul>
 *
 * <p>Determination follows the legally applicable place of supply versus the
 * supplier's (business) state — never the customer's billing address or the
 * Destination field. All rules live here so the service, the PDF/email
 * renderers and the frontend mirror share one definition.</p>
 */
public enum TaxMode {
    /** Intra-state: CGST + SGST/UTGST legs (rate split in half). */
    CGST_SGST,
    /** Inter-state: single IGST leg at the full rate. */
    IGST;

    /** Null-safe parse: unknown/blank values become {@code null} (not recorded). */
    public static TaxMode fromRaw(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * The legally applicable mode for a supply, or {@code null} when the
     * information is insufficient to establish it. Same state (incl. a Union
     * Territory matching itself) -> split; different states -> IGST. Never
     * guesses from partial data.
     */
    public static TaxMode determine(String supplierState, String placeOfSupply) {
        String supplier = supplierState == null ? "" : supplierState.trim();
        String pos = placeOfSupply == null ? "" : placeOfSupply.trim();
        if (supplier.isEmpty() || pos.isEmpty()) {
            return null;
        }
        return supplier.equalsIgnoreCase(pos) ? CGST_SGST : IGST;
    }

    /**
     * The mode a renderer must show: the stored selection always wins (a user
     * override is never silently changed), then the legal heuristic for legacy
     * rows, then the intra-state split — which is exactly what every template
     * printed before this field existed, so unresolved invoices render
     * unchanged.
     */
    public static TaxMode resolveForDisplay(TaxMode stored, String supplierState, String placeOfSupply) {
        if (stored != null) {
            return stored;
        }
        TaxMode derived = determine(supplierState, placeOfSupply);
        return derived != null ? derived : CGST_SGST;
    }

    /**
     * The mode to persist when a create request does not carry one: derived
     * from supplier state vs place of supply, or {@code null} when it cannot
     * be determined — never a blind IGST/CGST_SGST assignment.
     */
    public static TaxMode defaultForSave(String supplierState, String placeOfSupply) {
        return determine(supplierState, placeOfSupply);
    }

    /**
     * Whether presenting {@code mode} matches GST rules for the supply.
     * {@code true} when the applicable mode cannot be established (validation
     * must not block what the system cannot verify).
     */
    public static boolean isConsistent(TaxMode mode, String supplierState, String placeOfSupply) {
        if (mode == null) {
            return true;
        }
        TaxMode applicable = determine(supplierState, placeOfSupply);
        return applicable == null || applicable == mode;
    }
}
