package com.insideinvoice.invoice.share;

import com.insideinvoice.business.entity.Business;
import com.insideinvoice.business.industry.IndustryField;
import com.insideinvoice.business.industry.IndustryRegistry;
import com.insideinvoice.customer.entity.Customer;
import com.insideinvoice.invoice.entity.Invoice;
import com.insideinvoice.invoice.entity.InvoiceItem;
import com.insideinvoice.invoice.entity.InvoiceType;
import com.insideinvoice.labels.renderer.PdfCanvas;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * On-demand A4 PDF for public invoice links, drawn with the project's existing PDFBox
 * toolkit ({@link PdfCanvas} + bundled Liberation/Noto fonts). Nothing is cached or
 * persisted: bytes are generated per request and the document is closed afterwards.
 *
 * <p>All monetary values are the persisted invoice figures (subtotal / taxAmount /
 * grandTotal and per-item columns); no tax is recomputed. The CGST/SGST display split
 * mirrors the existing invoice templates (equal halves of the stored tax amount).</p>
 */
@Service
public class PublicInvoicePdfService {

    private static final double PAGE_W = 210;
    private static final double PAGE_H = 297;
    private static final double M = 12;          // page margin
    private static final double CONTENT_W = PAGE_W - 2 * M; // 186mm
    private static final double BOTTOM_SAFE = 268;          // keep clear of the footer

    // item table columns (sum = CONTENT_W)
    private static final double[] COL_W = {10, 66, 22, 14, 18, 22, 34};
    // when the business hides HSN/SAC, drop that column and fold its width into DESC
    private static final double[] COL_W_NO_HSN = {10, 88, 14, 18, 22, 34};

    private static final Locale LOCALE_IN = Locale.forLanguageTag("en-IN");

    public byte[] render(ResolvedPublicInvoice resolved) throws IOException {
        return render(resolved, null);
    }

    /**
     * @param typeOverride {@code TAX_INVOICE} / {@code PROFORMA_INVOICE} to draw the other
     *                     document for the same invoice, or {@code null} for the stored type.
     */
    public byte[] render(ResolvedPublicInvoice resolved, String typeOverride) throws IOException {
        Invoice invoice = resolved.getInvoice();
        Business business = resolved.getBusiness();
        Customer customer = resolved.getCustomer();
        String effectiveType = PublicInvoiceAssembler.effectiveTypeName(invoice, typeOverride);
        boolean proforma = InvoiceType.PROFORMA_INVOICE.name().equals(effectiveType);

        try (PdfCanvas c = new PdfCanvas(300)) {
            org.apache.pdfbox.pdmodel.PDDocumentInformation meta =
                    c.document().getDocumentInformation();
            meta.setTitle((proforma ? "Proforma Invoice " : "Invoice ") + invoice.getInvoiceNumber());
            meta.setAuthor(business != null ? business.getBusinessName() : "Inside Invoice");
            startPage(c, null);

            // Display-only HSN/SAC switch; default ON so the PDF is unchanged
            // until a business opts out.
            boolean showHnSac = business == null || business.getShowHnSac() == null || business.getShowHnSac();

            double y = drawTitle(c, proforma);
            y = drawSellerAndMeta(c, invoice, business, y);
            y = drawBuyer(c, customer, y);
            y = drawItemsTable(c, invoice, y, showHnSac);
            y = ensureRoomForTotals(c, invoice, y);
            y = drawTotals(c, invoice, y);
            y = drawAmountInWords(c, invoice, y);
            y = drawTerms(c, invoice, business, y);
            drawPaymentBox(c, business, y);

            return c.save();
        }
    }

    /** Opens a page (with its footer) and optionally a continuation heading; returns the first free y. */
    private double startPage(PdfCanvas c, String continuationNote) throws IOException {
        c.beginSheetPage(PAGE_W, PAGE_H);
        c.strokeRgb(148, 163, 184);
        c.line(M, PAGE_H - 14, M + CONTENT_W, PAGE_H - 14, 0.2);
        c.text("This is a computer-generated invoice.", M, PAGE_H - 12.5, 7.5, false, PdfCanvas.Align.LEFT);
        c.text("Powered by Inside Invoice", M + CONTENT_W, PAGE_H - 12.5, 7.5, false, PdfCanvas.Align.RIGHT);
        if (continuationNote != null) {
            c.text(continuationNote, M, 12, 9, true, PdfCanvas.Align.LEFT);
            return 18;
        }
        return M;
    }

