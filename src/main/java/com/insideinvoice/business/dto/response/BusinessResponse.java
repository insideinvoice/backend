package com.insideinvoice.business.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessResponse {

    private Long id;
    private String businessName;
    private String ownerName;
    private String gstIn;
    private String phone;
    private String email;
    private String website;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String country;
    private String pincode;
    private String invoicePrefix;
    private Long nextInvoiceSequence;
    private String signature;
    private String bankName;
    private String accountNo;
    private String branch;
    private String ifsc;
    private String bankAddress;
    private String upiId;
    private String specialistIn;
    private Boolean specialistInEnabled;
    private String invoiceTemplate;
    private String printSettings;
    private String industry;
    /** Display-only HSN/SAC visibility; never affects stored codes or taxes. */
    private Boolean showHnSac;
    /** Resolved configuration for {@link #industry} — same package, no import needed. */
    private IndustryConfigResponse industryConfig;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
