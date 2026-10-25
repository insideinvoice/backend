package com.insideinvoice.einvoice.provider;

import com.insideinvoice.einvoice.entity.EInvoiceEnvironment;
import com.insideinvoice.einvoice.entity.EInvoiceProviderId;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Test/local-only provider. Succeeds ONLY in the sandbox environment — the
 * production combination is rejected by the config service before it ever
 * reaches here. Every result is marked mock so nothing it produces can be
 * mistaken for government registration data.
 */
@Component
public class MockEInvoiceProvider implements EInvoiceProvider {

    @Override
    public EInvoiceProviderId id() {
        return EInvoiceProviderId.MOCK;
    }

    @Override
    public ConnectionTestResult testConnection(EInvoiceEnvironment environment, Map<String, String> credentials) {
        if (environment == EInvoiceEnvironment.PRODUCTION) {
            return new ConnectionTestResult(
                    ConnectionTestResult.PROVIDER_UNAVAILABLE,
                    "The Mock provider can never be used in production.", true);
        }
        return new ConnectionTestResult(
                ConnectionTestResult.CONNECTED,
                "Mock sandbox connection established (local testing only — no IRNs are generated).", true);
    }

    @Override
    public IrnResult generateIrn(EInvoiceEnvironment environment, Map<String, String> credentials,
                                 Map<String, Object> payload) {
        if (environment == EInvoiceEnvironment.PRODUCTION) {
            return IrnResult.failure("MOCK_PRODUCTION", "The Mock provider can never be used in production");
        }
        // Deterministic, visibly mock-stamped identifiers: the payload hash
        // makes repeat submissions of the same document return the same IRN,
        // mirroring IRP idempotency without impersonating a government value.
        int hash = Math.abs(String.valueOf(payload).hashCode());
        String docNumber = String.valueOf(payload.getOrDefault("documentNumber", "DOC"));
        return new IrnResult(
                true,
                true,
                "MOCK-IRN-" + Integer.toHexString(hash),
                "MOCK-ACK-" + (1000000000L + (hash % 900000000L)),
                java.time.LocalDateTime.now(),
                "MOCK-SIGNED-QR-" + Integer.toHexString(hash),
                null,
                null,
                Map.of("mockResultFor", docNumber, "environment", environment.name()));
    }
}
