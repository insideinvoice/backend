package com.insideinvoice.einvoice.service;

import com.insideinvoice.business.entity.Business;
import com.insideinvoice.business.repository.BusinessRepository;
import com.insideinvoice.einvoice.dto.ConnectionTestResponse;
import com.insideinvoice.exception.BadRequestException;
import com.insideinvoice.exception.ResourceNotFoundException;
import com.insideinvoice.einvoice.dto.EInvoiceConfigRequest;
import com.insideinvoice.einvoice.dto.EInvoiceConfigResponse;
import com.insideinvoice.einvoice.entity.EInvoiceAuditEvent;
import com.insideinvoice.einvoice.entity.EInvoiceConfig;
import com.insideinvoice.einvoice.entity.EInvoiceEnvironment;
import com.insideinvoice.einvoice.entity.EInvoiceProviderId;
import com.insideinvoice.einvoice.provider.ConnectionTestResult;
import com.insideinvoice.einvoice.provider.EInvoiceProviderRegistry;
import com.insideinvoice.einvoice.repository.EInvoiceAuditEventRepository;
import com.insideinvoice.einvoice.repository.EInvoiceConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Settings-scoped e-invoicing configuration: one row per
 * business + GSTIN + environment. Enabling is ALWAYS the business's explicit
 * choice; disabling only flips the local flag — it never cancels government
 * registrations and never deletes audit history.
 */
@Service
@RequiredArgsConstructor
public class EInvoiceConfigService {

    private static final Pattern GSTIN = Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$");

    private final EInvoiceConfigRepository configRepository;
    private final EInvoiceAuditEventRepository auditRepository;
    private final EInvoiceCredentialService credentialService;
    private final EInvoiceProviderRegistry providerRegistry;
    private final BusinessRepository businessRepository;

    @Transactional(readOnly = true)
    public EInvoiceConfigResponse getConfig(Long businessId) {
        return decorate(
                configRepository.findFirstByBusinessIdOrderByUpdatedAtDesc(businessId).orElse(null));
    }

