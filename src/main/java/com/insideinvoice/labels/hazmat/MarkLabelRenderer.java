package com.insideinvoice.labels.hazmat;

import com.insideinvoice.labels.enums.HazmatLabelType;
import com.insideinvoice.labels.render.LabelAddress;
import com.insideinvoice.labels.render.LabelSizes;
import com.insideinvoice.labels.renderer.PdfCanvas;
import com.insideinvoice.labels.util.LabelUnits;

import java.io.IOException;
import java.util.Locale;

/**
 * Rectangular/alternate hazmat marks: lithium battery handling label, limited
 * quantity, excepted quantity, environmentally hazardous substance, orientation
 * arrows, cargo-aircraft-only and OVERPACK. All vector, thermal-aware.
 */
public class MarkLabelRenderer implements HazmatRenderer {

    @Override
    public byte[] render(HazmatSpec spec) throws Exception {
        LabelSizes.Size size = LabelSizes.require(spec.size());
        try (PdfCanvas c = new PdfCanvas(203)) {
            c.beginLabelPage(size.wMm(), size.hMm());
            switch (spec.effectiveType()) {
                case LITHIUM -> lithium(c, spec, size);
                case LIMITED_QTY -> limitedQuantity(c, spec, size);
                case EXCEPTED_QTY -> exceptedQuantity(c, spec, size);
                case ENV_HAZARD -> envHazard(c, spec, size);
                case ORIENTATION -> orientation(c, spec, size);
                case CAO -> cao(c, spec, size);
                case OVERPACK -> overpack(c, spec, size);
                default -> throw new IllegalArgumentException(
                        "Not a mark label type: " + spec.effectiveType());
            }
            return c.save();
        }
    }

    private int ink(boolean thermal) {
        return thermal ? 0x000000 : 0xE4002B;
    }

    private static int r(int rgb) {
        return (rgb >> 16) & 0xFF;
    }

    private static int g(int rgb) {
        return (rgb >> 8) & 0xFF;
    }

    private static int b(int rgb) {
        return rgb & 0xFF;
    }

    /** Red (or black, thermal) hatched border band. Returns the band width in mm. */
    private double hatchBorder(PdfCanvas c, double w, double h, boolean thermal) throws IOException {
        double band = Math.max(6, Math.min(w, h) * 0.08);
        int color = ink(thermal);
        c.saveState();
        // four hatched bands around the white centre
        hatchBand(c, 0, 0, w, band, color);
        hatchBand(c, 0, h - band, w, band, color);
        hatchBand(c, 0, band, band, h - 2 * band, color);
        hatchBand(c, w - band, band, band, h - 2 * band, color);
        c.restoreState();
        // keylines in the mark ink (red in colour mode, black on thermal)
        c.strokeRgb(r(color), g(color), b(color));
        c.strokeRect(0.4, 0.4, w - 0.8, h - 0.8, 0.5);
        c.strokeRect(band, band, w - 2 * band, h - 2 * band, 0.5);
        c.strokeBlack();
        // PDFBox page start leaves the fill colour on WHITE — reset it so text draws
        c.fillBlack();
        return band;
    }

    private void hatchBand(PdfCanvas c, double x, double y, double w, double h, int color)
            throws IOException {
        double gap = Math.max(1.5, LabelUnits.dotMm(203) * 8);
        c.saveState();
        c.clipPolygon(new double[][]{{x, y}, {x + w, y}, {x + w, y + h}, {x, y + h}});
        c.strokeRgb(r(color), g(color), b(color));
        double d = w + h;
        for (double o = -d; o <= d; o += gap) {
            double ax = x - 1;
            double ay = ax + o;
            c.line(ax, ay, x + w + 1, ay + w + 1, 0.55);
        }
        c.strokeBlack();
        c.restoreState();
    }

    // ------------------------------------------------------------- lithium

