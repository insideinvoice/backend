package com.insideinvoice.labels.util;

/**
 * Unit conversion and printer-dot-grid snapping for label rendering.
 * 1 in = 72 pt, 1 mm = 2.8346457 pt. At 203 DPI, 1 dot = 0.125 mm; at 300 DPI, 1 dot = 0.0846667 mm.
 * Every drawn coordinate/size must snap to the dot grid so thermal printers never blur edges.
 */
public final class LabelUnits {

    public static final double MM_TO_PT = 2.8346457;
    public static final double IN_TO_MM = 25.4;
    public static final double IN_TO_PT = 72.0;

    public static final int DPI_203 = 203;
    public static final int DPI_300 = 300;

    private LabelUnits() {
    }

    /** Width of one printer dot in millimetres for the given DPI. */
    public static double dotMm(int dpi) {
        return IN_TO_MM / dpi;
    }

    /** Width of one printer dot in PDF points for the given DPI. */
    public static double dotPt(int dpi) {
        return IN_TO_PT / dpi;
    }

    /** Rounds a millimetre value down/up to the nearest printer dot (round-half-up). */
    public static double snapMm(double mm, int dpi) {
        double dot = dotMm(dpi);
        return Math.round(mm / dot) * dot;
    }

    /** Rounds a point value to the nearest printer dot expressed in points. */
    public static double snapPt(double pt, int dpi) {
        double dot = dotPt(dpi);
        return Math.round(pt / dot) * dot;
    }

    /** Minimum line weight in mm: 1 dot, but borders use 2 dots (0.25 mm at 203 DPI). */
    public static double lineWeightMm(int dpi, boolean border) {
        return dotMm(dpi) * (border ? 2 : 1);
    }

    public static double mmToPt(double mm) {
        return mm * MM_TO_PT;
    }

    public static double ptToMm(double pt) {
        return pt / MM_TO_PT;
    }

    /** Converts an inch value to points. */
    public static double inToPt(double inches) {
        return inches * IN_TO_PT;
    }

    /** Converts an inch value to millimetres. */
    public static double inToMm(double inches) {
        return inches * IN_TO_MM;
    }
}
