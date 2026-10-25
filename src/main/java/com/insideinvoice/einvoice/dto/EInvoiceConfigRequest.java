package com.insideinvoice.einvoice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Settings payload. Credentials are WRITE-ONLY: they are encrypted on save
 * and never echoed back by any response DTO. On update, blank credential
 * fields keep the previously stored values.
 */
@Data
public class EInvoiceConfigRequest {

    /** Defaults to the business profile GSTIN when blank. */
    private String gstin;

    @NotNull(message = "Provider is required")
    private String provider;          // IRIS_IRP | MOCK

    @NotNull(message = "Environment is required")
    private String environment;       // SANDBOX | PRODUCTION

    @NotNull(message = "Enabled flag is required")
    private Boolean enabled;

    private Boolean autoRegister = false;

    private String clientId;
    private String clientSecret;

    /** E-invoice portal API username — required by the IRP auth flow. */
    private String apiUsername;

    /** E-invoice portal API password — write-only, encrypted at rest. */
    private String apiPassword;

    /** 32-char app key issued at IRP registration (encrypts the session key). */
    private String appKey;

    /** Optional provider endpoint override; blank = documented default for the provider+environment. */
    private String baseUrl;

    /** Optional provider-specific eligibility overrides (JSON object). */
    private String settings;
}