    /**
     * Battery cluster in the official UN3480 mark style, laid out on a 64 x 38
     * design grid (origin top-left, baseline at y = 38):
     *
     * <ul>
     *   <li>two thin cylindrical cells front-left</li>
     *   <li>one wide cell behind them with a white terminal button</li>
     *   <li>a 9 V block with two terminal posts</li>
     *   <li>a horizontal cell on the right with a white end terminal</li>
     *   <li>a flame rising off the horizontal cell, struck through by a
     *       lightning bolt (dark over paper, knocked out white over the cell)</li>
     * </ul>
     */
    private void drawBatteryCluster(PdfCanvas c, double x0, double top, double u, boolean thermal)
            throws IOException {
        int body = thermal ? 0x000000 : 0x333333;
        double base = top + 38 * u;

        // --- wide cell behind (x 15..30, top 1, baseline 38)
        c.fillRgb(r(body), g(body), b(body));
        c.strokeRgb(r(body), g(body), b(body));
        cylinder(c, x0 + 15 * u, top + 1 * u, 15 * u, 37 * u, body, thermal);
        // white terminal button on the wide cell's top face
        if (!thermal) {
            c.fillWhite();
            c.fillEllipse(x0 + 22.5 * u, top + 2.4 * u, 3.4 * u, 1.5 * u);
        }

        // --- thin cells in front-left (drawn after so they read as "in front")
        cylinder(c, x0 + 1 * u, top + 16 * u, 6 * u, 22 * u, body, thermal);
        cylinder(c, x0 + 8.5 * u, top + 10 * u, 6 * u, 28 * u, body, thermal);

        // --- 9 V block with two terminal posts (x 32..44, top 13)
        c.fillRgb(r(body), g(body), b(body));
        c.fillRect(x0 + 34 * u, top + 9 * u, 3.6 * u, 4.5 * u);
        c.fillRect(x0 + 39.4 * u, top + 9 * u, 3.6 * u, 4.5 * u);
        c.fillRect(x0 + 32 * u, top + 13 * u, 12 * u, 25 * u);

        // --- horizontal cell on the right (x 44..64, top 24..baseline)
        c.fillRect(x0 + 47 * u, top + 24 * u, 14 * u, 14 * u);
        c.fillEllipse(x0 + 47 * u, base - 7 * u, 3 * u, 7 * u);
        c.fillEllipse(x0 + 61 * u, base - 7 * u, 3 * u, 7 * u);
        // white positive terminal on the right end cap
        c.fillWhite();
        c.fillEllipse(x0 + 61 * u, base - 7 * u, 1.6 * u, 3 * u);

        // --- flame rising off the horizontal cell (same silhouette as the
        //     class-diamond flame pictogram)
        c.fillBlack();
        double flameS = 0.167 * u;
        c.svgPath(HazmatSymbols.FLAME_D, x0 + 41.16 * u, top + 7.67 * u, flameS, true, null);

        // --- lightning bolt: dark over paper, knocked out white where it
        //     crosses the horizontal cell
        double[][] bolt = {
                {x0 + 54.2 * u, top + 20 * u},
                {x0 + 49.4 * u, top + 30.8 * u},
                {x0 + 52.4 * u, top + 30.8 * u},
                {x0 + 51.2 * u, top + 38 * u},
                {x0 + 56.6 * u, top + 27.2 * u},
                {x0 + 53.6 * u, top + 27.2 * u},
                {x0 + 55.4 * u, top + 20 * u}};
        c.fillBlack();
        c.fillPolygon(bolt);
        c.saveState();
        c.clipPolygon(new double[][]{
                {x0 + 47 * u, top + 24 * u}, {x0 + 61 * u, top + 24 * u},
                {x0 + 61 * u, base}, {x0 + 47 * u, base}});
        c.fillWhite();
        c.fillPolygon(bolt);
        c.restoreState();

        c.fillBlack();
        c.strokeBlack();
    }

    /** Vertical cylinder: body, domed top face and a small terminal nub. */
    private void cylinder(PdfCanvas c, double x, double top, double w, double h, int body,
                          boolean thermal) throws IOException {
        c.fillRgb(r(body), g(body), b(body));
        c.fillRect(x, top, w, h);
        c.fillEllipse(x + w / 2, top, w / 2, w * 0.18);
        c.fillRect(x + w * 0.32, top - w * 0.2, w * 0.36, w * 0.22);
        if (thermal) {
            c.fillBlack();
        }
    }

