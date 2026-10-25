package com.insideinvoice.einvoice.provider;

import com.insideinvoice.einvoice.entity.EInvoiceEnvironment;
import com.insideinvoice.einvoice.entity.EInvoiceProviderId;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * IRIS IRP adapter — transport placeholder. The Settings slice stores the
 * credentials securely and reports an honest, explicit "not activated"
 * connection state; it never fabricates a successful IRP authentication.
 * Endpoint wiring lands with the IRP integration phase, at which point this
 * implementation performs the provider's documented auth + health calls.
 */
@Component
public class IrisIrpProvider implements EInvoiceProvider {

    @Override
    public EInvoiceProviderId id() {
        return EInvoiceProviderId.IRIS_IRP;
    }

    @Override
    public ConnectionTestResult testConnection(EInvoiceEnvironment environment, Map<String, String> credentials) {
        return new ConnectionTestResult(
                ConnectionTestResult.PROVIDER_UNAVAILABLE,
                "IRIS IRP API integration is not activated on this deployment yet. Credentials are stored "
                        + "securely and will be used once the integration is switched on — nothing was sent "
                        + "to the IRP.",
                false);
    }

    @Override
    public IrnResult generateIrn(EInvoiceEnvironment environment, Map<String, String> credentials,
                                 Map<String, Object> payload) {
        // Honest refusal: nothing is submitted and nothing is fabricated. The
        // endpoint wiring + signed-payload exchange land with the IRP phase.
        return IrnResult.failure(
                "IRP_NOT_ACTIVATED",
                "IRIS IRP submission is not activated on this deployment yet — the invoice was NOT sent "
                        + "to the government portal and no IRN was issued. Stored credentials will be used "
                        + "once the integration is switched on.");
    }
}
