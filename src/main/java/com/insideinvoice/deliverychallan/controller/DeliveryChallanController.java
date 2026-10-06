package com.insideinvoice.deliverychallan.controller;

import com.insideinvoice.auth.dto.response.ApiResponse;
import com.insideinvoice.common.dto.PagedResponse;
import com.insideinvoice.deliverychallan.dto.request.CreateDeliveryChallanRequest;
import com.insideinvoice.deliverychallan.dto.response.DeliveryChallanResponse;
import com.insideinvoice.deliverychallan.service.DeliveryChallanService;
import com.insideinvoice.security.CurrentUser;
import com.insideinvoice.security.UserPrincipal;
import com.insideinvoice.common.Constants;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/delivery-challans")
@RequiredArgsConstructor
public class DeliveryChallanController {

    private final DeliveryChallanService deliveryChallanService;

    @PostMapping
    public ResponseEntity<ApiResponse<DeliveryChallanResponse>> createDeliveryChallan(
            @Valid @RequestBody CreateDeliveryChallanRequest request,
            @CurrentUser UserPrincipal currentUser) {
        DeliveryChallanResponse response = deliveryChallanService.createDeliveryChallan(request,
                currentUser.getBusinessId(), currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Delivery challan created successfully", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<DeliveryChallanResponse>>> getAllDeliveryChallans(
            @CurrentUser UserPrincipal currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = Constants.DEFAULT_PAGE_SIZE) int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        PagedResponse<DeliveryChallanResponse> response = deliveryChallanService.getAllDeliveryChallans(
                currentUser.getBusinessId(), page, size, sortBy, sortDir);
        return ResponseEntity.ok(ApiResponse.success("Delivery challan retrieved successfully", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DeliveryChallanResponse>> getDeliveryChallan(
            @PathVariable Long id,
            @CurrentUser UserPrincipal currentUser) {
        DeliveryChallanResponse response = deliveryChallanService.getDeliveryChallan(id,
                currentUser.getBusinessId());
        return ResponseEntity.ok(ApiResponse.success("Delivery challan retrieved successfully", response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDeliveryChallan(
            @PathVariable Long id,
            @CurrentUser UserPrincipal currentUser) {
        deliveryChallanService.deleteDeliveryChallan(id, currentUser.getBusinessId());
        return ResponseEntity.ok(ApiResponse.success("Delivery challan deleted successfully"));
    }
}