    private void lithium(PdfCanvas c, HazmatSpec spec, LabelSizes.Size size) throws IOException {
        double w = size.wMm();
        double h = size.hMm();
        boolean thermal = spec.isThermal();
        double band = hatchBorder(c, w, h, thermal);
        double cx = w / 2;
        double inner = w - 2 * band;

        // battery cluster: 64 x 38 design units, centred in the white area
        double u = inner * 0.80 / 64.0;
        double clusterW = 64 * u;
        double clusterH = 38 * u;
        drawBatteryCluster(c, cx - clusterW / 2, band + Math.max(1.5, (h * 0.42 - clusterH) * 0.55),
                u, thermal);

        // UN number (large bold)
        String un = spec.getUnNumber() == null || spec.getUnNumber().isBlank()
                ? "UN3480"
                : spec.getUnNumber().toUpperCase(Locale.ENGLISH);
        double unSize = c.fitFontSize(un, inner - 8, 34, 12, true);
        c.text(un, cx, h * 0.55, unSize, true, PdfCanvas.Align.CENTER);

        // "For more information, call: …"
        String phone = nv(spec.getEmergencyPhone());
        String info = phone.isEmpty() ? "For more information, call: the shipper"
                : "For more information, call: " + phone;
        double iSize = c.fitFontSize(info, inner - 6, 11, 6, false);
        c.text(info, cx, h * 0.73, iSize, false, PdfCanvas.Align.CENTER);

        // "Global Response Access Code" line (only when provided)
        String gra = nv(spec.getGraCode());
        if (!gra.isEmpty()) {
            String graLine = "Global Response Access Code : " + gra;
            double gSize = c.fitFontSize(graLine, inner - 6, 11, 6, false);
            c.text(graLine, cx, h * 0.81, gSize, false, PdfCanvas.Align.CENTER);
        }

        String pi = spec.getLithiumPackingInstruction() == null
                ? "" : "IATA PI " + spec.getLithiumPackingInstruction().trim();
        if (!pi.isBlank()) {
            double pSize = c.fitFontSize(pi, inner - 10, 9, 6, false);
            c.text(pi, cx, h * 0.88, pSize, false, PdfCanvas.Align.CENTER);
        }
        // footer stays inside the white centre, clear of the hatch band
        ClassDiamondRenderer.drawFooter(c, spec, h - band - 5, w, band + 1.5);
    }

    // ------------------------------------------------- limited quantity

    private void limitedQuantity(PdfCanvas c, HazmatSpec spec, LabelSizes.Size size)
            throws IOException {
        double w = size.wMm();
        double h = size.hMm();
        double S = Math.min(w, h);
        double cx = w / 2;
        double cy = h / 2;
        double[][] outer = ClassDiamondRenderer.outerPts(cx, cy, S);

        c.fillWhite();
        c.fillPolygon(outer);
        // black top and bottom corner triangles (25% height each)
        double[][] top = {outer[0],
                {cx - S * 0.25, cy - S * 0.25}, {cx + S * 0.25, cy - S * 0.25}};
        double[][] bottom = {outer[2],
                {cx - S * 0.25, cy + S * 0.25}, {cx + S * 0.25, cy + S * 0.25}};
        c.fillBlack();
        c.fillPolygon(top);
        c.fillPolygon(bottom);

        c.strokeBlack();
        c.strokePolygon(outer, Math.max(0.01 * S, 0.5));

        // centre code: "Y" for air limited quantity (or the provided code)
        String code = spec.getLimitedQuantityCode() == null
                || spec.getLimitedQuantityCode().isBlank()
                ? (spec.getTransportMode() == com.insideinvoice.labels.enums.TransportMode.AIR
                        ? "Y" : "")
                : spec.getLimitedQuantityCode().trim().toUpperCase(Locale.ENGLISH);
        if (!code.isEmpty()) {
            double sizePt = LabelUnits.mmToPt(0.14 * S) / 0.72;
            double fit = c.fitFontSize(code, S * 0.3, sizePt, LabelUnits.mmToPt(8) / 0.72, true);
            c.text(code, cx, cy - LabelUnits.ptToMm(fit * 0.72) / 2, fit, true,
                    PdfCanvas.Align.CENTER);
        }
        if (spec.isThermal()) {
            String line = "LIMITED QUANTITY";
            double fs = c.fitFontSize(line, S * 0.5, 7, 6, true);
            c.text(line, cx, cy + S * 0.42, fs, true, PdfCanvas.Align.CENTER);
        }
        ClassDiamondRenderer.drawFooter(c, spec, h - 5.5, w);
    }

