package com.insideinvoice.invoice.service;

import com.insideinvoice.exception.BadRequestException;
import com.insideinvoice.invoice.dto.request.ImportedInvoiceRow;
import com.insideinvoice.invoice.dto.request.ImportedItemRow;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses an invoice bulk-import workbook into {@link ImportedInvoiceRow}s.
 *
 * <p>The workbook has two logical sheets, matched by header signature rather
 * than sheet name so renamed/reordered sheets still work:
 * <ul>
 *   <li><b>Invoice Register</b> (or "Invoices") — one row per invoice, must
 *       carry an "Invoice No" column.</li>
 *   <li><b>Invoice Items</b> — one row per line item, linked to the register by
 *       "Invoice No", must carry "Item Name" + "Qty" + "Rate".</li>
 * </ul>
 *
 * <p>A single-sheet fallback is also supported: if no separate items sheet is
 * found but the register itself carries Item Name/Qty/Rate columns, each
 * register row's single embedded item is used (matching the collapsed
 * one-row-per-invoice export layout).
 *
 * <p>Only input columns a human would type are read; money columns are ignored
 * and recomputed later by {@code InvoiceMapper}.
 */
@Component
public class ExcelImportParser {

    private static final Logger log = LoggerFactory.getLogger(ExcelImportParser.class);

    // Header aliases (lower-cased, punctuation-stripped) → canonical field.
    // Keys are matched after normalising the header cell (trim, lower-case,
    // collapse whitespace/punctuation) so "Invoice No", "Invoice No.", "invoice_no"
    // and "INVOICE NUMBER" all resolve to the same field.
    private static final Map<String, String> HEADER_ALIASES = buildHeaderAliases();

    private static Map<String, String> buildHeaderAliases() {
        Map<String, String> m = new HashMap<>();
        m.put("invoice no", "invoiceNumber");
        m.put("invoice number", "invoiceNumber");
        m.put("invoice no.", "invoiceNumber");
        m.put("type", "invoiceType");
        m.put("invoice type", "invoiceType");
        m.put("status", "status");
        m.put("invoice date", "invoiceDate");
        m.put("dated", "invoiceDate");
        m.put("due date", "dueDate");
        m.put("customer", "customerName");
        m.put("customer name", "customerName");
        m.put("customer gstin", "customerGstin");
        m.put("gstin", "customerGstin");
        m.put("customer email", "customerEmail");
        m.put("email", "customerEmail");
        m.put("customer phone", "customerPhone");
        m.put("phone", "customerPhone");
        m.put("customer address", "customerAddress");
        m.put("address", "customerAddress");
        m.put("customer city", "customerCity");
        m.put("city", "customerCity");
        m.put("customer state", "customerState");
        m.put("state", "customerState");
        m.put("customer pincode", "customerPincode");
        m.put("pincode", "customerPincode");
        m.put("place of supply", "placeOfSupply");
        m.put("payment terms", "paymentTerms");
        m.put("payment mode", "paymentMode");
        m.put("discount %", "discountPercent");
        m.put("discount percent", "discountPercent");
        m.put("discount", "discountPercent");
        m.put("notes", "notes");
        m.put("delivery note", "deliveryNote");
        m.put("delivery note date", "deliveryNoteDate");
        m.put("reference no", "referenceNumber");
        m.put("reference number", "referenceNumber");
        m.put("buyer order no", "buyerOrderNumber");
        m.put("buyer's order no", "buyerOrderNumber");
        m.put("dispatch doc no", "dispatchDocNumber");
        m.put("dispatched through", "dispatchedThrough");
        m.put("terms of delivery", "termsOfDelivery");
        m.put("destination", "destination");
        m.put("other references", "otherReferences");
        // Items sheet
        m.put("item name", "itemName");
        m.put("item", "itemName");
        m.put("description", "itemName");
        m.put("hsn", "hsn");
        m.put("hsn/sac", "hsn");
        m.put("hsn sac", "hsn");
        m.put("unit", "unit");
        m.put("qty", "qty");
        m.put("quantity", "qty");
        m.put("rate", "rate");
        m.put("price", "rate");
        m.put("gst %", "gstPercentage");
        m.put("gst percent", "gstPercentage");
        m.put("gst", "gstPercentage");
        m.put("gstpercentage", "gstPercentage");
        // V29 extended fields
        m.put("agreement no", "agreementNumber");
        m.put("agreement number", "agreementNumber");
        m.put("asset no", "assetNumber");
        m.put("asset number", "assetNumber");
        m.put("serial no", "serialNumber");
        m.put("serial number", "serialNumber");
        m.put("vehicle no", "vehicleNumber");
        m.put("vehicle number", "vehicleNumber");
        m.put("period start", "periodStart");
        m.put("period end", "periodEnd");
        m.put("billing period start", "billingPeriodStart");
        m.put("billing period end", "billingPeriodEnd");
        m.put("expected return date", "expectedReturnDate");
        m.put("deposit reference", "depositReference");
        m.put("patient reference", "patientReference");
        m.put("service date", "serviceDate");
        m.put("treatment reference", "treatmentReference");
        m.put("referring doctor", "referringDoctor");
        return m;
    }

