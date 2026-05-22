package com.insideinvoice.customer.controller;

import com.insideinvoice.auth.dto.response.ApiResponse;
import com.insideinvoice.common.Constants;
import com.insideinvoice.common.dto.PagedResponse;
import com.insideinvoice.customer.dto.request.CreateCustomerRequest;
import com.insideinvoice.customer.dto.request.UpdateCustomerRequest;
import com.insideinvoice.customer.dto.response.CustomerResponse;
import com.insideinvoice.customer.service.CustomerService;
import com.insideinvoice.security.CurrentUser;
import com.insideinvoice.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
@Tag(name = "Customers", description = "Customer management APIs")
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping
    @Operation(summary = "Create a new customer")
    public ResponseEntity<ApiResponse<CustomerResponse>> createCustomer(
            @Valid @RequestBody CreateCustomerRequest request,
            @CurrentUser UserPrincipal currentUser) {
        CustomerResponse response = customerService.createCustomer(request, currentUser.getBusinessId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Customer created successfully", response));
    }

    @GetMapping
    @Operation(summary = "Get all customers with pagination")
    public ResponseEntity<ApiResponse<PagedResponse<CustomerResponse>>> getAllCustomers(
            @RequestParam(defaultValue = Constants.DEFAULT_PAGE_NUMBER) int page,
            @RequestParam(defaultValue = Constants.DEFAULT_PAGE_SIZE) int size,
            @RequestParam(defaultValue = Constants.SORT_BY_CREATED_AT) String sortBy,
            @RequestParam(defaultValue = Constants.SORT_DIRECTION_DESC) String sortDir,
            @CurrentUser UserPrincipal currentUser) {
        PagedResponse<CustomerResponse> response = customerService.getAllCustomers(
                currentUser.getBusinessId(), page, size, sortBy, sortDir);
        return ResponseEntity.ok(ApiResponse.success("Customers retrieved successfully", response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get customer by ID")
    public ResponseEntity<ApiResponse<CustomerResponse>> getCustomer(
            @PathVariable Long id,
            @CurrentUser UserPrincipal currentUser) {
        CustomerResponse response = customerService.getCustomer(id, currentUser.getBusinessId());
        return ResponseEntity.ok(ApiResponse.success("Customer retrieved successfully", response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update customer")
    public ResponseEntity<ApiResponse<CustomerResponse>> updateCustomer(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCustomerRequest request,
            @CurrentUser UserPrincipal currentUser) {
        CustomerResponse response = customerService.updateCustomer(id, request, currentUser.getBusinessId());
        return ResponseEntity.ok(ApiResponse.success("Customer updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete customer")
    public ResponseEntity<ApiResponse<Void>> deleteCustomer(
            @PathVariable Long id,
            @CurrentUser UserPrincipal currentUser) {
        customerService.deleteCustomer(id, currentUser.getBusinessId());
        return ResponseEntity.ok(ApiResponse.success("Customer deleted successfully"));
    }
}
