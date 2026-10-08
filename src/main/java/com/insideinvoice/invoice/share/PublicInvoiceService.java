package com.insideinvoice.invoice.share;

import com.insideinvoice.exception.ResourceNotFoundException;
import com.insideinvoice.business.entity.Business;
import com.insideinvoice.business.repository.BusinessRepository;
import com.insideinvoice.customer.entity.Customer;
import com.insideinvoice.customer.repository.CustomerRepository;
import com.insideinvoice.invoice.entity.Invoice;
import com.insideinvoice.invoice.repository.InvoiceRepository;
import com.insideinvoice.invoice.share.dto.PublicInvoiceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

/**
 * Anonymous read path for public invoice links.
 *
 * <p>Access rule: exactly one condition grants access - a syntactically valid token that
 * resolves to an invoice row whose sharing is currently enabled. Every other input
 * (missing, malformed, unknown, revoked, rotated-away) fails with the same generic
 * 404 so the endpoint cannot be used to probe which tokens exist.</p>
 */
@Service
@RequiredArgsConstructor
public class PublicInvoiceService {

    /** base64url of 32 random bytes = 43 chars; the pattern also cheaply rejects junk before any DB work. */
    private static final Pattern TOKEN_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{22,64}$");

    private final InvoiceRepository invoiceRepository;
    private final BusinessRepository businessRepository;
    private final CustomerRepository customerRepository;
    private final PublicInvoiceAssembler assembler;

    @Transactional(readOnly = true)
    public ResolvedPublicInvoice resolve(String token) {
        if (token == null || !TOKEN_PATTERN.matcher(token).matches()) {
            throw notFound();
        }
        Invoice invoice = invoiceRepository.findActiveByShareToken(token).orElseThrow(PublicInvoiceService::notFound);

        // Force-load items while the transaction is open so callers can render outside it.
        invoice.getItems().size();

        Business business = businessRepository.findById(invoice.getBusinessId()).orElseThrow(PublicInvoiceService::notFound);

        Customer customer = customerRepository.findById(invoice.getCustomerId()).orElse(null);
        if (customer != null && !invoice.getBusinessId().equals(customer.getBusinessId())) {
            customer = null; // integrity guard: never surface a buyer row from another business
        }
        return new ResolvedPublicInvoice(invoice, business, customer);
    }

    @Transactional(readOnly = true)
    public PublicInvoiceResponse getPublicInvoice(String token) {
        return assembler.assemble(resolve(token));
    }

    static ResourceNotFoundException notFound() {
        // Generic and token-free: the same response for invalid, revoked and unknown links.
        return new ResourceNotFoundException("Invoice not found");
    }
}
