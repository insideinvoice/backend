package com.insideinvoice.invoice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.insideinvoice.invoice.entity.DiscountType;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceResponse {

    private Long id;
    private Long businessId;
    private String invoiceNumber;
    private String invoiceType;
    private Long customerId;
    private String customerName;
    /**
     * Phone of the linked customer record, carried on the row so the list screen's
     * share-by-SMS reads it directly (the invoice only stores customerId). Null when
     * the customer has no phone on file.
     */
    private String customerPhone;
    private LocalDate invoiceDate;
    private LocalDate dueDate;
    private BigDecimal subtotal;
    private BigDecimal taxAmount;
    private BigDecimal grandTotal;
    private BigDecimal discountPercent;
    /** FULL | INDIVIDUAL | null (none/legacy) — see entity.DiscountType. */
    private DiscountType discountType;
    private String shippingAddress;
    private Boolean reverseCharge;
    private BigDecimal cessPercentage;
    private BigDecimal cessAmount;
    private String ewayBillNumber;

    /** IRP registration results (V49) — provider-generated, read-only for users. */
    private String irn;
    private String ackNo;
    private java.time.LocalDate ackDate;
    private String einvoiceStatus;

    private String paymentTerms;
    private String notes;
    private String status;
    private String placeOfSupply;
    private String deliveryNote;
    private LocalDate deliveryNoteDate;
    private String referenceNumber;
    private String buyerOrderNumber;
    private String dispatchDocNumber;
    private String dispatchedThrough;
    private String termsOfDelivery;
    private String otherReferences;
    private String destination;
    /** V29 industry-extended optional fields (Rental & Healthcare); null when unused. */
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
    private Long createdBy;
    private String paymentMode;
    /** Construction billing representation (COMPLETE_PROJECT / ITEMIZED); null = standard. */
    private String billingMode;
    /**
     * Invoice-level GST tax mode (CGST_SGST / IGST); null = not recorded
     * (legacy rows — renderers fall back to the place-of-supply heuristic).
     */
    private String taxMode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<InvoiceItemResponse> items;
}
