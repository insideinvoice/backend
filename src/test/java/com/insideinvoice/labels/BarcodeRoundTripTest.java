package com.insideinvoice.labels;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.common.BitMatrix;
import com.insideinvoice.labels.renderer.PdfCanvas;
import com.insideinvoice.labels.util.BarcodeUtils;
import com.insideinvoice.labels.util.CheckDigits;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip proof: every barcode we draw into a PDF is rasterized at 300 DPI and decoded
 * back with ZXing — the decoded value must equal the input.
 */
class BarcodeRoundTripTest {

    private static String decodeFirst(byte[] pdf, int pageIndex) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            float scale = 300f / 72f;
            BufferedImage image = renderer.renderImage(pageIndex, scale);
            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
            hints.put(DecodeHintType.POSSIBLE_FORMATS,
                    java.util.Arrays.asList(com.google.zxing.BarcodeFormat.CODE_128,
                            com.google.zxing.BarcodeFormat.DATA_MATRIX,
                            com.google.zxing.BarcodeFormat.QR_CODE,
                            com.google.zxing.BarcodeFormat.UPC_A,
                            com.google.zxing.BarcodeFormat.CODE_39));
            try {
                return new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(
                        new BufferedImageLuminanceSource(image))), hints).getText();
            } catch (Exception fullPage) {
                // ZXing's DataMatrix detector can miss a symbol on a mostly-blank page;
                // scanners crop to the viewfinder region, so retry on the symbol bbox.
                try {
                    return decodeCrop(image, hints);
                } catch (Exception cropFail) {
                    ByteArrayOutputStream diag = new ByteArrayOutputStream();
                    ImageIO.write(image, "png", diag);
                    cropFail.addSuppressed(fullPage);
                    throw new AssertionError("ZXing failed to decode page " + pageIndex
                            + " (" + diag.size() + " byte png)", cropFail);
                }
            }
        }
    }

    private static byte[] pageWith(DrawOp draw) throws IOException {
        try (PdfCanvas canvas = new PdfCanvas(203)) {
            canvas.beginLabelPage(101.6, 152.4);
            draw.apply(canvas);
            return canvas.save();
        }
    }

    private static String decodeCrop(BufferedImage image, Map<DecodeHintType, Object> hints) throws Exception {
        int minX = image.getWidth(), minY = image.getHeight(), maxX = 0, maxY = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0xFF) < 128) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX <= minX || maxY <= minY) {
            throw new IllegalStateException("no dark pixels");
        }
        // ~4-module margin around the symbol bbox
        int module = Math.max(6, Math.max(maxX - minX + 1, maxY - minY + 1) / 24);
        int margin = 4 * module;
        int x0 = Math.max(0, minX - margin);
        int y0 = Math.max(0, minY - margin);
        int x1 = Math.min(image.getWidth() - 1, maxX + margin);
        int y1 = Math.min(image.getHeight() - 1, maxY + margin);
        BufferedImage crop = image.getSubimage(x0, y0, x1 - x0 + 1, y1 - y0 + 1);
        return new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(
                new BufferedImageLuminanceSource(crop))), hints).getText();
    }

    interface DrawOp {
        void apply(PdfCanvas canvas) throws IOException;
    }

    @Test
    void code128TrackingRoundTrips() throws IOException {
        String value = "1Z999AA10123456784";
        byte[] pdf = pageWith(c -> {
            BitMatrix m = BarcodeUtils.code128(value);
            c.barcodeLinear(m, 5, 30, 91.6, 20, 10, 2);
            c.text(value, 50.8, 52, 9, true, PdfCanvas.Align.CENTER);
        });
        assertThat(decodeFirst(pdf, 0)).isEqualTo(value);
    }

    @Test
    void gs1128SsccRoundTrips() throws IOException {
        String sscc = CheckDigits.sscc18("12345678901234501");
        String value = "00" + sscc;
        byte[] pdf = pageWith(c -> {
            BitMatrix m = BarcodeUtils.gs1128(value);
            c.barcodeLinear(m, 5, 30, 91.6, 22, 10, 2);
        });
        String decoded = decodeFirst(pdf, 0);
        assertThat(decoded.replace(" ", "")).contains(sscc);
    }

    @Test
    void code128NumericPayloadRoundTrips() throws IOException {
        String value = "98765432109876543210";
        byte[] pdf = pageWith(c -> c.barcodeLinear(BarcodeUtils.code128(value),
                5, 40, 91.6, 18, 10, 3));
        assertThat(decodeFirst(pdf, 0)).isEqualTo(value);
    }

    @Test
    void dataMatrixRoundTrips() throws IOException {
        String value = "INV-0001|BLR7|1Z999AA10123456784";
        byte[] pdf = pageWith(c -> c.barcode2D(BarcodeUtils.dataMatrix(value),
                10, 20, 30, 30, 1));
        assertThat(decodeFirst(pdf, 0)).isEqualTo(value);
    }

    @Test
    void qrRoundTrips() throws IOException {
        String value = "https://insideinvoice.app/i/INV-0001";
        byte[] pdf = pageWith(c -> c.barcode2D(BarcodeUtils.qr(value),
                15, 15, 40, 40, 4));
        assertThat(decodeFirst(pdf, 0)).isEqualTo(value);
    }

    @Test
    void upcARoundTrips() throws IOException {
        String value = CheckDigits.gtin("03600029145");
        byte[] pdf = pageWith(c -> c.barcodeLinear(BarcodeUtils.upcA(value),
                5, 40, 91.6, 20, 9, 2));
        assertThat(decodeFirst(pdf, 0)).isEqualTo(value);
    }

    @Test
    void barcodeEncodeRejectsEmptyValues() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> BarcodeUtils.code128(" "));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> BarcodeUtils.qr(""));
    }

    @Test
    void moduleWidthAlwaysIntegerDots() {
        // 90 mm at 203 DPI = 720 dots; 64 modules + 10 quiet each side = 84 modules => 8 dots
        int w = BarcodeUtils.moduleWidthDots(64, 10, 720, 2);
        assertThat(w).isEqualTo(8);
        // never below the 2-dot minimum even in absurdly narrow boxes
        assertThat(BarcodeUtils.moduleWidthDots(64, 10, 10, 2)).isEqualTo(2);
    }
}
