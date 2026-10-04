package com.insideinvoice.labels.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Single constants file for every label layout: physical sizes (mm), zone geometry,
 * and the 203-DPI-safe typography scale from the spec. All values are millimetres
 * unless the name ends in {@code Pt}.
 */
public final class LabelSizes {

    private LabelSizes() {
    }

    // ----------------------------------------------------------------- sizes

    public record Size(String key, double wMm, double hMm) {
    }

    // shipping
    public static final Size SHIP_4X6 = new Size("4x6in", 101.6, 152.4);   // DEFAULT
    public static final Size SHIP_4X8 = new Size("4x8in", 101.6, 203.2);
    public static final Size SHIP_4X4 = new Size("4x4in", 101.6, 101.6);
    public static final Size SHIP_4X3 = new Size("4x3in", 101.6, 76.2);
    public static final Size SHIP_100X150 = new Size("100x150mm", 100, 150);
    public static final Size SHIP_100X100 = new Size("100x100mm", 100, 100);
    public static final Size SHEET_A4_2UP = new Size("a4-2up", 210, 297);
    public static final Size SHEET_A4_4UP = new Size("a4-4up", 210, 297);
    public static final Size SHEET_LETTER_2UP = new Size("letter-2up", 215.9, 279.4);
    public static final Size SHEET_LETTER_30UP = new Size("letter-30up", 215.9, 279.4);
    // hazmat
    public static final Size HAZMAT_4X4 = new Size("hazmat-4x4", 101.6, 101.6);     // DEFAULT
    public static final Size HAZMAT_4X6 = new Size("hazmat-4x6", 101.6, 152.4);
    public static final Size HAZMAT_100X100 = new Size("hazmat-100mm", 100, 100);
    public static final Size HAZMAT_50X50 = new Size("hazmat-50mm", 50, 50);
    public static final Size A4 = new Size("a4", 210, 297);

    public static final String DEFAULT_SHIPPING = SHIP_4X6.key();

    private static final Map<String, Size> BY_KEY = new LinkedHashMap<>();

    static {
        for (Size s : new Size[]{
                SHIP_4X6, SHIP_4X8, SHIP_4X4, SHIP_4X3, SHIP_100X150, SHIP_100X100,
                SHEET_A4_2UP, SHEET_A4_4UP, SHEET_LETTER_2UP, SHEET_LETTER_30UP,
                HAZMAT_4X4, HAZMAT_4X6, HAZMAT_100X100, HAZMAT_50X50, A4}) {
            BY_KEY.put(s.key(), s);
        }
    }

    public static Size require(String key) {
        Size s = BY_KEY.get(key == null || key.isBlank() ? DEFAULT_SHIPPING : key);
        if (s == null) {
            throw new IllegalArgumentException("Unknown label size: " + key);
        }
        return s;
    }

    public static boolean isSheet(String key) {
        String k = key == null ? DEFAULT_SHIPPING : key;
        return k.equals(SHEET_A4_2UP.key()) || k.equals(SHEET_A4_4UP.key())
                || k.equals(SHEET_LETTER_2UP.key()) || k.equals(SHEET_LETTER_30UP.key());
    }

    // ------------------------------------------------------------ sheet cells

    public record Cell(double xMm, double yMm, double wMm, double hMm) {
    }

    /**
     * Cell layout per sheet size. A4/Letter 2-up: two portrait 4x6 cells side by side.
     * A4 4-up: four A6 cells (2x2). Letter 30-up: Avery 5167, 3x10 cells of
     * 66.7 x 25.4 mm with 3 mm horizontal gaps and touching rows.
     */
    public static List<Cell> cells(String sizeKey) {
        String k = sizeKey == null ? DEFAULT_SHIPPING : sizeKey;
        List<Cell> cells = new ArrayList<>();
        switch (k) {
            case "a4-2up" -> {
                double gap = (210 - 2 * 101.6) / 2; // centred
                cells.add(new Cell(gap, (297 - 152.4) / 2, 101.6, 152.4));
                cells.add(new Cell(gap + 101.6, (297 - 152.4) / 2, 101.6, 152.4));
            }
            case "a4-4up" -> {
                double w = 105;
                double h = 148;
                double x0 = (210 - 2 * w) / 2;
                double y0 = (297 - 2 * h) / 2;
                cells.add(new Cell(x0, y0, w, h));
                cells.add(new Cell(x0 + w, y0, w, h));
                cells.add(new Cell(x0, y0 + h, w, h));
                cells.add(new Cell(x0 + w, y0 + h, w, h));
            }
            case "letter-2up" -> {
                double gap = (215.9 - 2 * 101.6) / 2;
                cells.add(new Cell(gap, (279.4 - 152.4) / 2, 101.6, 152.4));
                cells.add(new Cell(gap + 101.6, (279.4 - 152.4) / 2, 101.6, 152.4));
            }
            case "letter-30up" -> {
                double w = 66.7;
                double h = 25.4;
                double gapX = 3;
                double x0 = (215.9 - 3 * w - 2 * gapX) / 2;
                double y0 = (279.4 - 10 * h) / 2;
                for (int row = 0; row < 10; row++) {
                    for (int col = 0; col < 3; col++) {
                        cells.add(new Cell(x0 + col * (w + gapX), y0 + row * h, w, h));
                    }
                }
            }
            default -> throw new IllegalArgumentException("Not a sheet size: " + k);
        }
        return cells;
    }

    // ------------------------------------------------------ standard carrier
    // 4x6 zone layout (all snap-safe multiples of the 203-DPI dot = 0.125 mm)

    public static final double SAFE_MARGIN_MM = 3;
    public static final double RULE_MM = 0.5;
    public static final double KEYLINE_MM = 0.25;
    public static final double ZONE1_HEADER_MM = 16;
    public static final double ZONE2_SHIPTO_MM = 38;
    public static final double ZONE3_ROUTING_MM = 30;
    public static final double ZONE4_BARCODE_MIN_MM = 42;
    public static final double ZONE5_FOOTER_MM = 8;
    /** Labels shorter than this use the compact stack (no routing zone). */
    public static final double FULL_LAYOUT_MIN_HEIGHT_MM = 140;
    public static final double HANDLING_ICON_MM = 10;
    public static final double SERVICE_BOX_W_MM = 30;
    public static final double SERVICE_BOX_H_MM = 30;
    public static final double HEADER_RIGHT_W_MM = 35;
    public static final double DATAMATRIX_MM = 22;
    public static final double BARCODE_H_MM = 24;

    // ------------------------------------------------------------------ type
    // 203-DPI-safe scale (pt); never below 6 pt (spec floor)

    public static final double T_SHIP_TO_NAME = 15;      // 14-16 bold
    public static final double T_SHIP_TO_ADDR = 11.5;    // 11-12 bold uppercase
    public static final double T_SHIP_FROM = 7.5;        // 7-8 regular
    public static final double T_SERVICE_BOX = 24;       // service/zone box
    public static final double T_SERVICE_BIG = 32;       // zone-2 indicator (28-36)
    public static final double T_TRACKING = 9;
    public static final double T_BARCODE_TEXT = 8.5;
    public static final double T_FOOTER = 6;
    public static final double T_CAPTION = 6;
    public static final double T_ADDRESS_FLOOR = 6;      // shrink floor
    public static final double LINE_HEIGHT = 1.15;

    // FNSKU 30-up cells (Avery 5167)
    public static final double FNSKU_CELL_W = 66.7;
    public static final double FNSKU_CELL_H = 25.4;
    public static final double FNSKU_BARCODE_H = 8;
}
