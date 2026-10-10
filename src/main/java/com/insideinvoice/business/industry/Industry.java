package com.insideinvoice.business.industry;

import java.util.Arrays;
import java.util.List;

/**
 * Stable industry identifiers. These are the values persisted in
 * {@code businesses.industry}; display names are presentation-only and must
 * never be stored.
 *
 * <p>{@link #OTHER} is the backward-compatible default for businesses created
 * before industry selection existed and for users who pick nothing.</p>
 */
public enum Industry {

    TRADING("TRADING", "Trading, Retail & Distribution",
            "Shops, wholesalers and distributors selling goods — electrical, electronics, hardware, "
                    + "furniture, garments, groceries, auto parts and building materials."),
    CONSTRUCTION("CONSTRUCTION", "Construction & Contracting",
            "Civil contractors and construction service providers billing projects and sites."),
    MANUFACTURING("MANUFACTURING", "Manufacturing & Fabrication",
            "Factories, fabrication units and industrial production businesses."),
    PROFESSIONAL_SERVICES("PROFESSIONAL_SERVICES", "Professional & Business Services",
            "IT companies, software development, consulting, marketing and design agencies."),
    REPAIR("REPAIR", "Repair & Maintenance",
            "Equipment servicing, vehicle repair and maintenance workshops."),
    TRANSPORT("TRANSPORT", "Transport & Logistics",
            "Freight, logistics, courier and transport operators."),
    FOOD("FOOD", "Food & Hospitality",
            "Restaurants, catering, bakeries, food suppliers and hospitality businesses."),
    RENTAL("RENTAL", "Rental & Leasing",
            "Equipment, machinery and asset rental businesses."),
    TELECOM_IT("TELECOM_IT", "Telecom, IT & Subscriptions",
            "Telecom operators, internet providers and subscription/SaaS businesses."),
    HEALTHCARE("HEALTHCARE", "Healthcare & Wellness Services",
            "Clinics, wellness providers and allied healthcare services."),
    EDUCATION("EDUCATION", "Education & Training",
            "Training centres, tutors and educational service providers."),
    AGRICULTURE("AGRICULTURE", "Agriculture & Primary Goods",
            "Producers and traders of agricultural products and primary goods."),
    OTHER("OTHER", "General Business",
            "A safe general-purpose configuration that keeps every existing invoice field available.");

    /**
     * Bean-validation pattern for incoming industry values. Kept as a literal
     * so it can be used inside annotations (which require compile-time
     * constants); {@code IndustryPatternTest} asserts it never drifts from
     * {@link #ids()}.
     */
    public static final String ID_PATTERN =
            "^(TRADING|CONSTRUCTION|MANUFACTURING|PROFESSIONAL_SERVICES|REPAIR|TRANSPORT"
                    + "|FOOD|RENTAL|TELECOM_IT|HEALTHCARE|EDUCATION|AGRICULTURE|OTHER)$";

    private final String id;
    private final String displayName;
    private final String description;

    Industry(String id, String displayName, String description) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /** All ids in declaration order — used to build the validation pattern. */
    public static List<String> ids() {
        return Arrays.stream(values()).map(Industry::getId).toList();
    }

    /**
     * Null-safe lookup used on every read path. Unknown, blank or legacy values
     * resolve to {@link #OTHER} instead of failing, so a bad row can never make
     * a business unreadable.
     */
    public static Industry resolve(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return OTHER;
        }
        String normalized = rawId.trim().toUpperCase();
        for (Industry industry : values()) {
            if (industry.id.equals(normalized)) {
                return industry;
            }
        }
        return OTHER;
    }

    /** Strict lookup used on write paths — unknown values must be rejected. */
    public static boolean isValid(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return false;
        }
        String normalized = rawId.trim().toUpperCase();
        return Arrays.stream(values()).anyMatch(i -> i.id.equals(normalized));
    }
}
