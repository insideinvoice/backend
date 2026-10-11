package com.insideinvoice.invoice;

import com.insideinvoice.invoice.dto.request.ImportedInvoiceRow;
import com.insideinvoice.invoice.dto.request.ImportedItemRow;
import com.insideinvoice.invoice.service.ExcelImportParser;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the bulk-import workbook parser. Builds real .xlsx files with
 * POI so the header-alias matching and two-sheet join are exercised against the
 * same format the exporter writes. No database involved.
 */
class ExcelImportParserTest {

    private final ExcelImportParser parser = new ExcelImportParser();

    private static MockMultipartFile workbook(WorkbookBuilder builder) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            builder.build(wb);
            wb.write(out);
            return new MockMultipartFile("file", "import.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    interface WorkbookBuilder {
        void build(XSSFWorkbook wb);
    }

    @Test
    @DisplayName("Two-sheet workbook: items joined to register by Invoice No")
    void twoSheetJoinByInvoiceNumber() throws Exception {
        MockMultipartFile file = workbook(wb -> {
            Sheet reg = wb.createSheet("Invoices");
            Row h = reg.createRow(0);
            String[] cols = { "Invoice No", "Type", "Invoice Date", "Due Date", "Customer",
                    "Customer GSTIN", "Discount %" };
            for (int i = 0; i < cols.length; i++) h.createCell(i).setCellValue(cols[i]);
            Row r = reg.createRow(1);
            r.createCell(0).setCellValue("INV-001");
            r.createCell(1).setCellValue("TAX_INVOICE");
            r.createCell(2).setCellValue("2026-10-01");
            r.createCell(3).setCellValue("2026-10-15");
            r.createCell(4).setCellValue("Acme Corp");
            r.createCell(5).setCellValue("29ABCDE1234F1Z5");
            r.createCell(6).setCellValue(10);

            Sheet items = wb.createSheet("Invoice Items");
            Row ih = items.createRow(0);
            String[] ic = { "Invoice No", "Item Name", "HSN/SAC", "Unit", "Qty", "Rate", "GST %" };
            for (int i = 0; i < ic.length; i++) ih.createCell(i).setCellValue(ic[i]);
            Row i1 = items.createRow(1);
            i1.createCell(0).setCellValue("INV-001");
            i1.createCell(1).setCellValue("Widget");
            i1.createCell(2).setCellValue("998877");
            i1.createCell(3).setCellValue("Piece");
            i1.createCell(4).setCellValue(2);
            i1.createCell(5).setCellValue(100);
            i1.createCell(6).setCellValue(18);
            Row i2 = items.createRow(2);
            i2.createCell(0).setCellValue("INV-001");
            i2.createCell(1).setCellValue("Gadget");
            i2.createCell(4).setCellValue(1);
            i2.createCell(5).setCellValue(50);
            i2.createCell(6).setCellValue(18);
        });

        List<ImportedInvoiceRow> rows = parser.parse(file);
        assertThat(rows).hasSize(1);
        ImportedInvoiceRow inv = rows.get(0);
        assertThat(inv.getInvoiceNumber()).isEqualTo("INV-001");
        assertThat(inv.getInvoiceType()).isEqualTo("TAX_INVOICE");
        assertThat(inv.getCustomerName()).isEqualTo("Acme Corp");
        assertThat(inv.getCustomerGstin()).isEqualTo("29ABCDE1234F1Z5");
        assertThat(inv.getDiscountPercent()).isEqualByComparingTo("10");
        assertThat(inv.getItems()).hasSize(2);
        ImportedItemRow first = inv.getItems().get(0);
        assertThat(first.getItemName()).isEqualTo("Widget");
        assertThat(first.getHsn()).isEqualTo("998877");
        assertThat(first.getQty()).isEqualByComparingTo("2");
        assertThat(first.getRate()).isEqualByComparingTo("100");
        assertThat(first.getGstPercentage()).isEqualByComparingTo("18");
    }

    @Test
    @DisplayName("Single-sheet collapsed layout: embedded item per register row")
    void singleSheetFallback() throws Exception {
        MockMultipartFile file = workbook(wb -> {
            Sheet s = wb.createSheet("Invoices");
            Row h = s.createRow(0);
            String[] cols = { "Invoice No", "Invoice Date", "Due Date", "Customer",
                    "Item Name", "Qty", "Rate", "GST %" };
            for (int i = 0; i < cols.length; i++) h.createCell(i).setCellValue(cols[i]);
            Row r = s.createRow(1);
            r.createCell(0).setCellValue("INV-100");
            r.createCell(1).setCellValue("2026-10-01");
            r.createCell(2).setCellValue("2026-10-15");
            r.createCell(3).setCellValue("Beta LLC");
            r.createCell(4).setCellValue("Service");
            r.createCell(5).setCellValue(1);
            r.createCell(6).setCellValue(500);
            r.createCell(7).setCellValue(18);
        });

        List<ImportedInvoiceRow> rows = parser.parse(file);
        assertThat(rows).hasSize(1);
        ImportedInvoiceRow inv = rows.get(0);
        assertThat(inv.getInvoiceNumber()).isEqualTo("INV-100");
        assertThat(inv.getItems()).hasSize(1);
        assertThat(inv.getItems().get(0).getItemName()).isEqualTo("Service");
    }

    @Test
    @DisplayName("Header aliases and a banner row above the header are tolerated")
    void bannerRowAndAliases() throws Exception {
        MockMultipartFile file = workbook(wb -> {
            Sheet s = wb.createSheet("Invoices");
            // Row 0 = industry banner (merged), row 1 = header with aliased names.
            s.createRow(0).createCell(0).setCellValue("Industry: General Business");
            Row h = s.createRow(1);
            String[] cols = { "invoice_no", "Invoice Date", "Due Date", "Customer Name", "Item Name", "Quantity", "Price", "GST" };
            for (int i = 0; i < cols.length; i++) h.createCell(i).setCellValue(cols[i]);
            Row r = s.createRow(2);
            r.createCell(0).setCellValue("INV-200");
            r.createCell(1).setCellValue("2026-10-01");
            r.createCell(2).setCellValue("2026-10-15");
            r.createCell(3).setCellValue("Gamma Inc");
            r.createCell(4).setCellValue("Bolt");
            r.createCell(5).setCellValue(3);
            r.createCell(6).setCellValue(20);
            r.createCell(7).setCellValue(12);
        });

        List<ImportedInvoiceRow> rows = parser.parse(file);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getInvoiceNumber()).isEqualTo("INV-200");
        assertThat(rows.get(0).getItems().get(0).getGstPercentage()).isEqualByComparingTo("12");
    }

    @Test
    @DisplayName("Workbook with no invoice sheet is rejected with a clear error")
    void missingInvoiceSheetRejected() throws Exception {
        MockMultipartFile file = workbook(wb -> {
            Sheet s = wb.createSheet("Random");
            s.createRow(0).createCell(0).setCellValue("Nothing here");
        });
        assertThatThrownBy(() -> parser.parse(file))
                .hasMessageContaining("Invoice Register");
    }
}
