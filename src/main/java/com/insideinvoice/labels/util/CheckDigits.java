package com.insideinvoice.labels.util;

/**
 * GS1 check digit calculation (mod-10) for SSCC-18, GTIN-14/12/8 and UPC.
 * Weights alternate 3,1 starting with 3 for the right-most data digit.
 */
public final class CheckDigits {

    private CheckDigits() {
    }

    /**
     * Computes the GS1 mod-10 check digit for a string of numeric data digits.
     */
    public static int checkDigit(String dataDigits) {
        if (dataDigits == null || dataDigits.isEmpty() || !dataDigits.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("Check digit input must be non-empty digits only");
        }
        int sum = 0;
        int weight = 3;
        for (int i = dataDigits.length() - 1; i >= 0; i--) {
            sum += (dataDigits.charAt(i) - '0') * weight;
            weight = weight == 3 ? 1 : 3;
        }
        return (10 - (sum % 10)) % 10;
    }

    /**
     * Builds a full SSCC-18 from its 17 leading digits (extension digit + company prefix + serial + pad),
     * appending the computed check digit.
     */
    public static String sscc18(String seventeenDigits) {
        if (seventeenDigits == null || seventeenDigits.length() != 17
                || !seventeenDigits.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("SSCC requires exactly 17 leading digits");
        }
        return seventeenDigits + checkDigit(seventeenDigits);
    }

    /** Validates a full 18-digit SSCC (with check digit) and returns true when valid. */
    public static boolean isValidSscc18(String sscc) {
        if (sscc == null || sscc.length() != 18 || !sscc.chars().allMatch(Character::isDigit)) {
            return false;
        }
        return checkDigit(sscc.substring(0, 17)) == sscc.charAt(17) - '0';
    }

    /**
     * Builds a full GTIN from its leading digits (13 for GTIN-14, 12 for GTIN-13,
     * 11 for GTIN-12/UPC-A, 7 for GTIN-8) by appending the check digit.
     */
    public static String gtin(String leadingDigits) {
        if (leadingDigits == null || !leadingDigits.chars().allMatch(Character::isDigit)
                || !(leadingDigits.length() == 7 || leadingDigits.length() == 11
                || leadingDigits.length() == 12 || leadingDigits.length() == 13)) {
            throw new IllegalArgumentException("GTIN requires 7, 11, 12 or 13 leading digits");
        }
        return leadingDigits + checkDigit(leadingDigits);
    }

    /**
     * Returns the 17-digit SSCC with its serial reference (last 6 digits) advanced
     * by {@code delta} (wraps at 1,000,000) — used for multi-carton sequences.
     * Accepts 17 digits or a full 18-digit SSCC (check digit is dropped).
     */
    public static String withSerialDelta(String sscc, int delta) {
        String digits = sscc == null ? "" : sscc.replaceAll("\\D", "");
        if (digits.length() == 18) {
            digits = digits.substring(0, 17);
        }
        if (digits.length() != 17) {
            throw new IllegalArgumentException("SSCC serial base requires 17 (or 18) digits");
        }
        long serial = Long.parseLong(digits.substring(11, 17));
        serial = Math.floorMod(serial + delta, 1_000_000L);
        return digits.substring(0, 11) + String.format("%06d", serial);
    }

    /** Validates a full GTIN (8/12/14 digits including check digit). */
    public static boolean isValidGtin(String gtin) {
        if (gtin == null || !gtin.chars().allMatch(Character::isDigit)) {
            return false;
        }
        int n = gtin.length();
        if (n != 8 && n != 12 && n != 14 && n != 13) {
            return false;
        }
        return checkDigit(gtin.substring(0, n - 1)) == gtin.charAt(n - 1) - '0';
    }
}
