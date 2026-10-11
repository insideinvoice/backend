package com.insideinvoice.invoice.share;

import com.insideinvoice.business.entity.Business;
import com.insideinvoice.invoice.entity.Invoice;
import com.insideinvoice.invoice.entity.InvoiceItem;
import com.insideinvoice.invoice.entity.InvoiceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V29 detail rows shared by the customer email and the public share PDF:
 * value-gating, industry-label overrides, industry-hidden suppression and the
 * rental/health extended rows with composite period ranges.
 */
class InvoiceDetailRowsTest {

    private Invoice baseInvoice() {
        Invoice invoice = Invoice.builder()
                .invoiceNumber("INV-EXT-1")
                .invoiceType(InvoiceType.TAX_INVOICE)
                .invoiceDate(LocalDate.of(2026, 10, 10))
                .dueDate(LocalDate.of(2026, 10, 24))
                .build();
        invoice.setItems(List.of(InvoiceItem.builder()
                .sno(1).itemName("Item").qty(java.math.BigDecimal.ONE)
                .rate(java.math.BigDecimal.TEN).gstPercentage(java.math.BigDecimal.ZERO)
                .build()));
        return invoice;
    }

    private InvoiceDetailRows.Row find(List<InvoiceDetailRows.Row> rows, String label) {
        return rows.stream().filter(r -> r.label().equals(label)).findFirst().orElse(null);
    }

    // ------------------------------------------------------------ core gating

    @Test
    @DisplayName("Industry labels override the defaults; present values are all emitted")
    void industryLabelsAndPresentValues() {
        Invoice invoice = baseInvoice();
        invoice.setDeliveryNote("HN-221");
        invoice.setReferenceNumber("AST-9087");
        invoice.setOtherReferences("PO-4411");
        invoice.setDispatchedThrough("Tempo");
        invoice.setTermsOfDelivery("Handover at yard");
        invoice.setDestination("Bengaluru");

        Business rental = Business.builder().businessName("RentCo").industry("RENTAL").build();
        List<InvoiceDetailRows.Row> rows = InvoiceDetailRows.rows(invoice, rental);

        assertThat(find(rows, "Handover Note")).isNotNull();          // RENTAL label override
        assertThat(find(rows, "Rental / Asset Ref.")).isNotNull();     // RENTAL label override
        assertThat(find(rows, "Agreement / P.O. No.")).isNotNull();    // RENTAL label override
        assertThat(find(rows, "Dispatched Through")).isNotNull();
        assertThat(find(rows, "Terms of Delivery")).isNotNull();
        assertThat(find(rows, "Destination")).isNotNull();
        // rental hides only dispatchDocNumber — no value for it here anyway
        assertThat(rows).extracting(InvoiceDetailRows.Row::label)
                .doesNotContain("Dispatch Doc No.");
    }

    @Test
    @DisplayName("A field the industry hides is never printed even when a value is stored")
    void industryHiddenFieldSuppressed() {
        Invoice invoice = baseInvoice();
        invoice.setDispatchDocNumber("DD-123");   // healthcare hides this
        invoice.setDeliveryNote("DN-9");          // healthcare hides this
        invoice.setReferenceNumber("VISIT-77");

        Business health = Business.builder().businessName("CareClinic").industry("HEALTHCARE").build();
        List<InvoiceDetailRows.Row> rows = InvoiceDetailRows.rows(invoice, health);

        assertThat(rows).extracting(InvoiceDetailRows.Row::label)
                .doesNotContain("Delivery Note")
                .doesNotContain("Dispatch Doc No.");
        assertThat(find(rows, "Visit / Case Ref.")).isNotNull(); // healthcare label override
    }

    @Test
    @DisplayName("Blank values never produce rows; delivery note date folds into one row")
    void blankValuesSkippedAndDeliveryNoteWithDate() {
        Invoice invoice = baseInvoice();
        invoice.setDeliveryNoteDate(LocalDate.of(2026, 10, 12));
        invoice.setDestination("   ");

        Business other = Business.builder().businessName("General").industry("OTHER").build();
        List<InvoiceDetailRows.Row> rows = InvoiceDetailRows.rows(invoice, other);

        InvoiceDetailRows.Row delivery = find(rows, "Delivery Note");
        assertThat(delivery).isNotNull();
        assertThat(delivery.value()).isEqualTo("12 Oct 2026");
        assertThat(rows).extracting(InvoiceDetailRows.Row::label).doesNotContain("Destination");
    }

    // --------------------------------------------------------- extended rows

