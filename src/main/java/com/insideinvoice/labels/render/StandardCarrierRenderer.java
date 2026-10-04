package com.insideinvoice.labels.render;

import com.insideinvoice.labels.enums.LabelPreset;
import com.insideinvoice.labels.renderer.PdfCanvas;
import com.insideinvoice.labels.util.BarcodeUtils;
import com.insideinvoice.labels.util.LabelUnits;
import com.google.zxing.common.BitMatrix;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * "Standard Carrier 4x6" — the Part B layout: header / ship-to / routing /
 * hero tracking barcode / footer zones separated by 0.5 mm rules. Smaller label
 * sizes fall back to a compact stack. Subclasses override zones for the other
 * presets.
 */
public class StandardCarrierRenderer extends AbstractShippingRenderer {

    protected static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    @Override
    public LabelPreset preset() {
        return LabelPreset.STANDARD_CARRIER_4X6;
    }

    // --------------------------------------------------------------- hooks

    /** Extra black banner height drawn above the zones (Return label). */
    protected double bannerHeightMm() {
        return 0;
    }

    protected String shipToCaption() {
        return "SHIP TO:";
    }

    /** Amazon FBA: emphasise the FC code in the ship-to block. */
    protected boolean emphasizeFc() {
        return false;
    }

    // ------------------------------------------------------------- drawing

    @Override
    protected void drawInstance(PdfCanvas canvas, ShippingLabelSpec spec, int index, int total,
                                double wMm, double hMm) throws IOException {
        double m = LabelSizes.SAFE_MARGIN_MM;
        if (spec.isBorder()) {
            canvas.strokeRect(0.5, 0.5, wMm - 1, hMm - 1, LabelSizes.KEYLINE_MM);
        }
        double y = m;
        if (bannerHeightMm() > 0) {
            drawBanner(canvas, spec, y, wMm);
            y += bannerHeightMm() + LabelSizes.RULE_MM;
        }
        if (hMm >= LabelSizes.FULL_LAYOUT_MIN_HEIGHT_MM) {
            drawFull(canvas, spec, index, total, wMm, hMm, y);
        } else {
            drawCompact(canvas, spec, index, total, wMm, hMm, y);
        }
    }

    protected void drawBanner(PdfCanvas canvas, ShippingLabelSpec spec, double y, double wMm)
            throws IOException {
        double m = LabelSizes.SAFE_MARGIN_MM;
        double h = bannerHeightMm();
        canvas.fillBlack();
        canvas.fillRect(m, y, wMm - 2 * m, h);
        canvas.fillWhite();
        canvas.text("RETURN", wMm / 2, y + (h - LabelUnits.ptToMm(16)) / 2, 16, true,
                PdfCanvas.Align.CENTER);
        canvas.fillBlack();
    }

