package com.insideinvoice.labels.hazmat;

import com.insideinvoice.labels.renderer.PdfCanvas;

import java.io.IOException;

/**
 * Vector symbol library for hazmat diamonds. Every symbol is drawn inside a
 * square box (viewBox 0..100, unit = {@code h / 100} mm) with pure vector
 * paths — no raster. {@code ink} is the symbol colour, {@code bg} punches
 * cut-outs (eyes, flame core) so symbols read correctly on coloured fields.
 */
public final class HazmatSymbols {

    private static final double U = 100.0; // viewBox units

    private HazmatSymbols() {
    }

    private static void path(PdfCanvas c, String d, double x, double y, double s)
            throws IOException {
        c.svgPath(d, x, y, s, true, null);
    }

    public static void draw(PdfCanvas c, HazardClass.SymbolKind kind, double x, double y, double h,
                            int[] ink, int[] bg) throws IOException {
        switch (kind) {
            case FLAME -> flame(c, x, y, h, ink, bg);
            case CYLINDER -> cylinder(c, x, y, h, ink);
            case SKULL -> skull(c, x, y, h, ink, bg);
            case BOMB -> bomb(c, x, y, h, ink);
            case FLAME_OVER_CIRCLE -> flameOverCircle(c, x, y, h, ink, bg);
            case BIOHAZARD -> biohazard(c, x, y, h, ink, bg);
            case TREFOIL -> trefoil(c, x, y, h, ink, bg);
            case CORROSIVE -> corrosive(c, x, y, h, ink, bg);
            case BATTERY -> battery(c, x, y, h, ink, bg);
            case NONE -> { /* numeral-only labels (class 9) */ }
        }
    }

    // ---------------------------------------------------------------- flame

    private static void flame(PdfCanvas c, double x, double y, double h,
                              int[] ink, int[] bg) throws IOException {
        c.fillRgb(ink[0], ink[1], ink[2]);
        path(c, "M50 4 C64 26 84 40 84 62 C84 84 69 96 50 96 C31 96 16 84 16 62 "
                + "C16 40 36 26 50 4 Z", x, y, h / U);
        if (bg != null) {
            c.fillRgb(bg[0], bg[1], bg[2]);
            path(c, "M50 44 C57 53 65 59 65 70 C65 81 58 88 50 88 C42 88 35 81 35 70 "
                    + "C35 59 43 53 50 44 Z", x, y, h / U);
        }
        c.fillBlack();
    }

    // ------------------------------------------------------------- cylinder

    private static void cylinder(PdfCanvas c, double x, double y, double h, int[] ink)
            throws IOException {
        c.fillRgb(ink[0], ink[1], ink[2]);
        // body with domed top, neck, valve handle — union path (non-zero fill)
        path(c, "M32 96 L32 40 Q32 26 50 26 Q68 26 68 40 L68 96 Z "
                + "M45 27 L45 16 L55 16 L55 27 Z "
                + "M40 8 L60 8 L60 16 L40 16 Z", x, y, h / U);
        c.fillBlack();
    }

    // ---------------------------------------------------------------- skull

    private static void skull(PdfCanvas c, double x, double y, double h,
                              int[] ink, int[] bg) throws IOException {
        double s = h / U;
        // crossbones first (behind)
        c.fillRgb(ink[0], ink[1], ink[2]);
        bone(c, x, y, s, 10, 84, 90, 74, 7);
        bone(c, x, y, s, 10, 74, 90, 84, 7);
        // head + jaw
        path(c, "M50 6 C31 6 19 21 19 39 C19 51 25 60 31 64 L31 75 "
                + "C31 80 38 84 50 84 C62 84 69 80 69 75 L69 64 "
                + "C75 60 81 51 81 39 C81 21 69 6 50 6 Z", x, y, s);
        // eyes + nose punched in background colour
        c.fillRgb(bg[0], bg[1], bg[2]);
        c.fillEllipse(x + 37 * s, y + 40 * s, 8.5 * s, 8.5 * s);
        c.fillEllipse(x + 63 * s, y + 40 * s, 8.5 * s, 8.5 * s);
        c.fillPolygon(new double[][]{
                {x + 50 * s, y + 50 * s}, {x + 44 * s, y + 62 * s}, {x + 56 * s, y + 62 * s}});
        // teeth bars
        c.fillRgb(ink[0], ink[1], ink[2]);
        for (int i = 0; i < 4; i++) {
            double tx = x + (40 + i * 7) * s;
            c.fillRect(tx, y + 74 * s, 2.4 * s, 8 * s);
        }
        c.fillBlack();
    }

