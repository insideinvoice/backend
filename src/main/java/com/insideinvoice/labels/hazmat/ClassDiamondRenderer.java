package com.insideinvoice.labels.hazmat;

import com.insideinvoice.labels.enums.HazmatLabelType;
import com.insideinvoice.labels.render.LabelAddress;
import com.insideinvoice.labels.render.LabelSizes;
import com.insideinvoice.labels.renderer.PdfCanvas;
import com.insideinvoice.labels.util.LabelUnits;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * The Part C diamond: true 45° square-on-point with the 0.05·S inner border,
 * 0.127·S numeral, 0.30·S symbol in the upper half, class name in the middle
 * band, class 9 underline and class 1 division+compat group. Colour mode fills
 * the exact class colours; thermal mode falls back to black borders, hatched
 * half/stripes and a "COLOR: …" caption. Also draws the 4x6 package marking
 * block below the diamond.
 */
public class ClassDiamondRenderer implements HazmatRenderer {

    static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.ENGLISH);

    @Override
    public byte[] render(HazmatSpec spec) throws Exception {
        String sizeKey = spec.size();
        LabelSizes.Size size = LabelSizes.require(sizeKey);
        try (PdfCanvas c = new PdfCanvas(203)) {
            if ("a4".equals(sizeKey)) {
                drawPlacard(c, spec, size.wMm(), size.hMm());
            } else if (size.wMm() < size.hMm() - 1) {
                // 4x6: diamond in the top square, marking block below
                c.beginLabelPage(size.wMm(), size.hMm());
                double s = size.wMm();
                drawClassDiamond(c, spec, size.wMm() / 2, s / 2, s);
                double blockTop = s + LabelSizes.RULE_MM + 1;
                c.line(LabelSizes.SAFE_MARGIN_MM, s, size.wMm() - LabelSizes.SAFE_MARGIN_MM, s,
                        LabelSizes.RULE_MM);
                drawMarkingBlock(c, spec, blockTop, size.wMm(), size.hMm() - blockTop
                        - LabelSizes.SAFE_MARGIN_MM);
            } else {
                c.beginLabelPage(size.wMm(), size.hMm());
                double s = Math.min(size.wMm(), size.hMm());
                drawClassDiamond(c, spec, size.wMm() / 2, size.hMm() / 2, s);
                if (spec.isThermal()) {
                    drawColorCaption(c, spec, size.wMm() / 2, size.hMm() - 8, size.wMm() - 4);
                }
                drawFooter(c, spec, size.hMm() - 5.5, size.wMm());
            }
            return c.save();
        }
    }

    // --------------------------------------------------------------- diamond

    /**
     * Draws the class diamond centred at (cx, cy) with point-to-point size S.
     * Shared by the 4x4/100mm/4x6 labels and the A4 placard.
     */
    static void drawClassDiamond(PdfCanvas c, HazmatSpec spec, double cx, double cy, double S)
            throws IOException {
        HazardClass hc = HazardClass.fromKey(spec.getHazardClass());
        boolean thermal = spec.isThermal();

        double[][] outer = outerPts(cx, cy, S);
        double[][] inner = innerPts(cx, cy, S);
        double lineW = Math.max(0.01 * S, 2 * LabelUnits.dotMm(203));

        // 1) background
        drawBackground(c, hc, thermal, cx, cy, S);

        // 2) borders
        c.strokeBlack();
        c.strokePolygon(outer, lineW);
        c.strokePolygon(inner, lineW);

        // 3) symbol (upper half)
        int[] ink = resolveInk(c, hc, thermal, spec);
        if (hc.symbol() != HazardClass.SymbolKind.NONE) {
            double symH = 0.30 * S;
            HazmatSymbols.draw(c, hc.symbol(), cx - symH / 2, cy - 0.35 * S, symH,
                    ink, new int[]{255, 255, 255});
        }

        // 4) middle-band class name / required text (lower half, above the numeral)
        String name = middleText(spec, hc);
        if (!name.isEmpty()) {
            int nameBg = lowerColor(hc, thermal);
            int[] nameInk = pick(nameBg, hc, thermal, spec, false);
            c.fillRgb(nameInk[0], nameInk[1], nameInk[2]);
            double avail = S * 0.74;
            double y = cy + 0.055 * S;
            if (name.length() > 42) {
                var wrapped = c.fitParagraph(name, avail, Math.min(0.045 * S, 8), 5.5, 3, true);
                double lineHeight = LabelUnits.ptToMm(wrapped.fontSize() * 1.15);
                c.drawLines(wrapped.lines(), cx, y, lineHeight, wrapped.fontSize(), true,
                        PdfCanvas.Align.CENTER);
            } else {
                double size = c.fitFontSize(name, avail, 0.05 * S, Math.min(6, 0.05 * S), true);
                c.text(name, cx, y, size, true, PdfCanvas.Align.CENTER);
            }
            c.fillBlack();
        }

        // 5) numeral in the lower corner (+ class 1 compat group, class 9 underline)
        String numeral = numeral(spec, hc);
        if (!numeral.isEmpty()) {
            double numH = Math.max(0.127 * S, thermal ? 8 : 8);
            double sizePt = LabelUnits.mmToPt(numH) / 0.72;
            double avail = (0.5 * S - 0.24 * S) * 2; // diamond width at numeral band
            double fit = c.fitFontSize(numeral, avail, sizePt, LabelUnits.mmToPt(6) / 0.72, true);
            int lowerBg = lowerColor(hc, thermal);
            int[] numInk = pick(lowerBg, hc, thermal, spec, false);
            double topY = cy + 0.215 * S - LabelUnits.ptToMm(fit * 0.72) / 2;
            c.fillRgb(numInk[0], numInk[1], numInk[2]);
            c.text(numeral, cx, topY, fit, true, PdfCanvas.Align.CENTER);
            double underY = topY + LabelUnits.ptToMm(fit * 0.82);
            if (numeral.equals("9") || numeral.startsWith("9")) { // class 9 underlined
                double w = c.stringWidth(numeral, fit, true);
                c.fillRgb(numInk[0], numInk[1], numInk[2]);
                c.fillRect(cx - w / 2, underY, w, Math.max(0.01 * S, 0.5));
            }
            c.fillBlack();
            // subsidiary risks under the numeral
            List<String> subs = spec.subsidiaryList();
            if (!subs.isEmpty() && S >= 90) {
                String sub = "SUB: " + String.join(" / ", subs);
                double ss = c.fitFontSize(sub, S * 0.4, 7, 6, false);
                c.text(sub, cx, underY + 1.2, ss, false, PdfCanvas.Align.CENTER);
            }
        }

        // 6) radioactive category boxes (right side of the upper half)
        if (hc == HazardClass.C7) {
            drawRadiationBoxes(c, spec, cx, cy, S, ink);
        }
    }

    // -------------------------------------------------- background patterns

    private static void drawBackground(PdfCanvas c, HazardClass hc, boolean thermal,
                                       double cx, double cy, double S) throws IOException {
        double[][] outer = outerPts(cx, cy, S);
        int red = rgb("E4002B");
        int yellow52 = rgb("FFEB00");
        int yellow7 = rgb("FFF200");

        switch (hc.pattern()) {
            case SOLID -> {
                c.fillWhite();
                c.fillPolygon(outer);
                if (!thermal) {
                    c.fillRgb(hc.rgb()[0], hc.rgb()[1], hc.rgb()[2]);
                    c.fillPolygon(outer);
                }
            }
            case STRIPES_RED7 -> {
                c.fillWhite();
                c.fillPolygon(outer);
                int stripe = thermal ? 0x000000 : red;
                stripesClipped(c, outer, cx, cy, S, 7, stripe, false);
            }
            case STRIPES_BLACK7_UPPER -> {
                c.fillWhite();
                c.fillPolygon(outer);
                double[][] upper = upperHalf(cx, cy, outer);
                stripesClipped(c, upper, cx, cy, S, 7, 0x000000, false);
            }
            case HALF_RED_BOTTOM -> {
                c.fillWhite();
                c.fillPolygon(outer);
                double[][] lower = {{cx, cy}, outer[1], outer[2], outer[3]};
                if (thermal) {
                    hatchClipped(c, lower, S);
                } else {
                    c.fillRgb(red >> 16, (red >> 8) & 0xFF, red & 0xFF);
                    c.fillPolygon(lower);
                }
            }
            case HALF_TOP_RED_BOTTOM_YELLOW -> {
                double[][] upper = upperHalf(cx, cy, outer);
                double[][] lower = {{cx, cy}, outer[1], outer[2], outer[3]};
                if (thermal) {
                    c.fillWhite();
                    c.fillPolygon(outer);
                    hatchClipped(c, lower, S);
                } else {
                    c.fillRgb(red >> 16, (red >> 8) & 0xFF, red & 0xFF);
                    c.fillPolygon(upper);
                    c.fillRgb(yellow52 >> 16, (yellow52 >> 8) & 0xFF, yellow52 & 0xFF);
                    c.fillPolygon(lower);
                }
            }
            case HALF_YELLOW_TOP_WHITE_BOTTOM -> {
                double[][] upper = upperHalf(cx, cy, outer);
                double[][] lower = {{cx, cy}, outer[1], outer[2], outer[3]};
                if (thermal) {
                    c.fillWhite();
                    c.fillPolygon(outer);
                    hatchClipped(c, upper, S);
                } else {
                    c.fillRgb(yellow7 >> 16, (yellow7 >> 8) & 0xFF, yellow7 & 0xFF);
                    c.fillPolygon(upper);
                    c.fillWhite();
                    c.fillPolygon(lower);
                }
            }
            case HALF_BLACK_BOTTOM -> {
                double[][] lower = {{cx, cy}, outer[1], outer[2], outer[3]};
                if (thermal) {
                    c.fillWhite();
                    c.fillPolygon(outer);
                    hatchClipped(c, lower, S);
                } else {
                    c.fillWhite();
                    c.fillPolygon(outer);
                    c.fillBlack();
                    c.fillPolygon(lower);
                }
            }
        }
        c.fillWhite();
        c.fillBlack();
    }

    /** Vertical stripes across the bbox of {@code shape}, clipped to the shape. */
    private static void stripesClipped(PdfCanvas c, double[][] shape, double cx, double cy,
                                       double S, int count, int color, boolean upperOnly)
            throws IOException {
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (double[] p : shape) {
            minX = Math.min(minX, p[0]);
            maxX = Math.max(maxX, p[0]);
            minY = Math.min(minY, p[1]);
            maxY = Math.max(maxY, p[1]);
        }
        c.saveState();
        c.clipPolygon(shape);
        c.fillRgb(color >> 16, (color >> 8) & 0xFF, color & 0xFF);
        double stripeW = (maxX - minX) / (count * 2.0);
        for (int i = 0; i < count; i++) {
            double x = minX + (2 * i + 1) * stripeW;
            c.fillRect(x, minY, stripeW, maxY - minY);
        }
        c.restoreState();
        c.fillBlack();
    }

    /** 45° black hatch inside the given polygon (thermal half-fill fallback). */
    private static void hatchClipped(PdfCanvas c, double[][] shape, double S)
            throws IOException {
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (double[] p : shape) {
            minX = Math.min(minX, p[0]);
            maxX = Math.max(maxX, p[0]);
            minY = Math.min(minY, p[1]);
            maxY = Math.max(maxY, p[1]);
        }
        c.saveState();
        c.clipPolygon(shape);
        c.strokeRgb(0, 0, 0);
        double gap = Math.max(1.5, LabelUnits.dotMm(203) * 8);
        double diagonal = Math.hypot(maxX - minX, maxY - minY);
        for (double o = -diagonal; o <= diagonal; o += gap) {
            double shift = o * Math.sqrt(2);
            double ax = minX - 1;
            double ay = ax + shift;
            double bx = maxX + 1;
            double by = bx + shift;
            c.line(ax, ay, bx, by, Math.max(0.4, 0.01 * S));
        }
        c.strokeBlack();
        c.restoreState();
    }

    // ------------------------------------------------------------ text parts

    public static String numeral(HazmatSpec spec, HazardClass hc) {
        if (hc == HazardClass.C1) {
            String div = spec.getDivision() == null ? "" : spec.getDivision().trim();
            if (div.isEmpty()) {
                div = "1.4";
            } else if (div.matches("\\d")) {
                div = "1." + div;
            }
            String group = spec.getCompatGroup() == null ? ""
                    : spec.getCompatGroup().trim().toUpperCase(Locale.ENGLISH);
            return div + group;
        }
        return hc.numeral();
    }

    private static String middleText(HazmatSpec spec, HazardClass hc) {
        if (hc == HazardClass.C6_2) {
            return "INFECTIOUS SUBSTANCE – In case of damage or leakage notify "
                    + "public health authority";
        }
        if (hc == HazardClass.C7) {
            return "RADIOACTIVE";
        }
        if (hc == HazardClass.C1 && spec.getDivision() != null && !spec.getDivision().isBlank()) {
            return "EXPLOSIVES " + spec.getDivision().trim().toUpperCase(Locale.ENGLISH);
        }
        return hc.className();
    }

    // ------------------------------------------------------- colours / ink

    private static int upperColor(HazardClass hc, boolean thermal) {
        if (thermal) {
            return 0xFFFFFF;
        }
        return switch (hc.pattern()) {
            case HALF_RED_BOTTOM, HALF_BLACK_BOTTOM, STRIPES_RED7, STRIPES_BLACK7_UPPER,
                    SOLID -> hc.rgb()[0] << 16 | hc.rgb()[1] << 8 | hc.rgb()[2];
            case HALF_TOP_RED_BOTTOM_YELLOW -> rgb("E4002B");
            case HALF_YELLOW_TOP_WHITE_BOTTOM -> rgb("FFF200");
        };
    }

    private static int lowerColor(HazardClass hc, boolean thermal) {
        if (thermal) {
            return 0xFFFFFF;
        }
        return switch (hc.pattern()) {
            case HALF_RED_BOTTOM -> rgb("E4002B");
            case HALF_TOP_RED_BOTTOM_YELLOW -> rgb("FFEB00");
            case HALF_BLACK_BOTTOM -> 0x000000;
            case HALF_YELLOW_TOP_WHITE_BOTTOM, STRIPES_RED7, STRIPES_BLACK7_UPPER -> 0xFFFFFF;
            case SOLID -> hc.rgb()[0] << 16 | hc.rgb()[1] << 8 | hc.rgb()[2];
        };
    }

    private static int[] pick(int bg, HazardClass hc, boolean thermal, HazmatSpec spec,
                              boolean useUpper) {
        if (thermal) {
            return new int[]{0, 0, 0};
        }
        if (!"AUTO".equals(hc.textColor())) {
            return "WHITE".equals(hc.textColor()) ? new int[]{255, 255, 255} : new int[]{0, 0, 0};
        }
        return luminance(bg) > 140 ? new int[]{0, 0, 0} : new int[]{255, 255, 255};
    }

    private static int[] resolveInk(PdfCanvas c, HazardClass hc, boolean thermal, HazmatSpec spec) {
        if (thermal) {
            return new int[]{0, 0, 0};
        }
        if (hc.pattern() == HazardClass.Pattern.SOLID) {
            if ("WHITE".equals(hc.textColor())) {
                return new int[]{255, 255, 255};
            }
            return new int[]{0, 0, 0};
        }
        int bg = upperColor(hc, false);
        return luminance(bg) > 140 ? new int[]{0, 0, 0} : new int[]{255, 255, 255};
    }

    private static int luminance(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return (int) (0.299 * r + 0.587 * g + 0.114 * b);
    }

    private static int rgb(String hex) {
        return Integer.parseInt(hex, 16);
    }

    // ---------------------------------------------------------- radioactive

    private static void drawRadiationBoxes(PdfCanvas c, HazmatSpec spec, double cx, double cy,
                                           double S, int[] ink) throws IOException {
        String cat = spec.getRadiationCategory() == null || spec.getRadiationCategory().isBlank()
                ? "I" : spec.getRadiationCategory().trim().toUpperCase(Locale.ENGLISH);
        String[] cats = {"I", "II", "III"};
        double box = 0.085 * S;
        double x = cx + 0.20 * S;
        double y0 = cy - 0.30 * S;
        for (int i = 0; i < 3; i++) {
            double y = y0 + i * (box + 0.02 * S);
            boolean on = cats[i].equals(cat);
            if (on) {
                c.fillRect(x, y, box, box);
            }
            c.strokeRect(x, y, box, box, Math.max(0.01 * S, 0.5));
            double fs = LabelUnits.mmToPt(box * 0.6) / 0.72;
            if (on) {
                c.fillWhite();
                c.text(cats[i], x + box / 2, y + box * 0.18, Math.max(6, fs), true,
                        PdfCanvas.Align.CENTER);
                c.fillBlack();
            } else {
                c.text(cats[i], x + box / 2, y + box * 0.18, Math.max(6, fs), true,
                        PdfCanvas.Align.CENTER);
            }
        }
    }

    // ------------------------------------------------------- marking block

    private void drawMarkingBlock(PdfCanvas c, HazmatSpec spec, double top, double w, double h)
            throws IOException {
        double m = LabelSizes.SAFE_MARGIN_MM;
        double cw = w - 2 * m;
        double y = top + 0.5;

        // row 1: UN number + PG/ERG
        String un = spec.getUnNumber() == null ? "" : spec.getUnNumber()
                .toUpperCase(Locale.ENGLISH);
        if (!un.isEmpty()) {
            double size = c.fitFontSize(un, cw * 0.5, 20, 14, true);
            c.text(un, m, y, size, true, PdfCanvas.Align.LEFT);
        }
        String right = joinNonBlank(
                nv(spec.getPackingGroup()).isBlank() ? "" : "PG: " + spec.getPackingGroup(),
                nv(spec.getErgGuide()).isBlank() ? "" : "ERG: " + spec.getErgGuide());
        if (!right.isEmpty()) {
            double size = c.fitFontSize(right, cw * 0.48, 10, 7, true);
            c.text(right, w - m, y + 2, size, true, PdfCanvas.Align.RIGHT);
        }
        y += LabelUnits.ptToMm(20 * 0.9) + 1;

        // row 2: proper shipping name (+ technical name)
        String psn = nv(spec.getProperShippingName()).toUpperCase(Locale.ENGLISH);
        if (!psn.isEmpty()) {
            y = c.drawParagraph(psn, m, y, cw, 11, 7, 2, true, PdfCanvas.Align.LEFT);
        }
        String tech = nv(spec.getTechnicalName());
        if (!tech.isEmpty()) {
            y = c.drawParagraph("(" + tech + ")", m, y + 0.3, cw, 8, 6, 2, false,
                    PdfCanvas.Align.LEFT);
        }

        // row 3: net qty / packages / transport
        java.util.List<String> factParts = new java.util.ArrayList<>();
        if (!nv(spec.getNetQuantity()).isBlank()) {
            factParts.add(spec.getNetQuantity().trim());
        }
        if (spec.getPackageCount() != null) {
            factParts.add("in " + spec.getPackageCount() + " PKG(S)");
        }
        if (spec.getTransportMode() != null) {
            factParts.add(spec.getTransportMode().name());
        }
        if (Boolean.TRUE.equals(spec.getMarinePollutant())) {
            factParts.add("MARINE POLLUTANT");
        }
        if (Boolean.TRUE.equals(spec.getOverpack())) {
            factParts.add("OVERPACK");
        }
        String facts = String.join("  ", factParts);
        if (!facts.isBlank()) {
            double size = c.fitFontSize(facts, cw, 8.5, 6, true);
            c.text(facts, m, y + 0.5, size, true, PdfCanvas.Align.LEFT);
            y += LabelUnits.ptToMm(size * LabelSizes.LINE_HEIGHT) + 0.6;
        }

        // row 4: consignor / consignee two columns
        double colW = cw / 2 - 1.5;
        double colTop = y;
        y = column(c, "CONSIGNOR:", spec.getConsignor(), m, y, colTop + 14, colW);
        double y2 = column(c, "CONSIGNEE:", spec.getConsignee(), m + cw / 2 + 1.5, colTop,
                colTop + 14, colW);
        y = Math.max(y, y2);

        // row 5: emergency phone (bold)
        String phone = nv(spec.getEmergencyPhone());
        if (!phone.isEmpty()) {
            String line = "24-HOUR EMERGENCY: " + phone;
            double size = c.fitFontSize(line, cw, 9, 6.5, true);
            c.text(line, m, y + 0.4, size, true, PdfCanvas.Align.LEFT);
            y += LabelUnits.ptToMm(size * LabelSizes.LINE_HEIGHT);
        }

        // footer
        drawFooter(c, spec, top + h - 3.5, w);
        if (spec.isThermal() && y > top + h - 7) {
            // keep footer clear — nothing else to do; text fitting above already bounded
        }
    }

    private double column(PdfCanvas c, String caption, LabelAddress addr, double x, double y,
                          double bottom, double w) throws IOException {
        if (addr == null) {
            return y;
        }
        c.text(caption, x, y, 6, true, PdfCanvas.Align.LEFT);
        y += LabelUnits.ptToMm(6 * LabelSizes.LINE_HEIGHT);
        for (String line : addr.nonBlankLines()) {
            if (y + 2.4 > bottom) {
                break;
            }
            double size = c.fitFontSize(line, w, 6.5, 6, false);
            c.text(line, x, y, size, false, PdfCanvas.Align.LEFT);
            y += LabelUnits.ptToMm(size * LabelSizes.LINE_HEIGHT);
        }
        return y;
    }

    // --------------------------------------------------------------- footer

    static void drawFooter(PdfCanvas c, HazmatSpec spec, double y, double w) throws IOException {
        drawFooter(c, spec, y, w, LabelSizes.SAFE_MARGIN_MM);
    }

    /** Footer with a custom side margin (marks keep it clear of the hatch band). */
    static void drawFooter(PdfCanvas c, HazmatSpec spec, double y, double w, double m)
            throws IOException {
        String left = "Generated by " + spec.footerIdentity() + " · © " + java.time.Year.now().getValue();

        double size = c.fitFontSize(left, w - 2 * m - w * 0.28, 6, 5.5, false);
        c.text(left, m, y, size, false, PdfCanvas.Align.LEFT);
        String right = nv(spec.getLabelNumber());
        if (spec.getPrintTimestamp() != null) {
            right = (right + "  " + TS_FMT.format(spec.getPrintTimestamp())).trim();
        }
        if (!right.isEmpty()) {
            double rs = c.fitFontSize(right, w - 2 * m - w * 0.6, 6, 5.5, false);
            c.text(right, w - m, y, rs, false, PdfCanvas.Align.RIGHT);
        }
    }

    private void drawColorCaption(PdfCanvas c, HazmatSpec spec, double cx, double y, double w)
            throws IOException {
        HazardClass hc = HazardClass.fromKey(spec.getHazardClass());
        String name = colorName(hc.hex());
        String line = "COLOR: " + name;
        double size = c.fitFontSize(line, w * 0.5, 7, 6, true);
        c.text(line, cx, y, size, true, PdfCanvas.Align.CENTER);
    }

    static String colorName(String hex) {
        return switch (hex.toUpperCase(Locale.ENGLISH)) {
            case "E4002B" -> "RED";
            case "00A651" -> "GREEN";
            case "0072BC" -> "BLUE";
            case "FFD700", "FFEB00", "FFF200" -> "YELLOW";
            case "FF6600" -> "ORANGE";
            case "FFFFFF" -> "WHITE";
            default -> hex.toUpperCase(Locale.ENGLISH);
        };
    }

    // ------------------------------------------------------------- placard

    private void drawPlacard(PdfCanvas c, HazmatSpec spec, double w, double h) throws Exception {
        c.beginSheetPage(w, h); // exact A4, no snap
        double s = 170;
        double cx = w / 2;
        double cy = s / 2 + 14;
        drawClassDiamond(c, spec, cx, cy, s);

        // orange UN ID panel (scaled from 300x120 to fit A4 width)
        double panelW = w - 30;
        double panelH = panelW * 120.0 / 300.0;
        double py = cy + s / 2 + 12;
        c.fillRgb(255, 102, 0);
        c.fillRect(15, py, panelW, panelH);
        c.strokeBlack();
        c.strokeRect(15, py, panelW, panelH, 1.5);
        c.strokeRect(15 + 3, py + 3, panelW - 6, panelH - 6, 0.8);
        String un = nv(spec.getUnNumber()).toUpperCase(Locale.ENGLISH);
        double unSize = c.fitFontSize(un, panelW * 0.8, 48, 24, true);
        c.text(un, w / 2, py + (panelH - LabelUnits.ptToMm(unSize * 0.72)) / 2, unSize, true,
                PdfCanvas.Align.CENTER);

        double fy = py + panelH + 4;
        c.text("PLACARD — 250 mm class diamond with UN ID panel", w / 2, fy, 7, false,
                PdfCanvas.Align.CENTER);
        drawFooter(c, spec, h - 8, w);
    }

    // ------------------------------------------------------------- geometry

    public static double[][] outerPts(double cx, double cy, double S) {
        double r = S / 2;
        return new double[][]{{cx, cy - r}, {cx + r, cy}, {cx, cy + r}, {cx - r, cy}};
    }

    /** Upper half of the diamond: top vertex → right vertex → centre → left vertex. */
    static double[][] upperHalf(double cx, double cy, double[][] outer) {
        return new double[][]{outer[0], outer[1], {cx, cy}, outer[3]};
    }

    public static double[][] innerPts(double cx, double cy, double S) {
        // perpendicular offset 0.05*S from each edge => vertex inset 0.05*S*sqrt(2)
        double r = S / 2 - 0.05 * S * Math.sqrt(2);
        return new double[][]{{cx, cy - r}, {cx + r, cy}, {cx, cy + r}, {cx - r, cy}};
    }

    static String nv(String s) {
        return s == null ? "" : s;
    }

    private static String joinNonBlank(String a, String b) {
        StringBuilder sb = new StringBuilder();
        if (a != null && !a.isBlank()) {
            sb.append(a.trim());
        }
        if (b != null && !b.isBlank()) {
            if (sb.length() > 0) {
                sb.append("   ");
            }
            sb.append(b.trim());
        }
        return sb.toString();
    }
}