    // ------------------------------------------------ excepted quantity

    private void exceptedQuantity(PdfCanvas c, HazmatSpec spec, LabelSizes.Size size)
            throws IOException {
        double w = size.wMm();
        double h = size.hMm();
        boolean thermal = spec.isThermal();
        double band = hatchBorder(c, w, h, thermal);

        double cx = w / 2;
        // centre mark: * (most), E (ethylene oxide style) or the class division
        String mark = spec.getHazardClass() == null || spec.getHazardClass().isBlank()
                ? "*" : spec.getHazardClass().trim().toUpperCase(Locale.ENGLISH);
        double sizePt = LabelUnits.mmToPt(Math.min(w, h) * 0.22) / 0.72;
        double fit = c.fitFontSize(mark, w - 2 * band - 10, sizePt, 18, true);
        c.text(mark, cx, h * 0.30, fit, true, PdfCanvas.Align.CENTER);

        String eq = "EXCEPTED QUANTITY";
        double eSize = c.fitFontSize(eq, w - 2 * band - 8, 12, 6, true);
        c.text(eq, cx, h * 0.55, eSize, true, PdfCanvas.Align.CENTER);

        LabelAddress consignor = spec.getConsignor();
        if (consignor != null) {
            String name = consignor.getCompany() != null && !consignor.getCompany().isBlank()
                    ? consignor.getCompany() : consignor.getName();
            if (name != null && !name.isBlank()) {
                double nSize = c.fitFontSize(name.toUpperCase(Locale.ENGLISH), w - 2 * band - 8, 9, 6,
                        false);
                c.text(name.toUpperCase(Locale.ENGLISH), cx, h * 0.66, nSize, false,
                        PdfCanvas.Align.CENTER);
            }
        }
        ClassDiamondRenderer.drawFooter(c, spec, h - band - 5, w, band + 1.5);
    }

    // ------------------------------------------------------ env hazardous

    private void envHazard(PdfCanvas c, HazmatSpec spec, LabelSizes.Size size) throws IOException {
        double w = size.wMm();
        double h = size.hMm();
        c.strokeRect(0.5, 0.5, w - 1, h - 1, 0.75);

        double symW = Math.min(w, h) * 0.66;
        HazmatSymbols.fishAndTree(c, w / 2 - symW / 2, h * 0.16, symW, new int[]{0, 0, 0});

        String text = "ENVIRONMENTALLY HAZARDOUS SUBSTANCE";
        double y = h * 0.72;
        double size1 = c.fitFontSize(text, w - 24, 9, 6, true);
        c.text(text, w / 2, y, size1, true, PdfCanvas.Align.CENTER);
        String sub = nv(spec.getProperShippingName());
        if (!sub.isBlank()) {
            double sSize = c.fitFontSize(sub.toUpperCase(Locale.ENGLISH), w - 24, 7, 6, false);
            c.text(sub.toUpperCase(Locale.ENGLISH), w / 2, y + 4, sSize, false,
                    PdfCanvas.Align.CENTER);
        }
        ClassDiamondRenderer.drawFooter(c, spec, h - 5.5, w);
    }

    // -------------------------------------------------- orientation arrows

