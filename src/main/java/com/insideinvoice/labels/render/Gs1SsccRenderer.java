package com.insideinvoice.labels.render;

import com.insideinvoice.labels.enums.LabelPreset;
import com.insideinvoice.labels.renderer.PdfCanvas;
import com.insideinvoice.labels.util.BarcodeUtils;
import com.insideinvoice.labels.util.CheckDigits;
import com.insideinvoice.labels.util.LabelUnits;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * "GS1-128 SSCC Pallet/Carton Logistics Label": sequential SSCC-18 per carton with
 * the (00) AI in a GS1-128 hero barcode, human-readable AI lines per GS1 General
 * Specifications, and ship-from/ship-to areas labelled per the GS1 Logistic Label.
 */
public class Gs1SsccRenderer extends StandardCarrierRenderer {

    @Override
    public LabelPreset preset() {
        return LabelPreset.GS1_SSCC_4X6;
    }

    /** Sequential SSCC for carton {@code index} (1-based): serial incremented, check digit recomputed. */
    public static String ssccFor(ShippingLabelSpec spec, int index) {
        ShippingLabelSpec.Gs1Fields gs1 = spec.getGs1();
        if (gs1 == null || gs1.getSscc() == null || gs1.getSscc().isBlank()) {
            throw new IllegalArgumentException(
                    "GS1 preset requires an SSCC (17 digits) in the label data");
        }
        String base17 = CheckDigits.withSerialDelta(gs1.getSscc(), index - 1);
        return CheckDigits.sscc18(base17);
    }

    @Override
    protected String dmPayload(ShippingLabelSpec spec) {
        if (spec.getGs1() != null && spec.getGs1().getSscc() != null
                && !spec.getGs1().getSscc().isBlank()) {
            return "00" + ssccFor(spec, Math.max(spec.cartons(), 1));
        }
        return super.dmPayload(spec);
    }

    @Override
    protected void drawBarcodeZone(PdfCanvas canvas, ShippingLabelSpec spec, int index, int total,
                                   double z4, double wMm, double m, double cw, double z4h)
            throws IOException {
        String sscc = ssccFor(spec, index);
        String ai00 = "00" + sscc;

        double y = z4 + 0.8;
        boolean full = z4h >= LabelSizes.ZONE4_BARCODE_MIN_MM - 3;
        double reserve = (full ? 13 : 9) + aiLinesHeightMm(spec);
        double barH = clamp(z4h - 0.8 - reserve, 12.7, LabelSizes.BARCODE_H_MM);

        double barBottom = canvas.barcodeLinear(BarcodeUtils.gs1128(ai00), m, y, cw, barH, 10, 2);
        y = barBottom + 1.5;

        String hri = "(00) " + groupTracking(sscc);
        double hriSize = canvas.fitFontSize(hri, cw - 2, LabelSizes.T_BARCODE_TEXT, 7, true);
        canvas.text(hri, wMm / 2, y, hriSize, true, PdfCanvas.Align.CENTER);
        y += LabelUnits.ptToMm(hriSize * LabelSizes.LINE_HEIGHT) + 0.6;

        // AI information lines, two columns, 6 pt (GS1 General Specifications HRI style)
        List<String[]> rows = aiRows(spec);
        double rowH = LabelUnits.ptToMm(6.5 * LabelSizes.LINE_HEIGHT);
        double colW = cw / 2 - 1;
        for (String[] row : rows) {
            if (y + rowH > z4 + z4h) {
                break;
            }
            double s1 = canvas.fitFontSize(row[0], colW, 6.5, 6, false);
            canvas.text(row[0], m, y, s1, false, PdfCanvas.Align.LEFT);
            if (row.length > 1) {
                double s2 = canvas.fitFontSize(row[1], colW, 6.5, 6, false);
                canvas.text(row[1], m + cw / 2 + 1, y, s2, false, PdfCanvas.Align.LEFT);
            }
            y += rowH;
        }
    }

    private double aiLinesHeightMm(ShippingLabelSpec spec) {
        return aiRows(spec).size() * LabelUnits.ptToMm(6.5 * LabelSizes.LINE_HEIGHT);
    }

    private List<String[]> aiRows(ShippingLabelSpec spec) {
        List<String[]> rows = new ArrayList<>();
        ShippingLabelSpec.Gs1Fields g = spec.getGs1();
        if (g == null) {
            return rows;
        }
        String gtin = blank(g.getGtin()) ? null : "(02) " + CheckDigits.gtin(
                g.getGtin().replaceAll("\\D", "").length() >= 13
                        ? g.getGtin().replaceAll("\\D", "").substring(0, 13)
                        : padGtin(g.getGtin().replaceAll("\\D", "")));
        addRow(rows, gtin, "(37) " + nv(g.getCount()));
        addRow(rows, blank(g.getBatchLot()) ? null : "(10) " + g.getBatchLot(),
                expiryOrProd(g));
        addRow(rows, blank(g.getPoNumber()) ? null : "(400) " + g.getPoNumber(),
                blank(g.getPostalCode()) ? null : "(420) " + g.getPostalCode());
        return rows;
    }

    private String expiryOrProd(ShippingLabelSpec.Gs1Fields g) {
        if (!blank(g.getExpiryDate())) {
            return "(17) " + g.getExpiryDate();
        }
        if (!blank(g.getProductionDate())) {
            return "(11) " + g.getProductionDate();
        }
        return null;
    }

    private void addRow(List<String[]> rows, String a, String b) {
        if (a == null && b == null) {
            return;
        }
        if (a == null) {
            rows.add(new String[]{"", b});
        } else if (b == null) {
            rows.add(new String[]{a});
        } else {
            rows.add(new String[]{a, b});
        }
    }

    private String padGtin(String digits) {
        return "0".repeat(Math.max(0, 13 - digits.length())) + digits;
    }
}
