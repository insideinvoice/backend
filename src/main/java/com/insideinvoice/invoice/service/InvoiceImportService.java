package com.insideinvoice.invoice.service;

import com.insideinvoice.customer.entity.Customer;
import com.insideinvoice.customer.repository.CustomerRepository;
import com.insideinvoice.exception.BadRequestException;
import com.insideinvoice.invoice.dto.request.CreateInvoiceRequest;
import com.insideinvoice.invoice.dto.request.ImportedInvoiceRow;
import com.insideinvoice.invoice.dto.request.ImportedItemRow;
import com.insideinvoice.invoice.dto.request.InvoiceItemRequest;
import com.insideinvoice.invoice.dto.response.ImportReport;
import com.insideinvoice.invoice.dto.response.ImportRowReport;
import com.insideinvoice.invoice.entity.Invoice;
import com.insideinvoice.invoice.entity.InvoiceType;
import com.insideinvoice.invoice.mapper.InvoiceMapper;
import com.insideinvoice.invoice.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Bulk invoice import: validate an uploaded workbook (dry-run) or commit it.
 *
 * <p>Totals are always recomputed server-side by {@link InvoiceMapper} from the
 * trusted inputs (qty, rate, GST%, discount%) — the spreadsheet's own money
 * columns are never read. Customers are matched by GSTIN first, then by name;
 * an unmatched customer is a blocking error (rows report it, nothing is
 * written for that row). Duplicate invoice numbers are skipped so re-importing
 * the same workbook is an idempotent no-op.
 */
