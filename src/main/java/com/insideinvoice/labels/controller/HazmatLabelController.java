package com.insideinvoice.labels.controller;

import com.insideinvoice.auth.dto.response.ApiResponse;
import com.insideinvoice.common.Constants;
import com.insideinvoice.common.dto.PagedResponse;
import com.insideinvoice.labels.dto.request.CreateHazmatLabelRequest;
import com.insideinvoice.labels.dto.request.UpdateHazmatLabelRequest;
import com.insideinvoice.labels.dto.response.HazardClassResponse;
import com.insideinvoice.labels.dto.response.HazmatLabelResponse;
import com.insideinvoice.labels.dto.response.UnNumberResponse;
import com.insideinvoice.labels.service.HazmatLabelService;
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
@RequestMapping("/api/labels/hazmat")
@RequiredArgsConstructor
@Tag(name = "Hazmat Labels", description = "Dangerous goods label CRUD, preview, PDF, ZPL and reference endpoints")
public class HazmatLabelController {

    private final HazmatLabelService hazmatLabelService;

    @PostMapping
    @Operation(summary = "Create a hazmat label")
    public ResponseEntity<ApiResponse<HazmatLabelResponse>> create(
            @Valid @RequestBody CreateHazmatLabelRequest request,
            @CurrentUser UserPrincipal currentUser,
            HttpServletRequest httpRequest) {
        HazmatLabelResponse response = hazmatLabelService.create(
                request, currentUser.getBusinessId(), currentUser.getId(), httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Hazmat label created successfully", response));
    }

    @GetMapping
    @Operation(summary = "List hazmat labels")
    public ResponseEntity<ApiResponse<PagedResponse<HazmatLabelResponse>>> list(
            @RequestParam(defaultValue = Constants.DEFAULT_PAGE_NUMBER) int page,
            @RequestParam(defaultValue = Constants.DEFAULT_PAGE_SIZE) int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @CurrentUser UserPrincipal currentUser) {
        PagedResponse<HazmatLabelResponse> response = hazmatLabelService.list(
                currentUser.getBusinessId(), page, size, q, status);
        return ResponseEntity.ok(ApiResponse.success("Hazmat labels retrieved successfully", response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a hazmat label")
    public ResponseEntity<ApiResponse<HazmatLabelResponse>> get(
            @PathVariable Long id,
            @CurrentUser UserPrincipal currentUser) {
        HazmatLabelResponse response = hazmatLabelService.get(id, currentUser.getBusinessId());
        return ResponseEntity.ok(ApiResponse.success("Hazmat label retrieved successfully", response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a hazmat label")
    public ResponseEntity<ApiResponse<HazmatLabelResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateHazmatLabelRequest request,
            @CurrentUser UserPrincipal currentUser,
            HttpServletRequest httpRequest) {
        HazmatLabelResponse response = hazmatLabelService.update(
                id, request, currentUser.getBusinessId(), currentUser.getId(), httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("Hazmat label updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a hazmat label")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id,
            @CurrentUser UserPrincipal currentUser,
            HttpServletRequest httpRequest) {
        hazmatLabelService.delete(id, currentUser.getBusinessId(), currentUser.getId(), httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("Hazmat label deleted successfully", null));
    }

    @PostMapping("/preview")
    @Operation(summary = "Render a hazmat label PDF for unsaved input")
    public ResponseEntity<byte[]> preview(
            @Valid @RequestBody CreateHazmatLabelRequest request) throws Exception {
        return pdfResponse(hazmatLabelService.preview(request), "hazmat-preview.pdf");
    }

    @PostMapping("/{id}/preview")
    @Operation(summary = "Render the stored label to PDF (no save)")
    public ResponseEntity<byte[]> previewById(
            @PathVariable Long id,
            @CurrentUser UserPrincipal currentUser) throws Exception {
        return pdfResponse(hazmatLabelService.previewById(id, currentUser.getBusinessId()),
                "hazmat-label-" + id + ".pdf");
    }

    @GetMapping("/{id}/pdf")
    @Operation(summary = "Get stored PDF (regenerates when missing)")
    public ResponseEntity<byte[]> getPdf(
            @PathVariable Long id,
            @CurrentUser UserPrincipal currentUser) throws Exception {
        return pdfResponse(hazmatLabelService.getPdf(id, currentUser.getBusinessId()),
                "hazmat-label-" + id + ".pdf");
    }

    @PostMapping("/{id}/generate")
    @Operation(summary = "Regenerate and store the label PDF")
    public ResponseEntity<byte[]> generate(
            @PathVariable Long id,
            @CurrentUser UserPrincipal currentUser,
            HttpServletRequest httpRequest) throws Exception {
        return pdfResponse(hazmatLabelService.generatePdf(id, currentUser.getBusinessId(), currentUser.getId(), httpRequest.getRemoteAddr()),
                "hazmat-label-" + id + ".pdf");
    }

    @PostMapping("/bulk-pdf")
    @Operation(summary = "Merge selected labels into one PDF")
    public ResponseEntity<byte[]> bulkPdf(
            @RequestParam List<Long> ids,
            @CurrentUser UserPrincipal currentUser,
            HttpServletRequest httpRequest) throws Exception {
        return pdfResponse(hazmatLabelService.bulkPdf(ids, currentUser.getBusinessId(), currentUser.getId(), httpRequest.getRemoteAddr()),
                "hazmat-labels.pdf");
    }

    @GetMapping("/{id}/zpl")
    @Operation(summary = "Export the label as ZPL II")
    public ResponseEntity<String> zpl(
            @PathVariable Long id,
            @CurrentUser UserPrincipal currentUser) throws Exception {
        String zpl = hazmatLabelService.zpl(id, currentUser.getBusinessId());
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .header("Content-Disposition", "inline; filename=\"hazmat-label-" + id + ".zpl\"")
                .body(zpl);
    }

    @PostMapping("/{id}/mark-printed")
    @Operation(summary = "Mark a label as printed")
    public ResponseEntity<ApiResponse<HazmatLabelResponse>> markPrinted(
            @PathVariable Long id,
            @CurrentUser UserPrincipal currentUser,
            HttpServletRequest httpRequest) {
        HazmatLabelResponse response = hazmatLabelService.markPrinted(
                id, currentUser.getBusinessId(), currentUser.getId(), httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("Hazmat label marked as printed", response));
    }

    @GetMapping("/un-numbers")
    @Operation(summary = "Search UN number reference data")
    public ResponseEntity<ApiResponse<List<UnNumberResponse>>> unNumbers(
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(ApiResponse.success("UN numbers retrieved successfully", hazmatLabelService.unNumbers(q)));
    }

    @GetMapping("/classes")
    @Operation(summary = "List hazard class metadata")
    public ResponseEntity<ApiResponse<List<HazardClassResponse>>> classes() {
        return ResponseEntity.ok(ApiResponse.success("Hazard classes retrieved successfully", hazmatLabelService.classes()));
    }

    private ResponseEntity<byte[]> pdfResponse(byte[] data, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "inline; filename=\"" + filename + "\"")
                .body(data);
    }
}