    // ------------------------------------------------------------------ blocks

    private double drawTitle(PdfCanvas c, boolean proforma) throws IOException {
        String title = proforma ? "PROFORMA INVOICE" : "TAX INVOICE";
        c.fillRgb(240, 244, 248);
        c.fillRect(M, 10, CONTENT_W, 11);
        c.strokeRgb(30, 41, 59);
        c.strokeRect(M, 10, CONTENT_W, 11, 0.5);
        c.text(title, PAGE_W / 2, 12.5, 14, true, PdfCanvas.Align.CENTER);
        return 25;
    }

    private double drawSellerAndMeta(PdfCanvas c, Invoice invoice, Business b, double y) throws IOException {
        double leftX = M;
        double rightX = M + 112;
        double rightW = CONTENT_W - 112;
        double top = y;

        if (b != null) {
            c.text(nz(b.getBusinessName()), leftX, y, 11, true, PdfCanvas.Align.LEFT);
            y += 5;
            List<String> addr = new ArrayList<>();
            addIfPresent(addr, b.getAddressLine1());
            addIfPresent(addr, b.getAddressLine2());
            String cityLine = joinNonEmpty(", ", b.getCity(), b.getState(),
                    b.getPincode() != null ? " - " + b.getPincode() : null);
            addIfPresent(addr, cityLine);
            if (addr.isEmpty()) {
                addIfPresent(addr, b.getCountry());
            }
            y = c.drawLines(c.wrap(String.join(", ", addr), 106, 8.5, false), leftX, y, 4.2, 8.5, false, PdfCanvas.Align.LEFT);
            if (b.getGstIn() != null && !b.getGstIn().isBlank()) {
                c.text("GSTIN: " + b.getGstIn(), leftX, y, 8.5, true, PdfCanvas.Align.LEFT);
                y += 4.4;
            }
            String contact = joinNonEmpty("   |   ",
                    b.getPhone() != null ? "Ph: " + b.getPhone() : null,
                    b.getEmail() != null ? "Email: " + b.getEmail() : null);
            if (!contact.isEmpty()) {
                c.text(contact, leftX, y, 8, false, PdfCanvas.Align.LEFT);
                y += 4.2;
            }
            if (Boolean.TRUE.equals(b.getSpecialistInEnabled()) && b.getSpecialistIn() != null && !b.getSpecialistIn().isBlank()) {
                c.text("SPECIALIST IN: " + b.getSpecialistIn().toUpperCase(Locale.ROOT), leftX, y, 8, true, PdfCanvas.Align.LEFT);
                y += 4.2;
            }
        }

        // meta column (right)
        double metaY = top;
        metaY = metaRow(c, rightX, rightW, metaY, "Invoice No.", nz(invoice.getInvoiceNumber()));
        metaY = metaRow(c, rightX, rightW, metaY, "Invoice Date", str(invoice.getInvoiceDate()));
        metaY = metaRow(c, rightX, rightW, metaY, "Due Date", str(invoice.getDueDate()));
        metaY = metaRow(c, rightX, rightW, metaY, "Payment Mode", nz(invoice.getPaymentMode()));
        metaY = metaRow(c, rightX, rightW, metaY, "Place of Supply", nz(invoice.getPlaceOfSupply()));

        return Math.max(y, metaY) + 4;
    }

    private double metaRow(PdfCanvas c, double x, double w, double y, String label, String value) throws IOException {
        if (value == null || value.isBlank()) {
            return y;
        }
        c.text(label, x, y, 8, false, PdfCanvas.Align.LEFT);
        c.text(value, x + w, y, 8.5, true, PdfCanvas.Align.RIGHT);
        return y + 4.6;
    }

