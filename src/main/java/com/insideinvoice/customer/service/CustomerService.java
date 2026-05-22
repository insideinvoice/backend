package com.insideinvoice.customer.service;

import com.insideinvoice.common.dto.PagedResponse;
import com.insideinvoice.customer.dto.request.CreateCustomerRequest;
import com.insideinvoice.customer.dto.request.UpdateCustomerRequest;
import com.insideinvoice.customer.dto.response.CustomerResponse;

public interface CustomerService {

    CustomerResponse createCustomer(CreateCustomerRequest request, Long businessId);

    PagedResponse<CustomerResponse> getAllCustomers(Long businessId, int page, int size, String sortBy, String sortDir);

    CustomerResponse getCustomer(Long id, Long businessId);

    CustomerResponse updateCustomer(Long id, UpdateCustomerRequest request, Long businessId);

    void deleteCustomer(Long id, Long businessId);
}