    @Test
    @DisplayName("Rental invoice: extended rows in order, periods composite, both ends range")
    void rentalExtendedRows() {
        Invoice invoice = baseInvoice();
        invoice.setAgreementNumber("RA-5566");
        invoice.setAssetNumber("CRANE-12");
        invoice.setSerialNumber("SN-8899");
        invoice.setVehicleNumber("KA-01-AB-1234");
        invoice.setPeriodStart(LocalDate.of(2026, 10, 12));
        invoice.setPeriodEnd(LocalDate.of(2026, 10, 16));
        invoice.setBillingPeriodStart(LocalDate.of(2026, 10, 1));
        invoice.setBillingPeriodEnd(LocalDate.of(2026, 10, 31));
        invoice.setExpectedReturnDate(LocalDate.of(2026, 10, 17));
        invoice.setDepositReference("DEP-100");

        Business rental = Business.builder().businessName("RentCo").industry("RENTAL").build();
        List<InvoiceDetailRows.Row> rows = InvoiceDetailRows.rows(invoice, rental);

        List<String> labels = rows.stream().map(InvoiceDetailRows.Row::label).toList();
        assertThat(labels).containsSubsequence(
                "Rental Agreement No.", "Asset / Equipment ID", "Serial Number", "Vehicle Reg. No.",
                "Rental Period", "Billing Period", "Expected Return", "Deposit Reference");
        assertThat(find(rows, "Rental Period").value()).isEqualTo("12 Oct 2026 – 16 Oct 2026");
        assertThat(find(rows, "Billing Period").value()).isEqualTo("01 Oct 2026 – 31 Oct 2026");
        assertThat(find(rows, "Expected Return").value()).isEqualTo("17 Oct 2026");
    }

    @Test
    @DisplayName("Healthcare invoice: patient rows emit; rental-only rows do not leak")
    void healthcareExtendedRows() {
        Invoice invoice = baseInvoice();
        invoice.setPatientReference("PAT-2211");
        invoice.setServiceDate(LocalDate.of(2026, 10, 10));
        invoice.setTreatmentReference("Sess-14");
        invoice.setReferringDoctor("Dr. Iyer");
        invoice.setAgreementNumber("RA-1"); // rental-only — must not leak

        Business health = Business.builder().businessName("CareClinic").industry("HEALTHCARE").build();
        List<InvoiceDetailRows.Row> rows = InvoiceDetailRows.rows(invoice, health);

        assertThat(rows).extracting(InvoiceDetailRows.Row::label)
                .contains("Patient / Customer ID", "Service Date", "Treatment / Session Ref.", "Referring Doctor")
                .doesNotContain("Rental Agreement No.");
        assertThat(find(rows, "Service Date").value()).isEqualTo("10 Oct 2026");
    }

    @Test
    @DisplayName("Non-rental/health industry: extended fields never appear (no cross-industry leak)")
    void noCrossIndustryLeak() {
        Invoice invoice = baseInvoice();
        invoice.setAgreementNumber("RA-1");
        invoice.setPatientReference("PAT-1");
        invoice.setReferenceNumber("REF-1");

        Business trading = Business.builder().businessName("Traders").industry("TRADING").build();
        List<InvoiceDetailRows.Row> rows = InvoiceDetailRows.rows(invoice, trading);

        assertThat(rows).extracting(InvoiceDetailRows.Row::label)
                .contains("Reference No.")
                .doesNotContain("Rental Agreement No.", "Patient / Customer ID");
    }

    @Test
    @DisplayName("One-sided period shows the single date; both absent emits no period row")
    void oneSidedPeriod() {
        Invoice invoice = baseInvoice();
        invoice.setPeriodStart(LocalDate.of(2026, 10, 12));

        Business rental = Business.builder().businessName("RentCo").industry("RENTAL").build();
        InvoiceDetailRows.Row period = find(InvoiceDetailRows.rows(invoice, rental), "Rental Period");
        assertThat(period).isNotNull();
        assertThat(period.value()).isEqualTo("12 Oct 2026");

        Invoice empty = baseInvoice();
        assertThat(find(InvoiceDetailRows.rows(empty, rental), "Rental Period")).isNull();
    }

    // ------------------------------------------------------------------ email

    @Test
    @DisplayName("Email HTML and plain text carry the detail rows for a rental invoice")
    void emailCarriesDetailRows() {
        Invoice invoice = baseInvoice();
        invoice.setSubtotal(new java.math.BigDecimal("1000.00"));
        invoice.setTaxAmount(new java.math.BigDecimal("180.00"));
        invoice.setGrandTotal(new java.math.BigDecimal("1180.00"));
        invoice.setAgreementNumber("RA-5566");
        invoice.setPeriodStart(LocalDate.of(2026, 10, 12));
        invoice.setPeriodEnd(LocalDate.of(2026, 10, 16));

        Business rental = Business.builder().businessName("RentCo").state("Karnataka").industry("RENTAL").build();
        InvoiceEmailTemplate.Content content =
                InvoiceEmailTemplate.build(invoice, com.insideinvoice.customer.entity.Customer.builder().name("Ravi").build(),
                        rental, "https://x/i/tok");

        assertThat(content.html())
                .contains("Rental Agreement No.")
                .contains("RA-5566")
                .contains("Rental Period")
                .contains("12 Oct 2026");
        assertThat(content.text())
                .contains("Rental Agreement No.: RA-5566")
                .contains("Rental Period: 12 Oct 2026");
    }
}
