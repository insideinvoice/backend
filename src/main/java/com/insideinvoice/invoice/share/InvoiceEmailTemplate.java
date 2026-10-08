package com.insideinvoice.invoice.share;

import com.insideinvoice.business.entity.Business;
import com.insideinvoice.customer.entity.Customer;
import com.insideinvoice.invoice.entity.Invoice;
import com.insideinvoice.invoice.entity.InvoiceItem;
import com.insideinvoice.invoice.entity.InvoiceType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders the customer-facing invoice email: subject, rich HTML and a plain-text
 * fallback. The heading brands the sender's business ("Acme Enterprise"), while
 * the footer makes clear the message is delivered by Inside Invoice.
 *
 * <p>Everything user-supplied (business, customer, item names) is HTML-escaped.
 * Totals come from the invoice record (server truth), not the client.</p>
 */
public final class InvoiceEmailTemplate {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private InvoiceEmailTemplate() {
    }

    public record Content(String subject, String html, String text) {
    }

    public static Content build(Invoice invoice, Customer customer, Business business, String shareUrl) {
        String typeLabel = invoice.getInvoiceType() == InvoiceType.PROFORMA_INVOICE
                ? "Proforma Invoice" : "Tax Invoice";
        String businessName = blankTo(safe(business.getBusinessName()), "Your Business");

        String subject = typeLabel + " " + invoice.getInvoiceNumber()
                + " \u2014 " + businessName
                + " | Rs. " + inr(invoice.getGrandTotal());

        String html = buildHtml(invoice, customer, business, businessName, typeLabel, shareUrl);
        String text = buildText(invoice, customer, business, businessName, typeLabel, shareUrl);
        return new Content(subject, html, text);
    }

    // ---------------------------------------------------------------- HTML