    /**
     * Normalises a header cell for alias lookup: trim, lower-case, collapse
     * runs of whitespace/punctuation to single spaces, strip trailing dots.
     */
    private static String normalizeHeader(String raw) {
        if (raw == null) return "";
        String s = raw.trim().toLowerCase();
        s = s.replaceAll("[\\s_]+", " ");
        s = s.replaceAll("[./]+$", "");
        s = s.trim();
        return s;
    }

    /** Reads the workbook and returns one {@link ImportedInvoiceRow} per register row. */
    public List<ImportedInvoiceRow> parse(MultipartFile file) {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet itemsSheet = null;
            Map<String, Integer> itemsHeader = null;
            Sheet registerSheet = null;
            Map<String, Integer> registerHeader = null;

            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                Map<String, Integer> header = readHeader(sheet);
                if (header == null || header.isEmpty()) continue;
                // The items sheet also carries an "Invoice No" column for linking,
                // so classify by the item columns first: a sheet with itemName+qty
                // +rate IS the items sheet; a register has invoiceNumber but not
                // those item columns.
                boolean hasItemCols = header.containsKey("itemName")
                        && header.containsKey("qty") && header.containsKey("rate");
                boolean hasInvoiceNo = header.containsKey("invoiceNumber");
                if (hasItemCols && itemsSheet == null) {
                    itemsSheet = sheet;
                    itemsHeader = header;
                } else if (hasInvoiceNo && !hasItemCols && registerSheet == null) {
                    registerSheet = sheet;
                    registerHeader = header;
                }
            }

            // Single-sheet fallback: a lone sheet carrying invoiceNo + item
            // columns (the collapsed one-row-per-invoice layout) was captured as
            // the items sheet; use it as the register with embedded items.
            if (registerSheet == null && itemsSheet != null
                    && itemsHeader.containsKey("invoiceNumber")) {
                return parseSingleSheet(itemsSheet, itemsHeader);
            }

            // Two-sheet layout where the register also embeds items (defensive).
            if (registerSheet != null && itemsSheet == null) {
                boolean embedded = registerHeader.containsKey("itemName")
                        && registerHeader.containsKey("qty") && registerHeader.containsKey("rate");
                if (embedded) {
                    return parseSingleSheet(registerSheet, registerHeader);
                }
            }

            if (registerSheet == null) {
                throw new BadRequestException(
                        "No Invoice Register sheet found — the workbook needs a sheet with an \"Invoice No\" column");
            }

            Map<String, List<ImportedItemRow>> itemsByInvoice = itemsSheet != null
                    ? groupItemsByInvoice(itemsSheet, itemsHeader)
                    : Map.of();

