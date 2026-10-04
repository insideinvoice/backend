package com.insideinvoice.labels.render;

import com.insideinvoice.labels.enums.LabelPreset;
import com.insideinvoice.labels.renderer.PdfCanvas;
import com.insideinvoice.labels.util.BarcodeUtils;
import com.insideinvoice.labels.util.LabelUnits;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * "FNSKU 30-up" — Avery 5167 letter sheet: 3x10 cells of 66.7 x 25.4 mm, each
 * cell a compact FNSKU barcode label (product title, condition chip, FNSKU
 * Code 128 + HRT, SKU line). One instance per (item x copies).
 */
public class Fnsku30UpRenderer extends StandardCarrierRenderer {

    @Override
    public LabelPreset preset() {
        return LabelPreset.FNSKU_30UP;
    }

    @Override
    protected String sizeKey(ShippingLabelSpec spec) {
        String key = spec.size();
        return LabelSizes.isSheet(key) ? key : "letter-30up";
    }

    @Override
    protected int instanceCount(ShippingLabelSpec spec) {
        List<ShippingLabelSpec.FnskuItem> items = spec.getFnskuItems();
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("FNSKU preset requires at least one product item");
        }
        int copies = spec.getFnskuCopies() == null || spec.getFnskuCopies() < 1
                ? 1 : Math.min(spec.getFnskuCopies(), 50);
        return Math.min(items.size() * copies, 90); // max 3 sheets
    }

    @Override
    protected void drawInstance(PdfCanvas canvas, ShippingLabelSpec spec, int index, int total,
                                double wMm, double hMm) throws IOException {
        List<ShippingLabelSpec.FnskuItem> items = spec.getFnskuItems();
        int copies = spec.getFnskuCopies() == null || spec.getFnskuCopies() < 1
                ? 1 : Math.min(spec.getFnskuCopies(), 50);
        ShippingLabelSpec.FnskuItem item = items.get((index - 1) / copies % items.size());

        double w = LabelSizes.FNSKU_CELL_W;
        double h = LabelSizes.FNSKU_CELL_H;
        double m = 2;

        if (spec.isBorder()) {
            canvas.strokeRect(0.5, 0.5, w - 1, h - 1, LabelSizes.KEYLINE_MM);
        }

        // row 1: title (left) + condition chip (right)
        double y = m + 0.4;
        double chipW = 0;
        String condition = upper(item.getCondition(), null);
        if (condition != null) {
            String label = condition.length() > 10 ? condition.substring(0, 10) : condition;
            double chipSize = 6.5;
            chipW = canvas.stringWidth(label, chipSize, true) + 3;
            canvas.fillBlack();
            canvas.fillRect(w - m - chipW, y - 0.5, chipW, LabelUnits.ptToMm(chipSize * 1.4));
            canvas.fillWhite();
            canvas.text(label, w - m - chipW / 2, y, chipSize, true, PdfCanvas.Align.CENTER);
            canvas.fillBlack();
        }
        double titleW = w - 2 * m - chipW - 2;
        String title = item.getTitle() == null ? "PRODUCT" : item.getTitle();
        double titleSize = canvas.fitFontSize(title, titleW, 7.5, 6, true);
        canvas.text(title, m, y, titleSize, true, PdfCanvas.Align.LEFT);
        y += LabelUnits.ptToMm(titleSize * LabelSizes.LINE_HEIGHT) + 0.8;

        // row 2: FNSKU barcode (falls back to title when no FNSKU)
        String payload = blank(item.getFnsku()) ? "TITLE:" + title : item.getFnsku();
        double barH = LabelSizes.FNSKU_BARCODE_H;
        double barBottom = canvas.barcodeLinear(BarcodeUtils.code128(payload),
                m, y, w - 2 * m, barH, 10, 2);
        y = barBottom + 0.6;

        // row 3: HRT
        String hrt = blank(item.getFnsku()) ? title : item.getFnsku();
        double hrtSize = canvas.fitFontSize(hrt, w - 2 * m, 7, 6, true);
        canvas.text(hrt, w / 2, y, hrtSize, true, PdfCanvas.Align.CENTER);
        y += LabelUnits.ptToMm(hrtSize * LabelSizes.LINE_HEIGHT) + 0.3;

        // row 4: identity + copy mark
        String sku = nv(spec.getLabelNumber());
        if (!sku.isEmpty()) {
            double s = canvas.fitFontSize(sku, w * 0.5, 6, 6, false);
            canvas.text(sku, m, y, s, false, PdfCanvas.Align.LEFT);
        }
        String copy = index + "/" + total;
        double cs = canvas.fitFontSize(copy, w * 0.3, 6, 6, false);
        canvas.text(copy, w - m, y, cs, false, PdfCanvas.Align.RIGHT);
    }
}