    private double drawBuyer(PdfCanvas c, Customer customer, double y) throws IOException {
        double boxH = 22;
        c.strokeRgb(30, 41, 59);
        c.strokeRect(M, y, CONTENT_W, boxH, 0.4);
        c.fillRgb(248, 250, 252);
        c.fillRect(M, y, CONTENT_W, 4.6);
        c.text("BUYER (BILL TO)", M + 2, y + 0.6, 7.5, true, PdfCanvas.Align.LEFT);

        double ty = y + 6;
        if (customer != null) {
            c.text(nz(customer.getName()), M + 2, ty, 10, true, PdfCanvas.Align.LEFT);
            ty += 4.6;
            String addr = joinNonEmpty(", ", customer.getBillingAddress(), customer.getCity(),
                    customer.getState(), customer.getPincode() != null ? " - " + customer.getPincode() : null);
            ty = c.drawLines(c.wrap(addr, CONTENT_W - 4, 8.5, false), M + 2, ty, 4.2, 8.5, false, PdfCanvas.Align.LEFT);
            String detail = joinNonEmpty("   |   ",
                    customer.getGstIn() != null && !customer.getGstIn().isBlank() ? "GSTIN: " + customer.getGstIn() : null,
                    customer.getPhone() != null ? "Ph: " + customer.getPhone() : null,
                    customer.getEmail());
            if (!detail.isEmpty()) {
                c.text(detail, M + 2, ty, 8, false, PdfCanvas.Align.LEFT);
            }
        } else {
            c.text("-", M + 2, ty, 9, false, PdfCanvas.Align.LEFT);
        }
        return y + boxH + 5;
    }

    private double drawItemsTable(PdfCanvas c, Invoice invoice, double y, boolean showHnSac) throws IOException {
        y = tableHeader(c, y, showHnSac);
        int index = 0;
        for (InvoiceItem item : invoice.getItems()) {
            index++;
            double[] colW = showHnSac ? COL_W : COL_W_NO_HSN;
            List<String> descLines = c.wrap(nz(item.getItemName()), colW[1] - 3, 8, false);
            if (descLines.size() > 2) {
                descLines = descLines.subList(0, 2);
            }
            double rowH = Math.max(7.0, 2.2 + descLines.size() * 4.0);
            if (y + rowH > BOTTOM_SAFE) {
                y = startPage(c, "Invoice " + invoice.getInvoiceNumber() + " (continued)");
                y = tableHeader(c, y, showHnSac);
            }
            boolean alt = index % 2 == 0;
            if (alt) {
                c.fillRgb(248, 250, 252);
                c.fillRect(M, y, CONTENT_W, rowH);
            }
            double[] x = colX(showHnSac);
            c.text(String.valueOf(item.getSno() != null ? item.getSno() : index), x[0] + colW[0] / 2, y + 1.6, 8, false, PdfCanvas.Align.CENTER);
            c.drawLines(descLines, x[1] + 1.5, y + 1.6, 4.0, 8, false, PdfCanvas.Align.LEFT);
            if (showHnSac) {
                c.text(nz(item.getHsn()), x[2] + colW[2] / 2, y + 1.6, 8, false, PdfCanvas.Align.CENTER);
            }
            int gstIdx = showHnSac ? 3 : 2;
            int qtyIdx = showHnSac ? 4 : 3;
            int rateIdx = showHnSac ? 5 : 4;
            int amtIdx = showHnSac ? 6 : 5;
            c.text(dec(item.getGstPercentage(), false) + "%", x[gstIdx] + colW[gstIdx] - 1.5, y + 1.6, 8, false, PdfCanvas.Align.RIGHT);
            c.text(num(item.getQty()) + unitSuffix(item), x[qtyIdx] + colW[qtyIdx] - 1.5, y + 1.6, 8, false, PdfCanvas.Align.RIGHT);
            c.text(num(item.getRate()), x[rateIdx] + colW[rateIdx] - 1.5, y + 1.6, 8, false, PdfCanvas.Align.RIGHT);
            c.text(num(item.getTaxableValue()), x[amtIdx] + colW[amtIdx] - 1.5, y + 1.6, 8, true, PdfCanvas.Align.RIGHT);
            c.strokeRgb(203, 213, 225);
            c.line(M, y + rowH, M + CONTENT_W, y + rowH, 0.2);
            y += rowH;
        }
        // close the table's top border look with a stronger bottom line
        c.strokeRgb(30, 41, 59);
        c.line(M, y, M + CONTENT_W, y, 0.4);
        return y + 3;
    }

