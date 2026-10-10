package com.insideinvoice.invoice.share;

import com.insideinvoice.business.entity.Business;
import com.insideinvoice.customer.entity.Customer;
import com.insideinvoice.invoice.entity.Invoice;
import com.insideinvoice.invoice.entity.InvoiceItem;
import com.insideinvoice.invoice.entity.InvoiceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceEmailTemplateTest {

    private Invoice invoiceWith(String placeOfSupply) {
        Invoice invoice = Invoice.builder()
                .invoiceNumber("INV-ESC-1")
                .invoiceType(InvoiceType.TAX_INVOICE)
                .invoiceDate(LocalDate.of(2026, 10, 8))
                .dueDate(LocalDate.of(2026, 10, 23))
                .subtotal(new BigDecimal("200.00"))
                .taxAmount(new BigDecimal("36.00"))
                .grandTotal(new BigDecimal("236.00"))
                .placeOfSupply(placeOfSupply)
                .build();
        invoice.setItems(List.of(InvoiceItem.builder()
                .sno(1)
                .itemName("Consulting <script>alert(1)</script>")
                .hsn("9983")
                .qty(new BigDecimal("2.00"))
                .rate(new BigDecimal("100.00"))
                .gstPercentage(new BigDecimal("18"))
                .taxableValue(new BigDecimal("200.00"))
                .taxAmount(new BigDecimal("36.00"))
                .total(new BigDecimal("236.00"))
                .build()));
        return invoice;
    }

    private Customer customer() {
        return Customer.builder().name("Ravi Kumar").email("ravi@example.com").build();
    }

    @Test
    @DisplayName("Indian grouping: pairs left of the last 3 digits")
    void indianGrouping() {
        assertThat(InvoiceEmailTemplate.inr(new BigDecimal("1234567.5"))).isEqualTo("12,34,567.50");
        assertThat(InvoiceEmailTemplate.inr(new BigDecimal("236"))).isEqualTo("236.00");
        assertThat(InvoiceEmailTemplate.inr(new BigDecimal("1000"))).isEqualTo("1,000.00");
        assertThat(InvoiceEmailTemplate.inr(new BigDecimal("12345"))).isEqualTo("12,345.00");
        assertThat(InvoiceEmailTemplate.inr(new BigDecimal("12345678"))).isEqualTo("1,23,45,678.00");
        assertThat(InvoiceEmailTemplate.inr(new BigDecimal("-2500.5"))).isEqualTo("-2,500.50");
        assertThat(InvoiceEmailTemplate.inr(null)).isEqualTo("0.00");
    }

    @Test
    @DisplayName("Same state as place of supply -> CGST + SGST halves that add back to the tax")
    void intraStateShowsCgstSgst() {
        Business business = Business.builder().businessName("Acme Enterprises").state("Karnataka").build();
        InvoiceEmailTemplate.Content content =
                InvoiceEmailTemplate.build(invoiceWith("Karnataka"), customer(), business, "https://x/i/tok");

        assertThat(content.html()).contains("CGST").contains("SGST").doesNotContain("IGST");
        assertThat(content.html()).contains("\u20b918.00"); // half of 36.00, each leg
        assertThat(content.text()).contains("CGST: Rs.18.00").contains("SGST: Rs.18.00");
    }

    @Test
    @DisplayName("Different state -> IGST with the full tax")
    void interStateShowsIgst() {
        Business business = Business.builder().businessName("Acme Enterprises").state("Karnataka").build();
        InvoiceEmailTemplate.Content content =
                InvoiceEmailTemplate.build(invoiceWith("Maharashtra"), customer(), business, "https://x/i/tok");

        assertThat(content.html()).contains("IGST").doesNotContain("CGST").doesNotContain("SGST");
        assertThat(content.text()).contains("IGST: Rs.36.00");
    }

    @Test
    @DisplayName("Missing state data falls back to a generic GST row")
    void missingStateFallsBackToGenericGst() {
        Business business = Business.builder().businessName("Acme Enterprises").build();
        InvoiceEmailTemplate.Content content =
                InvoiceEmailTemplate.build(invoiceWith("Karnataka"), customer(), business, "https://x/i/tok");

        assertThat(content.html()).contains("GST").doesNotContain("IGST").doesNotContain("CGST");
    }

    @Test
    @DisplayName("User-supplied names are HTML-escaped in subject, HTML and heading")
    void userContentIsEscaped() {
        Business business = Business.builder().businessName("Evil <img src=x onerror=alert(1)>").build();
        InvoiceEmailTemplate.Content content =
                InvoiceEmailTemplate.build(invoiceWith("Karnataka"), customer(), business, "https://x/i/tok");

        assertThat(content.html())
                .contains("Evil &lt;img src=x onerror=alert(1)&gt;")
                .doesNotContain("<img src=x");
        assertThat(content.subject()).contains("Evil <img src=x onerror=alert(1)>"); // header-safe, not HTML
        assertThat(content.subject()).contains("INV-ESC-1").contains("Rs. 236.00");
    }

    @Test
    @DisplayName("HTML carries the share link, totals and platform attribution")
    void htmlCarriesLinkAndAttribution() {
        Business business = Business.builder().businessName("Acme Enterprises").state("Karnataka").build();
        InvoiceEmailTemplate.Content content =
                InvoiceEmailTemplate.build(invoiceWith("Karnataka"), customer(), business, "https://x/i/tok");

        assertThat(content.html())
                .contains("href=\"https://x/i/tok\"")
                .contains("Acme Enterprises")
                .contains("Inside Invoice")
                .contains("Ravi Kumar")
                .contains("236.00")
                .contains("Rupees Two Hundred Thirty Six Only")
                .contains("href=\"https://www.insideinvoice.com\"")
                .contains(">www.insideinvoice.com</a>");
        assertThat(content.text())
                .contains("https://x/i/tok")
                .contains("www.insideinvoice.com");
    }

    // ------------------------------------------------------------- discount

    /** RS-46 sample: Laptop, HSN 8471, qty 1, rate 50,000, GST 18%, discount 56%. */
    private Invoice rs46Invoice() {
        Invoice invoice = Invoice.builder()
                .invoiceNumber("RS-46")
                .invoiceType(InvoiceType.TAX_INVOICE)
                .invoiceDate(LocalDate.of(2026, 10, 9))
                .dueDate(LocalDate.of(2026, 10, 9))
                .subtotal(new BigDecimal("50000.00"))
                .taxAmount(new BigDecimal("3960.00"))
                .grandTotal(new BigDecimal("25960.00"))
                .discountPercent(new BigDecimal("56"))
                .placeOfSupply("Karnataka")
                .build();
        invoice.setItems(List.of(InvoiceItem.builder()
                .sno(1)
                .itemName("Laptop")
                .hsn("8471")
                .qty(new BigDecimal("1"))
                .rate(new BigDecimal("50000.00"))
                .gstPercentage(new BigDecimal("18"))
                .taxableValue(new BigDecimal("50000.00"))
                .taxAmount(new BigDecimal("9000.00"))
                .total(new BigDecimal("59000.00"))
                .build()));
        return invoice;
    }

    private Business rsBusiness() {
        return Business.builder()
                .businessName("RS Hardware")
                .state("Karnataka")
                .addressLine1("12 MG Road")
                .city("Bengaluru")
                .pincode("560001")
                .gstIn("29ABCDE1234F1Z5")
                .build();
    }

    @Test
    @DisplayName("Discounted invoice: subtotal - discount == taxable, taxable + taxes == total, words match")
    void discountMathInvariantsHold() {
        Invoice invoice = rs46Invoice();
        InvoiceEmailTemplate.Content content =
                InvoiceEmailTemplate.build(invoice, customer(), rsBusiness(), "https://x/i/tok");

        BigDecimal subtotal = invoice.getSubtotal();
        BigDecimal discount = com.insideinvoice.invoice.mapper.InvoiceMapper
                .discountAmount(subtotal, invoice.getDiscountPercent());
        BigDecimal taxable = subtotal.subtract(discount);
        BigDecimal cgst = invoice.getTaxAmount().divide(new BigDecimal("2"), 2,
                java.math.RoundingMode.HALF_UP);
        BigDecimal sgst = invoice.getTaxAmount().subtract(cgst);
        BigDecimal roundOff = invoice.getGrandTotal().subtract(taxable.add(invoice.getTaxAmount()));

        assertThat(taxable).isEqualByComparingTo("22000.00");
        assertThat(taxable.add(cgst).add(sgst).add(roundOff)).isEqualByComparingTo("25960.00");
        assertThat(roundOff).isEqualByComparingTo("0.00");
        assertThat(content.html()).contains(AmountInWords.rupees(invoice.getGrandTotal()));
        assertThat(AmountInWords.rupees(invoice.getGrandTotal()))
                .isEqualTo("Rupees Twenty Five Thousand Nine Hundred Sixty Only");
    }

    @Test
    @DisplayName("Discount renders in hero, totals, items and subject — no contradictory with-tax line amount")
    void discountIsRenderedEverywhere() {
        InvoiceEmailTemplate.Content content =
                InvoiceEmailTemplate.build(rs46Invoice(), customer(), rsBusiness(), "https://x/i/tok");

        // Hero + subject + preheader all use the final discounted total.
        assertThat(content.subject()).contains("RS-46").contains("Rs. 25,960.00");
        assertThat(content.html())
                .contains("25,960.00")
                .contains("Invoice RS-46 for \u20b925,960.00 is ready.")
                // Totals: subtotal, negative discount with %, taxable, CGST/SGST halves, total.
                .contains("50,000.00")
                .contains("Discount (56% off)")
                .contains("-\u20b928,000.00")
                .contains("22,000.00")
                .contains("\u20b91,980.00")
                .contains("You saved \u20b928,000.00")
                // Item amount is pre-tax AFTER discount (sums to taxable value), never 59,000.
                .contains("\u20b922,000.00")
                .doesNotContain("59,000.00");
        assertThat(content.text())
                .contains("Subtotal (before discount): Rs.50,000.00")
                .contains("Discount (56% off): -Rs.28,000.00")
                .contains("Taxable value: Rs.22,000.00")
                .contains("CGST: Rs.1,980.00")
                .contains("SGST: Rs.1,980.00")
                .contains("Total: Rs.25,960.00")
                .contains("You saved Rs.28,000.00")
                .contains("Rupees Twenty Five Thousand Nine Hundred Sixty Only");
    }

    @Test
    @DisplayName("No discount: Discount row, savings note and discount wording simply do not render")
    void noDiscountRendersNoDiscountRow() {
        InvoiceEmailTemplate.Content content =
                InvoiceEmailTemplate.build(invoiceWith("Karnataka"), customer(), rsBusiness(), "https://x/i/tok");

        assertThat(content.html())
                .doesNotContain("Discount")
                .doesNotContain("You saved");
        assertThat(content.text())
                .doesNotContain("Discount")
                .doesNotContain("You saved");
    }

    @Test
    @DisplayName("Multi-item discount: amounts are allocated pro-rata and sum to the taxable value")
    void multiItemAmountsSumToTaxableValue() {
        Invoice invoice = Invoice.builder()
                .invoiceNumber("RS-47")
                .invoiceType(InvoiceType.TAX_INVOICE)
                .invoiceDate(LocalDate.of(2026, 10, 9))
                .dueDate(LocalDate.of(2026, 10, 23))
                .subtotal(new BigDecimal("50000.00"))
                .taxAmount(new BigDecimal("3960.00"))
                .grandTotal(new BigDecimal("25960.00"))
                .discountPercent(new BigDecimal("56"))
                .placeOfSupply("Karnataka")
                .build();
        invoice.setItems(List.of(
                InvoiceItem.builder().sno(1).itemName("Laptop").hsn("8471")
                        .qty(BigDecimal.ONE).rate(new BigDecimal("30000.00"))
                        .gstPercentage(new BigDecimal("18")).taxableValue(new BigDecimal("30000.00"))
                        .taxAmount(new BigDecimal("5400.00")).total(new BigDecimal("35400.00")).build(),
                InvoiceItem.builder().sno(2).itemName("Mouse").hsn("8471")
                        .qty(BigDecimal.ONE).rate(new BigDecimal("20000.00"))
                        .gstPercentage(new BigDecimal("18")).taxableValue(new BigDecimal("20000.00"))
                        .taxAmount(new BigDecimal("3600.00")).total(new BigDecimal("23600.00")).build()));

        InvoiceEmailTemplate.Content content =
                InvoiceEmailTemplate.build(invoice, customer(), rsBusiness(), "https://x/i/tok");

        // 22,000 taxable split 60/40 -> 13,200 + 8,800 = 22,000.
        BigDecimal first = new BigDecimal("13200.00");
        BigDecimal second = new BigDecimal("8800.00");
        assertThat(first.add(second)).isEqualByComparingTo("22000.00");
        assertThat(content.html())
                .contains("\u20b913,200.00")
                .contains("\u20b98,800.00")
                .doesNotContain("\u20b935,400.00")
                .doesNotContain("\u20b923,600.00");
    }

    @Test
    @DisplayName("Mixed GST rates: tax rows drop the rate suffix instead of guessing one")
    void mixedGstRatesOmitRateSuffix() {
        Invoice invoice = rs46Invoice();
        invoice.setItems(List.of(
                InvoiceItem.builder().sno(1).itemName("Laptop").hsn("8471")
                        .qty(BigDecimal.ONE).rate(new BigDecimal("30000.00"))
                        .gstPercentage(new BigDecimal("18")).taxableValue(new BigDecimal("30000.00"))
                        .taxAmount(new BigDecimal("5400.00")).total(new BigDecimal("35400.00")).build(),
                InvoiceItem.builder().sno(2).itemName("Services").hsn("9983")
                        .qty(BigDecimal.ONE).rate(new BigDecimal("20000.00"))
                        .gstPercentage(new BigDecimal("12")).taxableValue(new BigDecimal("20000.00"))
                        .taxAmount(new BigDecimal("2400.00")).total(new BigDecimal("22400.00")).build()));
        invoice.setTaxAmount(new BigDecimal("7800.00"));
        invoice.setGrandTotal(new BigDecimal("29800.00"));

        InvoiceEmailTemplate.Content content =
                InvoiceEmailTemplate.build(invoice, customer(), rsBusiness(), "https://x/i/tok");

        assertThat(content.html()).contains("CGST").doesNotContain("CGST @ 9%").doesNotContain("SGST @ 9%");
    }

    @Test
    @DisplayName("Uniform GST rate is shown on the tax rows: CGST @ 9% for an 18% invoice")
    void uniformGstRateIsShownOnTaxRows() {
        InvoiceEmailTemplate.Content content =
                InvoiceEmailTemplate.build(rs46Invoice(), customer(), rsBusiness(), "https://x/i/tok");

        assertThat(content.html()).contains("CGST @ 9%").contains("SGST @ 9%");
    }
}