    private static String buildHtml(Invoice invoice, Customer customer, Business business,
                                    String businessName, String typeLabel, String shareUrl) {
        BigDecimal grand = nvl(invoice.getGrandTotal());
        BigDecimal tax = nvl(invoice.getTaxAmount());
        BigDecimal subtotal = nvl(invoice.getSubtotal());

        StringBuilder html = new StringBuilder(8192);
        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head><meta charset=\"UTF-8\">")
            .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"></head>\n")
            .append("<body style=\"margin:0;padding:0;background-color:#f4f5f7;font-family:Arial,Helvetica,sans-serif;\">")
            .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"")
            .append(" style=\"background-color:#f4f5f7;padding:32px 16px;\"><tr><td align=\"center\">")
            .append("<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\"")
            .append(" style=\"max-width:600px;width:100%;background-color:#ffffff;border-radius:12px;border:1px solid #e2e8f0;\">");

        // ---- header: the user's business is the heading ----
        html.append("<tr><td style=\"padding:32px 40px 20px;\">")
            .append("<p style=\"margin:0 0 10px;display:inline-block;background-color:#eef2ff;color:#4338ca;")
            .append("font-size:11px;font-weight:bold;letter-spacing:1px;text-transform:uppercase;")
            .append("padding:5px 12px;border-radius:999px;\">").append(esc(typeLabel)).append("</p>")
            .append("<h1 style=\"margin:0 0 6px;color:#0f172a;font-size:24px;font-weight:bold;\">")
            .append(esc(businessName)).append("</h1>");

        String address = businessAddressLine(business);
        if (!address.isEmpty()) {
            html.append("<p style=\"margin:0;color:#64748b;font-size:13px;line-height:1.5;\">").append(esc(address));
            if (notBlank(business.getGstIn())) {
                html.append(" &nbsp;|&nbsp; GSTIN: ").append(esc(business.getGstIn().trim()));
            }
            html.append("</p>");
        } else if (notBlank(business.getGstIn())) {
            html.append("<p style=\"margin:0;color:#64748b;font-size:13px;\">GSTIN: ")
                .append(esc(business.getGstIn().trim())).append("</p>");
        }
        html.append("</td></tr>");

        // ---- greeting + graceful summary line ----
        String customerName = blankTo(safe(customer.getName()), "Customer");
        html.append("<tr><td style=\"padding:0 40px 4px;\">")
            .append("<p style=\"margin:0 0 8px;color:#0f172a;font-size:15px;\">Dear ").append(esc(customerName)).append(",</p>")
            .append("<p style=\"margin:0 0 4px;color:#475569;font-size:14px;line-height:1.65;\">")
            .append("Thank you for your business. Here is a quick summary of your invoice &mdash; ")
            .append("open the link below to view or download the complete copy at any time.")
            .append("</p></td></tr>");

        // ---- meta grid ----
        html.append("<tr><td style=\"padding:16px 40px 4px;\"><table role=\"presentation\" width=\"100%\"")
            .append(" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:#f8fafc;border:1px solid #e2e8f0;border-radius:10px;\">");
        html.append(metaRow("Invoice number", invoice.getInvoiceNumber(), "Date", formatDate(invoice.getInvoiceDate())));
        html.append(metaRow("Due date", formatDate(invoice.getDueDate()),
                "Place of supply", notBlank(invoice.getPlaceOfSupply()) ? invoice.getPlaceOfSupply() : "\u2014"));
        html.append("</table></td></tr>");

        // ---- items ----
        html.append("<tr><td style=\"padding:20px 40px 0;\">")
            .append("<p style=\"margin:0 0 8px;color:#0f172a;font-size:14px;font-weight:bold;\">Invoice summary</p>")
            .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"")
            .append(" style=\"border:1px solid #e2e8f0;border-radius:10px;border-collapse:collapse;\">")
            .append("<tr style=\"background-color:#f1f5f9;\">")
            .append(th("#", "left", "36px")).append(th("Item", "left", null))
            .append(th("Qty", "right", null)).append(th("Rate", "right", null))
            .append(th("Taxable", "right", null)).append(th("GST", "right", null))
            .append(th("Amount", "right", null)).append("</tr>");

        List<InvoiceItem> items = invoice.getItems() == null ? List.of() : invoice.getItems();
        int sno = 0;
        for (InvoiceItem item : items) {
            sno++;
            BigDecimal qty = nvl(item.getQty());
            BigDecimal rate = nvl(item.getRate());
            BigDecimal taxable = item.getTaxableValue() != null ? item.getTaxableValue()
                    : qty.multiply(rate);
            BigDecimal amount = item.getTotal() != null ? item.getTotal()
                    : taxable.add(item.getTaxAmount() != null ? item.getTaxAmount() : BigDecimal.ZERO);
            html.append("<tr>")
                .append(td(String.valueOf(sno), "left", "color:#94a3b8;font-size:12px;"))
                .append("<td style=\"padding:10px 8px;font-size:13px;color:#0f172a;border-top:1px solid #f1f5f9;\">")
                .append(esc(safe(item.getItemName())))
                .append(notBlank(item.getHsn())
                        ? "<br><span style=\"color:#94a3b8;font-size:11px;\">HSN " + esc(item.getHsn().trim()) + "</span>"
                        : "")
                .append("</td>")
                .append(td(stripZeros(qty), "right", "color:#334155;font-size:13px;"))
                .append(td("\u20b9" + inr(rate), "right", "color:#334155;font-size:13px;"))
                .append(td("\u20b9" + inr(taxable), "right", "color:#334155;font-size:13px;"))
                .append(td(nvl(item.getGstPercentage()).stripTrailingZeros().toPlainString() + "%", "right",
                        "color:#334155;font-size:13px;"))
                .append(td("\u20b9" + inr(amount), "right", "color:#0f172a;font-size:13px;font-weight:bold;"))
                .append("</tr>");
        }
        html.append("</table></td></tr>");

        // ---- totals with GST breakup ----
        html.append("<tr><td align=\"right\" style=\"padding:16px 40px 0;\">")
            .append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"width:100%;max-width:280px;\">")
            .append(totalRow("Taxable value", "\u20b9" + inr(subtotal), false));

        String gstKind = gstKind(invoice, business);
        if ("CGST_SGST".equals(gstKind)) {
            BigDecimal half = tax.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
            html.append(totalRow("CGST", "\u20b9" + inr(half), false));
            html.append(totalRow("SGST", "\u20b9" + inr(tax.subtract(half)), false));
        } else if ("IGST".equals(gstKind)) {
            html.append(totalRow("IGST", "\u20b9" + inr(tax), false));
        } else {
            html.append(totalRow("GST", "\u20b9" + inr(tax), false));
        }
        html.append("<tr><td style=\"padding:12px 0 0;\"></td>")
            .append("<td style=\"padding:12px 0 0;border-top:2px solid #e2e8f0;text-align:right;")
            .append("color:#0f172a;font-size:16px;font-weight:bold;\">Total&nbsp;&nbsp;\u20b9").append(inr(grand))
            .append("</td></tr>");
        html.append("</table>")
            .append("<p style=\"margin:8px 0 0;color:#64748b;font-size:12px;text-align:right;\">")
            .append(esc(AmountInWords.rupees(grand))).append("</p>")
            .append("</td></tr>");

        // ---- call to action ----
        html.append("<tr><td align=\"center\" style=\"padding:28px 40px 8px;\">")
            .append("<a href=\"").append(esc(shareUrl)).append("\"")
            .append(" style=\"display:inline-block;background-color:#4f46e5;color:#ffffff;text-decoration:none;")
            .append("font-size:15px;font-weight:bold;padding:14px 34px;border-radius:8px;\">View invoice</a>")
            .append("<p style=\"margin:12px 0 0;color:#94a3b8;font-size:12px;\">")
            .append("Secure read-only link &mdash; no sign-in needed.</p>")
            .append("</td></tr>");

        // ---- footer: Inside Invoice attribution ----
        html.append("<tr><td style=\"padding:20px 40px 28px;\">")
            .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"")
            .append(" style=\"background-color:#f8fafc;border-top:1px solid #e2e8f0;\">")
            .append("<tr><td style=\"padding:16px 20px;text-align:center;\">")
            .append("<p style=\"margin:0 0 4px;color:#64748b;font-size:12px;\">Sent by <strong style=\"color:#475569;\">Inside Invoice</strong> on behalf of ")
            .append(esc(businessName)).append("</p>")
            .append("<p style=\"margin:0;color:#94a3b8;font-size:12px;line-height:1.5;\">")
            .append("You are receiving this because ").append(esc(businessName))
            .append(" has your email address for invoicing. This is an automated message &mdash; please do not reply.</p>")
            .append("<p style=\"margin:8px 0 0;font-size:12px;\"><a href=\"https://www.insideinvoice.com\"")
            .append(" style=\"color:#4f46e5;font-weight:bold;text-decoration:none;\">")
            .append("www.insideinvoice.com</a></p>")
            .append("<p style=\"margin:6px 0 0;color:#cbd5e1;font-size:11px;\">&copy; 2026 Inside Invoice</p>")
            .append("</td></tr></table></td></tr>");

        html.append("</table></td></tr></table>\n</body>\n</html>");
        return html.toString();
    }

    private static String metaRow(String label1, String value1, String label2, String value2) {
        return "<tr>"
                + "<td style=\"padding:12px 16px;border-top:1px solid #e2e8f0;\">"
                + "<p style=\"margin:0 0 2px;color:#94a3b8;font-size:11px;text-transform:uppercase;letter-spacing:0.5px;\">" + esc(label1) + "</p>"
                + "<p style=\"margin:0;color:#0f172a;font-size:13px;font-weight:bold;\">" + esc(safe(value1)) + "</p></td>"
                + "<td style=\"padding:12px 16px;border-top:1px solid #e2e8f0;\">"
                + "<p style=\"margin:0 0 2px;color:#94a3b8;font-size:11px;text-transform:uppercase;letter-spacing:0.5px;\">" + esc(label2) + "</p>"
                + "<p style=\"margin:0;color:#0f172a;font-size:13px;font-weight:bold;\">" + esc(safe(value2)) + "</p></td>"
                + "</tr>";
    }

    private static String totalRow(String label, String value, boolean bold) {
        String labelStyle = bold ? "color:#0f172a;font-size:14px;font-weight:bold;"
                : "color:#475569;font-size:13px;";
        String valueStyle = bold ? "color:#0f172a;font-size:14px;font-weight:bold;"
                : "color:#334155;font-size:13px;";
        return "<tr><td style=\"padding:6px 0;text-align:left;" + labelStyle + "\">" + esc(label) + "</td>"
                + "<td style=\"padding:6px 0;text-align:right;" + valueStyle + "\">" + esc(value) + "</td></tr>";
    }

    private static String th(String text, String align, String width) {
        String w = width != null ? "width:" + width + ";" : "";
        return "<th align=\"" + align + "\" style=\"padding:9px 8px;font-size:11px;color:#64748b;"
                + "text-transform:uppercase;letter-spacing:0.5px;" + w + "border-bottom:1px solid #e2e8f0;\">"
                + esc(text) + "</th>";
    }

    private static String td(String text, String align, String style) {
        return "<td align=\"" + align + "\" style=\"padding:10px 8px;border-top:1px solid #f1f5f9;" + style + "\">"
                + esc(text) + "</td>";
    }

    // --------------------------------------------------------------- text

    private static String buildText(Invoice invoice, Customer customer, Business business,
                                    String businessName, String typeLabel, String shareUrl) {
        StringBuilder sb = new StringBuilder(2048);
        sb.append(typeLabel.toUpperCase().replace(' ', ' ')).append("\n");
        sb.append(businessName).append("\n");
        String address = businessAddressLine(business);
        if (!address.isEmpty()) sb.append(address).append("\n");
        if (notBlank(business.getGstIn())) sb.append("GSTIN: ").append(business.getGstIn().trim()).append("\n");
        sb.append("\n");

        sb.append("Invoice: ").append(invoice.getInvoiceNumber()).append("\n");
        sb.append("Date: ").append(formatDate(invoice.getInvoiceDate()))
          .append("  |  Due: ").append(formatDate(invoice.getDueDate())).append("\n");
        if (notBlank(invoice.getPlaceOfSupply()))
            sb.append("Place of supply: ").append(invoice.getPlaceOfSupply()).append("\n");
        sb.append("\n");

        List<InvoiceItem> items = invoice.getItems() == null ? List.of() : invoice.getItems();
        int sno = 0;
        sb.append("Items:\n");
        for (InvoiceItem item : items) {
            sno++;
            BigDecimal qty = nvl(item.getQty());
            BigDecimal rate = nvl(item.getRate());
            BigDecimal taxable = item.getTaxableValue() != null ? item.getTaxableValue() : qty.multiply(rate);
            BigDecimal amount = item.getTotal() != null ? item.getTotal()
                    : taxable.add(item.getTaxAmount() != null ? item.getTaxAmount() : BigDecimal.ZERO);
            sb.append("  ").append(sno).append(". ").append(safe(item.getItemName()));
            if (notBlank(item.getHsn())) sb.append(" (HSN ").append(item.getHsn().trim()).append(")");
            sb.append(" \u2014 ").append(stripZeros(qty)).append(" x Rs.").append(inr(rate))
              .append(", taxable Rs.").append(inr(taxable))
              .append(", GST ").append(nvl(item.getGstPercentage()).stripTrailingZeros().toPlainString()).append('%')
              .append(", amount Rs.").append(inr(amount)).append("\n");
        }
        sb.append("\n");

        BigDecimal subtotal = nvl(invoice.getSubtotal());
        BigDecimal tax = nvl(invoice.getTaxAmount());
        BigDecimal grand = nvl(invoice.getGrandTotal());
        sb.append("Taxable value: Rs.").append(inr(subtotal)).append("\n");
        String gstKind = gstKind(invoice, business);
        if ("CGST_SGST".equals(gstKind)) {
            BigDecimal half = tax.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
            sb.append("CGST: Rs.").append(inr(half)).append("\n");
            sb.append("SGST: Rs.").append(inr(tax.subtract(half))).append("\n");
        } else if ("IGST".equals(gstKind)) {
            sb.append("IGST: Rs.").append(inr(tax)).append("\n");
        } else {
            sb.append("GST: Rs.").append(inr(tax)).append("\n");
        }
        sb.append("Total: Rs.").append(inr(grand)).append("\n");
        sb.append(AmountInWords.rupees(grand)).append("\n\n");

        sb.append("View invoice (no sign-in needed): ").append(shareUrl).append("\n\n");

        sb.append("--\nSent by Inside Invoice on behalf of ").append(businessName)
          .append(".\nAutomated message, please do not reply.\n")
          .append("www.insideinvoice.com\n");
        return sb.toString();
    }

    // ------------------------------------------------------------ helpers

    /** CGST+SGST when the place of supply matches the business state, else IGST, else generic GST. */
    private static String gstKind(Invoice invoice, Business business) {
        String pos = invoice.getPlaceOfSupply();
        String state = business == null ? null : business.getState();
        if (!notBlank(pos) || !notBlank(state)) return "GST";
        return pos.trim().equalsIgnoreCase(state.trim()) ? "CGST_SGST" : "IGST";
    }

    /** Indian grouping: 1234567.50 -> 12,34,567.50 */
    static String inr(BigDecimal amount) {
        BigDecimal v = amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
        boolean negative = v.signum() < 0;
        String plain = v.abs().toPlainString();
        int dot = plain.indexOf('.');
        String intPart = dot < 0 ? plain : plain.substring(0, dot);
        String dec = dot < 0 ? "00" : plain.substring(dot + 1);
        StringBuilder sb = new StringBuilder();
        if (negative) sb.append('-');
        int n = intPart.length();
        if (n > 3) {
            // Indian grouping: last 3 digits stay together, everything left of
            // them groups in pairs -> 12,34,567
            String tail = intPart.substring(n - 3);
            String head = intPart.substring(0, n - 3);
            StringBuilder groups = new StringBuilder();
            int i = head.length();
            while (i > 0) {
                int start = Math.max(0, i - 2);
                if (groups.length() > 0) groups.insert(0, ',');
                groups.insert(0, head.substring(start, i));
                i = start;
            }
            sb.append(groups).append(',').append(tail);
        } else {
            sb.append(intPart);
        }
        return sb.append('.').append(dec).toString();
    }

    private static String stripZeros(BigDecimal value) {
        BigDecimal v = nvl(value).stripTrailingZeros();
        return v.scale() <= 0 ? v.toBigInteger().toString() : v.toPlainString();
    }

    private static String businessAddressLine(Business business) {
        if (business == null) return "";
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, business.getAddressLine1());
        addIfPresent(parts, business.getAddressLine2());
        String cityLine = String.join(" ", nonBlank(business.getCity()), nonBlank(business.getState())).trim();
        addIfPresent(parts, cityLine);
        addIfPresent(parts, nonBlank(business.getPincode()));
        return String.join(", ", parts);
    }

    private static void addIfPresent(List<String> parts, String value) {
        if (notBlank(value)) parts.add(value.trim());
    }

    private static String nonBlank(String value) {
        return notBlank(value) ? value.trim() : "";
    }

    private static String formatDate(java.time.LocalDate date) {
        return date == null ? "\u2014" : DATE.format(date);
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String blankTo(String value, String fallback) {
        return notBlank(value) ? value : fallback;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String esc(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
