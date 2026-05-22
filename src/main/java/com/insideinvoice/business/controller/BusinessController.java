package com.insideinvoice.business.controller;

import com.insideinvoice.auth.dto.response.ApiResponse;
import com.insideinvoice.business.dto.request.BusinessSetupRequest;
import com.insideinvoice.business.dto.request.BusinessUpdateRequest;
import com.insideinvoice.business.dto.response.BusinessResponse;
import com.insideinvoice.business.service.BusinessService;
import com.insideinvoice.security.CurrentUser;
import com.insideinvoice.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/business")
@RequiredArgsConstructor
@Tag(name = "Business", description = "Business management APIs")
public class BusinessController {

    private final BusinessService businessService;

    @PostMapping("/setup")
    @Operation(summary = "Complete initial business setup")
    public ResponseEntity<ApiResponse<BusinessResponse>> setupBusiness(
            @Valid @RequestBody BusinessSetupRequest request,
            @CurrentUser UserPrincipal currentUser) {
        BusinessResponse response = businessService.setupBusiness(
                currentUser.getId(), currentUser.getBusinessId(), request);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success("Business setup completed successfully", response));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current business profile")
    public ResponseEntity<ApiResponse<BusinessResponse>> getBusiness(
            @CurrentUser UserPrincipal currentUser) {
        BusinessResponse response = businessService.getBusiness(currentUser.getBusinessId());
        return ResponseEntity.ok(ApiResponse.success("Business retrieved successfully", response));
    }

    @PutMapping("/update")
    @Operation(summary = "Update business details")
    public ResponseEntity<ApiResponse<BusinessResponse>> updateBusiness(
            @Valid @RequestBody BusinessUpdateRequest request,
            @CurrentUser UserPrincipal currentUser) {
        BusinessResponse response = businessService.updateBusiness(currentUser.getBusinessId(), request);
        return ResponseEntity.ok(ApiResponse.success("Business updated successfully", response));
    }
}
