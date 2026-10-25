package com.insideinvoice.einvoice.controller;

import com.insideinvoice.auth.dto.response.ApiResponse;
import com.insideinvoice.einvoice.dto.ConnectionTestResponse;
import com.insideinvoice.einvoice.dto.EInvoiceConfigRequest;
import com.insideinvoice.einvoice.dto.EInvoiceConfigResponse;
import com.insideinvoice.einvoice.entity.EInvoiceAuditEvent;
import com.insideinvoice.einvoice.service.EInvoiceConfigService;
import com.insideinvoice.security.CurrentUser;
import com.insideinvoice.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Settings → GST & Compliance → E-Invoicing. Every endpoint is scoped to the
 * authenticated user's business — tenant isolation is enforced by resolving
 * the business from the principal, never from the request body.
 */
@RestController
@RequestMapping("/api/einvoice")
@RequiredArgsConstructor
@Tag(name = "E-Invoicing", description = "GST e-Invoicing (IRN) configuration APIs")
public class EInvoiceConfigController {

    private final EInvoiceConfigService configService;
    private final com.insideinvoice.einvoice.service.EInvoiceRegistrationService registrationService;

    @GetMapping("/config")
    @Operation(summary = "Get the business's e-invoicing configuration")
    public ResponseEntity<ApiResponse<EInvoiceConfigResponse>> getConfig(
            @CurrentUser UserPrincipal currentUser) {
        return ResponseEntity.ok(ApiResponse.success("E-invoicing configuration",
                configService.getConfig(currentUser.getBusinessId())));
    }

    @PutMapping("/config")
    @Operation(summary = "Create or update the e-invoicing configuration")
    public ResponseEntity<ApiResponse<EInvoiceConfigResponse>> saveConfig(
            @Valid @RequestBody EInvoiceConfigRequest request,
            @CurrentUser UserPrincipal currentUser) {
        return ResponseEntity.ok(ApiResponse.success("E-invoicing configuration saved",
                configService.saveConfig(currentUser.getId(), currentUser.getBusinessId(), request)));
    }

    @PostMapping("/config/test")
    @Operation(summary = "Test the provider connection for the saved configuration")
    public ResponseEntity<ApiResponse<ConnectionTestResponse>> testConnection(
            @CurrentUser UserPrincipal currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Connection test completed",
                configService.testConnection(currentUser.getId(), currentUser.getBusinessId())));
    }

    @GetMapping("/status/{invoiceId}")
    @Operation(summary = "Derived e-invoice registration status for one invoice")
    public ResponseEntity<ApiResponse<com.insideinvoice.einvoice.dto.EInvoiceStatusResponse>> status(
            @org.springframework.web.bind.annotation.PathVariable Long invoiceId,
            @CurrentUser UserPrincipal currentUser) {
        return ResponseEntity.ok(ApiResponse.success("E-invoice status",
                registrationService.getStatus(currentUser.getBusinessId(), invoiceId)));
    }

    @PostMapping("/register/{invoiceId}")
    @Operation(summary = "Submit one eligible invoice for IRN registration")
    public ResponseEntity<ApiResponse<com.insideinvoice.einvoice.dto.EInvoiceStatusResponse>> register(
            @org.springframework.web.bind.annotation.PathVariable Long invoiceId,
            @CurrentUser UserPrincipal currentUser) {
        return ResponseEntity.ok(ApiResponse.success("E-invoice registration processed",
                registrationService.register(currentUser.getId(), currentUser.getBusinessId(), invoiceId)));
    }

    @GetMapping("/audit")
    @Operation(summary = "Recent e-invoicing configuration audit events")
    public ResponseEntity<ApiResponse<List<EInvoiceAuditEvent>>> audit(
            @CurrentUser UserPrincipal currentUser) {
        return ResponseEntity.ok(ApiResponse.success("E-invoicing audit history",
                configService.auditHistory(currentUser.getBusinessId())));
    }
}
