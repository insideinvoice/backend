package com.insideinvoice.einvoice.service;

import com.insideinvoice.einvoice.dto.EInvoiceStatusResponse;
import com.insideinvoice.einvoice.entity.EInvoiceConfig;
import com.insideinvoice.einvoice.entity.EInvoiceRegistration;
import com.insideinvoice.einvoice.entity.EInvoiceRegistrationRequest;
import com.insideinvoice.einvoice.entity.EInvoiceProviderId;
import com.insideinvoice.einvoice.entity.EInvoiceStatus;
import com.insideinvoice.einvoice.entity.RegistrationRequestState;
import com.insideinvoice.einvoice.provider.EInvoiceProviderRegistry;
import com.insideinvoice.einvoice.provider.IrnResult;
import com.insideinvoice.einvoice.repository.EInvoiceAuditEventRepository;
import com.insideinvoice.einvoice.repository.EInvoiceConfigRepository;
import com.insideinvoice.einvoice.repository.EInvoiceRegistrationRepository;
import com.insideinvoice.einvoice.repository.EInvoiceRegistrationRequestRepository;
import com.insideinvoice.exception.BadRequestException;
import com.insideinvoice.exception.ResourceNotFoundException;
import com.insideinvoice.invoice.entity.Invoice;
import com.insideinvoice.invoice.entity.InvoiceType;
import com.insideinvoice.invoice.repository.InvoiceRepository;
import com.insideinvoice.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * IRN registration lifecycle for one invoice: eligibility → idempotent
 * request → provider call → persist the ACTUAL result → stamp the invoice.
 *
 * Hard rules enforced here:
 *  - the feature must be ENABLED for the business or the call is refused;
 *  - only TAX_INVOICE / CREDIT_NOTE / DEBIT_NOTE can be registered;
 *  - a registered invoice is never re-registered (idempotency record +
 *    existing-REGISTERED check — double clicks and retries are no-ops);
 *  - a FAILED/TIMEOUT attempt never writes an IRN or a success status;
 *  - REGISTERED_MOCK marks mock-provider results so they can never pass as
 *    government data.
 */
@Service
@RequiredArgsConstructor
public class EInvoiceRegistrationService {

    private final EInvoiceConfigRepository configRepository;
    private final EInvoiceRegistrationRepository registrationRepository;
    private final EInvoiceRegistrationRequestRepository requestRepository;
    private final EInvoiceAuditEventRepository auditRepository;
    private final EInvoiceCredentialService credentialService;
    private final EInvoiceProviderRegistry providerRegistry;
    private final EInvoicePayloadMapper payloadMapper;
    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;

    /**
     * Auto-registration hook for invoice creation: when the business enabled
     * e-invoicing AND ticked auto-register, a newly created eligible invoice
     * is submitted immediately. NEVER throws and never blocks invoicing —
     * a failure lands in the persisted registration + invoice status and the
     * user retries from the View page.
     */
    @Transactional
    public void autoRegisterIfConfigured(Long businessId, Long invoiceId) {
        try {
            EInvoiceConfig config = configRepository
                    .findFirstByBusinessIdOrderByUpdatedAtDesc(businessId)
                    .filter(c -> Boolean.TRUE.equals(c.getEnabled()) && Boolean.TRUE.equals(c.getAutoRegister()))
                    .orElse(null);
            if (config == null) return;
            // Self-invocation on purpose: doRegister runs in the caller's
            // transaction with NO new @Transactional boundary, so any failure
            // caught here can never mark the invoice create rollback-only.
            doRegister(config.getUpdatedBy() != null ? config.getUpdatedBy() : null, businessId, invoiceId);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(EInvoiceRegistrationService.class)
                    .warn("Auto e-invoice registration failed for invoice {}", invoiceId, e);
        }
    }

