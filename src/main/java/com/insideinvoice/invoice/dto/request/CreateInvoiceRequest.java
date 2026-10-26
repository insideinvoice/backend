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
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateInvoiceRequest implements InvoiceExtendedFields {

    // Either an existing customer id OR a `customer` object (new customer) must
    // be provided. Validated in InvoiceServiceImpl.
    private Long customerId;

    /**
     * Optional: create a new customer on the fly (or reuse an existing one
     * matched by phone/email within the same business). When customerId is
     * null this object is required.
     */
    @jakarta.validation.Valid
    private com.insideinvoice.customer.dto.request.CreateCustomerRequest customer;

    @NotBlank(message = "Invoice type is required")
    private String invoiceType;

    @NotNull(message = "Invoice date is required")
    private LocalDate invoiceDate;

    // Due date is a commercial field, not a Rule 46 statutory one — optional.
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

    // GST Rule 46 compliance fields
    private String shippingAddress;
    private Boolean reverseCharge = false;
    private BigDecimal cessPercentage;
    private String ewayBillNumber;

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

    /**
     * Invoice-level GST tax mode: CGST_SGST (intra-state) or IGST
     * (inter-state). Optional — absent means "derive it from the supplier
     * state vs place of supply" (or leave it unrecorded when undeterminable).
     * Never affects the stored totals: the total tax is mode-independent.
     */
    @Size(max = 20, message = "Tax mode must not exceed 20 characters")
    private String taxMode;

    @jakarta.validation.constraints.DecimalMin(value = "0", message = "Discount percent must be >= 0")
    @jakarta.validation.constraints.DecimalMax(value = "100", message = "Discount percent must be <= 100")
    private java.math.BigDecimal discountPercent;

    /**
     * Discount presentation (V45): FULL = invoice-level percent (above),
     * INDIVIDUAL = per-line percents on each item. Absent/null = none
     * configured — every legacy client and row.
     */
    private com.insideinvoice.invoice.entity.DiscountType discountType;

    /**
     * Optional per-invoice seal: ROUND (the business's round company seal),
     * STAMP (the rubber stamp) or NONE (no mark on this invoice). Absent/null
     * = follow the business's saved Settings choice.
     */
    @Size(max = 20, message = "Seal type must not exceed 20 characters")
    private String sealType;

    @Valid
    @NotEmpty(message = "At least one invoice item is required")
    private List<InvoiceItemRequest> items;
}
