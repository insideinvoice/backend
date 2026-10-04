package com.insideinvoice.labels.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoredShippingRefs {

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
}
