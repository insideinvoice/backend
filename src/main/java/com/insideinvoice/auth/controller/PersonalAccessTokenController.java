package com.insideinvoice.auth.controller;

import com.insideinvoice.auth.dto.request.CreatePatRequest;
import com.insideinvoice.auth.dto.response.ApiResponse;
import com.insideinvoice.auth.dto.response.PatResponse;
import com.insideinvoice.auth.entity.PersonalAccessToken;
import com.insideinvoice.auth.service.PersonalAccessTokenService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pats")
@RequiredArgsConstructor
@Tag(name = "Personal Access Tokens", description = "PAT management for third-party API integration")
public class PersonalAccessTokenController {

    private final PersonalAccessTokenService patService;

    @PostMapping
    @Operation(summary = "Create a new Personal Access Token (raw token shown once)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(
            @Valid @RequestBody CreatePatRequest request,
            @CurrentUser UserPrincipal currentUser) {
        PersonalAccessTokenService.CreateResult result = patService.create(
                currentUser.getId(), currentUser.getBusinessId(),
                request.getName(), request.getScopes(), request.getExpiresAt());

        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("token", result.rawToken());          // shown once
        data.put("pat", PatResponse.from(result.entity()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Personal access token created. Copy it now — it won't be shown again.", data));
    }

    @GetMapping
    @Operation(summary = "List the current user's Personal Access Tokens")
    public ResponseEntity<ApiResponse<List<PatResponse>>> list(@CurrentUser UserPrincipal currentUser) {
        List<PatResponse> list = patService.listForUser(currentUser.getId()).stream()
                .map(PatResponse::from).toList();
        return ResponseEntity.ok(ApiResponse.success("PATs retrieved successfully", list));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Revoke a Personal Access Token")
    public ResponseEntity<ApiResponse<Void>> revoke(
            @PathVariable Long id,
            @CurrentUser UserPrincipal currentUser) {
        patService.revoke(id, currentUser.getId(), currentUser.getBusinessId());
        return ResponseEntity.ok(ApiResponse.success("Token revoked successfully"));
    }
}
