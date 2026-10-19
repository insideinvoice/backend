package com.insideinvoice.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Reference data for the configurable invoice-jurisdiction feature: the
 * regions a business may pick (Jurisdiction Region in Settings) — India's
 * 28 states and 8 union territories — so the persistence layer and the
 * frontend dropdown agree on the exact persisted strings.
 *
 * <p>The stored strings MUST match {@code src/constants/indianRegions.js}
 * in the frontend, which renders the picker from the very same list.</p>
 *
 * <p>Offering the list is reference data only: it does NOT assert that a
 * region's capital is the legally correct jurisdiction. The city a business
 * prints is whatever it saves in {@code businesses.jurisdiction_city}.</p>
 */
public final class IndianRegions {

    private IndianRegions() {
    }

    public static final List<String> STATES = Collections.unmodifiableList(Arrays.asList(
            "Andhra Pradesh",
            "Arunachal Pradesh",
            "Assam",
            "Bihar",
            "Chhattisgarh",
            "Goa",
            "Gujarat",
            "Haryana",
            "Himachal Pradesh",
            "Jharkhand",
            "Karnataka",
            "Kerala",
            "Madhya Pradesh",
            "Maharashtra",
            "Manipur",
            "Meghalaya",
            "Mizoram",
            "Nagaland",
            "Odisha",
            "Punjab",
            "Rajasthan",
            "Sikkim",
            "Tamil Nadu",
            "Telangana",
            "Tripura",
            "Uttar Pradesh",
            "Uttarakhand",
            "West Bengal"
    ));

    public static final List<String> UNION_TERRITORIES = Collections.unmodifiableList(Arrays.asList(
            "Andaman and Nicobar Islands",
            "Chandigarh",
            "Dadra and Nagar Haveli and Daman and Diu",
            "Delhi (National Capital Territory)",
            "Jammu and Kashmir",
            "Ladakh",
            "Lakshadweep",
            "Puducherry"
    ));

    /** States first, then union territories — the order the UI presents them in. */
    public static final List<String> ALL;

    static {
        List<String> all = new ArrayList<>(STATES.size() + UNION_TERRITORIES.size());
        all.addAll(STATES);
        all.addAll(UNION_TERRITORIES);
        ALL = Collections.unmodifiableList(all);
    }

    /**
     * @param region raw client input; compared exactly against the reference
     *               strings above (the UI sends values straight from this
     *               same list, so an exact match is the correct contract).
     */
    public static boolean isValid(String region) {
        if (region == null || region.isBlank()) return false;
        return ALL.contains(region.trim());
    }

    /**
     * Used by the settings UI grouping on the backend side only when echoing
     * configuration back; the region is optional (legacy rows have it null).
     */
    public static boolean isUnionTerritory(String region) {
        return region != null && UNION_TERRITORIES.contains(region.trim());
    }
}
