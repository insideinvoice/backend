package com.insideinvoice.invoice.share;

import com.insideinvoice.business.entity.Business;
import com.insideinvoice.customer.entity.Customer;
import com.insideinvoice.invoice.entity.Invoice;
import com.insideinvoice.invoice.entity.InvoiceItem;
import com.insideinvoice.invoice.entity.InvoiceType;
import com.insideinvoice.invoice.mapper.InvoiceMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders the customer-facing invoice email: subject, rich HTML and a plain-text
 * fallback. The header brands the sender's business ("Acme Enterprise"), while the
 * footer makes clear the message is delivered by Inside Invoice.
 *
 * <p>Design: full-width header/footer bands with a 720px content column, hero
 * "Amount due" block with a bulletproof CTA, key/value details grid, an items
 * table that collapses into stacked cards under 600px, and a right-aligned
 * totals block that includes the invoice-level discount. Everything is
 * table-based, inline-styled email HTML (Gmail, Outlook, Apple Mail, Yahoo)
 * with a small media-query stylesheet for mobile only.</p>
 *
 * <p>Discount truth: {@link InvoiceMapper#discountAmount(BigDecimal, BigDecimal)}
 * — the exact formula the invoice totals engine applies. Item amounts in the
 * table are pre-tax values after the discount share, so the column sums to the
 * taxable value.</p>
 *
 * <p>Everything user-supplied (business, customer, item names) is HTML-escaped.
 * Totals come from the invoice record (server truth), not the client.</p>
 */
public final class InvoiceEmailTemplate {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private static final String FONT = "-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif";
    private static final String BG = "#f4f6f8";
    private static final String CARD = "#ffffff";
    private static final String BORDER = "#e5e7eb";
    private static final String INK = "#111827";
    private static final String MUTED = "#6b7280";
    private static final String SUBTLE = "#9ca3af";
    private static final String INDIGO = "#4338ca";
    private static final String GREEN = "#047857";
    private static final String DISCOUNT_GREEN = "#059669";
    private static final String TH_BG = "#f9fafb";
    private static final String ROW_LINE = "#f3f4f6";

    private static final String PILL_BG = "#eef2ff";

    private InvoiceEmailTemplate() {
    }

    public record Content(String subject, String html, String text) {
    }

    public static Content build(Invoice invoice, Customer customer, Business business, String shareUrl) {
        return build(invoice, customer, business, shareUrl, null);
    }

    /**
     * @param logoUrl optional public URL of the seller's logo (rendered max 40px
     *                high, beside the business name). Blank/absent falls back to
     *                the text-only header.
     */
    public static Content build(Invoice invoice, Customer customer, Business business,
                                String shareUrl, String logoUrl) {
        String typeLabel = invoice.getInvoiceType() == InvoiceType.PROFORMA_INVOICE
                ? "Proforma Invoice" : "Tax Invoice";
        String businessName = blankTo(safe(business.getBusinessName()), "Your Business");

        String subject = typeLabel + " " + invoice.getInvoiceNumber()
                + " \u2014 " + businessName
                + " | Rs. " + inr(invoice.getGrandTotal());

        String html = buildHtml(invoice, customer, business, businessName, typeLabel, shareUrl, logoUrl);
        String text = buildText(invoice, customer, business, businessName, typeLabel, shareUrl);
        return new Content(subject, html, text);
    }

    // ---------------------------------------------------------------- HTML

    private static String buildHtml(Invoice invoice, Customer customer, Business business,
                                    String businessName, String typeLabel, String shareUrl, String logoUrl) {
        BigDecimal subtotal = nvl(invoice.getSubtotal());
        BigDecimal tax = nvl(invoice.getTaxAmount());
        BigDecimal grand = nvl(invoice.getGrandTotal());
        BigDecimal discount = InvoiceMapper.discountAmount(subtotal, invoice.getDiscountPercent());
        BigDecimal taxable = subtotal.subtract(discount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal roundOff = grand.subtract(taxable.add(tax)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal ratio = subtotal.signum() > 0
                ? taxable.divide(subtotal, 10, RoundingMode.HALF_UP) : BigDecimal.ONE;

        StringBuilder html = new StringBuilder(16384);
        // Display-only HSN/SAC switch: default ON (null-safe) so emails are
        // unchanged until a business opts out.
        boolean showHsn = business == null || business.getShowHnSac() == null || business.getShowHnSac();

        // ---- head ----
        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n")
            .append("<meta charset=\"UTF-8\">\n")
            .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
            .append("<meta name=\"color-scheme\" content=\"light dark\">\n")
            .append("<meta name=\"supported-color-schemes\" content=\"light dark\">\n")
            .append("<title>").append(esc(typeLabel)).append(" ").append(esc(invoice.getInvoiceNumber()))
            .append("</title>\n")
            .append("<style>\n")
            .append("  body,table,td,a{-webkit-text-size-adjust:100%;-ms-text-size-adjust:100%;}\n")
            .append("  table,td{mso-table-lspace:0pt;mso-table-rspace:0pt;}\n")
            .append("  img{-ms-interpolation-mode:bicubic;border:0;height:auto;line-height:100%;outline:none;text-decoration:none;}\n")
            .append("  table{border-collapse:collapse!important;}\n")
            .append("  body{margin:0!important;padding:0!important;width:100%!important;}\n")
            .append("  .m-only{display:none;mso-hide:all;}\n")
            .append("  @media only screen and (max-width:600px){\n")
            .append("    .container{width:100%!important;max-width:100%!important;}\n")
            .append("    .card{border-radius:0!important;border-left:0!important;border-right:0!important;}\n")
            .append("    .px{padding-left:16px!important;padding-right:16px!important;}\n")
            .append("    .hero-cta{display:block!important;text-align:center!important;padding-top:16px!important;}\n")
            .append("    .kv-cell{display:inline-block!important;width:50%!important;box-sizing:border-box!important;}\n")
            .append("    .kv-wide{width:100%!important;}\n")
            .append("    .items thead{display:none!important;}\n")
            .append("    .items tbody,.items tr,.items td{display:block!important;width:100%!important;}\n")
            .append("    .items tr{padding:12px 0!important;border-bottom:1px solid ").append(ROW_LINE).append(";}\n")
            .append("    .c-sno,.c-qty,.c-rate,.c-gst{display:none!important;}\n")
            .append("    .c-item{padding:0!important;border-top:0!important;}\n")
            .append("    .m-only{display:block!important;}\n")
            .append("    .c-amt{padding:6px 0 0!important;border-top:0!important;text-align:right!important;}\n")
            .append("    .totals{max-width:100%!important;}\n")
            .append("  }\n")
            .append("</style>\n</head>\n")
            .append("<body style=\"margin:0;padding:0;background-color:").append(BG).append(";")
            .append("font-family:").append(FONT).append(";color:").append(INK).append(";")
            .append("-webkit-font-smoothing:antialiased;\">");

        // Hidden preheader (stripped from the message body by mail clients).
        String preheader = "Invoice " + invoice.getInvoiceNumber() + " for \u20b9" + inr(grand) + " is ready."
                + (invoice.getDueDate() != null ? " Due " + DATE.format(invoice.getDueDate()) + "." : "");
        html.append("<div style=\"display:none;font-size:1px;line-height:1px;max-height:0;max-width:0;")
            .append("opacity:0;overflow:hidden;mso-hide:all;color:").append(BG).append(";\">")
            .append(esc(preheader))
            .append("&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;</div>");

        html.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"")
            .append(" bgcolor=\"").append(BG).append("\" style=\"background-color:").append(BG).append(";\">");

        // ---- full-width header band ----
        html.append("<tr><td bgcolor=\"").append(CARD).append("\" style=\"background-color:").append(CARD)
            .append(";border-bottom:1px solid ").append(BORDER).append(";padding:0;\">")
            .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr><td align=\"center\">")
            .append("<table role=\"presentation\" width=\"720\" class=\"container\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"")
            .append(" style=\"width:720px;max-width:720px;\">")
            .append("<tr><td class=\"px\" style=\"padding:20px 32px 18px;\">")
            .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr>");

        if (notBlank(logoUrl) && logoUrl.trim().matches("https?://\\S+")) {
            html.append("<td valign=\"middle\" style=\"padding-right:12px;\">")
                .append("<img src=\"").append(esc(logoUrl.trim()))
                .append("\" width=\"40\" height=\"40\" alt=\"").append(esc(businessName))
                .append("\" style=\"display:block;height:40px;width:auto;max-height:40px;border:0;border-radius:8px;\">")
                .append("</td>");
        }
        html.append("<td valign=\"middle\" style=\"font-family:").append(FONT).append(";font-size:20px;")
            .append("font-weight:700;color:").append(INK).append(";line-height:1.3;\">")
            .append(esc(businessName)).append("</td>")
            .append("<td valign=\"middle\" align=\"right\">")
            .append("<span style=\"display:inline-block;background-color:").append(PILL_BG).append(";color:").append(INDIGO)
            .append(";font-size:11px;font-weight:700;letter-spacing:0.6px;text-transform:uppercase;")
            .append("padding:6px 12px;border-radius:999px;\">").append(esc(typeLabel)).append("</span>")
            .append("</td></tr></table>");

        String address = businessAddressLine(business);
        if (!address.isEmpty() || notBlank(business.getGstIn())) {
            html.append("<p style=\"margin:8px 0 0;font-family:").append(FONT).append(";font-size:12px;")
                .append("color:").append(MUTED).append(";line-height:1.5;\">");
            if (!address.isEmpty()) {
                html.append(esc(address));
                if (notBlank(business.getGstIn())) html.append(" &nbsp;|&nbsp; ");
            }
            if (notBlank(business.getGstIn())) {
                html.append("GSTIN: ").append(esc(business.getGstIn().trim()));
            }
            html.append("</p>");
        }
        html.append("</td></tr></table></td></tr></table></td></tr>");

        // ---- spacer ----
        html.append("<tr><td height=\"24\" style=\"height:24px;line-height:24px;font-size:0;\">&nbsp;</td></tr>");

        // ---- content card ----
        html.append("<tr><td align=\"center\" class=\"px\" style=\"padding:0 16px;\">")
            .append("<table role=\"presentation\" width=\"720\" class=\"container card\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"")
            .append(" bgcolor=\"").append(CARD).append("\"")
            .append(" style=\"width:720px;max-width:720px;background-color:").append(CARD)
            .append(";border:1px solid ").append(BORDER).append(";border-radius:8px;\">");

        // ---- hero: amount due + primary CTA ----
        html.append("<tr><td class=\"px\" style=\"padding:28px 32px 4px;\">")
            .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr>")
            .append("<td valign=\"top\" style=\"font-family:").append(FONT).append(";\">")
            .append("<p style=\"margin:0 0 6px;font-size:11px;font-weight:600;letter-spacing:0.8px;")
            .append("text-transform:uppercase;color:").append(MUTED).append(";\">Amount due</p>")
            .append("<p style=\"margin:0;font-size:28px;line-height:1.15;font-weight:700;color:").append(INK).append(";")
            .append("letter-spacing:-0.4px;\">&#8377;").append(inr(grand)).append("</p>");
        if (invoice.getDueDate() != null) {
            html.append("<p style=\"margin:6px 0 0;font-size:13px;color:").append(MUTED).append(";\">Due ")
                .append(esc(DATE.format(invoice.getDueDate()))).append("</p>");
        }
        html.append("</td>");

        // Bulletproof button (table cell + padding renders in Outlook).
        html.append("<td valign=\"top\" align=\"right\" class=\"hero-cta\">")
            .append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"border-collapse:separate;\"><tr>")
            .append("<td align=\"center\" bgcolor=\"").append(INDIGO).append("\"")
            .append(" style=\"background-color:").append(INDIGO).append(";border-radius:6px;\">")
            .append("<a href=\"").append(esc(shareUrl)).append("\"")
            .append(" style=\"display:inline-block;padding:0 28px;line-height:44px;font-family:").append(FONT).append(";")
            .append("font-size:14px;font-weight:600;color:#ffffff;text-decoration:none;border-radius:6px;\">View Invoice</a>")
            .append("</td></tr></table>")
            .append("<p style=\"margin:10px 0 0;font-size:12px;color:").append(SUBTLE).append(";\">")
            .append("<a href=\"").append(esc(shareUrl)).append("\" style=\"color:").append(MUTED)
            .append(";text-decoration:underline;\">Open in browser</a></p>")
            .append("</td></tr></table></td></tr>");

        // ---- greeting ----
        String customerName = blankTo(safe(customer.getName()), "Customer");
        html.append("<tr><td class=\"px\" style=\"padding:20px 32px 4px;font-family:").append(FONT).append(";")
            .append("font-size:14px;line-height:1.6;color:#374151;\">")
            .append("<p style=\"margin:0 0 6px;color:").append(INK).append(";\">Dear ").append(esc(customerName)).append(",</p>")
            .append("<p style=\"margin:0;color:#374151;\">Thank you for your business. Here is a summary of your invoice")
            .append(" &mdash; the button above opens the complete copy, which you can view or download anytime.</p>")
            .append("</td></tr>");

        // ---- invoice details: key/value grid (4 on desktop, 2 on mobile) ----
        html.append("<tr><td class=\"px\" style=\"padding:20px 32px 0;font-family:").append(FONT).append(";")
            .append("font-size:11px;font-weight:600;letter-spacing:0.8px;text-transform:uppercase;color:")
            .append(MUTED).append(";\">Invoice details</td></tr>")
            .append("<tr><td class=\"px\" style=\"padding:8px 32px 0;\">")
            .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"")
            .append(" style=\"border-top:1px solid ").append(BORDER).append(";border-bottom:1px solid ").append(BORDER).append(";\">")
            .append("<tr>")
            .append(kvCell("Invoice no", invoice.getInvoiceNumber(), false))
            .append(kvCell("Invoice date", formatDate(invoice.getInvoiceDate()), false))
            .append(kvCell("Due date", formatDate(invoice.getDueDate()), false))
            .append(kvCell("Place of supply", notBlank(invoice.getPlaceOfSupply()) ? invoice.getPlaceOfSupply() : "\u2014", false))
            .append("</tr><tr>")
            .append(kvCell("Bill to", blankTo(safe(customer.getName()), "Customer"), true))
            .append("</tr>");
        // Reference / delivery / industry-extended rows (value- and
        // industry-gated; same content as the invoice view and PDF).
        List<InvoiceDetailRows.Row> detailRows = InvoiceDetailRows.rows(invoice, business);
        for (int i = 0; i < detailRows.size(); i += 4) {
            html.append("<tr>");
            int end = Math.min(i + 4, detailRows.size());
            for (int j = i; j < end; j++) {
                InvoiceDetailRows.Row r = detailRows.get(j);
                html.append(kvCell(r.label(), r.value(), false));
            }
            html.append("</tr>");
        }
        html.append("</table></td></tr>");

        // ---- items ----
        html.append("<tr><td class=\"px\" style=\"padding:24px 32px 0;font-family:").append(FONT).append(";")
            .append("font-size:11px;font-weight:600;letter-spacing:0.8px;text-transform:uppercase;color:")
            .append(MUTED).append(";\">Items</td></tr>")
            .append("<tr><td class=\"px\" style=\"padding:8px 32px 0;\">")
            .append("<table class=\"items\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"")
            .append(" style=\"width:100%;border-collapse:collapse;font-family:").append(FONT).append(";\">")
            .append("<thead><tr bgcolor=\"").append(TH_BG).append("\" style=\"background-color:").append(TH_BG).append(";\">")
            .append(itemTh("#", "left", "28px", "c-sno"))
            .append(itemTh("Item", "left", null, "c-item"))
            .append(itemTh("Qty", "right", "56px", "c-qty"))
            .append(itemTh("Rate", "right", "92px", "c-rate"))
            .append(itemTh("GST", "right", "52px", "c-gst"))
            .append(itemTh("Amount", "right", "100px", "c-amt"))
            .append("</tr></thead><tbody>");

        List<InvoiceItem> items = invoice.getItems() == null ? List.of() : invoice.getItems();
        List<BigDecimal> itemAmounts = allocateItemAmounts(items, ratio, taxable);
        int sno = 0;
        for (InvoiceItem item : items) {
            sno++;
            BigDecimal qty = nvl(item.getQty());
            BigDecimal rate = nvl(item.getRate());
            BigDecimal amount = itemAmounts.get(sno - 1);
            String gstPct = nvl(item.getGstPercentage()).stripTrailingZeros().toPlainString();

            html.append("<tr class=\"item-row\">")
                .append("<td class=\"c-sno\" align=\"left\" valign=\"top\" style=\"padding:14px 8px 14px 0;")
                .append("border-top:1px solid ").append(ROW_LINE).append(";font-size:13px;color:").append(SUBTLE).append(";\">")
                .append(sno).append("</td>")
                .append("<td class=\"c-item\" valign=\"top\" style=\"padding:14px 12px 14px 0;border-top:1px solid ")
                .append(ROW_LINE).append(";\">")
                .append("<p style=\"margin:0;font-family:").append(FONT).append(";font-size:14px;font-weight:600;")
                .append("color:").append(INK).append(";line-height:1.4;\">").append(esc(safe(item.getItemName()))).append("</p>");
            if (showHsn && notBlank(item.getHsn())) {
                html.append("<p style=\"margin:3px 0 0;font-family:").append(FONT).append(";font-size:12px;")
                    .append("color:").append(MUTED).append(";line-height:1.4;\">HSN ").append(esc(item.getHsn().trim()))
                    .append("</p>");
            }
            html.append("<p class=\"m-only\" style=\"margin:4px 0 0;font-family:").append(FONT).append(";font-size:12px;")
                .append("color:").append(MUTED).append(";\">Qty ").append(stripZeros(qty))
                .append(notBlank(item.getUnit()) ? " " + esc(item.getUnit().trim()) : "")
                .append(" \u00d7 \u20b9").append(inr(rate)).append(" &nbsp;\u00b7&nbsp; GST ").append(gstPct).append("%</p>")
                .append("</td>")
                .append("<td class=\"c-qty\" align=\"right\" valign=\"top\" style=\"padding:14px 8px;border-top:1px solid ")
                .append(ROW_LINE).append(";font-size:13px;color:#374151;white-space:nowrap;\">")
                .append(stripZeros(qty))
                .append(notBlank(item.getUnit()) ? " " + esc(item.getUnit().trim()) : "")
                .append("</td>")
                .append("<td class=\"c-rate\" align=\"right\" valign=\"top\" style=\"padding:14px 8px;border-top:1px solid ")
                .append(ROW_LINE).append(";font-size:13px;color:#374151;white-space:nowrap;\">")
                .append("\u20b9").append(inr(rate)).append("</td>")
                .append("<td class=\"c-gst\" align=\"right\" valign=\"top\" style=\"padding:14px 8px;border-top:1px solid ")
                .append(ROW_LINE).append(";font-size:13px;color:#374151;white-space:nowrap;\">")
                .append(gstPct).append("%</td>")
                .append("<td class=\"c-amt\" align=\"right\" valign=\"top\" style=\"padding:14px 0 14px 8px;border-top:1px solid ")
                .append(ROW_LINE).append(";font-size:14px;font-weight:600;color:").append(INK).append(";white-space:nowrap;\">")
                .append("\u20b9").append(inr(amount)).append("</td>")
                .append("</tr>");
        }
        html.append("</tbody></table></td></tr>");

        // ---- totals (right-aligned) ----
        html.append("<tr><td class=\"px\" align=\"right\" style=\"padding:20px 32px 28px;\">")
            .append("<table role=\"presentation\" class=\"totals\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"")
            .append(" style=\"width:100%;max-width:300px;font-family:").append(FONT).append(";\">");
        if (subtotal.signum() != 0) {
            html.append(totalRow("Subtotal", "\u20b9" + inr(subtotal), false, "#374151"));
        }
        if (discount.signum() > 0) {
            BigDecimal pct = nvl(invoice.getDiscountPercent()).stripTrailingZeros();
            String label = "Discount (" + pct.toPlainString() + "% off)";
            html.append(totalRow(label, "-\u20b9" + inr(discount), false, DISCOUNT_GREEN));
        }
        html.append(totalRow("Taxable value", "\u20b9" + inr(taxable), false, "#374151"));

        String gstKind = gstKind(invoice, business);
        BigDecimal uniformPct = uniformGstPercent(items);
        if ("CGST_SGST".equals(gstKind)) {
            BigDecimal half = tax.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
            if (tax.signum() != 0) {
                html.append(totalRow(rateLabel("CGST", uniformPct, 2), "\u20b9" + inr(half), false, "#374151"));
                html.append(totalRow(rateLabel("SGST", uniformPct, 2), "\u20b9" + inr(tax.subtract(half)), false, "#374151"));
            }
        } else if ("IGST".equals(gstKind)) {
            if (tax.signum() != 0) {
                html.append(totalRow(rateLabel("IGST", uniformPct, 1), "\u20b9" + inr(tax), false, "#374151"));
            }
        } else {
            if (tax.signum() != 0) {
                html.append(totalRow(rateLabel("GST", uniformPct, 1), "\u20b9" + inr(tax), false, "#374151"));
            }
        }
        if (roundOff.signum() != 0) {
            html.append(totalRow("Round off", (roundOff.signum() < 0 ? "-\u20b9" : "\u20b9") + inr(roundOff.abs()),
                    false, "#374151"));
        }
        html.append("<tr><td style=\"padding:14px 0 0;border-top:2px solid ").append(INK).append(";\"></td>")
            .append("<td style=\"padding:14px 0 0;border-top:2px solid ").append(INK).append(";text-align:right;")
            .append("color:").append(INK).append(";font-size:18px;font-weight:700;white-space:nowrap;\">Total&nbsp;&nbsp;\u20b9")
            .append(inr(grand)).append("</td></tr>");
        if (discount.signum() > 0) {
            html.append("<tr><td></td><td align=\"right\" style=\"padding:6px 0 0;text-align:right;font-size:12px;")
                .append("font-weight:600;color:").append(GREEN).append(";\">You saved \u20b9").append(inr(discount))
                .append("</td></tr>");
        }
        html.append("</table>")
            .append("<p style=\"margin:10px 0 0;font-family:").append(FONT).append(";font-size:12px;font-style:italic;")
            .append("color:").append(MUTED).append(";text-align:right;\">")
            .append(esc(AmountInWords.rupees(grand))).append("</p>")
            .append("</td></tr>");

        html.append("</table></td></tr>");

        // ---- spacer ----
        html.append("<tr><td height=\"24\" style=\"height:24px;line-height:24px;font-size:0;\">&nbsp;</td></tr>");

        // ---- footer band ----
        html.append("<tr><td class=\"px\" align=\"center\" style=\"padding:0 16px 32px;\">")
            .append("<table role=\"presentation\" width=\"720\" class=\"container\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"")
            .append(" style=\"width:720px;max-width:720px;font-family:").append(FONT).append(";\">")
            .append("<tr><td align=\"center\" style=\"padding:0 16px;\">")
            .append("<p style=\"margin:0 0 6px;font-size:12px;color:").append(MUTED).append(";line-height:1.6;\">")
            .append("Sent by <strong style=\"color:#374151;\">Inside Invoice</strong> on behalf of ")
            .append(esc(businessName)).append("</p>")
            .append("<p style=\"margin:0 0 4px;font-size:12px;color:").append(SUBTLE).append(";line-height:1.6;\">")
            .append("You received this message because ").append(esc(businessName))
            .append(" has your email address for invoicing.</p>")
            .append("<p style=\"margin:0 0 10px;font-size:12px;color:").append(SUBTLE).append(";line-height:1.6;\">")
            .append("This is an automated message &mdash; please do not reply.</p>")
            .append("<p style=\"margin:0 0 4px;font-size:12px;\">")
            .append("<a href=\"https://www.insideinvoice.com\" style=\"color:").append(INDIGO).append(";text-decoration:none;\">")
            .append("Support &amp; help centre</a></p>")
            .append("<p style=\"margin:0 0 6px;font-size:12px;color:").append(SUBTLE).append(";\">")
            .append("<a href=\"https://www.insideinvoice.com\" style=\"color:").append(SUBTLE)
            .append(";text-decoration:none;\">www.insideinvoice.com</a></p>")
            .append("<p style=\"margin:0;font-size:11px;color:#d1d5db;\">&copy; 2026 Inside Invoice. All rights reserved.</p>")
            .append("</td></tr></table></td></tr>");

        html.append("</table>\n</body>\n</html>");
        return html.toString();
    }

    private static String kvCell(String label, String value, boolean wide) {
        return "<td class=\"kv-cell" + (wide ? " kv-wide" : "") + "\" valign=\"top\""
                + " style=\"padding:12px 16px 12px 0;font-family:" + FONT + ";\">"
                + "<p style=\"margin:0 0 3px;font-size:11px;font-weight:600;letter-spacing:0.6px;"
                + "text-transform:uppercase;color:" + MUTED + ";\">" + esc(label) + "</p>"
                + "<p style=\"margin:0;font-size:14px;font-weight:600;color:" + INK + ";line-height:1.4;\">"
                + esc(safe(value)) + "</p></td>";
    }

    private static String itemTh(String text, String align, String width, String cssClass) {
        String w = width != null ? "width:" + width + ";" : "";
        return "<th class=\"" + cssClass + "\" align=\"" + align + "\""
                + " style=\"padding:10px 8px;font-size:11px;font-weight:600;color:" + MUTED + ";"
                + "text-transform:uppercase;letter-spacing:0.6px;" + w + "text-align:" + align + ";"
                + "border-bottom:1px solid " + BORDER + ";\">" + esc(text) + "</th>";
    }

    private static String totalRow(String label, String value, boolean bold, String valueColor) {
        String labelStyle = bold ? "color:" + INK + ";font-size:14px;font-weight:700;"
                : "color:#374151;font-size:13px;";
        String valueStyle = bold ? "color:" + INK + ";font-size:14px;font-weight:700;"
                : "color:" + valueColor + ";font-size:13px;font-weight:500;";
        return "<tr><td style=\"padding:5px 0;text-align:left;" + labelStyle + "\">" + esc(label) + "</td>"
                + "<td style=\"padding:5px 0;text-align:right;" + valueStyle + ";white-space:nowrap;\">"
                + esc(value) + "</td></tr>";
    }

    /** "CGST @ 9%" when every item shares one GST rate, otherwise a bare label. */
    private static String rateLabel(String label, BigDecimal uniformPercent, int divisor) {
        if (uniformPercent == null) return label;
        BigDecimal rate = uniformPercent.divide(BigDecimal.valueOf(divisor), 2, RoundingMode.HALF_UP)
                .stripTrailingZeros();
        return label + " @ " + rate.toPlainString() + "%";
    }

    /** The common GST rate when every item uses the same one; null when mixed. */
    private static BigDecimal uniformGstPercent(List<InvoiceItem> items) {
        BigDecimal common = null;
        for (InvoiceItem item : items) {
            BigDecimal pct = nvl(item.getGstPercentage());
            if (common == null) {
                common = pct;
            } else if (common.compareTo(pct) != 0) {
                return null;
            }
        }
        return common;
    }

    /**
     * Splits the discounted taxable value across items pro-rata (same ratio the
     * totals engine applies to GST), with the rounding residual landing on the
     * last row so the Amount column sums exactly to the taxable value.
     */
    private static List<BigDecimal> allocateItemAmounts(List<InvoiceItem> items, BigDecimal ratio, BigDecimal taxable) {
        List<BigDecimal> amounts = new ArrayList<>(items.size());
        BigDecimal allocated = BigDecimal.ZERO;
        for (InvoiceItem item : items) {
            BigDecimal base = item.getTaxableValue() != null ? item.getTaxableValue()
                    : nvl(item.getQty()).multiply(nvl(item.getRate()));
            BigDecimal amount = base.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
            amounts.add(amount);
            allocated = allocated.add(amount);
        }
        if (!amounts.isEmpty()) {
            int last = amounts.size() - 1;
            amounts.set(last, amounts.get(last).add(taxable.subtract(allocated)));
        }
        return amounts;
    }

    // --------------------------------------------------------------- text

    private static String buildText(Invoice invoice, Customer customer, Business business,
                                    String businessName, String typeLabel, String shareUrl) {
        StringBuilder sb = new StringBuilder(2048);
        sb.append(typeLabel.toUpperCase()).append("\n");
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
        sb.append("Bill to: ").append(blankTo(safe(customer.getName()), "Customer")).append("\n");
        for (InvoiceDetailRows.Row r : InvoiceDetailRows.rows(invoice, business)) {
            sb.append(r.label()).append(": ").append(r.value()).append("\n");
        }
        sb.append("\n");

        BigDecimal subtotal = nvl(invoice.getSubtotal());
        BigDecimal tax = nvl(invoice.getTaxAmount());
        BigDecimal grand = nvl(invoice.getGrandTotal());
        BigDecimal discount = InvoiceMapper.discountAmount(subtotal, invoice.getDiscountPercent());
        BigDecimal taxable = subtotal.subtract(discount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal roundOff = grand.subtract(taxable.add(tax)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal ratio = subtotal.signum() > 0
                ? taxable.divide(subtotal, 10, RoundingMode.HALF_UP) : BigDecimal.ONE;

        // Display-only HSN/SAC switch; default ON so text is unchanged until opted out.
        boolean showHnSac = business == null || business.getShowHnSac() == null || business.getShowHnSac();
        List<InvoiceItem> items = invoice.getItems() == null ? List.of() : invoice.getItems();
        List<BigDecimal> itemAmounts = allocateItemAmounts(items, ratio, taxable);
        int sno = 0;
        sb.append("Items:\n");
        for (InvoiceItem item : items) {
            sno++;
            BigDecimal qty = nvl(item.getQty());
            BigDecimal rate = nvl(item.getRate());
            BigDecimal amount = itemAmounts.get(sno - 1);
            sb.append("  ").append(sno).append(". ").append(safe(item.getItemName()));
            if (showHnSac && notBlank(item.getHsn())) sb.append(" (HSN ").append(item.getHsn().trim()).append(")");
            sb.append(" \u2014 ").append(stripZeros(qty))
              .append(notBlank(item.getUnit()) ? " " + item.getUnit().trim() : "")
              .append(" x Rs.").append(inr(rate))
              .append(", GST ").append(nvl(item.getGstPercentage()).stripTrailingZeros().toPlainString()).append('%')
              .append(", amount Rs.").append(inr(amount)).append("\n");
        }
        sb.append("\n");

        if (subtotal.signum() != 0) {
            sb.append(discount.signum() > 0 ? "Subtotal (before discount): Rs." : "Subtotal: Rs.")
              .append(inr(subtotal)).append("\n");
        }
        if (discount.signum() > 0) {
            sb.append("Discount (").append(nvl(invoice.getDiscountPercent()).stripTrailingZeros().toPlainString())
              .append("% off): -Rs.").append(inr(discount)).append("\n");
        }
        sb.append("Taxable value: Rs.").append(inr(taxable)).append("\n");
        String gstKind = gstKind(invoice, business);
        if (tax.signum() != 0) {
            if ("CGST_SGST".equals(gstKind)) {
                BigDecimal half = tax.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
                sb.append("CGST: Rs.").append(inr(half)).append("\n");
                sb.append("SGST: Rs.").append(inr(tax.subtract(half))).append("\n");
            } else if ("IGST".equals(gstKind)) {
                sb.append("IGST: Rs.").append(inr(tax)).append("\n");
            } else {
                sb.append("GST: Rs.").append(inr(tax)).append("\n");
            }
        }
        if (roundOff.signum() != 0) {
            sb.append("Round off: ").append(roundOff.signum() < 0 ? "-" : "+")
              .append("Rs.").append(inr(roundOff.abs())).append("\n");
        }
        sb.append("Total: Rs.").append(inr(grand)).append("\n");
        if (discount.signum() > 0) {
            sb.append("You saved Rs.").append(inr(discount)).append("\n");
        }
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

    private static String formatDate(LocalDate date) {
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
