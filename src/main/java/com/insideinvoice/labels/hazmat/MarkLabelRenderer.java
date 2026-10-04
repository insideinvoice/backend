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

    private void hatchBorder(PdfCanvas c, double w, double h, boolean thermal) throws IOException {
        double band = Math.max(6, Math.min(w, h) * 0.08);
        int color = ink(thermal);
        c.saveState();
        // four hatched bands around the white centre
        hatchBand(c, 0, 0, w, band, color, thermal);
        hatchBand(c, 0, h - band, w, band, color, thermal);
        hatchBand(c, 0, band, band, h - 2 * band, color, thermal);
        hatchBand(c, w - band, band, band, h - 2 * band, color, thermal);
        c.restoreState();
        c.strokeRgb(0, 0, 0);
        c.strokeRect(0.5, 0.5, w - 1, h - 1, 0.75);
        c.strokeRect(band, band, w - 2 * band, h - 2 * band, 0.75);
        c.strokeBlack();
    }

    private void hatchBand(PdfCanvas c, double x, double y, double w, double h, int color,
                           boolean thermal) throws IOException {
        double gap = Math.max(1.6, LabelUnits.dotMm(203) * 8);
        c.saveState();
        c.clipPolygon(new double[][]{{x, y}, {x + w, y}, {x + w, y + h}, {x, y + h}});
        c.strokeRgb((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF);
        double d = w + h;
        for (double o = -d; o <= d; o += gap) {
            double ax = x - 1;
            double ay = ax + o;
            c.line(ax, ay, x + w + 1, ay + w + 1, 0.6);
        }
        c.strokeBlack();
        c.restoreState();
    }

    // ------------------------------------------------------------- lithium

    private void drawBatteryCluster(PdfCanvas c, double cx, double top, double maxH,
                                    boolean thermal) throws IOException {
        int g = thermal ? 0 : 51;
        c.fillRgb(g, g, g);
        c.strokeRgb(g, g, g);
        double u = maxH / 34.0;
        double totalW = 60 * u;
        double x0 = cx - totalW / 2;
        double base = top + 34 * u;
        cylBattery(c, x0, base - 24 * u, 7 * u, 24 * u);
        cylBattery(c, x0 + 9 * u, base - 30 * u, 7 * u, 30 * u);
        cylBattery(c, x0 + 18 * u, base - 18 * u, 7 * u, 18 * u);
        // square battery in the middle
        double sqX = x0 + 27 * u, sqY = base - 22 * u;
        c.fillRect(sqX, sqY, 13 * u, 22 * u);
        c.fillRect(sqX + 4.5 * u, sqY - 2.5 * u, 4 * u, 2.5 * u);
        // horizontal cylinder battery at right
        double hx = x0 + 44 * u, hy = base - 14 * u;
        c.fillRect(hx, hy, 16 * u, 10 * u);
        c.fillEllipse(hx + 16 * u, hy + 5 * u, 0.001, 5 * u); // terminals below
        // white white terminal disc
        c.fillGray(1f);
        c.fillEllipse(hx + 16 * u, hy + 5 * u, 3 * u, 4 * u);
        // re-fill cluster colour for flame + knob
        c.fillRgb(g, g, g);
        c.fillRect(hx + 16 * u, hy + 2 * u, 2.5 * u, 6 * u);
        double fx = hx + 11 * u, fy = hy + 2 * u;
        c.fillPolygon(new double[][]{
                {fx, fy}, {fx - 8 * u, fy - 9 * u}, {fx - 4 * u, fy - 8 * u},
                {fx - 6 * u, fy - 17 * u}, {fx - 1 * u, fy - 10 * u},
                {fx + 1 * u, fy - 7 * u}, {fx + 5 * u, fy - 13 * u},
                {fx + 8 * u, fy - 7 * u}, {fx + 4 * u, fy - 4 * u},
                {fx + 6 * u, fy}
        });
        c.fillBlack();
        c.strokeBlack();
    }

    private void cylBattery(PdfCanvas c, double x, double yTop, double w, double h) throws IOException {
        c.fillRect(x, yTop, w, h);
        c.fillEllipse(x + w / 2, yTop, w / 2, w * 0.28);
        c.fillRect(x + w / 3, yTop - 1.8, w / 3, 1.9);
    }

    private void lithium(PdfCanvas c, HazmatSpec spec, LabelSizes.Size size) throws IOException {
        double w = size.wMm();
        double h = size.hMm();
        boolean thermal = spec.isThermal();
        hatchBorder(c, w, h, thermal);

        double cx = w / 2;
        drawBatteryCluster(c, cx, h * 0.11, Math.min(w, h) * 0.38, thermal);

        // UN number (large bold)
        String un = spec.getUnNumber() == null || spec.getUnNumber().isBlank()
                ? "UN3480"
                : spec.getUnNumber().toUpperCase(Locale.ENGLISH);
        double unSize = c.fitFontSize(un, w - 16, 20, 12, true);
        c.text(un, cx, h * 0.55, unSize, true, PdfCanvas.Align.CENTER);

        // info phone line
        String phone = nv(spec.getEmergencyPhone());
        String info = phone.isEmpty() ? "For more information, call: the shipper"
                : "For more information, call: " + phone;
        double iSize = c.fitFontSize(info, w - 20, 8, 5.5, false);
        c.text(info, w * 0.16, h * 0.72, iSize, false, PdfCanvas.Align.LEFT);

        // "Global Response Access Code" line (only when provided)
        String gra = nv(spec.getGraCode());
        if (!gra.isEmpty()) {
            String graLine = "Global Response Access Code : " + gra;
            double gSize = c.fitFontSize(graLine, w - 20, 8, 5.5, false);
            c.text(graLine, w * 0.20, h * 0.80, gSize, false, PdfCanvas.Align.LEFT);
        }

        String pi = spec.getLithiumPackingInstruction() == null
                ? "" : "IATA PI " + spec.getLithiumPackingInstruction().trim();
        if (!pi.isBlank()) {
            double pSize = c.fitFontSize(pi, w - 24, 8, 6, false);
            c.text(pi, cx, h * 0.87, pSize, false, PdfCanvas.Align.CENTER);
        }
        ClassDiamondRenderer.drawFooter(c, spec, h - 5.5, w);
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
        hatchBorder(c, w, h, thermal);

        double cx = w / 2;
        // centre mark: * (most), E (ethylene oxide style) or the class division
        String mark = spec.getHazardClass() == null || spec.getHazardClass().isBlank()
                ? "*" : spec.getHazardClass().trim().toUpperCase(Locale.ENGLISH);
        double sizePt = LabelUnits.mmToPt(Math.min(w, h) * 0.22) / 0.72;
        double fit = c.fitFontSize(mark, w * 0.4, sizePt, 18, true);
        c.text(mark, cx, h * 0.30, fit, true, PdfCanvas.Align.CENTER);

        String eq = "EXCEPTED QUANTITY";
        double eSize = c.fitFontSize(eq, w - 30, 9, 6, true);
        c.text(eq, cx, h * 0.55, eSize, true, PdfCanvas.Align.CENTER);

        LabelAddress consignor = spec.getConsignor();
        if (consignor != null) {
            String name = consignor.getCompany() != null && !consignor.getCompany().isBlank()
                    ? consignor.getCompany() : consignor.getName();
            if (name != null && !name.isBlank()) {
                double nSize = c.fitFontSize(name.toUpperCase(Locale.ENGLISH), w - 30, 8, 6,
                        false);
                c.text(name.toUpperCase(Locale.ENGLISH), cx, h * 0.66, nSize, false,
                        PdfCanvas.Align.CENTER);
            }
        }
        ClassDiamondRenderer.drawFooter(c, spec, h - 5.5, w);
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
