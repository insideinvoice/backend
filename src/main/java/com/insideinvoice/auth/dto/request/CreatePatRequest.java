package com.insideinvoice.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreatePatRequest {
    @NotBlank(message = "Token name is required")
    @Size(max = 120, message = "Token name must not exceed 120 characters")
    private String name;

    /** Comma-separated scopes, e.g. "invoices:read,invoices:write". Defaults to full invoice access. */
    private String scopes;

    /** Optional expiry. Null = never expires. */
    private LocalDateTime expiresAt;
}
