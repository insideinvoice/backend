package com.insideinvoice.labels.controller;

import com.insideinvoice.auth.dto.response.ApiResponse;
import com.insideinvoice.common.Constants;
import com.insideinvoice.common.dto.PagedResponse;
import com.insideinvoice.labels.dto.request.CreateShippingLabelRequest;
import com.insideinvoice.labels.dto.request.UpdateShippingLabelRequest;
import com.insideinvoice.labels.dto.response.ShippingLabelResponse;
import com.insideinvoice.labels.service.ShippingLabelService;
import com.insideinvoice.security.CurrentUser;
import com.insideinvoice.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

import java.util.List;

@RestController
@RequestMapping("/api/labels/shipping")
@RequiredArgsConstructor
@Tag(name = "Shipping Labels", description = "Shipping label CRUD, preview, PDF, ZPL and fulfilment workflow")
public class ShippingLabelController {

    private final ShippingLabelService shippingLabelService;

    @PostMapping
    @Operation(summary = "Create a shipping label")
    public ResponseEntity<ApiResponse<ShippingLabelResponse>> create(
            @Valid @RequestBody CreateShippingLabelRequest request,
            @CurrentUser UserPrincipal currentUser,
            HttpServletRequest httpRequest) {
        ShippingLabelResponse response = shippingLabelService.create(
                request, currentUser.getBusinessId(), currentUser.getId(), httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Shipping label created successfully", response));
    }

    @GetMapping
    @Operation(summary = "List shipping labels")
    public ResponseEntity<ApiResponse<PagedResponse<ShippingLabelResponse>>> list(
            @RequestParam(defaultValue = Constants.DEFAULT_PAGE_NUMBER) int page,
            @RequestParam(defaultValue = Constants.DEFAULT_PAGE_SIZE) int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String carrier,
            @RequestParam(required = false) Long invoiceId,
            @CurrentUser UserPrincipal currentUser) {
        PagedResponse<ShippingLabelResponse> response = shippingLabelService.list(
                currentUser.getBusinessId(), page, size, q, status, carrier, invoiceId);
        return ResponseEntity.ok(ApiResponse.success("Shipping labels retrieved successfully", response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a shipping label")
    public ResponseEntity<ApiResponse<ShippingLabelResponse>> get(
            @PathVariable Long id,
            @CurrentUser UserPrincipal currentUser) {
        ShippingLabelResponse response = shippingLabelService.get(id, currentUser.getBusinessId());
        return ResponseEntity.ok(ApiResponse.success("Shipping label retrieved successfully", response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a shipping label")
    public ResponseEntity<ApiResponse<ShippingLabelResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateShippingLabelRequest request,
            @CurrentUser UserPrincipal currentUser,
            HttpServletRequest httpRequest) {
        ShippingLabelResponse response = shippingLabelService.update(
                id, request, currentUser.getBusinessId(), currentUser.getId(), httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("Shipping label updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a shipping label")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id,
            @CurrentUser UserPrincipal currentUser,
            HttpServletRequest httpRequest) {
        shippingLabelService.delete(id, currentUser.getBusinessId(), currentUser.getId(), httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("Shipping label deleted successfully", null));
    }

    @PostMapping("/preview")
    @Operation(summary = "Render a label PDF for unsaved input")
    public ResponseEntity<byte[]> preview(
            @Valid @RequestBody CreateShippingLabelRequest request) throws Exception {
        return pdfResponse(shippingLabelService.preview(request), "preview.pdf");
    }

    @PostMapping("/{id}/preview")
    @Operation(summary = "Render the stored label to PDF (no save)")
    public ResponseEntity<byte[]> previewById(
            @PathVariable Long id,
            @CurrentUser UserPrincipal currentUser) throws Exception {
        return pdfResponse(shippingLabelService.previewById(id, currentUser.getBusinessId()),
                "shipping-label-" + id + ".pdf");
    }

    @GetMapping("/{id}/pdf")
    @Operation(summary = "Get stored PDF (regenerates when missing)")
    public ResponseEntity<byte[]> getPdf(
            @PathVariable Long id,
            @CurrentUser UserPrincipal currentUser) throws Exception {
        return pdfResponse(shippingLabelService.getPdf(id, currentUser.getBusinessId()),
                "shipping-label-" + id + ".pdf");
    }

    @PostMapping("/{id}/generate")
    @Operation(summary = "Regenerate and store the label PDF")
    public ResponseEntity<byte[]> generate(
            @PathVariable Long id,
            @CurrentUser UserPrincipal currentUser,
            HttpServletRequest httpRequest) throws Exception {
        return pdfResponse(shippingLabelService.generatePdf(id, currentUser.getBusinessId(), currentUser.getId(), httpRequest.getRemoteAddr()),
                "shipping-label-" + id + ".pdf");
    }

    @PostMapping("/bulk-pdf")
    @Operation(summary = "Merge selected labels into one PDF")
    public ResponseEntity<byte[]> bulkPdf(
            @RequestParam List<Long> ids,
            @CurrentUser UserPrincipal currentUser,
            HttpServletRequest httpRequest) throws Exception {
        return pdfResponse(shippingLabelService.bulkPdf(ids, currentUser.getBusinessId(), currentUser.getId(), httpRequest.getRemoteAddr()),
                "shipping-labels.pdf");
    }

    @GetMapping("/{id}/zpl")
    @Operation(summary = "Export the label as ZPL II")
    public ResponseEntity<String> zpl(
            @PathVariable Long id,
            @CurrentUser UserPrincipal currentUser) throws Exception {
        String zpl = shippingLabelService.zpl(id, currentUser.getBusinessId());
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .header("Content-Disposition", "inline; filename=\"shipping-label-" + id + ".zpl\"")
                .body(zpl);
    }

    @PostMapping("/{id}/mark-printed")
    @Operation(summary = "Mark a label as printed")
    public ResponseEntity<ApiResponse<ShippingLabelResponse>> markPrinted(
            @PathVariable Long id,
            @CurrentUser UserPrincipal currentUser,
            HttpServletRequest httpRequest) {
        ShippingLabelResponse response = shippingLabelService.markPrinted(
                id, currentUser.getBusinessId(), currentUser.getId(), httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("Shipping label marked as printed", response));
    }

    @PostMapping("/from-invoice/{invoiceId}")
    @Operation(summary = "Create a draft shipping label from an invoice")
    public ResponseEntity<ApiResponse<ShippingLabelResponse>> fromInvoice(
            @PathVariable Long invoiceId,
            @CurrentUser UserPrincipal currentUser,
            HttpServletRequest httpRequest) {
        ShippingLabelResponse response = shippingLabelService.fromInvoice(
                invoiceId, currentUser.getBusinessId(), currentUser.getId(), httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Shipping label created from invoice", response));
    }

    private ResponseEntity<byte[]> pdfResponse(byte[] data, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "inline; filename=\"" + filename + "\"")
                .body(data);
    }
}
