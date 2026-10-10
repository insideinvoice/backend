package com.insideinvoice.business.controller;

import com.insideinvoice.auth.dto.response.ApiResponse;
import com.insideinvoice.business.dto.response.IndustryConfigResponse;
import com.insideinvoice.business.industry.IndustryRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only catalogue of industry profiles.
 *
 * <p>Serves the onboarding/settings dropdown and the resolved configuration for
 * every profile. The active tenant's profile still comes from
 * {@code GET /api/business/me}; this endpoint never returns tenant data, so
 * there is nothing to isolate.</p>
 */
@RestController
@RequestMapping("/api/industries")
@Tag(name = "Industry", description = "Industry profile catalogue")
public class IndustryController {

    @GetMapping
    @Operation(summary = "List all industry profiles with their field/document configuration")
    public ResponseEntity<ApiResponse<List<IndustryConfigResponse>>> listIndustries() {
        List<IndustryConfigResponse> industries = IndustryRegistry.all().stream()
                .map(IndustryConfigResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Industries retrieved successfully", industries));
    }
}
