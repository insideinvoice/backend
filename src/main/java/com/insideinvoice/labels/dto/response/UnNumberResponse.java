package com.insideinvoice.labels.dto.response;

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
public class UnNumberResponse {

    private String unNumber;
    private String properShippingName;
    private String hazardClass;
    private String division;
    private String compatGroup;
    private String packingGroup;
    private String subsidiaryRisks;
    private String ergGuide;
    private String ltdQty;
    private Boolean paxAllowed;
    private Boolean caoAllowed;
    private String specialProvisions;
}
