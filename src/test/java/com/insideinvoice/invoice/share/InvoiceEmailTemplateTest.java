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
}