    /** Full zone layout (label height >= 140 mm). */
    protected void drawFull(PdfCanvas canvas, ShippingLabelSpec spec, int index, int total,
                            double wMm, double hMm, double yStart) throws IOException {
        double m = LabelSizes.SAFE_MARGIN_MM;
        double cw = wMm - 2 * m;
        double bottom = hMm - m;
        double footerH = LabelSizes.ZONE5_FOOTER_MM;
        double footerTop = bottom - footerH;
        boolean icons = wantHandlingIcons(spec);

        double z1 = yStart;
        double z2 = z1 + LabelSizes.ZONE1_HEADER_MM + LabelSizes.RULE_MM;
        double z3 = z2 + LabelSizes.ZONE2_SHIPTO_MM + LabelSizes.RULE_MM;
        double z4 = z3 + LabelSizes.ZONE3_ROUTING_MM + LabelSizes.RULE_MM;
        double z4Bottom = footerTop - LabelSizes.RULE_MM;
        if (icons) {
            z4Bottom = footerTop - LabelSizes.RULE_MM
                    - LabelSizes.HANDLING_ICON_MM - LabelSizes.RULE_MM;
        }
        double z4h = z4Bottom - z4;
        if (z4h < LabelSizes.ZONE4_BARCODE_MIN_MM) {
            // shave the ship-to zone (approximate per spec) to restore the hero zone
            double deficit = Math.min(LabelSizes.ZONE4_BARCODE_MIN_MM - z4h, 4);
            z3 -= deficit;
            z4 -= deficit;
            z4h = z4Bottom - z4;
            icons = icons && z4h >= LabelSizes.ZONE4_BARCODE_MIN_MM;
            if (!icons) {
                z4Bottom = footerTop - LabelSizes.RULE_MM;
                z4h = z4Bottom - z4;
            }
        }

        drawHeaderZone(canvas, spec, index, total, z1, wMm, m, cw);
        rule(canvas, z1 + LabelSizes.ZONE1_HEADER_MM, m, wMm);
        drawShipToZone(canvas, spec, z2, wMm, m, cw, LabelSizes.ZONE2_SHIPTO_MM);
        rule(canvas, z2 + LabelSizes.ZONE2_SHIPTO_MM, m, wMm);
        drawRoutingZone(canvas, spec, z3, wMm, m, cw, LabelSizes.ZONE3_ROUTING_MM);
        rule(canvas, z3 + LabelSizes.ZONE3_ROUTING_MM, m, wMm);
        drawBarcodeZone(canvas, spec, index, total, z4, wMm, m, cw, z4h);
        if (icons) {
            double iconsTop = z4Bottom + LabelSizes.RULE_MM;
            rule(canvas, z4Bottom, m, wMm);
            drawHandlingIcons(canvas, spec, iconsTop, wMm);
        }
        rule(canvas, footerTop - LabelSizes.RULE_MM, m, wMm);
        drawFooterZone(canvas, spec, index, total, footerTop, wMm, m, cw);
    }

    /** Compact stack for 4x4 / 4x3 / 100x100 style sizes (no routing zone). */
    protected void drawCompact(PdfCanvas canvas, ShippingLabelSpec spec, int index, int total,
                               double wMm, double hMm, double yStart) throws IOException {
        double m = LabelSizes.SAFE_MARGIN_MM;
        double cw = wMm - 2 * m;
        double bottom = hMm - m;
        double footerH = LabelSizes.ZONE5_FOOTER_MM;
        double footerTop = bottom - footerH;

        double z1 = yStart;
        double z2 = z1 + LabelSizes.ZONE1_HEADER_MM + LabelSizes.RULE_MM;
        double avail = footerTop - LabelSizes.RULE_MM - z2;
        double shipH = Math.min(30, Math.max(16, avail * 0.45));
        double z4 = z2 + shipH + LabelSizes.RULE_MM;
        double z4h = footerTop - LabelSizes.RULE_MM - z4;
        if (z4h < 18) {
            shipH = Math.max(12, shipH - (18 - z4h));
            z4 = z2 + shipH + LabelSizes.RULE_MM;
            z4h = footerTop - LabelSizes.RULE_MM - z4;
        }

        drawHeaderZone(canvas, spec, index, total, z1, wMm, m, cw);
        rule(canvas, z1 + LabelSizes.ZONE1_HEADER_MM, m, wMm);
        drawShipToZone(canvas, spec, z2, wMm, m, cw, shipH);
        rule(canvas, z2 + shipH, m, wMm);
        drawBarcodeZone(canvas, spec, index, total, z4, wMm, m, cw, z4h);
        rule(canvas, footerTop - LabelSizes.RULE_MM, m, wMm);
        drawFooterZone(canvas, spec, index, total, footerTop, wMm, m, cw);
    }

    // ------------------------------------------------------------ zone 1

