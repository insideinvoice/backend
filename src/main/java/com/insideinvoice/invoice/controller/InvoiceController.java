package com.insideinvoice.invoice.controller;

import com.insideinvoice.auth.dto.response.ApiResponse;
import com.insideinvoice.common.Constants;
import com.insideinvoice.common.dto.PagedResponse;
import com.insideinvoice.invoice.dto.request.CreateInvoiceRequest;
import com.insideinvoice.invoice.dto.request.SendInvoiceEmailRequest;
import com.insideinvoice.invoice.dto.request.UpdateInvoiceRequest;
import com.insideinvoice.invoice.dto.response.InvoiceResponse;
import com.insideinvoice.invoice.service.InvoiceService;
import com.insideinvoice.invoice.share.InvoiceEmailService;
import com.insideinvoice.invoice.share.InvoiceShareService;
import com.insideinvoice.invoice.share.dto.ShareLinkResponse;
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
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
@Tag(name = "Invoices", description = "Invoice management APIs")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final InvoiceShareService invoiceShareService;
    private final InvoiceEmailService invoiceEmailService;

    @PostMapping
    @Operation(summary = "Create a new invoice")
    public ResponseEntity<ApiResponse<InvoiceResponse>> createInvoice(
            @Valid @RequestBody CreateInvoiceRequest request,
            @CurrentUser UserPrincipal currentUser) {
        InvoiceResponse response = invoiceService.createInvoice(
                request, currentUser.getBusinessId(), currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Invoice created successfully", response));
    }

    @GetMapping
    @Operation(summary = "Get all invoices with pagination")
    public ResponseEntity<ApiResponse<PagedResponse<InvoiceResponse>>> getAllInvoices(
            @RequestParam(defaultValue = Constants.DEFAULT_PAGE_NUMBER) int page,
            @RequestParam(defaultValue = Constants.DEFAULT_PAGE_SIZE) int size,
            @RequestParam(defaultValue = Constants.SORT_BY_CREATED_AT) String sortBy,
            @RequestParam(defaultValue = Constants.SORT_DIRECTION_DESC) String sortDir,
            @CurrentUser UserPrincipal currentUser) {
        PagedResponse<InvoiceResponse> response = invoiceService.getAllInvoices(
                currentUser.getBusinessId(), page, size, sortBy, sortDir);
        return ResponseEntity.ok(ApiResponse.success("Invoices retrieved successfully", response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get invoice by ID")
    public ResponseEntity<ApiResponse<InvoiceResponse>> getInvoice(
            @PathVariable Long id,
            @CurrentUser UserPrincipal currentUser) {
        InvoiceResponse response = invoiceService.getInvoice(id, currentUser.getBusinessId());
        return ResponseEntity.ok(ApiResponse.success("Invoice retrieved successfully", response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update invoice")
    public ResponseEntity<ApiResponse<InvoiceResponse>> updateInvoice(
            @PathVariable Long id,
            @Valid @RequestBody UpdateInvoiceRequest request,
            @CurrentUser UserPrincipal currentUser) {
        InvoiceResponse response = invoiceService.updateInvoice(id, request, currentUser.getBusinessId());
        return ResponseEntity.ok(ApiResponse.success("Invoice updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete invoice")
    public ResponseEntity<ApiResponse<Void>> deleteInvoice(
            @PathVariable Long id,
            @CurrentUser UserPrincipal currentUser) {
        invoiceService.deleteInvoice(id, currentUser.getBusinessId());
        return ResponseEntity.ok(ApiResponse.success("Invoice deleted successfully"));
    }

    @PostMapping("/{id}/share")
    @Operation(summary = "Create or retrieve the public share link")
    public ResponseEntity<ApiResponse<ShareLinkResponse>> createShare(
            @PathVariable Long id,
            @CurrentUser UserPrincipal currentUser) {
        ShareLinkResponse response = invoiceShareService.createOrRetrieve(id, currentUser.getBusinessId());
        return ResponseEntity.ok(ApiResponse.success("Share link ready", response));
    }

    @DeleteMapping("/{id}/share")
    @Operation(summary = "Revoke the public share link")
    public ResponseEntity<ApiResponse<ShareLinkResponse>> revokeShare(
            @PathVariable Long id,
            @CurrentUser UserPrincipal currentUser) {
        ShareLinkResponse response = invoiceShareService.revoke(id, currentUser.getBusinessId());
        return ResponseEntity.ok(ApiResponse.success("Share link revoked", response));
    }

    @PostMapping("/{id}/share/regenerate")
    @Operation(summary = "Issue a replacement share link and invalidate the previous token")
    public ResponseEntity<ApiResponse<ShareLinkResponse>> regenerateShare(
            @PathVariable Long id,
            @CurrentUser UserPrincipal currentUser) {
        ShareLinkResponse response = invoiceShareService.regenerate(id, currentUser.getBusinessId());
        return ResponseEntity.ok(ApiResponse.success("New share link created", response));
    }

    @PostMapping("/{id}/email")
    @Operation(summary = "Email the invoice and its share link to the customer")
    public ResponseEntity<ApiResponse<Void>> sendInvoiceEmail(
            @PathVariable Long id,
            @RequestBody(required = false) SendInvoiceEmailRequest request,
            @CurrentUser UserPrincipal currentUser) {
        String recipient = invoiceEmailService.sendInvoiceEmail(
                id, currentUser.getBusinessId(), request != null ? request.getFrontendOrigin() : null);
        return ResponseEntity.ok(ApiResponse.success("Invoice email sent to " + recipient));
    }
}