    private static void bone(PdfCanvas c, double x, double y, double s,
                             double x1, double y1, double x2, double y2, double w)
            throws IOException {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double len = Math.hypot(dx, dy);
        double nx = -dy / len * w / 2;
        double ny = dx / len * w / 2;
        c.fillPolygon(new double[][]{
                {x + (x1 + nx) * s, y + (y1 + ny) * s},
                {x + (x2 + nx) * s, y + (y2 + ny) * s},
                {x + (x2 - nx) * s, y + (y2 - ny) * s},
                {x + (x1 - nx) * s, y + (y1 - ny) * s}});
        // knobs at both ends
        c.fillEllipse(x + x1 * s, y + y1 * s, w * 0.9 * s, w * 0.9 * s);
        c.fillEllipse(x + x2 * s, y + y2 * s, w * 0.9 * s, w * 0.9 * s);
    }

    // ----------------------------------------------------------------- bomb

    private static void bomb(PdfCanvas c, double x, double y, double h, int[] ink)
            throws IOException {
        double s = h / U;
        c.fillRgb(ink[0], ink[1], ink[2]);
        c.fillEllipse(x + 46 * s, y + 62 * s, 30 * s, 30 * s);
        // fuse
        path(c, "M60 40 C70 30 78 26 86 16 L92 22 C84 32 76 38 66 48 Z", x, y, s);
        // spark
        c.strokeRgb(ink[0], ink[1], ink[2]);
        c.line(x + 88 * s, y + 8 * s, x + 88 * s, y + 20 * s, 2);
        c.line(x + 78 * s, y + 6 * s, x + 96 * s, y + 14 * s, 2);
        c.line(x + 80 * s, y + 18 * s, x + 96 * s, y + 6 * s, 2);
        c.strokeBlack();
        c.fillBlack();
    }

    // --------------------------------------------------- flame over circle

    private static void flameOverCircle(PdfCanvas c, double x, double y, double h,
                                        int[] ink, int[] bg) throws IOException {
        double s = h / U;
        flame(c, x + 25 * s, y, 50 * s, ink, bg);
        c.strokeRgb(ink[0], ink[1], ink[2]);
        c.strokeEllipse(x + 50 * s, y + 70 * s, 24 * s, 24 * s, Math.max(2, 0.04 * h));
        c.strokeBlack();
        c.fillBlack();
    }

    // ------------------------------------------------------------ biohazard

    private static void biohazard(PdfCanvas c, double x, double y, double h,
                                  int[] ink, int[] bg) throws IOException {
        double s = h / U;
        c.fillRgb(ink[0], ink[1], ink[2]);
        for (int i = 0; i < 3; i++) {
            double a = Math.toRadians(-90 + i * 120);
            double lx = 50 + Math.cos(a) * 31;
            double ly = 50 + Math.sin(a) * 31;
            c.fillEllipse(x + lx * s, y + ly * s, 27 * s, 27 * s);
        }
        c.fillRgb(bg[0], bg[1], bg[2]);
        c.fillEllipse(x + 50 * s, y + 50 * s, 20 * s, 20 * s);
        c.fillRgb(ink[0], ink[1], ink[2]);
        c.fillEllipse(x + 50 * s, y + 50 * s, 11 * s, 11 * s);
        c.fillBlack();
    }

    // -------------------------------------------------------------- trefoil

    private static void trefoil(PdfCanvas c, double x, double y, double h,
                                int[] ink, int[] bg) throws IOException {
        double s = h / U;
        c.fillRgb(ink[0], ink[1], ink[2]);
        for (int i = 0; i < 3; i++) {
            double mid = Math.toRadians(-90 + i * 120);
            double half = Math.toRadians(30);
            double[][] pts = new double[34][2];
            pts[0] = new double[]{50, 50};
            for (int j = 0; j <= 32; j++) {
                double a = mid - half + (2 * half) * j / 32.0;
                pts[j + 1] = new double[]{50 + Math.cos(a) * 44, 50 + Math.sin(a) * 44};
            }
            double[][] abs = new double[pts.length][2];
            for (int j = 0; j < pts.length; j++) {
                abs[j][0] = x + pts[j][0] * s;
                abs[j][1] = y + pts[j][1] * s;
            }
            c.fillPolygon(abs);
        }
        c.fillRgb(bg[0], bg[1], bg[2]);
        c.fillEllipse(x + 50 * s, y + 50 * s, 13 * s, 13 * s);
        c.fillBlack();
    }

    // ------------------------------------------------------------- corrosive