    protected void drawHeaderZone(PdfCanvas canvas, ShippingLabelSpec spec, int index, int total,
                                  double z1, double wMm, double m, double cw) throws IOException {
        double rightW = LabelSizes.HEADER_RIGHT_W_MM;
        double sepX = m + cw - rightW - 1;
        canvas.line(sepX - 0.75, z1 + 0.5, sepX - 0.75, z1 + LabelSizes.ZONE1_HEADER_MM - 0.5,
                LabelSizes.KEYLINE_MM);

        double leftW = sepX - m - 1.5;
        double y = z1 + 0.6;
        canvas.text("FROM:", m, y, LabelSizes.T_CAPTION, true, PdfCanvas.Align.LEFT);
        y += LabelUnits.ptToMm(LabelSizes.T_CAPTION * LabelSizes.LINE_HEIGHT) + 0.3;
        double budgetBottom = z1 + LabelSizes.ZONE1_HEADER_MM - 0.5;
        y = drawAddressLines(canvas, spec.getShipFrom(), m, y, leftW, budgetBottom,
                LabelSizes.T_SHIP_FROM);

        double rx = m + cw - rightW;
        double ry = z1 + 0.6;
        String carrier = upper(spec.getCarrier(), spec.serviceOrDefault());
        double carrierSize = canvas.fitFontSize(carrier, rightW - 2, 10, 7, true);
        canvas.text(carrier, rx, ry, carrierSize, true, PdfCanvas.Align.LEFT);
        ry += LabelUnits.ptToMm(carrierSize * LabelSizes.LINE_HEIGHT) + 0.4;
        String shipDate = spec.getShipDate() == null ? "" : "SHIP DATE: "
                + DATE_FMT.format(spec.getShipDate()).toUpperCase(Locale.ENGLISH);
        String weight = blank(spec.getWeightKg()) ? "" : "WT: " + spec.getWeightKg() + " KG";
        String dims = blank(spec.getDimsCm()) ? "" : "DIM: " + spec.getDimsCm() + " CM";
        String carton = "CARTON " + index + " OF " + total;
        for (String line : new String[]{shipDate, weight, dims}) {
            if (!line.isEmpty()) {
                double size = canvas.fitFontSize(line, rightW - 2, 7, 6, false);
                canvas.text(line, rx, ry, size, false, PdfCanvas.Align.LEFT);
                ry += LabelUnits.ptToMm(size * LabelSizes.LINE_HEIGHT);
            }
        }
        double cartonSize = canvas.fitFontSize(carton, rightW - 2, 8.5, 6.5, true);
        canvas.text(carton, rx, ry, cartonSize, true, PdfCanvas.Align.LEFT);
    }

    // ------------------------------------------------------------ zone 2

