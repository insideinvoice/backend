package com.insideinvoice.business.industry;

import com.insideinvoice.business.industry.IndustryConfig.DocumentAccess;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The single source of truth for industry profiles.
 *
 * <p>Application configuration, not data: no table, no admin UI, no per-tenant
 * rows. Tenants only store which profile they picked ({@code businesses.industry}).
 * The frontend ships the same definitions in {@code src/constants/industryConfig.js}
 * for instant, flicker-free rendering; the backend copy is authoritative for
 * anything the server decides.</p>
 *
 * <p>Design rules applied here:</p>
 * <ul>
 *   <li>{@link Industry#OTHER} (also the fallback for unknown/legacy values)
 *       hides nothing and enables every document — existing businesses keep
 *       behaving exactly as before.</li>
 *   <li>Only dispatch/delivery detail fields are ever hidden; identity, dates,
 *       place of supply, payment, line items, HSN/SAC and GST stay visible.</li>
 *   <li>Documents are usability defaults only. No legal or tax treatment is
 *       implied by any entry in this file.</li>
 * </ul>
 */
public final class IndustryRegistry {

    private static final Map<Industry, IndustryConfig> CONFIGS = new EnumMap<>(Industry.class);

    static {
        // --- Trading, Retail & Distribution ---------------------------------
        // Goods are bought, sold and moved: the full dispatch/delivery toolkit
        // stays available, including hazmat (hardware/chemical/building material
        // traders routinely ship regulated goods).
        register(IndustryConfig.of(Industry.TRADING,
                Set.of(),
                Map.of(
                        IndustryField.OTHER_REFERENCES, "PO / Order References",
                        IndustryField.REFERENCE_NUMBER, "Reference No."),
                Map.of(
                        IndustryField.OTHER_REFERENCES, "e.g. PO-1042 / SO-887",
                        IndustryField.REFERENCE_NUMBER, "e.g. RFQ / Quote ref"),
                DocumentAccess.of(true, true, true)));

        // --- Construction & Contracting -------------------------------------
        // No field is removed; the existing delivery/reference fields carry the
        // project vocabulary through labels instead of new columns.
        register(IndustryConfig.of(Industry.CONSTRUCTION,
                Set.of(),
                Map.of(
                        IndustryField.DELIVERY_NOTE, "Work Order No.",
                        IndustryField.DESTINATION, "Site / Work Location",
                        IndustryField.OTHER_REFERENCES, "Project / P.O. No.",
                        IndustryField.DISPATCHED_THROUGH, "Site / Carried By"),
                Map.of(
                        IndustryField.DELIVERY_NOTE, "e.g. WO-2026-14",
                        IndustryField.OTHER_REFERENCES, "e.g. Project name or P.O. number",
                        IndustryField.DESTINATION, "e.g. Bengaluru site"),
                DocumentAccess.of(true, true, false)));

        // --- Manufacturing & Fabrication ------------------------------------
        register(IndustryConfig.of(Industry.MANUFACTURING,
                Set.of(),
                Map.of(
                        IndustryField.DELIVERY_NOTE, "Dispatch Note No.",
                        IndustryField.DISPATCHED_THROUGH, "Vehicle / Transporter"),
                Map.of(
                        IndustryField.DISPATCH_DOC_NUMBER, "e.g. LR / e-way bill no.",
                        IndustryField.OTHER_REFERENCES, "e.g. Batch, lot or order ref"),
                DocumentAccess.of(true, true, true)));

        // --- Professional & Business Services -------------------------------
        // Pure service billing: dispatch and delivery details are noise here.
        register(IndustryConfig.of(Industry.PROFESSIONAL_SERVICES,
                Set.of(IndustryField.DELIVERY_NOTE, IndustryField.DELIVERY_NOTE_DATE,
                        IndustryField.DISPATCH_DOC_NUMBER, IndustryField.DISPATCHED_THROUGH,
                        IndustryField.TERMS_OF_DELIVERY, IndustryField.DESTINATION),
                Map.of(
                        IndustryField.REFERENCE_NUMBER, "Engagement Ref.",
                        IndustryField.OTHER_REFERENCES, "Client P.O. / Project Ref."),
                Map.of(
                        IndustryField.REFERENCE_NUMBER, "e.g. SOW / engagement reference",
                        IndustryField.OTHER_REFERENCES, "e.g. Client PO or project code"),
                DocumentAccess.of(false, false, false)));

        // --- Repair & Maintenance -------------------------------------------
        // Equipment usually moves in and out, so delivery details stay.
        register(IndustryConfig.of(Industry.REPAIR,
                Set.of(),
                Map.of(
                        IndustryField.REFERENCE_NUMBER, "Asset / Job Ref.",
                        IndustryField.OTHER_REFERENCES, "Job Card / P.O. No."),
                Map.of(
                        IndustryField.REFERENCE_NUMBER, "e.g. Vehicle reg. or asset ID",
                        IndustryField.OTHER_REFERENCES, "e.g. Job card number"),
                DocumentAccess.of(true, true, false)));

        // --- Transport & Logistics ------------------------------------------
        register(IndustryConfig.of(Industry.TRANSPORT,
                Set.of(),
                Map.of(
                        IndustryField.DELIVERY_NOTE, "Consignment / LR No.",
                        IndustryField.DISPATCHED_THROUGH, "Vehicle / Carrier",
                        IndustryField.DESTINATION, "Delivery Destination",
                        IndustryField.OTHER_REFERENCES, "Shipper P.O. / Reference"),
                Map.of(
                        IndustryField.DELIVERY_NOTE, "e.g. LR-8891 / consignment no.",
                        IndustryField.DISPATCHED_THROUGH, "e.g. Truck MH-12-AB-1234",
                        IndustryField.OTHER_REFERENCES, "e.g. Shipper PO number"),
                DocumentAccess.of(true, true, true)));

        // --- Food & Hospitality ---------------------------------------------
        // An ordinary restaurant invoice carries no dispatch paperwork. Delivery
        // note stays for caterers; hazmat and carrier labels stay off by default
        // (routes remain reachable if a food supplier really ships regulated goods).
        register(IndustryConfig.of(Industry.FOOD,
                Set.of(IndustryField.DISPATCH_DOC_NUMBER, IndustryField.DISPATCHED_THROUGH),
                Map.of(
                        IndustryField.OTHER_REFERENCES, "Order / Event Reference",
                        IndustryField.DELIVERY_NOTE, "Delivery / Order Note"),
                Map.of(
                        IndustryField.OTHER_REFERENCES, "e.g. Table, order or event ref",
                        IndustryField.DELIVERY_NOTE, "e.g. Delivery or catering order"),
                DocumentAccess.of(true, false, false)));

        // --- Rental & Leasing -----------------------------------------------
        register(IndustryConfig.of(Industry.RENTAL,
                Set.of(IndustryField.DISPATCH_DOC_NUMBER),
                Map.of(
                        IndustryField.DELIVERY_NOTE, "Handover Note",
                        IndustryField.REFERENCE_NUMBER, "Rental / Asset Ref.",
                        IndustryField.OTHER_REFERENCES, "Agreement / P.O. No."),
                Map.of(
                        IndustryField.REFERENCE_NUMBER, "e.g. Asset ID or agreement no.",
                        IndustryField.OTHER_REFERENCES, "e.g. Rental agreement / customer PO"),
                DocumentAccess.of(true, true, false)));

        // --- Telecom, IT & Subscriptions -------------------------------------
        register(IndustryConfig.of(Industry.TELECOM_IT,
                Set.of(IndustryField.DELIVERY_NOTE, IndustryField.DELIVERY_NOTE_DATE,
                        IndustryField.DISPATCH_DOC_NUMBER, IndustryField.DISPATCHED_THROUGH,
                        IndustryField.TERMS_OF_DELIVERY, IndustryField.DESTINATION),
                Map.of(
                        IndustryField.REFERENCE_NUMBER, "Service / Account Ref.",
                        IndustryField.OTHER_REFERENCES, "Subscription / Order Ref."),
                Map.of(
                        IndustryField.REFERENCE_NUMBER, "e.g. Account or service ID",
                        IndustryField.OTHER_REFERENCES, "e.g. Subscription or order number"),
                DocumentAccess.of(false, false, false)));

        // --- Healthcare & Wellness Services ----------------------------------
        // Only billing fields: no patient/medical data is collected or exposed.
        register(IndustryConfig.of(Industry.HEALTHCARE,
                Set.of(IndustryField.DELIVERY_NOTE, IndustryField.DELIVERY_NOTE_DATE,
                        IndustryField.DISPATCH_DOC_NUMBER, IndustryField.DISPATCHED_THROUGH,
                        IndustryField.TERMS_OF_DELIVERY, IndustryField.DESTINATION),
                Map.of(
                        IndustryField.REFERENCE_NUMBER, "Visit / Case Ref.",
                        IndustryField.OTHER_REFERENCES, "Appointment / Order Ref."),
                Map.of(
                        IndustryField.REFERENCE_NUMBER, "e.g. Visit or case reference",
                        IndustryField.OTHER_REFERENCES, "e.g. Appointment or order number"),
                DocumentAccess.of(false, false, false)));

        // --- Education & Training --------------------------------------------
        register(IndustryConfig.of(Industry.EDUCATION,
                Set.of(IndustryField.DELIVERY_NOTE, IndustryField.DELIVERY_NOTE_DATE,
                        IndustryField.DISPATCH_DOC_NUMBER, IndustryField.DISPATCHED_THROUGH,
                        IndustryField.TERMS_OF_DELIVERY, IndustryField.DESTINATION),
                Map.of(
                        IndustryField.REFERENCE_NUMBER, "Course / Batch Ref.",
                        IndustryField.OTHER_REFERENCES, "Enrolment / P.O. Reference"),
                Map.of(
                        IndustryField.REFERENCE_NUMBER, "e.g. Course or batch code",
                        IndustryField.OTHER_REFERENCES, "e.g. Enrolment ID or client PO"),
                DocumentAccess.of(false, false, false)));

        // --- Agriculture & Primary Goods -------------------------------------
        register(IndustryConfig.of(Industry.AGRICULTURE,
                Set.of(),
                Map.of(
                        IndustryField.DELIVERY_NOTE, "Consignment Note",
                        IndustryField.OTHER_REFERENCES, "Lot / Grade Reference"),
                Map.of(
                        IndustryField.OTHER_REFERENCES, "e.g. Lot, grade or produce ref",
                        IndustryField.DELIVERY_NOTE, "e.g. Consignment / lot note"),
                DocumentAccess.of(true, true, true)));

        // --- Other / General Business ----------------------------------------
        // Backward-compatible baseline: nothing hidden, every document enabled.
        register(IndustryConfig.of(Industry.OTHER,
                Set.of(),
                Map.of(),
                Map.of(),
                DocumentAccess.allEnabled()));
    }

    private IndustryRegistry() {}

    private static void register(IndustryConfig config) {
        CONFIGS.put(config.industry(), config);
    }

    /** Never returns null: unknown input degrades to the OTHER profile. */
    public static IndustryConfig forIndustry(Industry industry) {
        return CONFIGS.getOrDefault(industry == null ? Industry.OTHER : industry, CONFIGS.get(Industry.OTHER));
    }

    /** Null-safe convenience for raw column values. */
    public static IndustryConfig forRawIndustry(String rawIndustry) {
        return forIndustry(Industry.resolve(rawIndustry));
    }

    /** Ordered list used to serve {@code GET /api/industries}. */
    public static List<IndustryConfig> all() {
        return List.copyOf(CONFIGS.values());
    }

    /** Exposed for tests. */
    public static Map<Industry, IndustryConfig> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(CONFIGS));
    }
}
