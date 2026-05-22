package com.insideinvoice.customer.mapper;

import com.insideinvoice.customer.dto.request.CreateCustomerRequest;
import com.insideinvoice.customer.dto.request.UpdateCustomerRequest;
import com.insideinvoice.customer.dto.response.CustomerResponse;
import com.insideinvoice.customer.entity.Customer;
import org.springframework.stereotype.Component;

@Component
public class CustomerMapper {

    public Customer toEntity(CreateCustomerRequest request, Long businessId) {
        return Customer.builder()
                .businessId(businessId)
                .name(request.getName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .billingAddress(request.getBillingAddress())
                .shippingAddress(request.getShippingAddress())
                .gstIn(request.getGstIn())
                .city(request.getCity())
                .state(request.getState())
                .country(request.getCountry())
                .pincode(request.getPincode())
                .build();
    }

    public CustomerResponse toResponse(Customer customer) {
        return CustomerResponse.builder()
                .id(customer.getId())
                .name(customer.getName())
                .email(customer.getEmail())
                .phone(customer.getPhone())
                .billingAddress(customer.getBillingAddress())
                .shippingAddress(customer.getShippingAddress())
                .gstIn(customer.getGstIn())
                .city(customer.getCity())
                .state(customer.getState())
                .country(customer.getCountry())
                .pincode(customer.getPincode())
                .createdAt(customer.getCreatedAt())
                .updatedAt(customer.getUpdatedAt())
                .build();
    }

    public void updateEntity(Customer customer, UpdateCustomerRequest request) {
        if (request.getName() != null) customer.setName(request.getName());
        if (request.getEmail() != null) customer.setEmail(request.getEmail());
        if (request.getPhone() != null) customer.setPhone(request.getPhone());
        if (request.getBillingAddress() != null) customer.setBillingAddress(request.getBillingAddress());
        if (request.getShippingAddress() != null) customer.setShippingAddress(request.getShippingAddress());
        if (request.getGstIn() != null) customer.setGstIn(request.getGstIn());
        if (request.getCity() != null) customer.setCity(request.getCity());
        if (request.getState() != null) customer.setState(request.getState());
        if (request.getCountry() != null) customer.setCountry(request.getCountry());
        if (request.getPincode() != null) customer.setPincode(request.getPincode());
    }
}