@Service
@RequiredArgsConstructor
public class InvoiceImportService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceImportService.class);

    private final ExcelImportParser excelImportParser;
    private final CustomerRepository customerRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceMapper invoiceMapper;
    private final InvoiceNumberGenerator invoiceNumberGenerator;

    /** Validates the workbook and returns a per-row report without writing. */
    @Transactional(readOnly = true)
    public ImportReport validate(MultipartFile file, Long businessId) {
        List<ImportedInvoiceRow> parsed = excelImportParser.parse(file);
        return buildReport(parsed, businessId, true);
    }

    /**
     * Commits the workbook in one transaction: valid rows are created (totals
     * recomputed), duplicate invoice numbers are skipped, invalid rows are
     * blocked. Re-importing the same file is a safe no-op.
     */
    @Transactional
    public ImportReport commit(MultipartFile file, Long businessId, Long userId) {
        List<ImportedInvoiceRow> parsed = excelImportParser.parse(file);
        ImportReport report = buildReport(parsed, businessId, false);

        int created = 0;
        // The report rows are built in the same order as `parsed`, so the report
        // index maps 1:1 onto the parsed list (Excel row numbers are offset by
        // the header and cannot be used as list indices).
        List<ImportRowReport> rowReports = report.getRows();
        for (int i = 0; i < rowReports.size(); i++) {
            ImportRowReport rowReport = rowReports.get(i);
            if (!"CREATE".equals(rowReport.getAction())) continue;
            ImportedInvoiceRow source = parsed.get(i);
            Customer customer = resolveCustomer(source, businessId)
                    .orElseThrow(() -> new BadRequestException(
                            "Row " + rowReport.getRowNumber() + ": customer not found"));
            CreateInvoiceRequest request = toCreateRequest(source, customer);
            String number = source.getInvoiceNumber() != null && !source.getInvoiceNumber().isBlank()
                    ? source.getInvoiceNumber().trim()
                    : invoiceNumberGenerator.generateNextInvoiceNumber(businessId);
            Invoice invoice = invoiceMapper.toEntity(request, number, businessId, userId);
            invoiceRepository.save(invoice);
            created++;
        }

        report.setImported(created);
        log.info("Invoice import committed for businessId {}: {} created, {} skipped, {} blocked",
                businessId, created, report.getSkipped(), report.getBlocked());
        return report;
    }

    private ImportReport buildReport(List<ImportedInvoiceRow> parsed, Long businessId, boolean dryRun) {
        if (parsed.isEmpty()) {
            throw new BadRequestException("The workbook contains no invoice rows to import");
        }

        // Preload every referenced customer once so per-row matching is O(1).
        Map<String, Optional<Customer>> byGstin = new HashMap<>();
        Map<String, Optional<Customer>> byName = new HashMap<>();

        List<ImportRowReport> rows = new ArrayList<>();
        int skipped = 0;
        int blocked = 0;

        for (ImportedInvoiceRow row : parsed) {
            ImportRowReport report = new ImportRowReport();
            report.setRowNumber(row.getRowNumber());
            report.setInvoiceNumber(row.getInvoiceNumber());
            report.setCustomerName(row.getCustomerName());
            List<String> errors = new ArrayList<>();

            validateRow(row, errors);

            Optional<Customer> customer = resolveCustomerCached(row, businessId, byGstin, byName);
            if (customer.isEmpty()) {
                errors.add("Customer not found: " + describeCustomer(row)
                        + " — create the customer first or fix the GSTIN/name");
            }

            boolean duplicate = row.getInvoiceNumber() != null && !row.getInvoiceNumber().isBlank()
                    && invoiceRepository.existsByInvoiceNumberAndBusinessId(row.getInvoiceNumber().trim(), businessId);
            if (duplicate) {
                report.setWarnings(List.of("Invoice number " + row.getInvoiceNumber().trim()
                        + " already exists — row will be skipped"));
            }

            boolean valid = errors.isEmpty();
            report.setErrors(errors);
            report.setValid(valid);
            report.setItemCount(row.getItems() != null ? row.getItems().size() : 0);

            if (!valid) {
                report.setAction("BLOCKED");
                blocked++;
            } else if (duplicate) {
                report.setAction("SKIP");
                skipped++;
            } else {
                report.setAction("CREATE");
                // Recompute totals for the preview so the user sees the real
                // numbers the import will store, never the sheet's own.
                applyPreviewTotals(row, customer.orElse(null), report);
            }

            rows.add(report);
        }

        return ImportReport.builder()
                .dryRun(dryRun)
                .totalRows(parsed.size())
                .imported(0)
                .skipped(skipped)
                .blocked(blocked)
                .rows(rows)
                .build();
    }

    /** Field-level validation independent of customer/duplicate resolution. */
    private void validateRow(ImportedInvoiceRow row, List<String> errors) {
        if (row.getInvoiceType() == null || row.getInvoiceType().isBlank()) {
            errors.add("Invoice type is required (TAX_INVOICE or PROFORMA_INVOICE)");
        } else {
            String type = row.getInvoiceType().trim().toUpperCase().replace(' ', '_');
            try {
                InvoiceType.valueOf(type);
            } catch (IllegalArgumentException e) {
                errors.add("Invalid invoice type: " + row.getInvoiceType()
                        + " (expected TAX_INVOICE or PROFORMA_INVOICE)");
            }
        }
        if (row.getInvoiceDate() == null) errors.add("Invoice date is required");
        if (row.getDueDate() == null) errors.add("Due date is required");
        if (row.getItems() == null || row.getItems().isEmpty()) {
            errors.add("At least one line item is required");
        } else {
            for (ImportedItemRow item : row.getItems()) {
                if (item.getItemName() == null || item.getItemName().isBlank()) {
                    errors.add("Item row " + item.getRowNumber() + ": item name is required");
                }
                if (item.getQty() == null || item.getQty().signum() <= 0) {
                    errors.add("Item row " + item.getRowNumber() + ": quantity must be greater than 0");
                }
                if (item.getRate() == null || item.getRate().signum() <= 0) {
                    errors.add("Item row " + item.getRowNumber() + ": rate must be greater than 0");
                }
                if (item.getGstPercentage() == null) {
                    errors.add("Item row " + item.getRowNumber() + ": GST % is required");
                } else if (item.getGstPercentage().compareTo(BigDecimal.valueOf(40)) > 0) {
                    errors.add("Item row " + item.getRowNumber() + ": GST % must not exceed 40");
                }
            }
        }
        if (row.getDiscountPercent() != null
                && (row.getDiscountPercent().signum() < 0
                || row.getDiscountPercent().compareTo(BigDecimal.valueOf(100)) > 0)) {
            errors.add("Discount % must be between 0 and 100");
        }
    }

    private Optional<Customer> resolveCustomer(ImportedInvoiceRow row, Long businessId) {
        if (row.getCustomerGstin() != null && !row.getCustomerGstin().isBlank()) {
            Optional<Customer> byGst = customerRepository
                    .findByGstInIgnoreCaseAndBusinessId(row.getCustomerGstin().trim(), businessId);
            if (byGst.isPresent()) return byGst;
        }
        if (row.getCustomerName() != null && !row.getCustomerName().isBlank()) {
            List<Customer> matches = customerRepository
                    .findByNameIgnoreCaseAndBusinessId(row.getCustomerName().trim(), businessId);
            if (matches.size() == 1) return Optional.of(matches.get(0));
            if (matches.size() > 1) return Optional.empty(); // ambiguous
        }
        return Optional.empty();
    }

    private Optional<Customer> resolveCustomerCached(ImportedInvoiceRow row, Long businessId,
                                                     Map<String, Optional<Customer>> byGstin,
                                                     Map<String, Optional<Customer>> byName) {
        if (row.getCustomerGstin() != null && !row.getCustomerGstin().isBlank()) {
            String key = row.getCustomerGstin().trim().toLowerCase();
            return byGstin.computeIfAbsent(key,
                    k -> resolveCustomer(row, businessId));
        }
        if (row.getCustomerName() != null && !row.getCustomerName().isBlank()) {
            String key = row.getCustomerName().trim().toLowerCase();
            return byName.computeIfAbsent(key,
                    k -> resolveCustomer(row, businessId));
        }
        return Optional.empty();
    }

    private String describeCustomer(ImportedInvoiceRow row) {
        if (row.getCustomerGstin() != null && !row.getCustomerGstin().isBlank()) {
            return row.getCustomerName() != null
                    ? row.getCustomerName() + " (GSTIN " + row.getCustomerGstin() + ")"
                    : "GSTIN " + row.getCustomerGstin();
        }
        return row.getCustomerName() != null ? row.getCustomerName() : "(no name or GSTIN)";
    }

    /** Builds the preview totals via the real mapper so the report is accurate. */
    private void applyPreviewTotals(ImportedInvoiceRow row, Customer customer, ImportRowReport report) {
        try {
            CreateInvoiceRequest request = toCreateRequest(row, customer);
            Invoice preview = invoiceMapper.toEntity(request, row.getInvoiceNumber(), 0L, 0L);
            report.setSubtotal(preview.getSubtotal());
            report.setTaxAmount(preview.getTaxAmount());
            report.setGrandTotal(preview.getGrandTotal());
        } catch (Exception e) {
            log.debug("Could not compute preview totals for row {}", row.getRowNumber(), e);
        }
    }

    private CreateInvoiceRequest toCreateRequest(ImportedInvoiceRow row, Customer customer) {
        String type = row.getInvoiceType() == null ? "TAX_INVOICE"
                : row.getInvoiceType().trim().toUpperCase().replace(' ', '_');

        List<InvoiceItemRequest> items = new ArrayList<>();
        int sno = 1;
        for (ImportedItemRow item : row.getItems()) {
            items.add(InvoiceItemRequest.builder()
                    .sno(sno++)
                    .itemName(item.getItemName())
                    .hsn(item.getHsn())
                    .unit(item.getUnit())
                    .qty(item.getQty())
                    .rate(item.getRate())
                    .gstPercentage(item.getGstPercentage())
                    .build());
        }

        return CreateInvoiceRequest.builder()
                .customerId(customer.getId())
                .invoiceType(type)
                .invoiceDate(row.getInvoiceDate())
                .dueDate(row.getDueDate())
                .placeOfSupply(row.getPlaceOfSupply())
                .paymentTerms(row.getPaymentTerms())
                .paymentMode(row.getPaymentMode())
                .discountPercent(row.getDiscountPercent())
                .notes(row.getNotes())
                .invoiceNumber(row.getInvoiceNumber())
                .deliveryNote(row.getDeliveryNote())
                .deliveryNoteDate(row.getDeliveryNoteDate())
                .referenceNumber(row.getReferenceNumber())
                .buyerOrderNumber(row.getBuyerOrderNumber())
                .dispatchDocNumber(row.getDispatchDocNumber())
                .dispatchedThrough(row.getDispatchedThrough())
                .termsOfDelivery(row.getTermsOfDelivery())
                .otherReferences(row.getOtherReferences())
                .destination(row.getDestination())
                .agreementNumber(row.getAgreementNumber())
                .assetNumber(row.getAssetNumber())
                .serialNumber(row.getSerialNumber())
                .vehicleNumber(row.getVehicleNumber())
                .periodStart(row.getPeriodStart())
                .periodEnd(row.getPeriodEnd())
                .billingPeriodStart(row.getBillingPeriodStart())
                .billingPeriodEnd(row.getBillingPeriodEnd())
                .expectedReturnDate(row.getExpectedReturnDate())
                .depositReference(row.getDepositReference())
                .patientReference(row.getPatientReference())
                .serviceDate(row.getServiceDate())
                .treatmentReference(row.getTreatmentReference())
                .referringDoctor(row.getReferringDoctor())
                .items(items)
                .build();
    }
}
