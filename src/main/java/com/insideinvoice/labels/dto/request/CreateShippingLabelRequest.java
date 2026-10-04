package com.insideinvoice.labels.dto.request;

import com.insideinvoice.labels.enums.LabelPreset;
import com.insideinvoice.labels.render.LabelAddress;
import com.insideinvoice.labels.render.ShippingLabelSpec;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateShippingLabelRequest {

    private Long invoiceId;
    private LabelPreset preset;
    private String sizeKey;
    private Integer dpi;
    private Boolean thermalMode;
    private Boolean printBorder;
    private LabelAddress shipFrom;
    private LabelAddress shipTo;
    private String carrier;
    private String serviceLevel;
    private String serviceCode;
    private String trackingNumber;
    private LocalDate shipDate;
    private String weightKg;
    private String dimsCm;
    private Integer cartonCount;
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
    @Valid
    private List<ShippingLabelSpec.FnskuItem> fnskuItems;
    private String generatedBy;
}
