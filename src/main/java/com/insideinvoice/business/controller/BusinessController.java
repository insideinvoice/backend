package com.insideinvoice.business.controller;

import com.insideinvoice.auth.dto.response.ApiResponse;
import com.insideinvoice.business.dto.request.BusinessSetupRequest;
import com.insideinvoice.business.dto.request.BusinessUpdateRequest;
import com.insideinvoice.business.dto.request.UpdateInvoiceSettingsRequest;
import com.insideinvoice.business.dto.response.BusinessResponse;
import com.insideinvoice.business.service.BusinessService;
import com.insideinvoice.security.CurrentUser;
import com.insideinvoice.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;

@RestController
@RequestMapping("/api/business")
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

    @PutMapping("/invoice-settings")
    @Operation(summary = "Update invoice template & print settings")
    public ResponseEntity<ApiResponse<BusinessResponse>> updateInvoiceSettings(
            @Valid @RequestBody UpdateInvoiceSettingsRequest request,
            @CurrentUser UserPrincipal currentUser) {
        BusinessResponse response = businessService.updateInvoiceSettings(currentUser.getBusinessId(), request);
        return ResponseEntity.ok(ApiResponse.success("Invoice settings updated successfully", response));
    }

    @PostMapping(value = "/signature", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload signature image (max 1MB)")
    public ResponseEntity<ApiResponse<String>> uploadSignature(
            @RequestParam("file") MultipartFile file,
            @CurrentUser UserPrincipal currentUser) {
        if (file.getSize() > 1_048_576) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("File size must not exceed 1MB"));
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            // Client-side read failure (truncated/corrupt part) — a 400, not a generic 500.
            throw new com.insideinvoice.exception.BadRequestException("Could not read uploaded file");
        }
        // The stored value is rendered as data:image/png;base64, on every invoice, so a
        // non-image (or an unreadable one) would silently break the signature band.
        if (!isSupportedImage(bytes)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Signature must be a PNG, JPEG, WebP or GIF image"));
        }
        String base64 = Base64.getEncoder().encodeToString(bytes);
        businessService.updateSignature(currentUser.getBusinessId(), base64);
        return ResponseEntity.ok(ApiResponse.success("Signature uploaded successfully", base64));
    }

    /** Magic-byte sniff: PNG, JPEG, GIF87a/89a, or WebP (RIFF....WEBP). */
    private static boolean isSupportedImage(byte[] b) {
        if (b.length < 12) {
            return false;
        }
        boolean png = (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
        boolean jpeg = (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
        boolean gif = b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8'
                && (b[4] == '7' || b[4] == '9') && b[5] == 'a';
        boolean webp = b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
        return png || jpeg || gif || webp;
    }

    @DeleteMapping("/signature")
    @Operation(summary = "Remove signature")
    public ResponseEntity<ApiResponse<Void>> removeSignature(
            @CurrentUser UserPrincipal currentUser) {
        businessService.updateSignature(currentUser.getBusinessId(), null);
        return ResponseEntity.ok(ApiResponse.success("Signature removed"));
    }
}
