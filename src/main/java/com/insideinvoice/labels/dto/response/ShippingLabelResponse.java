package com.insideinvoice.labels.dto.response;

import com.insideinvoice.labels.enums.LabelPreset;
import com.insideinvoice.labels.enums.LabelStatus;
import com.insideinvoice.labels.render.LabelAddress;
import com.insideinvoice.labels.render.ShippingLabelSpec;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShippingLabelResponse {

    private Long id;
    private String labelNumber;
    private Long invoiceId;
    private LabelPreset preset;
    private String labelSize;
    private Integer dpi;
    private String carrier;
    private String serviceLevel;
    private String serviceCode;
    private String trackingNumber;
    private LocalDate shipDate;
    private LabelAddress shipFrom;
    private LabelAddress shipTo;
    private BigDecimal packageWeightKg;
    private String dimsCm;
    private Integer cartonCount;
    private Boolean thermalMode;
    private Boolean printBorder;
    private LabelStatus status;
    private String pdfSha256;
    private boolean hasPdf;
    private String generatedBy;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String billingType;
    private String codAmount;
    private String invoiceNo;
    private String poNumber;
    private String ref1;
    private String ref2;
    private String notes;
    private Boolean thisWayUp;
    private Boolean fragile;
    private Boolean keepDry;
    private Boolean doNotStack;
    private Boolean handleWithCare;
    private ShippingLabelSpec.FbaFields fba;
    private ShippingLabelSpec.Gs1Fields gs1;
    private Integer fnskuCopies;
    private List<ShippingLabelSpec.FnskuItem> fnskuItems;
}
