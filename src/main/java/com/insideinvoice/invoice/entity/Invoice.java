package com.insideinvoice.invoice.entity;

import com.insideinvoice.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "invoices", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"business_id", "invoice_number"})
}, indexes = {
        @Index(name = "idx_invoices_business_id", columnList = "business_id"),
        @Index(name = "idx_invoices_customer_id", columnList = "customer_id"),
        @Index(name = "idx_invoices_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice extends BaseEntity {

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "invoice_number", nullable = false)
    private String invoiceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_type", nullable = false)
    private InvoiceType invoiceType;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "subtotal", nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "tax_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "grand_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal grandTotal;

    @Column(name = "payment_terms")
    private String paymentTerms;

    @Column(name = "notes")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private InvoiceStatus status = InvoiceStatus.DRAFT;

    @Column(name = "place_of_supply")
    private String placeOfSupply;

    @Column(name = "delivery_note")
    private String deliveryNote;

    @Column(name = "delivery_note_date")
    private LocalDate deliveryNoteDate;

    @Column(name = "reference_number")
    private String referenceNumber;

    @Column(name = "buyer_order_number")
    private String buyerOrderNumber;

    @Column(name = "dispatch_doc_number")
    private String dispatchDocNumber;

    @Column(name = "dispatched_through")
    private String dispatchedThrough;

    @Column(name = "terms_of_delivery")
    private String termsOfDelivery;

    @Column(name = "other_references")
    private String otherReferences;

    @Column(name = "destination")
    private String destination;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "payment_mode", length = 20)
    private String paymentMode;

    /** Opaque public share token (base64url of 256 random bits). NULL = never shared. */
    @Column(name = "share_token", length = 64, unique = true)
    private String shareToken;

    @Column(name = "share_enabled", nullable = false)
    @Builder.Default
    private Boolean shareEnabled = false;

    @Column(name = "share_created_at")
    private java.time.LocalDateTime shareCreatedAt;

    @Column(name = "share_revoked_at")
    private java.time.LocalDateTime shareRevokedAt;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<InvoiceItem> items = new ArrayList<>();
}
