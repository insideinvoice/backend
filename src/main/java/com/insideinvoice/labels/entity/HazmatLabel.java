package com.insideinvoice.labels.entity;

import com.insideinvoice.common.BaseEntity;
import com.insideinvoice.labels.enums.ColorMode;
import com.insideinvoice.labels.enums.HazmatLabelType;
import com.insideinvoice.labels.enums.LabelStatus;
import com.insideinvoice.labels.enums.TransportMode;
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

@Entity
@Table(name = "hazmat_labels", indexes = {
        @Index(name = "idx_hazmat_labels_business_id", columnList = "business_id"),
        @Index(name = "idx_hazmat_labels_un_number", columnList = "un_number"),
        @Index(name = "idx_hazmat_labels_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HazmatLabel extends BaseEntity {

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "invoice_id")
    private Long invoiceId;

    @Column(name = "label_number", nullable = false, length = 40)
    private String labelNumber;

    @Column(name = "un_number", length = 10)
    private String unNumber;

    @Column(name = "proper_shipping_name", nullable = false, length = 200)
    private String properShippingName;

    @Column(name = "technical_name", length = 200)
    private String technicalName;

    @Column(name = "hazard_class", nullable = false, length = 10)
    private String hazardClass;

    @Column(name = "division", length = 4)
    private String division;

    @Column(name = "compat_group", length = 4)
    private String compatGroup;

    @Column(name = "packing_group", length = 4)
    private String packingGroup;

    @Column(name = "subsidiary_risks", length = 400)
    private String subsidiaryRisks;

    @Column(name = "net_quantity", length = 40)
    private String netQuantity;

    @Column(name = "package_count", nullable = false)
    private Integer packageCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "transport_mode", nullable = false, length = 10)
    private TransportMode transportMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "label_type", nullable = false, length = 24)
    private HazmatLabelType labelType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "consignor_json")
    private String consignorJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "consignee_json")
    private String consigneeJson;

    @Column(name = "emergency_phone", length = 40)
    private String emergencyPhone;

    @Column(name = "gra_code", length = 40)
    private String graCode;

    @Column(name = "erg_guide", length = 10)
    private String ergGuide;

    @Column(name = "lithium_wh", precision = 8, scale = 2)
    private BigDecimal lithiumWh;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "radiation_category", length = 8)
    private String radiationCategory;

    @Column(name = "limited_quantity_code", length = 40)
    private String limitedQuantityCode;

    @Column(name = "lithium_un_list", length = 120)
    private String lithiumUnList;

    @Column(name = "lithium_packing_instruction", length = 40)
    private String lithiumPackingInstruction;

    @Column(name = "generated_by", length = 80)
    private String generatedBy;

    @Column(name = "overpack", nullable = false)
    private Boolean overpack;

    @Column(name = "marine_pollutant", nullable = false)
    private Boolean marinePollutant;

    @Column(name = "label_size", nullable = false, length = 20)
    private String labelSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "color_mode", nullable = false, length = 12)
    private ColorMode colorMode;

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
