package com.insideinvoice.einvoice.provider;

import com.insideinvoice.einvoice.entity.EInvoiceEnvironment;
import com.insideinvoice.einvoice.entity.EInvoiceProviderId;

import java.util.Map;

/**
 * Abstraction over an authorised e-invoicing integration provider. IRN
 * generation/cancellation arrive with the IRP integration phase; Settings
 * only needs {@link #testConnection} for now, and every implementation must
 * report HONEST results — never fabricate a success.
 */
public interface EInvoiceProvider {

    EInvoiceProviderId id();

    /**
     * @param credentials decrypted provider credentials (client id/secret etc.)
     * @return the actual outcome of the connectivity/authentication attempt
     */
    ConnectionTestResult testConnection(EInvoiceEnvironment environment, Map<String, String> credentials);

    /**
     * Submit the e-invoice payload for IRN generation. Implementations MUST
     * return the provider's actual response or an explicit failure — never a
     * fabricated success.
     */
    IrnResult generateIrn(EInvoiceEnvironment environment, Map<String, String> credentials, Map<String, Object> payload);
}