    protected void drawShipToZone(PdfCanvas canvas, ShippingLabelSpec spec, double z2,
                                  double wMm, double m, double cw, double zoneH) throws IOException {
        double boxW = Math.min(LabelSizes.SERVICE_BOX_W_MM, cw / 2.4);
        double boxH = Math.min(LabelSizes.SERVICE_BOX_H_MM, zoneH - 4);
        boolean withBox = zoneH >= 22;
        double leftW = withBox ? cw - boxW - 4 : cw - 4;
        double x = m + (withBox ? 4 : 0); // leave room for the rotated caption

        canvas.rotatedText(shipToCaption(), m + 1.2, z2 + zoneH / 2, -90,
                LabelSizes.T_CAPTION, true);

        double y = z2 + 0.6;
        double budgetBottom = z2 + zoneH - 0.5;

        LabelAddress to = spec.getShipTo();
        String name = upper(to == null ? "" : to.getName(), "RECIPIENT");
        double nameSize = canvas.fitFontSize(name, leftW, LabelSizes.T_SHIP_TO_NAME, 10, true);
        canvas.text(name, x, y, nameSize, true, PdfCanvas.Align.LEFT);
        y += LabelUnits.ptToMm(nameSize * LabelSizes.LINE_HEIGHT);

        if (emphasizeFc() && spec.getFba() != null && !blank(spec.getFba().getShipToFc())) {
            String fc = "FC: " + spec.getFba().getShipToFc().toUpperCase(Locale.ENGLISH);
            double fcSize = canvas.fitFontSize(fc, leftW, 12, 9, true);
            canvas.text(fc, x, y, fcSize, true, PdfCanvas.Align.LEFT);
            y += LabelUnits.ptToMm(fcSize * LabelSizes.LINE_HEIGHT);
        } else if (to != null && !blank(to.getCompany())) {
            String company = to.getCompany().toUpperCase(Locale.ENGLISH);
            double cSize = canvas.fitFontSize(company, leftW, 10, 8, true);
            canvas.text(company, x, y, cSize, true, PdfCanvas.Align.LEFT);
            y += LabelUnits.ptToMm(cSize * LabelSizes.LINE_HEIGHT);
        }

        // reserve city / country+phone lines, address gets the rest (shrink + wrap)
        double reserve = LabelUnits.ptToMm(11.5 * LabelSizes.LINE_HEIGHT)
                + LabelUnits.ptToMm(9 * LabelSizes.LINE_HEIGHT) + 0.6;
        double addrBudget = budgetBottom - y - reserve;
        int maxAddrLines = addrBudget <= 0 ? 1
                : Math.max(1, (int) Math.floor(addrBudget
                        / LabelUnits.ptToMm(9.5 * LabelSizes.LINE_HEIGHT)));
        String addrText = to == null ? "" : to.joinedLines();
        if (!addrText.isEmpty()) {
            y = canvas.drawParagraph(addrText.toUpperCase(Locale.ENGLISH), x, y, leftW,
                    LabelSizes.T_SHIP_TO_ADDR, 7, maxAddrLines, true, PdfCanvas.Align.LEFT);
        }
        String cityLine = to == null ? "" : to.cityLine().toUpperCase(Locale.ENGLISH);
        if (!cityLine.isEmpty() && y < budgetBottom - 2) {
            double size = canvas.fitFontSize(cityLine, leftW, 11.5, 9, true);
            canvas.text(cityLine, x, y, size, true, PdfCanvas.Align.LEFT);
            y += LabelUnits.ptToMm(size * LabelSizes.LINE_HEIGHT) + 0.3;
        }
        if (to != null) {
            String tail = joinUp(to.getCountry(), blank(to.getPhone()) ? null : "PH: " + to.getPhone());
            if (!tail.isEmpty()) {
                double size = canvas.fitFontSize(tail, leftW, 9, 7.5, false);
                if (y + LabelUnits.ptToMm(size * LabelSizes.LINE_HEIGHT) <= budgetBottom + 0.8) {
                    canvas.text(tail, x, y, size, false, PdfCanvas.Align.LEFT);
                }
            }
        }

        if (withBox) {
            drawServiceBox(canvas, spec, m + cw - boxW, z2 + (zoneH - boxH) / 2, boxW, boxH);
        }
    }

    protected void drawServiceBox(PdfCanvas canvas, ShippingLabelSpec spec, double bx, double by,
                                  double boxW, double boxH) throws IOException {
        canvas.strokeRect(bx, by, boxW, boxH, LabelSizes.KEYLINE_MM);
        double invH = Math.min(boxH * 0.55, 18);
        canvas.fillBlack();
        canvas.fillRect(bx + 0.5, by + 0.5, boxW - 1, invH);
        canvas.fillWhite();
        String code = upper(spec.serviceOrDefault(), spec.getCarrier() == null ? "SVC"
                : spec.getCarrier());
        double codeSize = canvas.fitFontSize(code, boxW - 3, LabelSizes.T_SERVICE_BIG, 16, true);
        canvas.text(code, bx + boxW / 2, by + (invH - LabelUnits.ptToMm(codeSize)) / 2,
                codeSize, true, PdfCanvas.Align.CENTER);
        canvas.fillBlack();

        double y = by + invH + 1.2;
        String pin = spec.getShipTo() == null ? "" : spec.getShipTo().getPincode();
        String secondary = blank(pin)
                ? upper(spec.getShipTo() == null ? "" : spec.getShipTo().getCity(), "")
                : pin;
        if (!secondary.isEmpty()) {
            double size = canvas.fitFontSize(secondary, boxW - 3, 13, 9, true);
            canvas.text(secondary, bx + boxW / 2, y, size, true, PdfCanvas.Align.CENTER);
            y += LabelUnits.ptToMm(size * LabelSizes.LINE_HEIGHT) + 0.4;
        }
        String level = upper(spec.getServiceLevel(), null);
        if (level != null && y + 3 < by + boxH) {
            double size = canvas.fitFontSize(level, boxW - 3, 8, 6, false);
            canvas.text(level, bx + boxW / 2, y, size, false, PdfCanvas.Align.CENTER);
        }
    }

    // ------------------------------------------------------------ zone 3

