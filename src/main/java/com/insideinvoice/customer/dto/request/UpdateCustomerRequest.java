package com.insideinvoice.customer.dto.request;

import jakarta.validation.constraints.Email;
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
public class UpdateCustomerRequest {

    // Create requires a name; update must not allow blanking it. @Pattern (not
    // @NotBlank) so an omitted name still means "leave unchanged" for partial updates.
    @Pattern(regexp = ".*\\S.*", message = "Customer name is required")
    @Size(max = 255, message = "Customer name must not exceed 255 characters")
    private String name;

    @Email(message = "Email must be valid")
    private String email;

    @Pattern(regexp = "^[0-9]{10}$", message = "Phone must be exactly 10 digits")
    private String phone;

    private String billingAddress;

    private String shippingAddress;

    private String gstIn;

    private String city;

    private String state;

    private String country;

    @Pattern(regexp = "^[0-9]{6}$", message = "Pincode must be a valid 6-digit pincode")
    private String pincode;
}