    @Transactional
    public EInvoiceConfigResponse saveConfig(Long userId, Long businessId, EInvoiceConfigRequest request) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));

        String gstin = resolveGstin(request.getGstin(), business);
        EInvoiceProviderId provider = parseEnum(EInvoiceProviderId.class, request.getProvider(), "provider");
        EInvoiceEnvironment environment = parseEnum(EInvoiceEnvironment.class, request.getEnvironment(), "environment");

        if (provider == EInvoiceProviderId.MOCK && environment == EInvoiceEnvironment.PRODUCTION) {
            throw new BadRequestException("The Mock provider is for sandbox testing only and can never be used in production");
        }

        boolean enabling = Boolean.TRUE.equals(request.getEnabled());
        Map<String, String> incomingCredentials = new LinkedHashMap<>();
        if (request.getClientId() != null && !request.getClientId().isBlank())
            incomingCredentials.put("clientId", request.getClientId().trim());
        if (request.getClientSecret() != null && !request.getClientSecret().isBlank())
            incomingCredentials.put("clientSecret", request.getClientSecret().trim());
        if (request.getApiUsername() != null && !request.getApiUsername().isBlank())
            incomingCredentials.put("apiUsername", request.getApiUsername().trim());
        if (request.getApiPassword() != null && !request.getApiPassword().isBlank())
            incomingCredentials.put("apiPassword", request.getApiPassword().trim());
        if (request.getAppKey() != null && !request.getAppKey().isBlank())
            incomingCredentials.put("appKey", request.getAppKey().trim());
        if (request.getBaseUrl() != null && !request.getBaseUrl().isBlank())
            incomingCredentials.put("baseUrl", request.getBaseUrl().trim());

        EInvoiceConfig config = configRepository
                .findByBusinessIdAndGstinAndEnvironment(businessId, gstin, environment)
                .orElseGet(() -> EInvoiceConfig.builder()
                        .businessId(businessId)
                        .gstin(gstin)
                        .provider(provider)
                        .environment(environment)
                        .createdBy(userId)
                        .build());

        String previousCredentials = config.getCredentialsEncrypted();
        Map<String, String> effectiveCredentials = new LinkedHashMap<>(credentialService.decrypt(previousCredentials));
        effectiveCredentials.putAll(incomingCredentials);

        config.setProvider(provider);
        config.setEnvironment(environment);
        config.setEnabled(enabling);
        config.setAutoRegister(Boolean.TRUE.equals(request.getAutoRegister()));
        if (!effectiveCredentials.isEmpty()) {
            config.setCredentialsEncrypted(credentialService.encrypt(effectiveCredentials));
        }
        if (request.getSettings() != null) config.setSettings(request.getSettings());
        config.setUpdatedBy(userId);
        // Any provider change invalidates the previous connection verdict.
        config.setConnectionStatus("NOT_TESTED");
        config.setLastConnectionError(null);
        config.setLastConnectionTestAt(null);

        EInvoiceConfig saved = configRepository.save(config);
        audit(businessId, userId, saved.getId(), "CONFIG_SAVED",
                "enabled=" + enabling + ", provider=" + provider + ", env=" + environment,
                enabling ? "ENABLED" : "DISABLED");
        return decorate(saved);
    }

    /** Fills the hasX booleans from the decrypted envelope — values never leave. */
    private EInvoiceConfigResponse decorate(EInvoiceConfig config) {
        if (config == null) return null;
        EInvoiceConfigResponse resp = EInvoiceConfigResponse.from(config);
        String envelope = config.getCredentialsEncrypted();
        resp.setHasPortalCredentials(
                credentialService.hasField(envelope, "apiUsername")
                        && credentialService.hasField(envelope, "apiPassword"));
        resp.setHasAppKey(credentialService.hasField(envelope, "appKey"));
        return resp;
    }

    @Transactional
    public ConnectionTestResponse testConnection(Long userId, Long businessId) {
        EInvoiceConfig config = configRepository.findFirstByBusinessIdOrderByUpdatedAtDesc(businessId)
                .orElseThrow(() -> new BadRequestException("Save the e-invoicing configuration before testing the connection"));

        ConnectionTestResult result = providerRegistry.get(config.getProvider())
                .testConnection(config.getEnvironment(), credentialService.decrypt(config.getCredentialsEncrypted()));

        config.setConnectionStatus(result.status());
        config.setLastConnectionTestAt(LocalDateTime.now());
        config.setLastConnectionError(
                ConnectionTestResult.CONNECTED.equals(result.status()) ? null : result.message());
        config.setUpdatedBy(userId);
        configRepository.save(config);

        audit(businessId, userId, config.getId(), "CONNECTION_TESTED",
                result.status() + (result.mock() ? " (mock)" : ""), result.status());

        return ConnectionTestResponse.builder()
                .connectionStatus(result.status())
                .message(result.message())
                .mock(result.mock())
                .testedAt(config.getLastConnectionTestAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<EInvoiceAuditEvent> auditHistory(Long businessId) {
        return auditRepository.findTop50ByBusinessIdOrderByCreatedAtDescIdDesc(businessId);
    }

    private String resolveGstin(String requested, Business business) {
        String gstin = (requested == null || requested.isBlank())
                ? business.getGstIn() : requested.trim().toUpperCase();
        if (gstin == null || gstin.isBlank()) {
            throw new BadRequestException("Add the business GSTIN in Settings before configuring e-invoicing");
        }
        if (!GSTIN.matcher(gstin).matches()) {
            throw new BadRequestException("GSTIN must be a valid 15-character GST identification number");
        }
        return gstin;
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String raw, String field) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException(field + " is required");
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown " + field + ": " + raw);
        }
    }

    private void audit(Long businessId, Long userId, Long configId, String eventType,
                       String reason, String providerResult) {
        auditRepository.save(EInvoiceAuditEvent.builder()
                .businessId(businessId)
                .actorUserId(userId)
                .actorType("USER")
                .eventType(eventType)
                .operation("SETTINGS")
                .reason(reason)
                .providerResult(providerResult)
                .correlationId("config-" + configId)
                .build());
    }
}
