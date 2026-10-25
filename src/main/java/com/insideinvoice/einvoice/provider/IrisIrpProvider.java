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
        // The full NIC/IRIS auth flow needs all three pieces; report precisely
        // what is missing instead of a generic failure. Nothing is sent yet.
        boolean hasClient = credentials.containsKey("clientId") && credentials.containsKey("clientSecret");
        boolean hasPortal = credentials.containsKey("apiUsername") && credentials.containsKey("apiPassword");
        boolean hasAppKey = credentials.containsKey("appKey");
        if (!(hasClient && hasPortal && hasAppKey)) {
            java.util.List<String> missing = new java.util.ArrayList<>();
            if (!hasClient) missing.add("client ID + secret");
            if (!hasPortal) missing.add("portal username + password");
            if (!hasAppKey) missing.add("app key");
            return new ConnectionTestResult(
                    ConnectionTestResult.PROVIDER_UNAVAILABLE,
                    "Configuration incomplete for " + environment.name() + ": missing "
                            + String.join(", ", missing)
                            + ". Everything is configured from Settings; nothing was sent to the IRP.",
                    false);
        }
        return new ConnectionTestResult(
                ConnectionTestResult.PROVIDER_UNAVAILABLE,
                "Configuration complete (client, portal credentials, app key"
                        + (credentials.containsKey("baseUrl") ? ", endpoint override" : "")
                        + "). IRIS IRP API submission is not activated on this deployment yet — nothing was "
                        + "sent to the IRP; the endpoint wiring lands with the integration phase.",
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