    private void orientation(PdfCanvas c, HazmatSpec spec, LabelSizes.Size size)
            throws IOException {
        double w = size.wMm();
        double h = size.hMm();
        c.strokeRect(0.5, 0.5, w - 1, h - 1, 0.75);
        int color = spec.isThermal() ? 0x000000 : 0xE4002B;

        int arrows = 2;
        double arrowW = Math.min(w * 0.3, h * 0.32);
        double gap = w * 0.08;
        double total = arrows * arrowW + (arrows - 1) * gap;
        double x0 = (w - total) / 2;
        double top = h * 0.14;
        double arrowH = h * 0.58;
        for (int i = 0; i < arrows; i++) {
            double x = x0 + i * (arrowW + gap);
            upArrow(c, x, top, arrowW, arrowH, color);
        }
        String text = "THIS WAY UP";
        double y = top + arrowH + h * 0.05;
        c.fillRgb((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF);
        double fs = c.fitFontSize(text, w - 20, 14, 8, true);
        c.text(text, w / 2, y, fs, true, PdfCanvas.Align.CENTER);
        c.fillBlack();
        ClassDiamondRenderer.drawFooter(c, spec, h - 5.5, w);
    }

    private void upArrow(PdfCanvas c, double x, double y, double w, double h, int color)
            throws IOException {
        c.fillRgb((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF);
        double headH = h * 0.34;
        double shaftW = w * 0.42;
        c.fillPolygon(new double[][]{
                {x + w / 2, y},
                {x + w, y + headH},
                {x + w / 2 + shaftW / 2, y + headH},
                {x + w / 2 + shaftW / 2, y + h},
                {x + w / 2 - shaftW / 2, y + h},
                {x + w / 2 - shaftW / 2, y + headH},
                {x, y + headH}});
        c.fillBlack();
    }

    // -------------------------------------------------- cargo aircraft only

    private void cao(PdfCanvas c, HazmatSpec spec, LabelSizes.Size size) throws IOException {
        double w = size.wMm();
        double h = size.hMm();
        c.fillRgb(255, 102, 0);
        c.fillRect(0, 0, w, h);
        c.fillBlack();
        c.strokeRect(1, 1, w - 2, h - 2, 1);

        double cx = w / 2;
        String l1 = "CARGO AIRCRAFT ONLY";
        double s1 = c.fitFontSize(l1, w - 16, 15, 9, true);
        c.text(l1, cx, h * 0.14, s1, true, PdfCanvas.Align.CENTER);

        upArrow(c, cx - w * 0.13, h * 0.34, w * 0.26, h * 0.30, 0x000000);

        String l2 = "INVALID FOR PASSENGER AIRCRAFT";
        double s2 = c.fitFontSize(l2, w - 14, 11, 7, true);
        c.text(l2, cx, h * 0.70, s2, true, PdfCanvas.Align.CENTER);

        String un = nv(spec.getUnNumber());
        if (!un.isBlank()) {
            double s3 = c.fitFontSize(un.toUpperCase(Locale.ENGLISH), w - 16, 10, 7, false);
            c.text(un.toUpperCase(Locale.ENGLISH), cx, h * 0.82, s3, false, PdfCanvas.Align.CENTER);
        }
        ClassDiamondRenderer.drawFooter(c, spec, h - 5.5, w);
    }

    // ------------------------------------------------------------- overpack

    private void overpack(PdfCanvas c, HazmatSpec spec, LabelSizes.Size size) throws IOException {
        double w = size.wMm();
        double h = size.hMm();
        c.strokeRect(2, 2, w - 4, h - 4, 1);
        String text = "OVERPACK";
        double fs = c.fitFontSize(text, w - 24, 36, 14, true);
        c.text(text, w / 2, h / 2 - LabelUnits.ptToMm(fs * 0.72) / 2, fs, true,
                PdfCanvas.Align.CENTER);
        String sub = nv(spec.getUnNumber());
        if (!sub.isBlank()) {
            double ss = c.fitFontSize(sub.toUpperCase(Locale.ENGLISH), w - 30, 10, 7, false);
            c.text(sub.toUpperCase(Locale.ENGLISH), w / 2, h * 0.72, ss, false,
                    PdfCanvas.Align.CENTER);
        }
        ClassDiamondRenderer.drawFooter(c, spec, h - 5.5, w);
    }

    private static String nv(String s) {
        return s == null ? "" : s;
    }
}