    protected void drawRoutingZone(PdfCanvas canvas, ShippingLabelSpec spec, double z3,
                                   double wMm, double m, double cw, double zoneH) throws IOException {
        double dm = Math.min(LabelSizes.DATAMATRIX_MM, zoneH - 4);
        double dmX = m;
        double dmY = z3 + (zoneH - dm) / 2;
        String payload = dmPayload(spec);
        canvas.barcode2D(BarcodeUtils.dataMatrix(payload), dmX, dmY, dm, dm, 1);

        double rx = m + dm + 3;
        double rw = cw - dm - 3;
        double y = z3 + 1.2;
        canvas.text("TRACKING #:", rx, y, 6.5, true, PdfCanvas.Align.LEFT);
        y += LabelUnits.ptToMm(6.5 * LabelSizes.LINE_HEIGHT) + 0.3;
        String tracking = groupTracking(spec.barcodePayload());
        double tSize = canvas.fitFontSize(tracking, rw, LabelSizes.T_TRACKING, 7.5, true);
        canvas.text(tracking, rx, y, tSize, true, PdfCanvas.Align.LEFT);
        y += LabelUnits.ptToMm(tSize * LabelSizes.LINE_HEIGHT) + 0.5;

        String billing = billingText(spec);
        if (!billing.isEmpty()) {
            double size = canvas.fitFontSize(billing, rw, 8.5, 6.5, true);
            canvas.text(billing, rx, y, size, true, PdfCanvas.Align.LEFT);
            y += LabelUnits.ptToMm(size * LabelSizes.LINE_HEIGHT) + 0.3;
        }
        for (String ref : referenceLines(spec)) {
            if (y + 3 > z3 + zoneH) {
                break;
            }
            double size = canvas.fitFontSize(ref, rw, 7.5, 6, false);
            canvas.text(ref, rx, y, size, false, PdfCanvas.Align.LEFT);
            y += LabelUnits.ptToMm(size * LabelSizes.LINE_HEIGHT);
        }
    }

    // ------------------------------------------------------------ zone 4

    protected void drawBarcodeZone(PdfCanvas canvas, ShippingLabelSpec spec, int index, int total,
                                   double z4, double wMm, double m, double cw, double z4h)
            throws IOException {
        double y = z4 + 0.8;
        boolean fba = spec.getFba() != null;
        boolean full = z4h >= LabelSizes.ZONE4_BARCODE_MIN_MM - 3;
        double reserve = (full ? 8.5 : 4.5) + (fba ? 16.5 : 0);
        double barH = clamp(z4h - 0.8 - reserve, 12.7, LabelSizes.BARCODE_H_MM);
        if (barH > z4h - 4) {
            barH = Math.max(9, z4h - 4);
        }

        String payload = spec.barcodePayload();
        BitMatrix matrix = BarcodeUtils.code128(payload);
        double barBottom = canvas.barcodeLinear(matrix, m, y, cw, barH, 10, 2);
        y = barBottom + 1.5;

        String hrt = groupTracking(payload);
        double hrtSize = canvas.fitFontSize(hrt, cw - 2, LabelSizes.T_BARCODE_TEXT, 7, true);
        canvas.text(hrt, wMm / 2, y, hrtSize, true, PdfCanvas.Align.CENTER);
        y += LabelUnits.ptToMm(hrtSize * LabelSizes.LINE_HEIGHT) + 0.5;

        if (full && !blank(spec.getTrackingNumber()) && y + 4 <= z4 + z4h) {
            String line = "TRACKING #: " + hrt;
            double size = canvas.fitFontSize(line, cw, LabelSizes.T_TRACKING, 7.5, true);
            if (y + LabelUnits.ptToMm(size * LabelSizes.LINE_HEIGHT) <= z4 + z4h) {
                canvas.text(line, wMm / 2, y, size, true, PdfCanvas.Align.CENTER);
                y += LabelUnits.ptToMm(size * LabelSizes.LINE_HEIGHT);
            }
        }
        if (fba) {
            drawFbaBlock(canvas, spec.getFba(), m, y + 0.5, cw,
                    z4 + z4h - (y + 0.5));
        }
    }

