package com.insideinvoice.customer.dto.request;

import jakarta.validation.constraints.Email;
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
public class CreateCustomerRequest {

    @NotBlank(message = "Customer name is required")
    @Size(max = 255, message = "Customer name must not exceed 255 characters")
    private String name;

    @Email(message = "Email must be valid")
    private String email;

    @Pattern(regexp = "^[+]?[0-9\\s\\-()]{10,18}$", message = "Phone must be a valid phone number")
    private String phone;

    private String billingAddress;

    private String shippingAddress;

    @Pattern(regexp = "^[0-9]{2}[A-Za-z]{5}[0-9]{4}[A-Za-z]{1}[1-9A-Za-z]{1}Z[0-9A-Za-z]{1}$",
            message = "GSTIN must be a valid GST identification number")
    private String gstIn;

    private String city;

    private String state;

    private String country;

    @Pattern(regexp = "^[0-9]{6}$", message = "Pincode must be a valid 6-digit pincode")
    private String pincode;
}