    private double tableHeader(PdfCanvas c, double y, boolean showHnSac) throws IOException {
        double[] colW = showHnSac ? COL_W : COL_W_NO_HSN;
        c.fillRgb(30, 41, 59);
        c.fillRect(M, y, CONTENT_W, 7);
        double[] x = colX(showHnSac);
        c.fillWhite();
        c.text("SNO", x[0] + colW[0] / 2, y + 1.5, 7.5, true, PdfCanvas.Align.CENTER);
        c.text("DESCRIPTION", x[1] + 1.5, y + 1.5, 7.5, true, PdfCanvas.Align.LEFT);
        if (showHnSac) {
            c.text("HSN/SAC", x[2] + colW[2] / 2, y + 1.5, 7.5, true, PdfCanvas.Align.CENTER);
        }
        int gstIdx = showHnSac ? 3 : 2;
        int qtyIdx = showHnSac ? 4 : 3;
        int rateIdx = showHnSac ? 5 : 4;
        int amtIdx = showHnSac ? 6 : 5;
        c.text("GST %", x[gstIdx] + colW[gstIdx] - 1.5, y + 1.5, 7.5, true, PdfCanvas.Align.RIGHT);
        c.text("QUANTITY", x[qtyIdx] + colW[qtyIdx] - 1.5, y + 1.5, 7.5, true, PdfCanvas.Align.RIGHT);
        c.text("RATE", x[rateIdx] + colW[rateIdx] - 1.5, y + 1.5, 7.5, true, PdfCanvas.Align.RIGHT);
        c.text("AMOUNT", x[amtIdx] + colW[amtIdx] - 1.5, y + 1.5, 7.5, true, PdfCanvas.Align.RIGHT);
        return y + 7;
    }

    /** " 2 Nos" when the item has a unit, else empty — display only, never affects maths. */
    private static String unitSuffix(InvoiceItem item) {
        String unit = item.getUnit();
        return unit == null || unit.isBlank() ? "" : " " + unit.trim();
    }

    private double ensureRoomForTotals(PdfCanvas c, Invoice invoice, double y) throws IOException {
        if (y + 56 <= BOTTOM_SAFE) {
            return y;
        }
        return startPage(c, "Invoice " + invoice.getInvoiceNumber() + " (continued)");
    }

    private double drawTotals(PdfCanvas c, Invoice invoice, double y) throws IOException {
        double boxW = 86;
        double x = M + CONTENT_W - boxW;
        BigDecimal tax = nz(invoice.getTaxAmount());
        BigDecimal half = tax.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);

        double rowH = 6;
        double startY = y;
        y = totalRow(c, x, boxW, y, rowH, "Taxable Value", num(invoice.getSubtotal()), false, false);
        y = totalRow(c, x, boxW, y, rowH, "CGST", num(half), false, false);
        y = totalRow(c, x, boxW, y, rowH, "SGST", num(half), false, false);
        y = totalRow(c, x, boxW, y, rowH + 1.5, "Grand Total", num(invoice.getGrandTotal()), true, true);

