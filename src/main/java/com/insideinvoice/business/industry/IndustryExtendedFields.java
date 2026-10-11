package com.insideinvoice.business.industry;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * V29 industry-extended invoice fields (Rental & Leasing, Healthcare & Wellness).
 *
 * <p>These fields are deliberately NOT part of {@link IndustryField} — they are
 * plain invoice columns with display-only semantics (reference, dates, captions).
 * They never feed totals, tax or totals footers. This registry exists so the
 * server-side renderers (customer email, share PDF) can gate rows by the
 * business's industry exactly the way the frontend does.</p>
 *
 * <p>Labels are display wording shared by email and server PDF. The frontend
 * mirrors the same strings in {@code industryConfig.js}.</p>
 */
public final class IndustryExtendedFields {

    public static final String AGREEMENT_NUMBER = "agreementNumber";
    public static final String ASSET_NUMBER = "assetNumber";
    public static final String SERIAL_NUMBER = "serialNumber";
    public static final String VEHICLE_NUMBER = "vehicleNumber";
    public static final String PERIOD_START = "periodStart";
    public static final String PERIOD_END = "periodEnd";
    public static final String BILLING_PERIOD_START = "billingPeriodStart";
    public static final String BILLING_PERIOD_END = "billingPeriodEnd";
    public static final String EXPECTED_RETURN_DATE = "expectedReturnDate";
    public static final String DEPOSIT_REFERENCE = "depositReference";
    public static final String PATIENT_REFERENCE = "patientReference";
    public static final String SERVICE_DATE = "serviceDate";
    public static final String TREATMENT_REFERENCE = "treatmentReference";
    public static final String REFERRING_DOCTOR = "referringDoctor";

    private static final Map<Industry, List<String>> BY_INDUSTRY = Map.of(
            Industry.RENTAL, List.of(
                    AGREEMENT_NUMBER, ASSET_NUMBER, SERIAL_NUMBER, VEHICLE_NUMBER,
                    PERIOD_START, PERIOD_END, BILLING_PERIOD_START, BILLING_PERIOD_END,
                    EXPECTED_RETURN_DATE, DEPOSIT_REFERENCE),
            Industry.HEALTHCARE, List.of(
                    PATIENT_REFERENCE, SERVICE_DATE, TREATMENT_REFERENCE, REFERRING_DOCTOR,
                    BILLING_PERIOD_START, BILLING_PERIOD_END));

    private static final Map<String, String> LABELS = Map.of(
            AGREEMENT_NUMBER, "Rental Agreement No.",
            ASSET_NUMBER, "Asset / Equipment ID",
            SERIAL_NUMBER, "Serial Number",
            VEHICLE_NUMBER, "Vehicle Reg. No.",
            EXPECTED_RETURN_DATE, "Expected Return",
            DEPOSIT_REFERENCE, "Deposit Reference",
            PATIENT_REFERENCE, "Patient / Customer ID",
            SERVICE_DATE, "Service Date",
            TREATMENT_REFERENCE, "Treatment / Session Ref.",
            REFERRING_DOCTOR, "Referring Doctor");

    private IndustryExtendedFields() {
    }

    /** Whether the industry exposes the given extended field id. */
    public static boolean applies(Industry industry, String field) {
        return industry != null
                && BY_INDUSTRY.getOrDefault(industry, List.of()).contains(field);
    }

    /** Ordered extended field ids for an industry (empty when none). */
    public static List<String> fieldsFor(Industry industry) {
        return BY_INDUSTRY.getOrDefault(industry, List.of());
    }

    /** Display label for a single extended field. */
    public static String label(String field) {
        if (PERIOD_START.equals(field) || PERIOD_END.equals(field)) {
            return "Rental Period";
        }
        if (BILLING_PERIOD_START.equals(field) || BILLING_PERIOD_END.equals(field)) {
            return "Billing Period";
        }
        return LABELS.getOrDefault(field, field);
    }

    /** Display label for a start/end pair (composite value builders). */
    public static String compositeLabel(Set<String> fields) {
        if (fields.contains(PERIOD_START)) {
            return "Rental Period";
        }
        if (fields.contains(BILLING_PERIOD_START)) {
            return "Billing Period";
        }
        return "";
    }
}