    /** Computed status the View page renders for one invoice. */
    @Transactional(readOnly = true)
    public EInvoiceStatusResponse getStatus(Long businessId, Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .filter(i -> i.getBusinessId().equals(businessId))
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
        EInvoiceConfig config = configRepository
                .findFirstByBusinessIdOrderByUpdatedAtDesc(businessId).orElse(null);

        EInvoiceRegistration registration = registrationRepository
                .findFirstByInvoiceIdOrderByCreatedAtDescIdDesc(invoiceId).orElse(null);

        String derived;
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            derived = "DISABLED";
        } else if (registration == null) {
            derived = isRegisterableType(invoice) ? "READY_TO_REGISTER" : "NOT_APPLICABLE";
        } else {
            derived = registration.getStatus().name();
        }
        return EInvoiceStatusResponse.builder()
                .invoiceId(invoiceId)
                .derivedStatus(derived)
                .registration(registration == null ? null : EInvoiceStatusResponse.RegistrationView.from(registration))
                .build();
    }

    @Transactional
    public EInvoiceStatusResponse register(Long userId, Long businessId, Long invoiceId) {
        return doRegister(userId, businessId, invoiceId);
    }

    private EInvoiceStatusResponse doRegister(Long userId, Long businessId, Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .filter(i -> i.getBusinessId().equals(businessId))
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));

        EInvoiceConfig config = configRepository
                .findFirstByBusinessIdOrderByUpdatedAtDesc(businessId)
                .filter(c -> Boolean.TRUE.equals(c.getEnabled()))
                .orElseThrow(() -> new BadRequestException(
                        "Enable e-invoicing in Settings before registering invoices"));

        if (!isRegisterableType(invoice)) {
            throw new BadRequestException(
                    invoice.getInvoiceType() + " cannot be registered as an e-invoice — only tax invoices, credit notes and debit notes can carry an IRN");
        }

        // Idempotency: an active registration (or an in-flight request) makes
        // any repeat click a no-op returning the current state.
        EInvoiceRegistration existing = registrationRepository
                .findFirstByInvoiceIdOrderByCreatedAtDescIdDesc(invoiceId).orElse(null);
        if (existing != null && (existing.getStatus() == EInvoiceStatus.REGISTERED
                || existing.getStatus() == EInvoiceStatus.REGISTRATION_PENDING
                || existing.getStatus() == EInvoiceStatus.CANCELLATION_PENDING)) {
            return EInvoiceStatusResponse.builder()
                    .invoiceId(invoiceId)
                    .derivedStatus(existing.getStatus().name())
                    .registration(EInvoiceStatusResponse.RegistrationView.from(existing))
                    .build();
        }
        EInvoiceRegistrationRequest request = requestRepository.findByInvoiceId(invoiceId).orElse(null);
        if (request != null && request.getState() == RegistrationRequestState.SUBMITTED) {
            return EInvoiceStatusResponse.builder()
                    .invoiceId(invoiceId)
                    .derivedStatus(EInvoiceStatus.REGISTRATION_PENDING.name())
                    .registration(existing == null ? null : EInvoiceStatusResponse.RegistrationView.from(existing))
                    .build();
        }

                String buyerGstin = invoice.getCustomerId() != null
                ? customerRepository.findById(invoice.getCustomerId()).map(c -> c.getGstIn()).orElse(null)
                : null;
        String buyerName = invoice.getCustomerId() != null
                ? customerRepository.findById(invoice.getCustomerId()).map(c -> c.getName()).orElse(null)
                : null;
        // Real providers need credentials for an actual submission; the Mock
        // provider never talks to an IRP. Refusing HERE (not at the toggle)
        // keeps enabling free while nothing is ever half-submitted.
        if (config.getProvider() != EInvoiceProviderId.MOCK) {
            Map<String, String> creds = credentialService.decrypt(config.getCredentialsEncrypted());
            if (creds.isEmpty()) {
                audit(businessId, userId, invoiceId, "IRN_BLOCKED", "no credentials stored", "FAILED");
                throw new BadRequestException(
                        "Add the provider API credentials in Settings before registering — nothing was sent to the government portal");
            }
        }

        Map<String, Object> payload = payloadMapper.buildPayload(invoice, config.getGstin(), buyerGstin, buyerName);
        String payloadHash = sha256(payload.toString());
        String idempotencyKey = sha256(businessId + ":" + invoiceId + ":" + payloadHash);

        EInvoiceRegistrationRequest req = request != null ? request
                : EInvoiceRegistrationRequest.builder().invoiceId(invoiceId).build();
        req.setIdempotencyKey(idempotencyKey);
        req.setState(RegistrationRequestState.SUBMITTED);
        req.setPayloadHash(payloadHash);
        req.setAttempts((req.getAttempts() == null ? 0 : req.getAttempts()) + 1);
        req.setLastAttemptAt(LocalDateTime.now());
        req.setProviderCorrelationId("req-" + UUID.randomUUID());
        req = requestRepository.save(req);

        audit(businessId, userId, invoiceId, "IRN_SUBMIT", "attempt " + req.getAttempts(), "PENDING");

        IrnResult result;
        try {
            result = providerRegistry.get(config.getProvider())
                    .generateIrn(config.getEnvironment(),
                            credentialService.decrypt(config.getCredentialsEncrypted()), payload);
        } catch (Exception e) {
            req.setState(RegistrationRequestState.FAILED);
            req.setLastResponseAt(LocalDateTime.now());
            req = requestRepository.save(req);
            audit(businessId, userId, invoiceId, "IRN_ERROR", e.getMessage(), "FAILED");
            throw new BadRequestException("E-invoice submission failed before reaching the provider: " + e.getMessage());
        }

        EInvoiceRegistration registration = existing != null ? existing
                : EInvoiceRegistration.builder()
                        .invoiceId(invoiceId)
                        .businessId(businessId)
                        .gstin(config.getGstin())
                        .provider(config.getProvider())
                        .environment(config.getEnvironment())
                        .docType(invoice.getInvoiceType().name())
                        .docNumber(invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : ("DRAFT-" + invoiceId))
                        .docDate(invoice.getInvoiceDate())
                        .build();

        if (result.success()) {
            registration.setStatus(EInvoiceStatus.REGISTERED);
            registration.setIrn(result.irn());
            registration.setAckNumber(result.ackNo());
            registration.setAckDate(result.ackDate());
            registration.setSignedQrData(result.signedQr());
            registration.setProviderReference(req.getProviderCorrelationId());
            registration.setIsMock(result.mock());
            registration.setRegisteredAt(LocalDateTime.now());
            registration.setErrorCode(null);
            registration.setErrorMessage(null);
            registration = registrationRepository.save(registration);

            // Stamp the invoice itself — the PDF and every screen read these.
            invoice.setIrn(result.irn());
            invoice.setAckNo(result.ackNo());
            invoice.setAckDate(result.ackDate() != null ? result.ackDate().toLocalDate() : null);
            invoice.setEinvoiceStatus(result.mock() ? "REGISTERED_MOCK" : "REGISTERED");
            invoiceRepository.save(invoice);

            req.setState(RegistrationRequestState.COMPLETED);
            req.setLastResponseAt(LocalDateTime.now());
            req = requestRepository.save(req);
            audit(businessId, userId, invoiceId, result.mock() ? "IRN_REGISTERED_MOCK" : "IRN_REGISTERED",
                    "ack " + result.ackNo(), result.mock() ? "REGISTERED_MOCK" : "REGISTERED");
        } else {
            registration.setStatus(EInvoiceStatus.REGISTRATION_FAILED);
            registration.setErrorCode(result.errorCode());
            registration.setErrorMessage(result.errorMessage());
            registration.setIsMock(false);
            registration = registrationRepository.save(registration);

            invoice.setEinvoiceStatus("FAILED");
            invoiceRepository.save(invoice);

            req.setState(RegistrationRequestState.FAILED);
            req.setLastResponseAt(LocalDateTime.now());
            req = requestRepository.save(req);
            audit(businessId, userId, invoiceId, "IRN_FAILED",
                    result.errorCode() + ": " + result.errorMessage(), "FAILED");
        }

        return EInvoiceStatusResponse.builder()
                .invoiceId(invoiceId)
                .derivedStatus(registration.getStatus().name())
                .registration(EInvoiceStatusResponse.RegistrationView.from(registration))
                .build();
    }

    private boolean isRegisterableType(Invoice invoice) {
        InvoiceType type = invoice.getInvoiceType();
        return type == InvoiceType.TAX_INVOICE
                || type == InvoiceType.CREDIT_NOTE
                || type == InvoiceType.DEBIT_NOTE;
    }

    private String sha256(String text) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private void audit(Long businessId, Long userId, Long invoiceId, String eventType, String reason, String providerResult) {
        auditRepository.save(com.insideinvoice.einvoice.entity.EInvoiceAuditEvent.builder()
                .businessId(businessId)
                .actorUserId(userId)
                .actorType("USER")
                .eventType(eventType)
                .invoiceId(invoiceId)
                .operation("REGISTER")
                .reason(reason)
                .providerResult(providerResult)
                .correlationId("inv-" + invoiceId)
                .build());
    }
}
