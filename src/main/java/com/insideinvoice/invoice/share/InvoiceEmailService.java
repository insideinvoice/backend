package com.insideinvoice.invoice.share;

import com.insideinvoice.business.entity.Business;
import com.insideinvoice.business.repository.BusinessRepository;
import com.insideinvoice.customer.entity.Customer;
import com.insideinvoice.customer.repository.CustomerRepository;
import com.insideinvoice.email.EmailService;
import com.insideinvoice.exception.BadRequestException;
import com.insideinvoice.exception.ResourceNotFoundException;
import com.insideinvoice.invoice.entity.Invoice;
import com.insideinvoice.invoice.repository.InvoiceRepository;
import com.insideinvoice.invoice.share.dto.ShareLinkResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Sends an invoice to the customer by email, always carrying a working public
 * share link (the link is created on demand). Ownership comes exclusively from
 * the authenticated business id — never from the request.
 *
 * <p>No transaction wraps the outbound HTTP call: the share link commits first
 * (its own transaction inside {@link InvoiceShareService}), items are initialized
 * via join fetch, and email failures surface as {@code EmailDeliveryException}
 * without rolling anything back.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceEmailService {

    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final BusinessRepository businessRepository;
    private final InvoiceShareService invoiceShareService;
    private final EmailService emailService;

    @Value("${app.frontend-base-url:https://insideinvoice.in}")
    private String defaultFrontendBaseUrl;

    /**
     * @return the recipient address the invoice was sent to
     */
    public String sendInvoiceEmail(Long invoiceId, Long businessId, String frontendOrigin) {
        Invoice invoice = invoiceRepository.findByIdAndBusinessIdWithItems(invoiceId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", invoiceId));

        Customer customer = customerRepository.findByIdAndBusinessId(invoice.getCustomerId(), businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", invoice.getCustomerId()));
        if (customer.getEmail() == null || customer.getEmail().isBlank()) {
            throw new BadRequestException("This customer has no email address. Add one to the customer and try again.");
        }

        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business", "id", businessId));

        // Validate the origin BEFORE any side effect — a bad request must not mint a link.
        String base = resolveFrontendBase(frontendOrigin);

        // The email must never ship a dead link: create one if absent or revoked.
        ShareLinkResponse share = invoiceShareService.createOrRetrieve(invoiceId, businessId);
        String shareUrl = base + "/i/" + share.getToken();

        InvoiceEmailTemplate.Content content =
                InvoiceEmailTemplate.build(invoice, customer, business, shareUrl);

        String fromName = safeName(business.getBusinessName()) + " via Inside Invoice";
        emailService.sendHtmlEmail(fromName, customer.getEmail().trim(),
                content.subject(), content.html(), content.text(),
                notBlank(business.getEmail()) ? business.getEmail().trim() : null);

        log.info("Invoice email sent | invoice: {} | to: {}", invoice.getInvoiceNumber(), customer.getEmail());
        return customer.getEmail().trim();
    }

    /**
     * Frontends pass their own origin so preview/canonical domains stay correct;
     * otherwise the configured {@code app.frontend-base-url} is used. Only a bare
     * http(s) host is accepted — nothing else can end up inside the email.
     */
    private String resolveFrontendBase(String frontendOrigin) {
        String base = (frontendOrigin == null || frontendOrigin.isBlank())
                ? defaultFrontendBaseUrl : frontendOrigin.trim();
        if (base.length() > 100) {
            throw new BadRequestException("Invalid frontend origin for the invoice link");
        }
        base = base.replaceAll("/+$", "");
        if (!base.matches("https?://[^\\s/?#]+")) {
            throw new BadRequestException("Invalid frontend origin for the invoice link");
        }
        return base;
    }

    private static String safeName(String businessName) {
        return businessName == null || businessName.isBlank() ? "Inside Invoice" : businessName.trim();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
