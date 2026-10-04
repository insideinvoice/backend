package com.insideinvoice.labels.util;

import com.insideinvoice.labels.hazmat.HazmatRenderers;
import com.insideinvoice.labels.hazmat.HazmatSpec;
import com.insideinvoice.labels.render.LabelSizes;
import com.insideinvoice.labels.render.ShippingLabelSpec;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * ZPL II output: native text/barcode fields for shipping labels (secondary
 * printer output), and a monochrome ^GFA raster for hazmat labels so the
 * diamond/mark geometry is pixel-identical to the PDF.
 */
public final class ZplExporter {

    private ZplExporter() {
    }

    // ------------------------------------------------------------------ ship

    public static String shipping(ShippingLabelSpec spec) {
        LabelSizes.Size size = LabelSizes.require(spec.size());
        int dpi = spec.effectiveDpi();
        int pageW = dots(size.wMm(), dpi);
        int pageH = dots(size.hMm(), dpi);
        StringBuilder z = new StringBuilder("^XA\n");
        z.append("^PW").append(pageW).append('\n');
        z.append("^LL").append(pageH).append('\n');

        int x = dots(4, dpi);
        int y = dots(4, dpi);

        z.append(text(x, y, "^A0N,16,16", "CARRIER: " + nz(spec.getCarrier()) +
                "  SERVICE: " + nz(spec.getServiceLevel()) + " " + nz(spec.getServiceCode())));
        y += dots(7, dpi);
        z.append(text(x, y, "^A0N,18,18", "FROM:"));
        y += dots(6, dpi);
        y = shipBlock(z, x, y, spec.getShipFrom(), dpi, "^A0N,20,20", 3);
        y += dots(4, dpi);
        z.append(rule(x, y, pageW - 2 * dots(4, dpi)));
        y += dots(4, dpi);
        z.append(text(x, y, "^A0N,24,24", "SHIP TO:"));
        y += dots(8, dpi);
        y = shipBlock(z, x, y, spec.getShipTo(), dpi, "^A0N,36,36", 5);
        y += dots(4, dpi);
        z.append(rule(x, y, pageW - 2 * dots(4, dpi)));
        y += dots(6, dpi);

        if (spec.getInvoiceNo() != null && !spec.getInvoiceNo().isBlank()) {
            z.append(text(x, y, "^A0N,20,20", "INV: " + spec.getInvoiceNo()));
            y += dots(6, dpi);
        }
        String ref = first(spec.getBillingType(), spec.getCodAmount() != null ? "COD " + spec.getCodAmount() : null,
                spec.getPoNumber() != null ? "PO: " + spec.getPoNumber() : null);
        if (ref != null) {
            z.append(text(x, y, "^A0N,20,20", ref));
            y += dots(6, dpi);
        }
        z.append(text(x, y, "^A0N,20,20", "CARTON " + Math.max(spec.cartons(), 1) + " TOTAL"));
        y += dots(8, dpi);

        // Hero tracking barcode (Code 128) near the bottom.
        int barH = dots(18, dpi);
        int barY = Math.max(y, pageH - dots(34, dpi));
        String tracking = spec.barcodePayload();
        z.append(String.format("^FO%d,%d^BY2^BCN,%d,Y,N,N^FD>:%s^FS%n",
                x, barY, barH, escape(tracking)));
        y = barY + barH + dots(8, dpi);
        z.append(text(x, y, "^A0N,20,20", tracking));

        // Optional GS1 SSCC (Code 128) + Data Matrix.
        if (spec.getGs1() != null && spec.getGs1().getSscc() != null && !spec.getGs1().getSscc().isBlank()) {
            z.append(String.format("^FO%d,%d^BXN,6,200^FD>:%s^FS%n", x, y + dots(4, dpi),
                    escape(spec.getGs1().getSscc())));
            z.append(text(x + dots(25, dpi), y + dots(6, dpi), "^A0N,18,18",
                    "SSCC: " + spec.getGs1().getSscc()));
        }
        z.append("^XZ\n");
        return z.toString();
    }

    private static int shipBlock(StringBuilder z, int x, int y,
                                 com.insideinvoice.labels.render.LabelAddress addr, int dpi,
                                 String font, int maxLines) {
        if (addr == null) {
            return y;
        }
        if (addr.getName() != null && !addr.getName().isBlank()) {
            z.append(text(x, y, font, addr.getName()));
            y += dots(dpi == 300 ? 10 : 7, dpi);
        }
        if (addr.getCompany() != null && !addr.getCompany().isBlank()) {
            z.append(text(x, y, font, addr.getCompany()));
            y += dots(dpi == 300 ? 10 : 7, dpi);
        }
        int lines = 0;
        if (addr.getAddressLines() != null) {
            for (String line : addr.getAddressLines()) {
                if (line == null || line.isBlank() || lines++ >= maxLines) {
                    continue;
                }
                z.append(text(x, y, font, line));
                y += dots(dpi == 300 ? 10 : 7, dpi);
            }
        }
        String cityLine = first(addr.getCity(), addr.getState(), addr.getPincode(), addr.getCountry());
        if (cityLine != null) {
            z.append(text(x, y, font, cityLine));
            y += dots(dpi == 300 ? 10 : 7, dpi);
        }
        if (addr.getPhone() != null && !addr.getPhone().isBlank()) {
            z.append(text(x, y, font, "PH: " + addr.getPhone()));
            y += dots(dpi == 300 ? 10 : 7, dpi);
        }
        return y;
    }

    private static String text(int x, int y, String font, String payload) {
        return String.format("^FO%d,%d%s^FD%s^FS%n", x, y, font, escape(payload));
    }

    private static String rule(int x, int y, int w) {
        return String.format("^FO%d,%d^GB%d,3,3^FS%n", x, y, Math.max(w, 10));
    }

    private static String first(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                if (sb.length() > 0) {
                    sb.append("  ");
                }
                sb.append(p.trim());
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    // ------------------------------------------------------------------ hazmat

    public static String hazmat(HazmatSpec spec) throws Exception {
        byte[] pdf = HazmatRenderers.forSpec(spec).render(spec);
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            int pages = doc.getNumberOfPages();
            StringBuilder z = new StringBuilder();
            for (int p = 0; p < pages; p++) {
                BufferedImage img = renderer.renderImageWithDPI(p, 203);
                z.append("^XA\n");
                z.append("^PW").append(img.getWidth()).append('\n');
                z.append("^LL").append(img.getHeight()).append('\n');
                z.append("^FO0,0").append(gfa(img)).append("^FS\n^XZ\n");
            }
            return z.toString();
        }
    }

    private static String gfa(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int rowBytes = (w + 7) / 8;
        byte[] data = new byte[rowBytes * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int lum = (r * 299 + g * 587 + b * 114) / 1000;
                if (lum < 128) {
                    int idx = y * rowBytes + (x / 8);
                    data[idx] |= (byte) (0x80 >> (x % 8));
                }
            }
        }
        StringBuilder hex = new StringBuilder("^GFA,");
        hex.append(data.length).append(',').append(data.length).append(',').append(rowBytes).append(',');
        for (byte b : data) {
            hex.append(String.format("%02X", b));
        }
        return hex.toString();
    }

    private static int dots(double mm, int dpi) {
        return (int) Math.round(mm * dpi / 25.4);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("^", " ").replace("\n", " ").replace("\r", "");
    }
}
