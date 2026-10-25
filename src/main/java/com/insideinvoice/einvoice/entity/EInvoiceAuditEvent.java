package com.insideinvoice.einvoice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Append-only audit trail. Never exposes credentials or tokens. */
@Entity
@Table(name = "einvoice_audit_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EInvoiceAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "invoice_id")
    private Long invoiceId;

    @Column(name = "registration_id")
    private Long registrationId;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_type", nullable = false, length = 10)
    @Builder.Default
    private String actorType = "USER";

    @Column(name = "from_status", length = 30)
    private String fromStatus;

    @Column(name = "to_status", length = 30)
    private String toStatus;

    @Column(length = 40)
    private String operation;

    @Column(name = "provider_result", length = 20)
    private String providerResult;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;
}
