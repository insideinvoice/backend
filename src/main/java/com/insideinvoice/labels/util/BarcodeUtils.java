package com.insideinvoice.labels.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.Writer;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.datamatrix.DataMatrixWriter;
import com.google.zxing.oned.Code128Writer;
import com.google.zxing.oned.Code39Writer;
import com.google.zxing.oned.UPCAWriter;
import com.google.zxing.pdf417.PDF417Writer;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.EnumMap;
import java.util.Map;

/**
 * Barcode encoders. All functions return module matrices (1 = black module);
 * the renderer scales each module to an integer number of printer dots.
 */
public final class BarcodeUtils {

    /** FNC1 escape character used by ZXing's Code128Writer for GS1-128 (FNC1 in first position). */
    public static final char FNC1 = '\u00F1';

    private BarcodeUtils() {
    }

    private static Map<EncodeHintType, Object> hints() {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.MARGIN, 0);
        hints.put(EncodeHintType.CHARACTER_SET, "ISO-8859-1");
        return hints;
    }

    private static BitMatrix encode(Writer writer, BarcodeFormat format, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Barcode value must not be empty");
        }
        try {
            // Minimal matrix: the renderer scales modules to an integer number of printer dots.
            return writer.encode(value, format, 1, 1, hints());
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to encode barcode: " + e.getMessage(), e);
        }
    }

    public static BitMatrix code128(String value) {
        return encode(new Code128Writer(), BarcodeFormat.CODE_128, value);
    }

    /** GS1-128: payload prefixed with FNC1 so scanners report Application Identifiers. */
    public static BitMatrix gs1128(String payload) {
        return encode(new Code128Writer(), BarcodeFormat.CODE_128, FNC1 + payload);
    }

    public static BitMatrix code39(String value) {
        return encode(new Code39Writer(), BarcodeFormat.CODE_39, value);
    }

    public static BitMatrix upcA(String value) {
        return encode(new UPCAWriter(), BarcodeFormat.UPC_A, value);
    }

    public static BitMatrix dataMatrix(String value) {
        return encode(new DataMatrixWriter(), BarcodeFormat.DATA_MATRIX, value);
    }

    public static BitMatrix qr(String value) {
        return encode(new QRCodeWriter(), BarcodeFormat.QR_CODE, value);
    }

    public static BitMatrix pdf417(String value) {
        return encode(new PDF417Writer(), BarcodeFormat.PDF_417, value);
    }

    /** Number of modules (columns) of an encoded matrix. */
    public static int moduleCount(BitMatrix matrix) {
        return matrix.getWidth();
    }

    /**
     * Chooses the largest integer module width (in dots) so a linear barcode with
     * {@code moduleCount} modules plus {@code quietZoneModules} quiet modules on each
     * side fits into {@code availableDots}. Always returns at least {@code minModuleDots}.
     */
    public static int moduleWidthDots(int moduleCount, int quietZoneModules, int availableDots, int minModuleDots) {
        int totalModules = moduleCount + 2 * quietZoneModules;
        if (totalModules <= 0 || availableDots <= 0) {
            return minModuleDots;
        }
        int w = availableDots / totalModules;
        return Math.max(minModuleDots, w);
    }
}