    protected void drawFbaBlock(PdfCanvas canvas, ShippingLabelSpec.FbaFields fba,
                                double x, double y, double w, double h) throws IOException {
        if (h < 6) {
            return;
        }
        double colW = w / 2 - 1;
        double rowH = LabelUnits.ptToMm(7 * LabelSizes.LINE_HEIGHT);
        String left1 = "FBA SHIPMENT ID: " + nv(fba.getShipmentId());
        String left2 = "FROM: " + nv(fba.getFromWarehouse());
        String left3 = "PRODUCT UNITS: " + (fba.getProductUnits() == null ? ""
                : fba.getProductUnits());
        String right1 = "SHIP TO: " + nv(fba.getShipToFc());
        String right2 = "CREATED: " + nv(fba.getCreatedDate());
        String right3 = "BOX ID: " + nv(fba.getBoxId());
        String[] left = {left1, left2, left3};
        String[] right = {right1, right2, right3};
        double yy = y;
        for (int i = 0; i < 3 && yy + rowH < y + h - 6; i++) {
            double size = 7;
            if (!left[i].endsWith(": ") && !left[i].endsWith(":")) {
                double s1 = canvas.fitFontSize(left[i], colW, size, 6, false);
                canvas.text(left[i], x, yy, s1, false, PdfCanvas.Align.LEFT);
            }
            if (!right[i].endsWith(": ") && !right[i].endsWith(":")) {
                double s2 = canvas.fitFontSize(right[i], colW, size, 6, i == 0);
                canvas.text(right[i], x + w / 2 + 1, yy, s2, i == 0, PdfCanvas.Align.LEFT);
            }
            yy += rowH;
        }
        double barH = Math.min(7, Math.max(4, y + h - yy - 2.5));
        if (!blank(fba.getBoxId()) && barH >= 4) {
            canvas.barcodeLinear(BarcodeUtils.code128(fba.getBoxId()), x, yy + 0.3, w, barH, 10, 2);
        }
    }

    // ------------------------------------------------------------ zone 5

    protected void drawFooterZone(PdfCanvas canvas, ShippingLabelSpec spec, int index, int total,
                                  double footerTop, double wMm, double m, double cw)
            throws IOException {
        double y = footerTop + 1.6;
        canvas.text("Generated by " + spec.footerIdentity(), m, y, LabelSizes.T_FOOTER, false,
                PdfCanvas.Align.LEFT);
        String right = nv(spec.getLabelNumber()) + "  " + index + "/" + total
                + "  " + timestamp(spec);
        double size = canvas.fitFontSize(right, cw * 0.6, LabelSizes.T_FOOTER, 6, false);
        canvas.text(right, wMm - m, y, size, false, PdfCanvas.Align.RIGHT);
        String carrier = upper(spec.getCarrier(), null);
        if (carrier != null) {
            double cs = canvas.fitFontSize(carrier, cw * 0.3, LabelSizes.T_FOOTER, 6, false);
            canvas.text(carrier, wMm / 2, y, cs, false, PdfCanvas.Align.CENTER);
        }
    }

    // ------------------------------------------------------- handling icons

    protected boolean wantHandlingIcons(ShippingLabelSpec spec) {
        return Boolean.TRUE.equals(spec.getThisWayUp()) || Boolean.TRUE.equals(spec.getFragile())
                || Boolean.TRUE.equals(spec.getKeepDry()) || Boolean.TRUE.equals(spec.getDoNotStack())
                || Boolean.TRUE.equals(spec.getHandleWithCare());
    }

    protected void drawHandlingIcons(PdfCanvas canvas, ShippingLabelSpec spec, double yTop,
                                     double wMm) throws IOException {
        double size = LabelSizes.HANDLING_ICON_MM;
        double gap = 2;
        int count = countIcons(spec);
        double totalW = count * size + (count - 1) * gap;
        double x = (wMm - totalW) / 2;
        if (Boolean.TRUE.equals(spec.getThisWayUp())) {
            drawUpIcon(canvas, x, yTop, size);
            x += size + gap;
        }
        if (Boolean.TRUE.equals(spec.getFragile())) {
            drawFragileIcon(canvas, x, yTop, size);
            x += size + gap;
        }
        if (Boolean.TRUE.equals(spec.getKeepDry())) {
            drawUmbrellaIcon(canvas, x, yTop, size);
            x += size + gap;
        }
        if (Boolean.TRUE.equals(spec.getDoNotStack())) {
            drawNoStackIcon(canvas, x, yTop, size);
            x += size + gap;
        }
        if (Boolean.TRUE.equals(spec.getHandleWithCare())) {
            drawCautionIcon(canvas, x, yTop, size);
        }
    }

