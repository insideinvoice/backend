package com.insideinvoice.business.industry;

import java.util.List;
import java.util.Set;

/**
 * Canonical identifiers for the invoice fields the industry configuration can
 * control. The values are the camelCase keys the frontend form already uses,
 * so no field renaming or DTO churn is needed on either side.
 *
 * <p>The catalogue is deliberately small: every concept in the industry
 * examples that the product already supports maps onto one of these existing
 * fields (typically via an industry label override), rather than inventing new
 * columns.</p>
 */
public final class IndustryField {

    private IndustryField() {}

    /* ---- invoice header ---- */
    public static final String INVOICE_TYPE = "invoiceType";
    public static final String INVOICE_NUMBER = "invoiceNumber";
    public static final String INVOICE_DATE = "invoiceDate";
    public static final String DUE_DATE = "dueDate";
    public static final String PLACE_OF_SUPPLY = "placeOfSupply";
    public static final String DESTINATION = "destination";
    public static final String PAYMENT_TERMS = "paymentTerms";
    public static final String PAYMENT_MODE = "paymentMode";

    /* ---- references & delivery ---- */
    public static final String DELIVERY_NOTE = "deliveryNote";
    public static final String DELIVERY_NOTE_DATE = "deliveryNoteDate";
    public static final String REFERENCE_NUMBER = "referenceNumber";
    public static final String DISPATCH_DOC_NUMBER = "dispatchDocNumber";
    public static final String DISPATCHED_THROUGH = "dispatchedThrough";
    public static final String TERMS_OF_DELIVERY = "termsOfDelivery";
    public static final String OTHER_REFERENCES = "otherReferences";

    /* ---- items & totals ---- */
    public static final String ITEM_NAME = "itemName";
    public static final String HSN = "hsn";
    public static final String QTY = "qty";
    public static final String RATE = "rate";
    public static final String GST_PERCENTAGE = "gstPercentage";
    public static final String DISCOUNT = "discount";

    /* ---- other ---- */
    public static final String NOTES = "notes";
    public static final String SPECIALIST_IN = "specialistIn";
    public static final String UPI_QR = "upiQr";

    /**
     * Every field the product supports. Fields outside this set cannot be
     * hidden by any industry — this is the guard that keeps GST/tax and core
     * accounting fields permanently available.
     */
    public static final List<String> ALL = List.of(
            INVOICE_TYPE, INVOICE_NUMBER, INVOICE_DATE, DUE_DATE, PLACE_OF_SUPPLY,
            DESTINATION, PAYMENT_TERMS, PAYMENT_MODE,
            DELIVERY_NOTE, DELIVERY_NOTE_DATE, REFERENCE_NUMBER, DISPATCH_DOC_NUMBER,
            DISPATCHED_THROUGH, TERMS_OF_DELIVERY, OTHER_REFERENCES,
            ITEM_NAME, HSN, QTY, RATE, GST_PERCENTAGE, DISCOUNT,
            NOTES, SPECIALIST_IN, UPI_QR);

    /**
     * Fields no industry may hide. Identity, dates, place of supply, payment
     * terms, line items, HSN/SAC and GST rates are required by the existing
     * accounting/GST workflow regardless of the business's trade.
     */
    public static final Set<String> PROTECTED_FIELDS = Set.of(
            INVOICE_TYPE, INVOICE_NUMBER, INVOICE_DATE, DUE_DATE, PLACE_OF_SUPPLY,
            PAYMENT_TERMS, PAYMENT_MODE, REFERENCE_NUMBER, OTHER_REFERENCES,
            ITEM_NAME, HSN, QTY, RATE, GST_PERCENTAGE, DISCOUNT, NOTES);

    /**
     * Fields an industry is allowed to turn off by default. All of them are
     * optional dispatch/delivery details in the existing schema.
     */
    public static final Set<String> CONFIGURABLE_FIELDS = Set.of(
            DESTINATION, DELIVERY_NOTE, DELIVERY_NOTE_DATE,
            DISPATCH_DOC_NUMBER, DISPATCHED_THROUGH, TERMS_OF_DELIVERY);

    /** Fields belonging to the "References & Delivery" card in the invoice form. */
    public static final List<String> REFERENCES_SECTION = List.of(
            DELIVERY_NOTE, DELIVERY_NOTE_DATE, REFERENCE_NUMBER, DISPATCH_DOC_NUMBER,
            DISPATCHED_THROUGH, TERMS_OF_DELIVERY, OTHER_REFERENCES);

    /** Fields validated as required by the backend for every industry. */
    public static final Set<String> BASE_REQUIRED = Set.of(
            INVOICE_DATE, DUE_DATE, ITEM_NAME, QTY, RATE);
}
