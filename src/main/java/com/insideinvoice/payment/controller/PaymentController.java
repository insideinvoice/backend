package com.insideinvoice.payment.controller;

import com.insideinvoice.auth.dto.response.ApiResponse;
import com.insideinvoice.common.Constants;
import com.insideinvoice.common.dto.PagedResponse;
import com.insideinvoice.payment.dto.request.CreatePaymentRequest;
import com.insideinvoice.payment.dto.response.PaymentResponse;
import com.insideinvoice.payment.service.PaymentService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment management APIs")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @Operation(summary = "Record a payment against an invoice")
    public ResponseEntity<ApiResponse<PaymentResponse>> createPayment(
            @Valid @RequestBody CreatePaymentRequest request,
            @CurrentUser UserPrincipal currentUser) {
        PaymentResponse response = paymentService.createPayment(request, currentUser.getBusinessId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Payment recorded successfully", response));
    }

    @GetMapping
    @Operation(summary = "Get all payments with pagination")
    public ResponseEntity<ApiResponse<PagedResponse<PaymentResponse>>> getAllPayments(
            @RequestParam(defaultValue = Constants.DEFAULT_PAGE_NUMBER) int page,
            @RequestParam(defaultValue = Constants.DEFAULT_PAGE_SIZE) int size,
            @RequestParam(defaultValue = Constants.SORT_BY_CREATED_AT) String sortBy,
            @RequestParam(defaultValue = Constants.SORT_DIRECTION_DESC) String sortDir,
            @CurrentUser UserPrincipal currentUser) {
        PagedResponse<PaymentResponse> response = paymentService.getAllPayments(
                currentUser.getBusinessId(), page, size, sortBy, sortDir);
        return ResponseEntity.ok(ApiResponse.success("Payments retrieved successfully", response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get payment by ID")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(
            @PathVariable Long id,
            @CurrentUser UserPrincipal currentUser) {
        PaymentResponse response = paymentService.getPayment(id, currentUser.getBusinessId());
        return ResponseEntity.ok(ApiResponse.success("Payment retrieved successfully", response));
    }

    @GetMapping("/by-invoice/{invoiceId}")
    @Operation(summary = "Get all payments for an invoice")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getPaymentsByInvoice(
            @PathVariable Long invoiceId,
            @CurrentUser UserPrincipal currentUser) {
        List<PaymentResponse> response = paymentService.getPaymentsByInvoice(invoiceId, currentUser.getBusinessId());
        return ResponseEntity.ok(ApiResponse.success("Payments retrieved successfully", response));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a payment")
    public ResponseEntity<ApiResponse<Void>> deletePayment(
            @PathVariable Long id,
            @CurrentUser UserPrincipal currentUser) {
        paymentService.deletePayment(id, currentUser.getBusinessId());
        return ResponseEntity.ok(ApiResponse.success("Payment deleted successfully"));
    }
}
