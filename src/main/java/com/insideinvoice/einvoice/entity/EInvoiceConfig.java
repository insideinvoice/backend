package com.insideinvoice.einvoice.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import lombok.*;

@Entity
@Table(name = "einvoice_configs",
       uniqueConstraints = @UniqueConstraint(columnNames = {"business_id", "gstin", "environment"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EInvoiceConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = false;

    /** Register eligible invoices automatically on generation. */
    @Column(name = "auto_register", nullable = false)
    @Builder.Default
    private Boolean autoRegister = false;

    /** AES-GCM encrypted provider credentials JSON. Never returned by APIs. */
    @Column(name = "credentials_encrypted", columnDefinition = "TEXT")
    private String credentialsEncrypted;

    @Column(name = "connection_status", nullable = false, length = 30)
    @Builder.Default
    private String connectionStatus = "NOT_TESTED";

    @Column(name = "last_connection_test_at")
    private java.time.LocalDateTime lastConnectionTestAt;

    @Column(name = "last_connection_error", columnDefinition = "TEXT")
    private String lastConnectionError;

    /** Eligibility overrides / provider-specific flags (jsonb). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private String settings = "{}";

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private java.time.LocalDateTime createdAt;

    /**
     * Last WRITE, not last insert. The column default only stamps creation
     * time, so without this annotation an updated row kept its INSERT
     * timestamp — and every "which config is the active one?" lookup
     * (ORDER BY updated_at DESC) kept returning the row that was merely
     * created last. Symptom: switching the provider to Mock saved fine but
     * the test/status/register calls still used the older IRIS row.
     */
    @Column(name = "updated_at", insertable = false)
    @UpdateTimestamp
    private java.time.LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;
}
