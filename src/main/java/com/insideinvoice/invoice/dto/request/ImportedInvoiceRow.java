package com.insideinvoice.invoice.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * One invoice as read from the Invoice Register (or "Invoices") sheet of an
 * import workbook, plus the line items joined from the Invoice Items sheet by
 * invoice number. Customer identity is carried as raw sheet text (name, GSTIN,
 * email, phone) — the import service resolves it to a {@code Customer} id,
 * matching by GSTIN first then name, so the workbook never has to contain
 * internal ids.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportedInvoiceRow {

    /** 1-based Excel row number on the register sheet, echoed in the report. */
    private int rowNumber;

    private String invoiceNumber;

    private String invoiceType;

    private LocalDate invoiceDate;

    private LocalDate dueDate;

    private String customerName;

    private String customerGstin;

    private String customerEmail;

    private String customerPhone;

    private String customerAddress;

    private String customerCity;

    private String customerState;

    private String customerPincode;

    private String placeOfSupply;

    private String paymentTerms;

    private String paymentMode;

    private BigDecimal discountPercent;

    private String notes;

    private String deliveryNote;

    private LocalDate deliveryNoteDate;

    private String referenceNumber;

    private String buyerOrderNumber;

    private String dispatchDocNumber;

    private String dispatchedThrough;

    private String termsOfDelivery;

    private String otherReferences;

    private String destination;

    /* ---- V29 industry-extended reference fields (Rental & Healthcare). ---- */

    private String agreementNumber;

    private String assetNumber;

    private String serialNumber;

    private String vehicleNumber;

    private LocalDate periodStart;

    private LocalDate periodEnd;

    private LocalDate billingPeriodStart;

    private LocalDate billingPeriodEnd;

    private LocalDate expectedReturnDate;

    private String depositReference;

    private String patientReference;

    private LocalDate serviceDate;

    private String treatmentReference;

    private String referringDoctor;

    /** Line items joined from the Invoice Items sheet. */
    @Builder.Default
    private List<ImportedItemRow> items = new ArrayList<>();

    /** Errors collected for this invoice row (empty when valid). */
    @Builder.Default
    private List<String> errors = new ArrayList<>();

    public boolean isValid() {
        return errors == null || errors.isEmpty();
    }
}
