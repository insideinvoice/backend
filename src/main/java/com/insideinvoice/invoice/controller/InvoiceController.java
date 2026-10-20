package com.insideinvoice.invoice.controller;

import com.insideinvoice.auth.dto.response.ApiResponse;
import com.insideinvoice.common.Constants;
import com.insideinvoice.common.dto.PagedResponse;
import com.insideinvoice.invoice.dto.request.CreateInvoiceRequest;
import com.insideinvoice.invoice.dto.request.UpdateInvoiceRequest;
import com.insideinvoice.invoice.dto.response.ImportReport;
import com.insideinvoice.invoice.dto.response.InvoiceResponse;
import com.insideinvoice.invoice.service.InvoiceImportService;
import com.insideinvoice.invoice.service.InvoiceService;
import com.insideinvoice.invoice.share.InvoiceEmailService;
import com.insideinvoice.invoice.share.InvoiceShareService;
import com.insideinvoice.invoice.share.dto.InvoiceEmailQueueResponse;
import com.insideinvoice.invoice.share.dto.InvoiceEmailStatusResponse;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
@Tag(name = "Invoices", description = "Invoice management APIs")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final InvoiceImportService invoiceImportService;
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

    @PostMapping(value = "/import", consumes = { "multipart/form-data" })
    @Operation(summary = "Bulk import invoices from an Excel workbook",
            description = "Parses a multi-sheet workbook (Invoice Register + Invoice Items). "
                    + "dryRun=true validates and returns a per-row report without writing; "
                    + "dryRun=false commits in one transaction, recomputing totals server-side "
                    + "and skipping duplicate invoice numbers.")
    public ResponseEntity<ApiResponse<ImportReport>> importInvoices(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "true") boolean dryRun,
            @CurrentUser UserPrincipal currentUser) {
        ImportReport report = dryRun
                ? invoiceImportService.validate(file, currentUser.getBusinessId())
                : invoiceImportService.commit(file, currentUser.getBusinessId(), currentUser.getId());
        String message = dryRun
                ? "Validation complete: " + report.getRows().size() + " rows checked"
                : "Import complete: " + report.getImported() + " created, "
                        + report.getSkipped() + " skipped, " + report.getBlocked() + " blocked";
        return ResponseEntity.ok(ApiResponse.success(message, report));
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

    @PostMapping(value = "/{id}/email", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Queue the invoice email (PDF + share link) for asynchronous delivery",
            description = "Validates synchronously (401/404/400), mints the share link, then queues the "
                    + "provider call and returns 200 immediately with data.status=PENDING. "
                    + "Poll GET /{id}/email-status for the terminal SENT/FAILED state.")
    public ResponseEntity<ApiResponse<InvoiceEmailQueueResponse>> sendInvoiceEmail(
            @PathVariable Long id,
            @RequestParam(name = "frontendOrigin", required = false) String frontendOrigin,
            @RequestPart(name = "pdf", required = false) MultipartFile pdf,
            @CurrentUser UserPrincipal currentUser) throws java.io.IOException {
        // The PDF is rendered in the browser (seal/stamp included) and attached so
        // the emailed document matches what the user downloads. Optional: a missing
        // or invalid file still sends the email with its share link. The bytes are
        // read HERE — the async worker only ever sees plain byte[].
        byte[] pdfBytes = (pdf != null && !pdf.isEmpty()) ? pdf.getBytes() : null;
        InvoiceEmailQueueResponse queued = invoiceEmailService.queueInvoiceEmail(
                id, currentUser.getBusinessId(), frontendOrigin,
                pdfBytes, pdf != null ? pdf.getOriginalFilename() : null);
        return ResponseEntity.ok(ApiResponse.success(
                "Invoice email queued for " + queued.recipient(), queued));
    }

    @GetMapping("/{id}/email-status")
    @Operation(summary = "Async email delivery status for an invoice",
            description = "PENDING (queued/never queued), SENT (emailedAt set) or FAILED (error set).")
    public ResponseEntity<ApiResponse<InvoiceEmailStatusResponse>> getInvoiceEmailStatus(
            @PathVariable Long id,
            @CurrentUser UserPrincipal currentUser) {
        InvoiceEmailStatusResponse status = invoiceEmailService.getEmailStatus(id, currentUser.getBusinessId());
        return ResponseEntity.ok(ApiResponse.success("Invoice email status retrieved", status));
    }
}