            return parseRegister(registerSheet, registerHeader, itemsByInvoice);
        } catch (IOException e) {
            log.warn("Failed to read import workbook", e);
            throw new BadRequestException("Could not read the Excel file — is it a valid .xlsx workbook?");
        }
    }

    private Map<String, Integer> readHeader(Sheet sheet) {
        // Header is expected on row 0 (banner row 1 is skipped by the exporter's
        // consumers; we tolerate a banner by scanning the first 3 rows for one
        // that resolves to an invoiceNumber/itemName column).
        for (int r = 0; r <= 2 && r < sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            Map<String, Integer> header = new LinkedHashMap<>();
            for (int c = 0; c < row.getLastCellNum(); c++) {
                Cell cell = row.getCell(c);
                if (cell == null) continue;
                String key = HEADER_ALIASES.get(normalizeHeader(asString(cell)));
                if (key != null && !header.containsKey(key)) header.put(key, c);
            }
            if (!header.isEmpty()) return header;
        }
        return Map.of();
    }

    private List<ImportedInvoiceRow> parseRegister(Sheet sheet, Map<String, Integer> header,
                                                   Map<String, List<ImportedItemRow>> itemsByInvoice) {
        List<ImportedInvoiceRow> rows = new ArrayList<>();
        int first = firstDataRow(sheet, header);
        for (int r = first; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null || isEmpty(row, header)) continue;
            ImportedInvoiceRow invoice = mapInvoiceRow(row, header, r + 1);
            String key = invoice.getInvoiceNumber();
            if (key != null && !key.isBlank()) {
                invoice.setItems(itemsByInvoice.getOrDefault(key.trim(), new ArrayList<>()));
            }
            rows.add(invoice);
        }
        return rows;
    }

    private List<ImportedInvoiceRow> parseSingleSheet(Sheet sheet, Map<String, Integer> header) {
        List<ImportedInvoiceRow> rows = new ArrayList<>();
        int first = firstDataRow(sheet, header);
        for (int r = first; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null || isEmpty(row, header)) continue;
            ImportedInvoiceRow invoice = mapInvoiceRow(row, header, r + 1);
            ImportedItemRow item = mapItemRow(row, header, r + 1);
            if (item.getItemName() != null || item.getQty() != null || item.getRate() != null) {
                invoice.setItems(new ArrayList<>(List.of(item)));
            }
            rows.add(invoice);
        }
        return rows;
    }

    private Map<String, List<ImportedItemRow>> groupItemsByInvoice(Sheet sheet, Map<String, Integer> header) {
        Map<String, List<ImportedItemRow>> byInvoice = new LinkedHashMap<>();
        int first = firstDataRow(sheet, header);
        for (int r = first; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null || isEmpty(row, header)) continue;
            ImportedItemRow item = mapItemRow(row, header, r + 1);
            String invoiceNo = asString(cellAt(row, header.get("invoiceNumber")));
            if (invoiceNo == null || invoiceNo.isBlank()) {
                item.getErrors().add("Item row has no Invoice No — cannot link to an invoice");
            } else {
                byInvoice.computeIfAbsent(invoiceNo.trim(), k -> new ArrayList<>()).add(item);
            }
        }
        return byInvoice;
    }

    /** First row after the header (and optional banner) that holds data. */
    private int firstDataRow(Sheet sheet, Map<String, Integer> header) {
        // findHeaderRow already scanned rows 0-2; data starts right after the
        // row whose cells produced this header. We re-derive it cheaply.
        for (int r = 0; r <= 2 && r < sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            for (int c = 0; c < row.getLastCellNum(); c++) {
                Cell cell = row.getCell(c);
                if (cell == null) continue;
                if (HEADER_ALIASES.containsKey(normalizeHeader(asString(cell)))) return r + 1;
            }
        }
        return 1;
    }

    private boolean isEmpty(Row row, Map<String, Integer> header) {
        for (Integer col : header.values()) {
            Cell cell = row.getCell(col);
            if (cell != null && cell.getCellType() != CellType.BLANK
                    && !asString(cell).isBlank()) return false;
        }
        return true;
    }

    private Cell cellAt(Row row, Integer col) {
        return col == null ? null : row.getCell(col);
    }

    private ImportedInvoiceRow mapInvoiceRow(Row row, Map<String, Integer> header, int rowNumber) {
        ImportedInvoiceRow r = new ImportedInvoiceRow();
        r.setRowNumber(rowNumber);
        r.setInvoiceNumber(asString(cellAt(row, header.get("invoiceNumber"))));
        r.setInvoiceType(asString(cellAt(row, header.get("invoiceType"))));
        r.setInvoiceDate(asDate(cellAt(row, header.get("invoiceDate"))));
        r.setDueDate(asDate(cellAt(row, header.get("dueDate"))));
        r.setCustomerName(asString(cellAt(row, header.get("customerName"))));
        r.setCustomerGstin(asString(cellAt(row, header.get("customerGstin"))));
        r.setCustomerEmail(asString(cellAt(row, header.get("customerEmail"))));
        r.setCustomerPhone(asString(cellAt(row, header.get("customerPhone"))));
        r.setCustomerAddress(asString(cellAt(row, header.get("customerAddress"))));
        r.setCustomerCity(asString(cellAt(row, header.get("customerCity"))));
        r.setCustomerState(asString(cellAt(row, header.get("customerState"))));
        r.setCustomerPincode(asString(cellAt(row, header.get("customerPincode"))));
        r.setPlaceOfSupply(asString(cellAt(row, header.get("placeOfSupply"))));
        r.setPaymentTerms(asString(cellAt(row, header.get("paymentTerms"))));
        r.setPaymentMode(asString(cellAt(row, header.get("paymentMode"))));
        r.setDiscountPercent(asDecimal(cellAt(row, header.get("discountPercent"))));
        r.setNotes(asString(cellAt(row, header.get("notes"))));
        r.setDeliveryNote(asString(cellAt(row, header.get("deliveryNote"))));
        r.setDeliveryNoteDate(asDate(cellAt(row, header.get("deliveryNoteDate"))));
        r.setReferenceNumber(asString(cellAt(row, header.get("referenceNumber"))));
        r.setBuyerOrderNumber(asString(cellAt(row, header.get("buyerOrderNumber"))));
        r.setDispatchDocNumber(asString(cellAt(row, header.get("dispatchDocNumber"))));
        r.setDispatchedThrough(asString(cellAt(row, header.get("dispatchedThrough"))));
        r.setTermsOfDelivery(asString(cellAt(row, header.get("termsOfDelivery"))));
        r.setOtherReferences(asString(cellAt(row, header.get("otherReferences"))));
        r.setDestination(asString(cellAt(row, header.get("destination"))));
        r.setAgreementNumber(asString(cellAt(row, header.get("agreementNumber"))));
        r.setAssetNumber(asString(cellAt(row, header.get("assetNumber"))));
        r.setSerialNumber(asString(cellAt(row, header.get("serialNumber"))));
        r.setVehicleNumber(asString(cellAt(row, header.get("vehicleNumber"))));
        r.setPeriodStart(asDate(cellAt(row, header.get("periodStart"))));
        r.setPeriodEnd(asDate(cellAt(row, header.get("periodEnd"))));
        r.setBillingPeriodStart(asDate(cellAt(row, header.get("billingPeriodStart"))));
        r.setBillingPeriodEnd(asDate(cellAt(row, header.get("billingPeriodEnd"))));
        r.setExpectedReturnDate(asDate(cellAt(row, header.get("expectedReturnDate"))));
        r.setDepositReference(asString(cellAt(row, header.get("depositReference"))));
        r.setPatientReference(asString(cellAt(row, header.get("patientReference"))));
        r.setServiceDate(asDate(cellAt(row, header.get("serviceDate"))));
        r.setTreatmentReference(asString(cellAt(row, header.get("treatmentReference"))));
        r.setReferringDoctor(asString(cellAt(row, header.get("referringDoctor"))));
        return r;
    }

    private ImportedItemRow mapItemRow(Row row, Map<String, Integer> header, int rowNumber) {
        ImportedItemRow item = new ImportedItemRow();
        item.setRowNumber(rowNumber);
        item.setItemName(asString(cellAt(row, header.get("itemName"))));
        item.setHsn(asString(cellAt(row, header.get("hsn"))));
        item.setUnit(asString(cellAt(row, header.get("unit"))));
        item.setQty(asDecimal(cellAt(row, header.get("qty"))));
        item.setRate(asDecimal(cellAt(row, header.get("rate"))));
        item.setGstPercentage(asDecimal(cellAt(row, header.get("gstPercentage"))));
        return item;
    }

    private String asString(Cell cell) {
        if (cell == null) return null;
        CellType type = cell.getCellType() == CellType.FORMULA
                ? cell.getCachedFormulaResultType() : cell.getCellType();
        switch (type) {
            case STRING: return trimToNull(cell.getStringCellValue());
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return String.valueOf(cell.getDateCellValue().toInstant()
                            .atZone(ZoneId.systemDefault()).toLocalDate());
                }
                double d = cell.getNumericCellValue();
                if (d == Math.floor(d) && !Double.isInfinite(d)) return String.valueOf((long) d);
                return String.valueOf(d);
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            default: return null;
        }
    }

    private BigDecimal asDecimal(Cell cell) {
        if (cell == null) return null;
        CellType type = cell.getCellType() == CellType.FORMULA
                ? cell.getCachedFormulaResultType() : cell.getCellType();
        if (type == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros();
        }
        if (type == CellType.STRING) {
            String s = trimToNull(cell.getStringCellValue());
            if (s == null) return null;
            try {
                return new BigDecimal(s.replace(",", ""));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private LocalDate asDate(Cell cell) {
        if (cell == null) return null;
        CellType type = cell.getCellType() == CellType.FORMULA
                ? cell.getCachedFormulaResultType() : cell.getCellType();
        if (type == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        String s = asString(cell);
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
