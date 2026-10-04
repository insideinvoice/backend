package com.insideinvoice.labels.dto.response;

import com.insideinvoice.labels.enums.ColorMode;
import com.insideinvoice.labels.enums.HazmatLabelType;
import com.insideinvoice.labels.enums.LabelStatus;
import com.insideinvoice.labels.enums.TransportMode;
import com.insideinvoice.labels.render.LabelAddress;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HazmatLabelResponse {

    private Long id;
    private String labelNumber;
    private Long invoiceId;
    private HazmatLabelType labelType;
    private String labelSize;
    private ColorMode colorMode;
    private TransportMode transportMode;
    private String unNumber;
    private String properShippingName;
    private String technicalName;
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
    private LabelStatus status;
    private String pdfSha256;
    private boolean hasPdf;
    private String generatedBy;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
