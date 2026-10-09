package com.insideinvoice.invoice.share;

import com.insideinvoice.auth.dto.response.ApiResponse;
import com.insideinvoice.invoice.share.dto.PublicInvoiceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * Anonymous endpoints behind unguessable share tokens. Apart from the token, the only
 * input is the optional {@code ?type=} presentation override (TAX_INVOICE /
 * PROFORMA_INVOICE) that selects which of the two documents is rendered from the SAME
 * invoice - it never selects a different invoice. Every request (including PDF
 * generation) re-validates the token independently - the browser page is never trusted.
 */
@RestController
@RequestMapping("/api/public/invoices")
@RequiredArgsConstructor
@Tag(name = "Public invoices", description = "Token-gated invoice sharing APIs")
public class PublicInvoiceController {

    private final PublicInvoiceService publicInvoiceService;
    private final PublicInvoicePdfService publicInvoicePdfService;

    @GetMapping("/{token}")
    @Operation(summary = "Public invoice details by share token (optional ?type= document override)")
    public ResponseEntity<ApiResponse<PublicInvoiceResponse>> getInvoice(
            @PathVariable String token,
            @RequestParam(name = "type", required = false) String type) {
        PublicInvoiceResponse invoice = publicInvoiceService.getPublicInvoice(token, type);
        return ResponseEntity.ok(ApiResponse.success("Invoice retrieved successfully", invoice));
    }

    @GetMapping("/{token}/pdf")
    @Operation(summary = "On-demand PDF for a public share token (optional ?type= document override)")
    public ResponseEntity<byte[]> getPdf(
            @PathVariable String token,
            @RequestParam(name = "type", required = false) String type) throws IOException {
        String effectiveType = PublicInvoiceService.normalizeType(type);
        ResolvedPublicInvoice resolved = publicInvoiceService.resolve(token);
        byte[] pdf = publicInvoicePdfService.render(resolved, effectiveType);
        String filename = safeFilename(resolved.getInvoice().getInvoiceNumber());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(pdf);
    }

    private static String safeFilename(String invoiceNumber) {
        String safe = invoiceNumber == null ? "" : invoiceNumber.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.isBlank()) {
            safe = "invoice";
        }
        return "invoice_" + (safe.length() > 50 ? safe.substring(0, 50) : safe) + ".pdf";
    }
}
