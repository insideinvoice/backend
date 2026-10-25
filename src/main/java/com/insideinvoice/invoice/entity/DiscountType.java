package com.insideinvoice.invoice.entity;

/**
 * How an invoice expresses its discount ({@code invoices.discount_type}, V45).
 *
 * <ul>
 *   <li>{@link #FULL} — one invoice-level percentage (the original flow):
 *       {@code invoices.discount_percent} is applied to the subtotal and GST
 *       follows the pro-rated taxable value.</li>
 *   <li>{@link #INDIVIDUAL} — per-line percentages
 *       ({@code invoice_items.discount_percent}); each line's GST applies to
 *       its own net value. Only the e-Invoice template renders this shape, so
 *       an invoice stored as INDIVIDUAL is pinned to that template everywhere
 *       it is shown (form preview, view, share link, email).</li>
 * </ul>
 *
 * <p>NULL means "no discount configured" — every legacy row. Rendering treats
 * null exactly like {@link #FULL} whenever {@code discount_percent > 0}, so
 * historical invoices are never reinterpreted or recalculated.</p>
 */
public enum DiscountType {
    FULL,
    INDIVIDUAL
}
