package com.insideinvoice.labels.hazmat;

/**
 * Hazard class colour + layout configuration: hex/CMYK values, background
 * pattern, symbol kind and the numeral drawn in the lower corner of the diamond.
 * Colours are per 49 CFR 172.407 / IATA DGR 7.2 (exact hex; CMYK derived by the
 * standard unconstrained-ICC conversion from the same hex).
 */
public enum HazardClass {

    C1("1", "EXPLOSIVES", "FF6600", Pattern.SOLID, SymbolKind.BOMB, "BLACK", "1.4G"),
    C2_1("2.1", "FLAMMABLE GAS", "E4002B", Pattern.SOLID, SymbolKind.FLAME, "WHITE", "2"),
    C2_2("2.2", "NON-FLAMMABLE NON-TOXIC GAS", "00A651", Pattern.SOLID, SymbolKind.CYLINDER, "BLACK", "2"),
    C2_3("2.3", "TOXIC GAS", "FFFFFF", Pattern.SOLID, SymbolKind.SKULL, "BLACK", "2"),
    C3("3", "FLAMMABLE LIQUID", "E4002B", Pattern.SOLID, SymbolKind.FLAME, "WHITE", "3"),
    C4_1("4.1", "FLAMMABLE SOLID", "FFFFFF", Pattern.STRIPES_RED7, SymbolKind.FLAME, "BLACK", "4"),
    C4_2("4.2", "SPONTANEOUS COMBUSTIBLE", "FFFFFF", Pattern.HALF_RED_BOTTOM, SymbolKind.FLAME, "AUTO", "4"),
    C4_3("4.3", "DANGEROUS WHEN WET", "0072BC", Pattern.SOLID, SymbolKind.FLAME, "WHITE", "4"),
    C5_1("5.1", "OXIDIZER", "FFD700", Pattern.SOLID, SymbolKind.FLAME_OVER_CIRCLE, "BLACK", "5.1"),
    C5_2("5.2", "ORGANIC PEROXIDE", "FFEB00", Pattern.HALF_TOP_RED_BOTTOM_YELLOW, SymbolKind.FLAME, "AUTO", "5.2"),
    C6_1("6.1", "TOXIC", "FFFFFF", Pattern.SOLID, SymbolKind.SKULL, "BLACK", "6"),
    C6_2("6.2", "INFECTIOUS SUBSTANCE", "FFFFFF", Pattern.SOLID, SymbolKind.BIOHAZARD, "BLACK", ""),
    C7("7", "RADIOACTIVE", "FFF200", Pattern.HALF_YELLOW_TOP_WHITE_BOTTOM, SymbolKind.TREFOIL, "AUTO", ""),
    C8("8", "CORROSIVE", "FFFFFF", Pattern.HALF_BLACK_BOTTOM, SymbolKind.CORROSIVE, "AUTO", "8"),
    C9("9", "MISCELLANEOUS", "FFFFFF", Pattern.STRIPES_BLACK7_UPPER, SymbolKind.NONE, "BLACK", "9"),
    C9A("9A", "LITHIUM BATTERY", "FFFFFF", Pattern.SOLID, SymbolKind.BATTERY, "BLACK", "9");

    /** Background fill patterns (colour mode) and their thermal B/W fallbacks. */
    public enum Pattern {
        /** Single solid background colour. */
        SOLID,
        /** White with 7 vertical red stripes (4.1). */
        STRIPES_RED7,
        /** White top, red bottom (4.2 / 8 uses black). */
        HALF_RED_BOTTOM,
        /** Red top, yellow bottom (5.2). */
        HALF_TOP_RED_BOTTOM_YELLOW,
        /** Yellow top, white bottom (7). */
        HALF_YELLOW_TOP_WHITE_BOTTOM,
        /** White top, black bottom (8). */
        HALF_BLACK_BOTTOM,
        /** White with 7 black vertical stripes in the upper half (9). */
        STRIPES_BLACK7_UPPER
    }

    public enum SymbolKind {
        NONE, FLAME, CYLINDER, SKULL, BOMB, FLAME_OVER_CIRCLE, BIOHAZARD, TREFOIL,
        CORROSIVE, BATTERY
    }

    private final String key;
    private final String className;
    private final String hex;
    private final Pattern pattern;
    private final SymbolKind symbol;
    private final String textColor;
    private final String numeral;

    HazardClass(String key, String className, String hex, Pattern pattern,
                SymbolKind symbol, String textColor, String numeral) {
        this.key = key;
        this.className = className;
        this.hex = hex;
        this.pattern = pattern;
        this.symbol = symbol;
        this.textColor = textColor;
        this.numeral = numeral;
    }

    public String key() {
        return key;
    }

    public String className() {
        return className;
    }

    public String hex() {
        return hex;
    }

    public Pattern pattern() {
        return pattern;
    }

    public SymbolKind symbol() {
        return symbol;
    }

    /** BLACK, WHITE or AUTO (derive from the background under the text). */
    public String textColor() {
        return textColor;
    }

    /** Default numeral; class 1 overrides with division+compat from the spec. */
    public String numeral() {
        return numeral;
    }

    /** RGB triple from the exact hex value. */
    public int[] rgb() {
        return new int[]{
                Integer.parseInt(hex.substring(0, 2), 16),
                Integer.parseInt(hex.substring(2, 4), 16),
                Integer.parseInt(hex.substring(4, 6), 16)};
    }

    /**
     * CMYK from hex (standard naive conversion; exact for the given hex when the
     * printer profile is unconstrained).
     */
    public float[] cmyk() {
        int[] c = rgb();
        float r = c[0] / 255f;
        float g = c[1] / 255f;
        float b = c[2] / 255f;
        float k = 1 - Math.max(r, Math.max(g, b));
        if (k >= 1f) {
            return new float[]{0, 0, 0, 1};
        }
        return new float[]{(1 - r - k) / (1 - k), (1 - g - k) / (1 - k),
                (1 - b - k) / (1 - k), k};
    }

    /** Resolves "1", "2.1", ... "9", "9A"; unknown keys fall back to class 9. */
    public static HazardClass fromKey(String key) {
        if (key == null || key.isBlank()) {
            return C9;
        }
        String k = key.trim().toUpperCase(java.util.Locale.ENGLISH);
        for (HazardClass c : values()) {
            if (c.key.equals(k)) {
                return c;
            }
        }
        // "4.1s" style or bare division: exact match failed -> first digit class
        return switch (k.charAt(0)) {
            case '1' -> C1;
            case '2' -> switch (k) {
                case "2.3", "23" -> C2_3;
                case "2.2", "22" -> C2_2;
                default -> C2_1;
            };
            case '3' -> C3;
            case '4' -> k.startsWith("4.2") ? C4_2 : k.startsWith("4.3") ? C4_3 : C4_1;
            case '5' -> k.startsWith("5.2") ? C5_2 : C5_1;
            case '6' -> k.startsWith("6.2") ? C6_2 : C6_1;
            case '7' -> C7;
            case '8' -> C8;
            case '9' -> k.equals("9A") || k.equals("9") ? C9 : C9;
            default -> C9;
        };
    }
}
