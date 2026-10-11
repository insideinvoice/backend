package com.insideinvoice.invoice.share;

import com.insideinvoice.business.entity.Business;
import com.insideinvoice.business.industry.Industry;
import com.insideinvoice.business.industry.IndustryConfig;
import com.insideinvoice.business.industry.IndustryExtendedFields;
import com.insideinvoice.business.industry.IndustryField;
import com.insideinvoice.business.industry.IndustryRegistry;
import com.insideinvoice.invoice.entity.Invoice;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared reference/delivery detail rows for the customer-facing renderers —
 * the invoice email and the public share PDF — so both show exactly the same
 * extended content as the in-app invoice view and the frontend PDF.
 *
 * <p>Emission rules (mirroring the frontend):</p>
 * <ul>
 *   <li>a row appears only when its value is present;</li>
 *   <li>core fields are suppressed when the business's industry profile hides
 *       them (stored values are never dropped, just not printed);</li>
 *   <li>V29 extended fields (rental/health) are additionally gated by
 *       industry membership via {@link IndustryExtendedFields};</li>
 *   <li>start/end dates render as one composite row ("Rental Period",
 *       "Billing Period").</li>
 * </ul>
 *
 * <p>Nothing here touches totals, tax or line items.</p>
 */
public final class InvoiceDetailRows {

    public record Row(String label, String value) {
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private InvoiceDetailRows() {
    }

    public static List<Row> rows(Invoice invoice, Business business) {
        IndustryConfig cfg = IndustryRegistry.forRawIndustry(business == null ? null : business.getIndustry());
        List<Row> out = new ArrayList<>();

        // --- core reference / delivery fields (industry-labelled) ---
        addIfVisible(out, cfg, IndustryField.DELIVERY_NOTE, invoice.getDeliveryNote(), deliveryNoteValue(invoice));
        addIfVisible(out, cfg, IndustryField.REFERENCE_NUMBER, invoice.getReferenceNumber(), null);
        addIfVisible(out, cfg, IndustryField.DISPATCH_DOC_NUMBER, invoice.getDispatchDocNumber(), null);
        addIfVisible(out, cfg, IndustryField.DISPATCHED_THROUGH, invoice.getDispatchedThrough(), null);
        addIfVisible(out, cfg, IndustryField.TERMS_OF_DELIVERY, invoice.getTermsOfDelivery(), null);
        addIfVisible(out, cfg, IndustryField.DESTINATION, invoice.getDestination(), null);
        addIfVisible(out, cfg, IndustryField.OTHER_REFERENCES, invoice.getOtherReferences(), null);

        // --- V29 extended fields, ordered per industry ---
        Industry industry = Industry.resolve(business == null ? null : business.getIndustry());
        if (industry == Industry.RENTAL) {
            add(out, IndustryExtendedFields.label(IndustryExtendedFields.AGREEMENT_NUMBER), invoice.getAgreementNumber());
            add(out, IndustryExtendedFields.label(IndustryExtendedFields.ASSET_NUMBER), invoice.getAssetNumber());
            add(out, IndustryExtendedFields.label(IndustryExtendedFields.SERIAL_NUMBER), invoice.getSerialNumber());
            add(out, IndustryExtendedFields.label(IndustryExtendedFields.VEHICLE_NUMBER), invoice.getVehicleNumber());
            add(out, "Rental Period", range(invoice.getPeriodStart(), invoice.getPeriodEnd()));
            add(out, "Billing Period", range(invoice.getBillingPeriodStart(), invoice.getBillingPeriodEnd()));
            add(out, IndustryExtendedFields.label(IndustryExtendedFields.EXPECTED_RETURN_DATE), fmt(invoice.getExpectedReturnDate()));
            add(out, IndustryExtendedFields.label(IndustryExtendedFields.DEPOSIT_REFERENCE), invoice.getDepositReference());
        } else if (industry == Industry.HEALTHCARE) {
            add(out, IndustryExtendedFields.label(IndustryExtendedFields.PATIENT_REFERENCE), invoice.getPatientReference());
            add(out, IndustryExtendedFields.label(IndustryExtendedFields.SERVICE_DATE), fmt(invoice.getServiceDate()));
            add(out, IndustryExtendedFields.label(IndustryExtendedFields.TREATMENT_REFERENCE), invoice.getTreatmentReference());
            add(out, IndustryExtendedFields.label(IndustryExtendedFields.REFERRING_DOCTOR), invoice.getReferringDoctor());
            add(out, "Billing Period", range(invoice.getBillingPeriodStart(), invoice.getBillingPeriodEnd()));
        }
        return out;
    }

    private static void addIfVisible(List<Row> out, IndustryConfig cfg, String fieldId,
                                     String storedValue, String overrideValue) {
        if (!cfg.isFieldVisible(fieldId)) {
            return;
        }
        String value = overrideValue != null ? overrideValue : storedValue;
        add(out, labelFor(cfg, fieldId), value);
    }

    private static String labelFor(IndustryConfig cfg, String fieldId) {
        return cfg.labels().getOrDefault(fieldId, defaultLabel(fieldId));
    }

    private static String defaultLabel(String fieldId) {
        return switch (fieldId) {
            case IndustryField.DELIVERY_NOTE -> "Delivery Note";
            case IndustryField.REFERENCE_NUMBER -> "Reference No.";
            case IndustryField.DISPATCH_DOC_NUMBER -> "Dispatch Doc No.";
            case IndustryField.DISPATCHED_THROUGH -> "Dispatched Through";
            case IndustryField.TERMS_OF_DELIVERY -> "Terms of Delivery";
            case IndustryField.DESTINATION -> "Destination";
            case IndustryField.OTHER_REFERENCES -> "Other References";
            default -> fieldId;
        };
    }

    /** Delivery note with its date folded in when present: "DN-901 · 01 Oct 2026". */
    private static String deliveryNoteValue(Invoice invoice) {
        String note = invoice.getDeliveryNote();
        boolean hasNote = note != null && !note.isBlank();
        boolean hasDate = invoice.getDeliveryNoteDate() != null;
        if (!hasNote && !hasDate) {
            return null;
        }
        if (hasNote && hasDate) {
            return note.trim() + " \u00b7 " + DATE.format(invoice.getDeliveryNoteDate());
        }
        return hasNote ? note : DATE.format(invoice.getDeliveryNoteDate());
    }

    /** "12 Oct 2026 – 16 Oct 2026", either end alone, or null when both absent. */
    private static String range(LocalDate start, LocalDate end) {
        String s = fmt(start);
        String e = fmt(end);
        if (s.isEmpty() && e.isEmpty()) {
            return null;
        }
        if (s.isEmpty()) {
            return e;
        }
        if (e.isEmpty()) {
            return s;
        }
        return s + " \u2013 " + e;
    }

    private static String fmt(LocalDate d) {
        return d == null ? "" : DATE.format(d);
    }

    private static void add(List<Row> out, String label, String value) {
        if (value != null && !value.isBlank()) {
            out.add(new Row(label, value.trim()));
        }
    }
}