    private int countIcons(ShippingLabelSpec spec) {
        int n = 0;
        n += Boolean.TRUE.equals(spec.getThisWayUp()) ? 1 : 0;
        n += Boolean.TRUE.equals(spec.getFragile()) ? 1 : 0;
        n += Boolean.TRUE.equals(spec.getKeepDry()) ? 1 : 0;
        n += Boolean.TRUE.equals(spec.getDoNotStack()) ? 1 : 0;
        n += Boolean.TRUE.equals(spec.getHandleWithCare()) ? 1 : 0;
        return Math.max(n, 1);
    }

    private void drawUpIcon(PdfCanvas c, double x, double y, double s) throws IOException {
        c.strokeRect(x, y, s, s, LabelSizes.KEYLINE_MM);
        for (double dx : new double[]{s * 0.3, s * 0.7}) {
            double x0 = x + dx;
            c.line(x0, y + s * 0.75, x0, y + s * 0.3, 0.6);
            c.line(x0 - s * 0.12, y + s * 0.45, x0, y + s * 0.3, 0.6);
            c.line(x0 + s * 0.12, y + s * 0.45, x0, y + s * 0.3, 0.6);
        }
    }

    private void drawFragileIcon(PdfCanvas c, double x, double y, double s) throws IOException {
        c.strokeRect(x, y, s, s, LabelSizes.KEYLINE_MM);
        // wine glass: bowl (trapezoid), stem, base
        double cx = x + s / 2;
        double[][] bowl = {{cx - s * 0.22, y + s * 0.18}, {cx + s * 0.22, y + s * 0.18},
                {cx + s * 0.1, y + s * 0.5}, {cx - s * 0.1, y + s * 0.5}};
        c.strokePolygon(bowl, 0.6);
        c.line(cx, y + s * 0.5, cx, y + s * 0.78, 0.6);
        c.line(cx - s * 0.15, y + s * 0.82, cx + s * 0.15, y + s * 0.82, 0.6);
    }

    private void drawUmbrellaIcon(PdfCanvas c, double x, double y, double s) throws IOException {
        c.strokeRect(x, y, s, s, LabelSizes.KEYLINE_MM);
        double cx = x + s / 2;
        double[][] canopy = {{cx - s * 0.3, y + s * 0.45}, {cx - s * 0.18, y + s * 0.26},
                {cx, y + s * 0.22}, {cx + s * 0.18, y + s * 0.26}, {cx + s * 0.3, y + s * 0.45}};
        c.strokePolygon(canopy, 0.6);
        c.line(cx, y + s * 0.22, cx, y + s * 0.7, 0.6);
        c.line(cx, y + s * 0.7, cx + s * 0.1, y + s * 0.78, 0.6);
    }

    private void drawNoStackIcon(PdfCanvas c, double x, double y, double s) throws IOException {
        c.strokeRect(x, y, s, s, LabelSizes.KEYLINE_MM);
        c.strokeRect(x + s * 0.22, y + s * 0.5, s * 0.56, s * 0.28, 0.5);
        c.strokeRect(x + s * 0.22, y + s * 0.2, s * 0.56, s * 0.28, 0.5);
        c.line(x + s * 0.15, y + s * 0.85, x + s * 0.85, y + s * 0.15, 0.7);
    }

