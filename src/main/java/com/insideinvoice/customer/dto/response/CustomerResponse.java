package com.insideinvoice.customer.dto.response;

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
public class CustomerResponse {

    private Long id;
    private String name;
    private String email;
    private String phone;
    private String billingAddress;
    private String shippingAddress;
    private String gstIn;
    private String city;
    private String state;
    private String country;
    private String pincode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
