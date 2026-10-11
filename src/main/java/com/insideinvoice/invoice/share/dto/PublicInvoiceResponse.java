package com.insideinvoice.invoice.share.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Allow-listed payload for anonymous public invoice links.
 *
 * <p>Only fields that appear on the invoice document itself (or that the recipient needs to
 * understand it) are present. Internal identifiers (invoice id, business id, customer id,
 * created-by user), share tokens, audit timestamps and authentication data are deliberately
 * absent and are never populated from entities directly - each field is assigned explicitly
 * by the assembler.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicInvoiceResponse {

    private String invoiceNumber;
    private String invoiceType;
    private String status;
    private LocalDate invoiceDate;
    private LocalDate dueDate;

    private BigDecimal subtotal;
    private BigDecimal taxAmount;
    private BigDecimal grandTotal;
    private BigDecimal discountPercent;

    private String template;
    private String paperSize;

    /** Construction billing representation (COMPLETE_PROJECT / ITEMIZED); null = standard. */
    private String billingMode;

    /**
     * Display-only HSN/SAC switch snapshot; TRUE (default) keeps the historical
     * rendering. Never affects stored codes or GST calculations.
     */
    private Boolean showHnSac;

    private String paymentTerms;
    private String paymentMode;
    private String placeOfSupply;
    private String destination;
    private String termsOfDelivery;
    private String deliveryNote;
    private LocalDate deliveryNoteDate;
    private String referenceNumber;
    private String buyerOrderNumber;
    private String dispatchDocNumber;
    private String dispatchedThrough;
    private String otherReferences;
    private String notes;

    /** V29 industry-extended reference fields (Rental & Healthcare); null when unused. */
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

    private Seller seller;
    private Buyer buyer;
    private List<Item> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Seller {
        private String businessName;
        private String gstIn;
        private String phone;
        private String email;
        private String website;
        private String addressLine1;
        private String addressLine2;
        private String city;
        private String state;
        private String country;
        private String pincode;
        private String specialistIn;
        private Boolean specialistInEnabled;
        /** Display-only HSN/SAC switch, mirrored here so public templates can read it from the seller block. */
        private Boolean showHnSac;
        private String signature;
        private String industry;
        private PaymentInstructions payment;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PaymentInstructions {
        private String bankName;
        private String branch;
        private String accountNo;
        private String ifsc;
        private String bankAddress;
        private String upiId;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Buyer {
        private String name;
        private String gstIn;
        private String phone;
        private String email;
        private String billingAddress;
        private String city;
        private String state;
        private String country;
        private String pincode;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Item {
        private Integer sno;
        private String itemName;
        private String hsn;
        private String unit;
        private BigDecimal qty;
        private BigDecimal rate;
        private BigDecimal gstPercentage;
        private BigDecimal taxableValue;
        private BigDecimal taxAmount;
        private BigDecimal total;
    }
}
