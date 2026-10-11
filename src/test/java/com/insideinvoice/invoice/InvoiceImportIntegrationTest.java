package com.insideinvoice.invoice;

import com.insideinvoice.BaseIntegrationTest;
import com.insideinvoice.auth.entity.Role;
import com.insideinvoice.auth.entity.User;
import com.insideinvoice.customer.entity.Customer;
import com.insideinvoice.invoice.dto.response.ImportReport;
import com.insideinvoice.invoice.entity.Invoice;
import com.insideinvoice.invoice.service.InvoiceImportService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the bulk invoice-import service against a real database:
 * dry-run returns a per-row validation report without writing, commit creates
 * invoices with server-recomputed totals (never the spreadsheet's money cells),
 * and re-importing the same workbook skips duplicates (idempotent no-op).
 *
 * <p>Drives the service directly rather than through MockMvc multipart: the
 * service holds all the business logic, and MockMvc's multipart transport has a
 * known quirk of stripping the boundary (arriving as an empty part). The HTTP
 * wiring is a thin controller passthrough.</p>
 */
class InvoiceImportIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private InvoiceImportService invoiceImportService;

    private User alice;
    private Customer customer;

    @BeforeEach
    void seed() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        alice = makeUser("alice-" + run, Role.USER);
        customer = seedCustomer(alice.getBusinessId());
    }

    private MockMultipartFile twoRowWorkbook(String inv1, String inv2) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet reg = wb.createSheet("Invoices");
            Row h = reg.createRow(0);
            String[] cols = { "Invoice No", "Type", "Invoice Date", "Due Date", "Customer GSTIN", "Customer", "Discount %" };
            for (int i = 0; i < cols.length; i++) h.createCell(i).setCellValue(cols[i]);
            for (int n = 0; n < 2; n++) {
                Row r = reg.createRow(1 + n);
                r.createCell(0).setCellValue(n == 0 ? inv1 : inv2);
                r.createCell(1).setCellValue("TAX_INVOICE");
                r.createCell(2).setCellValue("2026-10-01");
                r.createCell(3).setCellValue("2026-10-15");
                r.createCell(4).setCellValue(customer.getGstIn());
                r.createCell(5).setCellValue(customer.getName());
                r.createCell(6).setCellValue(n == 0 ? 10 : 0);
            }
            Sheet items = wb.createSheet("Invoice Items");
            Row ih = items.createRow(0);
            String[] ic = { "Invoice No", "Item Name", "Unit", "Qty", "Rate", "GST %" };
            for (int i = 0; i < ic.length; i++) ih.createCell(i).setCellValue(ic[i]);
            Row i1 = items.createRow(1);
            i1.createCell(0).setCellValue(inv1);
            i1.createCell(1).setCellValue("Widget");
            i1.createCell(2).setCellValue("Piece");
            i1.createCell(3).setCellValue(2);
            i1.createCell(4).setCellValue(100);
            i1.createCell(5).setCellValue(18);
            Row i2 = items.createRow(2);
            i2.createCell(0).setCellValue(inv2);
            i2.createCell(1).setCellValue("Service");
            i2.createCell(2).setCellValue("Hour");
            i2.createCell(3).setCellValue(1);
            i2.createCell(4).setCellValue(500);
            i2.createCell(5).setCellValue(18);
            wb.write(out);
            return new MockMultipartFile("file", "import.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    private long invoiceCountWithPrefix(String prefix) {
        return invoiceRepository.findByBusinessId(alice.getBusinessId(), PageRequest.of(0, 100))
                .getContent().stream()
                .filter(i -> i.getInvoiceNumber() != null && i.getInvoiceNumber().startsWith(prefix))
                .count();
    }

    @Test
    @DisplayName("dryRun validates and returns a report without creating invoices")
    void dryRunDoesNotWrite() throws Exception {
        MockMultipartFile file = twoRowWorkbook("IMP-DR-1", "IMP-DR-2");
        long before = invoiceCountWithPrefix("IMP-DR-");

        ImportReport report = invoiceImportService.validate(file, alice.getBusinessId());

        assertThat(report.isDryRun()).isTrue();
        assertThat(report.getTotalRows()).isEqualTo(2);
        assertThat(report.getRows()).hasSize(2);
        assertThat(report.getRows().get(0).getAction()).isEqualTo("CREATE");
        assertThat(report.getRows().get(0).isValid()).isTrue();
        assertThat(invoiceCountWithPrefix("IMP-DR-")).isEqualTo(before);
    }

    @Test
    @DisplayName("commit creates invoices and recomputes totals server-side")
    void commitCreatesWithRecomputedTotals() throws Exception {
        MockMultipartFile file = twoRowWorkbook("IMP-CM-1", "IMP-CM-2");

        ImportReport report = invoiceImportService.commit(file, alice.getBusinessId(), alice.getId());

        assertThat(report.isDryRun()).isFalse();
        assertThat(report.getImported()).isEqualTo(2);
        assertThat(report.getSkipped()).isZero();
        assertThat(report.getBlocked()).isZero();

        // INV-CM-1: qty2*rate100 = 200 taxable, 10% discount -> 180 base, 18% GST = 32.40, total 212.40
        Invoice imported = invoiceRepository.findByBusinessId(alice.getBusinessId(), PageRequest.of(0, 100))
                .getContent().stream()
                .filter(i -> "IMP-CM-1".equals(i.getInvoiceNumber()))
                .findFirst().orElseThrow();
        assertThat(imported.getSubtotal()).isEqualByComparingTo("200.00");
        assertThat(imported.getGrandTotal()).isEqualByComparingTo("212.40");
        assertThat(invoiceItemRepository.findByInvoiceIdIn(
                java.util.List.of(imported.getId()))).hasSize(1);
    }

    @Test
    @DisplayName("re-importing the same workbook skips duplicates (idempotent)")
    void reimportSkipsDuplicates() throws Exception {
        MockMultipartFile file = twoRowWorkbook("IMP-DUP-1", "IMP-DUP-2");
        ImportReport first = invoiceImportService.commit(file, alice.getBusinessId(), alice.getId());
        assertThat(first.getImported()).isEqualTo(2);

        ImportReport second = invoiceImportService.commit(file, alice.getBusinessId(), alice.getId());
        assertThat(second.getImported()).isZero();
        assertThat(second.getSkipped()).isEqualTo(2);

        assertThat(invoiceCountWithPrefix("IMP-DUP-")).isEqualTo(2);
    }

    @Test
    @DisplayName("row with unknown customer is blocked in the report")
    void unknownCustomerIsBlocked() throws Exception {
        MockMultipartFile file;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet reg = wb.createSheet("Invoices");
            Row h = reg.createRow(0);
            String[] cols = { "Invoice No", "Type", "Invoice Date", "Due Date", "Customer GSTIN", "Customer" };
            for (int i = 0; i < cols.length; i++) h.createCell(i).setCellValue(cols[i]);
            Row r = reg.createRow(1);
            r.createCell(0).setCellValue("IMP-BAD-1");
            r.createCell(1).setCellValue("TAX_INVOICE");
            r.createCell(2).setCellValue("2026-10-01");
            r.createCell(3).setCellValue("2026-10-15");
            r.createCell(4).setCellValue("99ZZZZ9999Z9Z9");
            r.createCell(5).setCellValue("Ghost Customer");
            Sheet items = wb.createSheet("Invoice Items");
            Row ih = items.createRow(0);
            String[] ic = { "Invoice No", "Item Name", "Qty", "Rate", "GST %" };
            for (int i = 0; i < ic.length; i++) ih.createCell(i).setCellValue(ic[i]);
            Row ir = items.createRow(1);
            ir.createCell(0).setCellValue("IMP-BAD-1");
            ir.createCell(1).setCellValue("Widget");
            ir.createCell(2).setCellValue(1);
            ir.createCell(3).setCellValue(100);
            ir.createCell(4).setCellValue(18);
            wb.write(out);
            file = new MockMultipartFile("file", "import.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }

        ImportReport report = invoiceImportService.validate(file, alice.getBusinessId());
        assertThat(report.getRows().get(0).getAction()).isEqualTo("BLOCKED");
        assertThat(report.getRows().get(0).isValid()).isFalse();
        assertThat(report.getBlocked()).isEqualTo(1);
        assertThat(invoiceCountWithPrefix("IMP-BAD-")).isZero();
    }
}
