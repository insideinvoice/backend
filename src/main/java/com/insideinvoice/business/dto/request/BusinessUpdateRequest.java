package com.insideinvoice.business.dto.request;

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
public class BusinessUpdateRequest {

    @Size(max = 255, message = "Business name must not exceed 255 characters")
    private String businessName;

    @Pattern(regexp = "^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$",
            message = "GSTIN must be a valid GST identification number")
    private String gstIn;

    @Size(max = 20, message = "Phone must not exceed 20 characters")
    @Pattern(regexp = "^[+]?[0-9]{10,15}$", message = "Phone must be a valid phone number")
    private String phone;

    private String email;

    private String website;

    private String addressLine1;

    private String addressLine2;

    private String city;

    private String state;

    private String country;

    @Pattern(regexp = "^[0-9]{6}$", message = "Pincode must be a valid 6-digit pincode")
    private String pincode;

    @Size(min = 1, max = 10, message = "Invoice prefix must be between 1 and 10 characters")
    @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "Invoice prefix must be alphanumeric with hyphens only")
    private String invoicePrefix;
}
