package com.insideinvoice.einvoice.dto;

import com.insideinvoice.einvoice.entity.EInvoiceConfig;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;


/**
 * Safe projection of the stored configuration — credentials are represented
 * ONLY by a hasCredentials boolean; ciphertext and secrets never leave the
 * backend.
 */
@Data
@Builder
public class EInvoiceConfigResponse {

    private Long id;
    private String gstin;
    private String provider;
    private String environment;
    private Boolean enabled;
    private Boolean autoRegister;
    private boolean hasCredentials;
    /** E-invoice portal username/password stored (IRP auth flow requirement). */
    private boolean hasPortalCredentials;
    /** App key stored (decrypts the IRP session encryption key). */
    private boolean hasAppKey;
    private String connectionStatus;
    private LocalDateTime lastConnectionTestAt;
    private String lastConnectionError;
    private String settings;
    private LocalDateTime updatedAt;

    public static EInvoiceConfigResponse from(EInvoiceConfig config) {
        if (config == null) return null;
        return EInvoiceConfigResponse.builder()
                .id(config.getId())
                .gstin(config.getGstin())
                .provider(config.getProvider() == null ? null : config.getProvider().name())
                .environment(config.getEnvironment() == null ? null : config.getEnvironment().name())
                .enabled(Boolean.TRUE.equals(config.getEnabled()))
                .autoRegister(Boolean.TRUE.equals(config.getAutoRegister()))
                .hasCredentials(config.getCredentialsEncrypted() != null && !config.getCredentialsEncrypted().isBlank())
                .connectionStatus(config.getConnectionStatus())
                .lastConnectionTestAt(config.getLastConnectionTestAt())
                .lastConnectionError(config.getLastConnectionError())
                .settings(config.getSettings())
                .updatedAt(config.getUpdatedAt())
                .build();
    }
}