    private void drawCautionIcon(PdfCanvas c, double x, double y, double s) throws IOException {
        c.strokeRect(x, y, s, s, LabelSizes.KEYLINE_MM);
        double[][] tri = {{x + s / 2, y + s * 0.18}, {x + s * 0.84, y + s * 0.78},
                {x + s * 0.16, y + s * 0.78}};
        c.strokePolygon(tri, 0.7);
        c.line(x + s / 2, y + s * 0.38, x + s / 2, y + s * 0.6, 0.7);
        c.line(x + s / 2, y + s * 0.67, x + s / 2, y + s * 0.69, 0.7);
    }

    // ------------------------------------------------------------- helpers

    protected void rule(PdfCanvas canvas, double y, double m, double wMm) throws IOException {
        canvas.line(m, y, wMm - m, y, LabelSizes.RULE_MM);
    }

    /** Draws address lines (name bold, rest regular) shrinking to fit the budget. */
    protected double drawAddressLines(PdfCanvas canvas, LabelAddress addr, double x, double y,
                                      double wMm, double budgetBottom, double startPt)
            throws IOException {
        if (addr == null) {
            return y;
        }
        List<String> lines = addr.nonBlankLines();
        for (int i = 0; i < lines.size(); i++) {
            if (y + 2.4 > budgetBottom) {
                break;
            }
            boolean bold = i == 0;
            String line = lines.get(i);
            double size = canvas.fitFontSize(line, wMm, startPt, 6, bold);
            if (y + LabelUnits.ptToMm(size * LabelSizes.LINE_HEIGHT) > budgetBottom + 0.6) {
                size = Math.max(6, size - 0.5);
            }
            canvas.text(line, x, y, size, bold, PdfCanvas.Align.LEFT);
            y += LabelUnits.ptToMm(size * LabelSizes.LINE_HEIGHT);
        }
        return y;
    }

    protected String dmPayload(ShippingLabelSpec spec) {
        LabelAddress to = spec.getShipTo();
        return String.join("|",
                to == null ? "" : nv(to.getPincode()),
                to == null ? "" : nv(to.getCountry()),
                spec.barcodePayload(),
                spec.serviceOrDefault());
    }

    protected String billingText(ShippingLabelSpec spec) {
        String type = spec.getBillingType();
        if (blank(type)) {
            return "";
        }
        return switch (type.toUpperCase(Locale.ENGLISH)) {
            case "COD" -> blank(spec.getCodAmount()) ? "COD"
                    : "COD ₹ " + spec.getCodAmount();
            case "THIRD_PARTY", "3RD PARTY", "BILL TO THIRD PARTY" -> "BILL TO THIRD PARTY";
            default -> "PREPAID";
        };
    }

    protected List<String> referenceLines(ShippingLabelSpec spec) {
        return java.util.Arrays.stream(new String[]{
                "INV: " + nv(spec.getInvoiceNo()),
                "PO: " + nv(spec.getPoNumber()),
                "REF: " + joinUp(spec.getRef1(), spec.getRef2())})
                .filter(l -> !l.endsWith(": ") && !l.endsWith(":"))
                .toList();
    }

    protected String timestamp(ShippingLabelSpec spec) {
        LocalDateTime ts = spec.getPrintTimestamp();
        if (ts == null) {
            return "";
        }
        return DATE_FMT.format(ts.toLocalDate()).toUpperCase(Locale.ENGLISH)
                + " " + String.format(Locale.ENGLISH, "%02d:%02d", ts.getHour(), ts.getMinute());
    }

    public static String groupTracking(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String clean = value.replace(" ", "");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < clean.length(); i++) {
            if (i > 0 && i % 4 == 0) {
                sb.append(' ');
            }
            sb.append(clean.charAt(i));
        }
        return sb.toString();
    }

    protected static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    protected static String upper(String s, String fallback) {
        if (s == null || s.isBlank()) {
            return fallback == null ? null : fallback.toUpperCase(Locale.ENGLISH);
        }
        return s.toUpperCase(Locale.ENGLISH);
    }

    protected static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    protected static String nv(String s) {
        return s == null ? "" : s;
    }

    protected static String joinUp(String a, String b) {
        StringBuilder sb = new StringBuilder();
        if (!blank(a)) {
            sb.append(a.toUpperCase(Locale.ENGLISH));
        }
        if (!blank(b)) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(b.toUpperCase(Locale.ENGLISH));
        }
        return sb.toString();
    }
}
