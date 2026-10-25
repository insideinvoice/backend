package com.insideinvoice.einvoice.provider;

import com.insideinvoice.einvoice.entity.EInvoiceProviderId;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class EInvoiceProviderRegistry {

    private final Map<EInvoiceProviderId, EInvoiceProvider> providers = new EnumMap<>(EInvoiceProviderId.class);

    public EInvoiceProviderRegistry(List<EInvoiceProvider> implementations) {
        implementations.forEach(p -> providers.put(p.id(), p));
    }

    public EInvoiceProvider get(EInvoiceProviderId id) {
        EInvoiceProvider provider = providers.get(id);
        if (provider == null) {
            throw new IllegalArgumentException("Unknown e-invoice provider: " + id);
        }
        return provider;
    }
}
