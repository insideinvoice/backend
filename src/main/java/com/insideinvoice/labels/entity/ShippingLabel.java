package com.insideinvoice.labels.entity;

import com.insideinvoice.common.BaseEntity;
import com.insideinvoice.labels.enums.LabelPreset;
import com.insideinvoice.labels.enums.LabelStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "shipping_labels", indexes = {
        @Index(name = "idx_shipping_labels_business_id", columnList = "business_id"),
        @Index(name = "idx_shipping_labels_invoice_id", columnList = "invoice_id"),
        @Index(name = "idx_shipping_labels_created_at", columnList = "created_at"),
        @Index(name = "idx_shipping_labels_status", columnList = "status"),
        @Index(name = "idx_shipping_labels_carrier", columnList = "carrier")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShippingLabel extends BaseEntity {

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "invoice_id")
    private Long invoiceId;

    @Column(name = "label_number", nullable = false, length = 40)
    private String labelNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "preset", nullable = false, length = 40)
    private LabelPreset preset;

    @Column(name = "label_size", nullable = false, length = 20)
    private String labelSize;

    @Column(name = "dpi", nullable = false)
    private Integer dpi;

    @Column(name = "carrier", length = 60)
    private String carrier;

    @Column(name = "service_level", length = 80)
    private String serviceLevel;

    @Column(name = "tracking_number", length = 80)
    private String trackingNumber;

    @Column(name = "ship_date")
    private LocalDate shipDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "from_json")
    private String fromJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "to_json")
    private String toJson;

    @Column(name = "package_weight_kg", precision = 8, scale = 3)
    private BigDecimal packageWeightKg;

    @Column(name = "dims_cm")
    private String dimsCm;

    @Column(name = "carton_index", nullable = false)
    private Integer cartonIndex;

    @Column(name = "carton_total", nullable = false)
    private Integer cartonTotal;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reference_fields")
    private String referenceFields;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "barcode_payloads")
    private String barcodePayloads;

    @Column(name = "thermal_mode", nullable = false)
    private Boolean thermalMode;

    @Column(name = "service_code", length = 40)
    private String serviceCode;

    @Column(name = "generated_by", length = 80)
    private String generatedBy;

    @Column(name = "fnsku_copies")
    private Integer fnskuCopies;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fba_json")
    private String fbaJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gs1_json")
    private String gs1Json;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fnsku_items")
    private String fnskuItemsJson;

    @Column(name = "print_border", nullable = false)
    private Boolean printBorder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private LabelStatus status;

    @Column(name = "pdf_sha256", length = 64)
    private String pdfSha256;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "deleted_at")
    private java.time.OffsetDateTime deletedAt;
}
