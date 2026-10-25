package com.insideinvoice.labels.dto.request;

import com.insideinvoice.labels.enums.ColorMode;
import com.insideinvoice.labels.enums.HazmatLabelType;
import com.insideinvoice.labels.enums.TransportMode;
import com.insideinvoice.labels.render.LabelAddress;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateHazmatLabelRequest {

    private Long invoiceId;
    /**
     * Optional display number for the PREVIEW render only. The form sends the
     * saved label's real number (e.g. HZL-2026-0001) when editing, so downloads
     * from the editor print the actual number; ignored by create(), which always
     * assigns its own via nextLabelNumber().
     */
    private String labelNumber;
    private HazmatLabelType labelType;
    private String labelSize;
    private ColorMode colorMode;
    private TransportMode transportMode;
    private String unNumber;
    @NotBlank
    private String properShippingName;
    private String technicalName;
    @NotBlank
    private String hazardClass;
    private String division;
    private String compatGroup;
    private String packingGroup;
    private String subsidiaryRisks;
    private String netQuantity;
    private Integer packageCount;
    private LabelAddress consignor;
    private LabelAddress consignee;
    private String emergencyPhone;
    private String graCode;
    private String ergGuide;
    private BigDecimal lithiumWh;
    private String notes;
    private Boolean overpack;
    private Boolean marinePollutant;
    private String radiationCategory;
    private String limitedQuantityCode;
    private String lithiumUnList;
    private String lithiumPackingInstruction;
    private String generatedBy;
}
