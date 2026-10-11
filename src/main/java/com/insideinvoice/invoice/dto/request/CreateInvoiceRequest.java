package com.insideinvoice.invoice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateInvoiceRequest implements InvoiceExtendedFields {

    @NotNull(message = "Customer ID is required")
    private Long customerId;

    @NotBlank(message = "Invoice type is required")
    private String invoiceType;

    @NotNull(message = "Invoice date is required")
    private LocalDate invoiceDate;

    @NotNull(message = "Due date is required")
    private LocalDate dueDate;

    private String placeOfSupply;

    private String paymentTerms;

    @Size(max = 1000, message = "Notes must not exceed 1000 characters")
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

    private String invoiceNumber;

    private String paymentMode;

    /* ---- V29: industry-extended optional fields (Rental & Healthcare).
     * All optional, reference/display only, never part of totals. ---- */

    @Size(max = 100, message = "Agreement number must not exceed 100 characters")
    private String agreementNumber;

    @Size(max = 100, message = "Asset number must not exceed 100 characters")
    private String assetNumber;

    @Size(max = 100, message = "Serial number must not exceed 100 characters")
    private String serialNumber;

    @Size(max = 60, message = "Vehicle number must not exceed 60 characters")
    private String vehicleNumber;

    private LocalDate periodStart;

    private LocalDate periodEnd;

    private LocalDate billingPeriodStart;

    private LocalDate billingPeriodEnd;

    private LocalDate expectedReturnDate;

    @Size(max = 100, message = "Deposit reference must not exceed 100 characters")
    private String depositReference;

    @Size(max = 100, message = "Patient reference must not exceed 100 characters")
    private String patientReference;

    private LocalDate serviceDate;

    @Size(max = 100, message = "Treatment reference must not exceed 100 characters")
    private String treatmentReference;

    @Size(max = 150, message = "Referring doctor must not exceed 150 characters")
    private String referringDoctor;

    /**
     * Construction billing representation: COMPLETE_PROJECT or ITEMIZED.
     * Display/editor hint only; totals always come from the line items.
     */
    @Size(max = 20, message = "Billing mode must not exceed 20 characters")
    private String billingMode;

    @jakarta.validation.constraints.DecimalMin(value = "0", message = "Discount percent must be >= 0")
    @jakarta.validation.constraints.DecimalMax(value = "100", message = "Discount percent must be <= 100")
    private java.math.BigDecimal discountPercent;

    @Valid
    @NotEmpty(message = "At least one invoice item is required")
    private List<InvoiceItemRequest> items;
}
