package com.insideinvoice.business.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
public class BusinessSetupRequest {

    @NotBlank(message = "Business name is required")
    @Size(max = 255, message = "Business name must not exceed 255 characters")
    private String businessName;

    private String gstIn;

    @Size(max = 20, message = "Phone must not exceed 20 characters")
    private String phone;

    private String email;

    private String website;

    private String addressLine1;

    private String addressLine2;

    private String city;

    private String state;

    private String country;

    private String pincode;

    @Size(max = 50, message = "Invoice convention must not exceed 50 characters")
    @Pattern(regexp = "^[A-Za-z0-9._/-]*$", message = "Invoice convention may only contain letters, numbers and - . / _")
    private String invoicePrefix;

    private String bankName;

    private String accountNo;

    private String branch;

    private String ifsc;

    private String bankAddress;

    private String upiId;

    /**
     * Stable industry profile id. Optional on setup: a missing value falls back
     * to OTHER so onboarding can never fail on this field.
     */
    @Pattern(regexp = com.insideinvoice.business.industry.Industry.ID_PATTERN,
            message = "Unknown industry")
    private String industry;

    @Size(max = 500, message = "Specialist in must not exceed 500 characters")
    private String specialistIn;

    private Boolean specialistInEnabled;
}