    private static void corrosive(PdfCanvas c, double x, double y, double h,
                                  int[] ink, int[] bg) throws IOException {
        double s = h / U;
        // left test tube pouring onto the hand
        tiltedTube(c, x, y, s, 26, 44, -35, ink, bg);
        // right test tube pouring onto the metal plate
        tiltedTube(c, x, y, s, 74, 44, 35, ink, bg);
        // drips
        c.fillRgb(ink[0], ink[1], ink[2]);
        c.fillEllipse(x + 30 * s, y + 62 * s, 3 * s, 4 * s);
        c.fillEllipse(x + 70 * s, y + 60 * s, 3 * s, 4 * s);
        // hand (left)
        path(c, "M6 96 C6 86 12 80 24 82 L44 86 C51 87 54 92 52 96 Z", x, y, s);
        // metal plate (right)
        c.fillRect(x + 56 * s, y + 86 * s, 38 * s, 7 * s);
        c.fillRect(x + 62 * s, y + 93 * s, 4 * s, 5 * s);
        c.fillRect(x + 84 * s, y + 93 * s, 4 * s, 5 * s);
        c.fillBlack();
    }

    private static void tiltedTube(PdfCanvas c, double x, double y, double s,
                                   double cx, double cy, double deg, int[] ink, int[] bg)
            throws IOException {
        double rad = Math.toRadians(deg);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        // tube 10 wide x 34 long, axis from top-left to mouth
        double[][] local = {{-5, -17}, {5, -17}, {5, 17}, {-5, 17}};
        double[][] tube = new double[4][2];
        for (int i = 0; i < 4; i++) {
            double lx = local[i][0];
            double ly = local[i][1];
            tube[i][0] = cx + lx * cos - ly * sin;
            tube[i][1] = cy + lx * sin + ly * cos;
        }
        c.fillRgb(bg[0], bg[1], bg[2]);
        c.fillPolygon(shift(tube, x, y, s));
        c.strokeRgb(ink[0], ink[1], ink[2]);
        c.strokePolygon(shift(tube, x, y, s), 2);
        // liquid: lower half of the tube, filled ink
        double[][] liquid = {{-5, 0}, {5, 0}, {5, 15}, {-5, 15}};
        double[][] lq = new double[4][2];
        for (int i = 0; i < 4; i++) {
            double lx = liquid[i][0];
            double ly = liquid[i][1];
            lq[i][0] = cx + lx * cos - ly * sin;
            lq[i][1] = cy + lx * sin + ly * cos;
        }
        c.fillRgb(ink[0], ink[1], ink[2]);
        c.fillPolygon(shift(lq, x, y, s));
        c.strokeBlack();
        c.fillBlack();
    }

    private static double[][] shift(double[][] pts, double x, double y, double s) {
        double[][] out = new double[pts.length][2];
        for (int i = 0; i < pts.length; i++) {
            out[i][0] = x + pts[i][0] * s;
            out[i][1] = y + pts[i][1] * s;
        }
        return out;
    }

    // -------------------------------------------------------------- battery

    private static void battery(PdfCanvas c, double x, double y, double h,
                                int[] ink, int[] bg) throws IOException {
        double s = h / U;
        c.fillRgb(ink[0], ink[1], ink[2]);
        // cell body + terminal
        path(c, "M22 34 L78 34 L78 88 L22 88 Z M44 26 L56 26 L56 34 L44 34 Z", x, y, s);
        // bolt punched in bg
        c.fillRgb(bg[0], bg[1], bg[2]);
        c.fillPolygon(new double[][]{
                {x + 54 * s, y + 42 * s}, {x + 40 * s, y + 62 * s}, {x + 49 * s, y + 62 * s},
                {x + 44 * s, y + 80 * s}, {x + 62 * s, y + 56 * s}, {x + 52 * s, y + 56 * s}});
        c.fillBlack();
    }

    // -------------------------------------------------- environment (fish/tree)

    /** Fish + tree pollutant mark (ENV_HAZARD label type). */
    public static void fishAndTree(PdfCanvas c, double x, double y, double h, int[] ink)
            throws IOException {
        double s = h / U;
        c.fillRgb(ink[0], ink[1], ink[2]);
        // fish body
        path(c, "M6 58 C22 38 52 36 68 52 L88 40 L84 58 L88 76 L68 64 "
                + "C52 80 22 78 6 58 Z", x, y, s);
        c.fillBlack();
        // tree
        c.fillRgb(ink[0], ink[1], ink[2]);
        c.fillPolygon(new double[][]{
                {x + 72 * s, y + 8 * s}, {x + 94 * s, y + 40 * s}, {x + 50 * s, y + 40 * s}});
        c.fillRect(x + 68 * s, y + 40 * s, 8 * s, 14 * s);
        c.fillBlack();
    }
}
