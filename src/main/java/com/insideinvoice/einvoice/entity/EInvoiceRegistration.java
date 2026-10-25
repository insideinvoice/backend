package com.insideinvoice.einvoice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "einvoice_registrations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EInvoiceRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(nullable = false, length = 15)
    private String gstin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EInvoiceProviderId provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EInvoiceEnvironment environment;

    @Column(name = "doc_type", nullable = false, length = 20)
    private String docType;

    @Column(name = "doc_number", nullable = false, length = 50)
    private String docNumber;

    @Column(name = "doc_date", nullable = false)
    private LocalDate docDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EInvoiceStatus status;

    @Column(length = 64)
    private String irn;

    @Column(name = "ack_number", length = 20)
    private String ackNumber;

    @Column(name = "ack_date")
    private LocalDateTime ackDate;

    @Column(name = "signed_invoice_json", columnDefinition = "TEXT")
    private String signedInvoiceJson;

    @Column(name = "signed_qr_data", columnDefinition = "TEXT")
    private String signedQrData;

    @Column(name = "provider_reference", length = 100)
    private String providerReference;

    @Column(name = "error_code", length = 30)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "registered_at")
    private LocalDateTime registeredAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancellation_reason", length = 200)
    private String cancellationReason;

    /** True when produced by the MOCK provider (tests/local only). */
    @Column(name = "is_mock", nullable = false)
    @Builder.Default
    private Boolean isMock = false;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false)
    private LocalDateTime updatedAt;
}