        c.strokeRgb(30, 41, 59);
        c.strokeRect(x, startY, boxW, y - startY, 0.4);
        return y + 3;
    }

    private double totalRow(PdfCanvas c, double x, double w, double y, double h,
                            String label, String value, boolean bold, boolean shaded) throws IOException {
        if (shaded) {
            c.fillRgb(240, 244, 248);
            c.fillRect(x, y, w, h);
        }
        c.text(label, x + 2, y + (h - 3.4) / 2, bold ? 9.5 : 8.5, bold, PdfCanvas.Align.LEFT);
        c.text(value, x + w - 2, y + (h - 3.4) / 2, bold ? 10 : 8.5, bold, PdfCanvas.Align.RIGHT);
        if (!shaded) {
            c.strokeRgb(226, 232, 240);
            c.line(x, y + h, x + w, y + h, 0.2);
        }
        return y + h;
    }

    private double drawAmountInWords(PdfCanvas c, Invoice invoice, double y) throws IOException {
        c.text("Amount in words", M, y, 8, true, PdfCanvas.Align.LEFT);
        y += 4.4;
        String words = AmountInWords.rupees(invoice.getGrandTotal());
        y = c.drawLines(c.wrap(words, CONTENT_W - 90, 8.5, true), M, y, 4.4, 8.5, true, PdfCanvas.Align.LEFT);
        return y + 4;
    }

    private double drawTerms(PdfCanvas c, Invoice invoice, Business business, double y) throws IOException {
        StringBuilder terms = new StringBuilder();
        appendLabeled(terms, "Payment Terms", invoice.getPaymentTerms());
        // A value the industry profile hides is still stored — it just must not
        // be printed, so the public PDF follows the same suppression as the
        // private templates.
        boolean deliveryHidden = IndustryRegistry.forRawIndustry(
                business != null ? business.getIndustry() : null)
                .hiddenFields().contains(IndustryField.TERMS_OF_DELIVERY);
        if (!deliveryHidden) {
            appendLabeled(terms, "Terms of Delivery", invoice.getTermsOfDelivery());
        }
        appendLabeled(terms, "Notes", invoice.getNotes());
        if (terms.length() == 0) {
            return y;
        }
        c.text("Terms", M, y, 8, true, PdfCanvas.Align.LEFT);
        y += 4.4;
        y = c.drawParagraph(terms.toString(), M, y, CONTENT_W, 8, 7, 5, false, PdfCanvas.Align.LEFT);
        return y + 3;
    }

    private double drawPaymentBox(PdfCanvas c, Business b, double y) throws IOException {
        if (b == null) {
            return y;
        }
        List<String> lines = new ArrayList<>();
        addIfPresent(lines, b.getBankName() != null ? "Bank: " + b.getBankName() : null);
        addIfPresent(lines, b.getBranch() != null ? "Branch: " + b.getBranch() : null);
        addIfPresent(lines, b.getAccountNo() != null ? "A/C No: " + b.getAccountNo() : null);
        addIfPresent(lines, b.getIfsc() != null ? "IFSC: " + b.getIfsc() : null);
        addIfPresent(lines, b.getBankAddress() != null ? "Bank Address: " + b.getBankAddress() : null);
        addIfPresent(lines, b.getUpiId() != null ? "UPI ID: " + b.getUpiId() : null);
        if (lines.isEmpty()) {
            return y;
        }
        double boxH = 6 + lines.size() * 4.2;
        if (y + boxH > BOTTOM_SAFE) {
            y = startPage(c, null);
            boxH = 6 + lines.size() * 4.2;
        }
        c.strokeRgb(30, 41, 59);
        c.strokeRect(M, y, CONTENT_W, boxH, 0.4);
        c.fillRgb(240, 244, 248);
        c.fillRect(M, y, CONTENT_W, 5);
        c.text("PAYMENT DETAILS", M + 2, y + 0.7, 7.5, true, PdfCanvas.Align.LEFT);
        double ty = y + 6;
        for (String line : lines) {
            c.text(line, M + 2, ty, 8.5, false, PdfCanvas.Align.LEFT);
            ty += 4.2;
        }
        return y + boxH + 4;
    }

    // ------------------------------------------------------------------ helpers

    private static double[] colX(boolean showHnSac) {
        double[] colW = showHnSac ? COL_W : COL_W_NO_HSN;
        double[] x = new double[colW.length];
        double acc = M;
        for (int i = 0; i < colW.length; i++) {
            x[i] = acc;
            acc += colW[i];
        }
        return x;
    }

    private static String num(BigDecimal v) {
        if (v == null) {
            return "-";
        }
        return String.format(LOCALE_IN, "%,.2f", v);
    }

    private static String dec(BigDecimal v, boolean grouped) {
        if (v == null) {
            return "0.00";
        }
        return grouped ? String.format(LOCALE_IN, "%,.2f", v) : v.stripTrailingZeros().toPlainString();
    }

    private static String str(Object v) {
        return v == null ? "" : v.toString();
    }

    private static String nz(String v) {
        return v == null ? "" : v;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static void appendLabeled(StringBuilder sb, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append('\n');
        }
        sb.append(label).append(": ").append(value);
    }

    private static void addIfPresent(List<String> list, String value) {
        if (value != null && !value.isBlank()) {
            list.add(value.trim());
        }
    }

    private static String joinNonEmpty(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(sep);
            }
            sb.append(p.trim());
        }
        return sb.toString();
    }
}
