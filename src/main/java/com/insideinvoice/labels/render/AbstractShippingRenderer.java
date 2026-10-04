package com.insideinvoice.labels.render;

import com.insideinvoice.labels.renderer.PdfCanvas;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Page/cell orchestration shared by all shipping presets: label sizes render one
 * page per carton (or per FNSKU item), sheet sizes render N-up cells with
 * {@link PdfCanvas#setCell} offsets.
 */
public abstract class AbstractShippingRenderer implements LabelRenderer {

    @Override
    public byte[] render(ShippingLabelSpec spec) throws IOException {
        Objects.requireNonNull(spec, "spec");
        try (PdfCanvas canvas = new PdfCanvas(spec.effectiveDpi())) {
            drawAll(canvas, spec);
            return canvas.save();
        }
    }

    /** Number of label instances this spec produces (cartons, or FNSKU items). */
    protected int instanceCount(ShippingLabelSpec spec) {
        return spec.cartons();
    }

    /** Draws instance {@code index} (1-based) into the given cell/page box. */
    protected abstract void drawInstance(PdfCanvas canvas, ShippingLabelSpec spec,
                                         int index, int total,
                                         double boxWMm, double boxHMm) throws IOException;

    /** Size key to render at; presets may force their sheet (FNSKU 30-up). */
    protected String sizeKey(ShippingLabelSpec spec) {
        return spec.size();
    }

    private void drawAll(PdfCanvas canvas, ShippingLabelSpec spec) throws IOException {
        String sizeKey = sizeKey(spec);
        LabelSizes.Size size = LabelSizes.require(sizeKey);
        int total = instanceCount(spec);

        if (LabelSizes.isSheet(sizeKey)) {
            List<LabelSizes.Cell> cells = LabelSizes.cells(sizeKey);
            int index = 1;
            while (index <= total) {
                canvas.beginSheetPage(size.wMm(), size.hMm());
                for (LabelSizes.Cell cell : cells) {
                    if (index > total) {
                        break;
                    }
                    canvas.setCell(cell.xMm(), cell.yMm());
                    drawInstance(canvas, spec, index, total, cell.wMm(), cell.hMm());
                    index++;
                }
                canvas.clearCell();
            }
        } else {
            for (int index = 1; index <= total; index++) {
                canvas.beginLabelPage(size.wMm(), size.hMm());
                drawInstance(canvas, spec, index, total, size.wMm(), size.hMm());
            }
        }
    }
}
