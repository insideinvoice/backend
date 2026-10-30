package com.insideinvoice.invoice.share;

import com.insideinvoice.business.entity.Business;
import com.insideinvoice.business.repository.BusinessRepository;
import com.insideinvoice.customer.entity.Customer;
import com.insideinvoice.customer.repository.CustomerRepository;
import com.insideinvoice.email.EmailAttachment;
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

    /** Cap on the client-rendered PDF attachment; anything larger is dropped. */
    private static final long MAX_PDF_BYTES = 10 * 1024 * 1024; // 10 MB

    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final BusinessRepository businessRepository;
    private final InvoiceShareService invoiceShareService;
    private final EmailService emailService;
    private final HeadlessInvoicePdfService headlessInvoicePdfService;
    private final PublicInvoicePdfService publicInvoicePdfService;

    @Value("${app.frontend-base-url:https://insideinvoice.in}")
    private String defaultFrontendBaseUrl;

    /**
     * @return the address this invoice would be emailed to, validating that it
     *         CAN be emailed at all (invoice exists in this business, customer
     *         exists and has an address).
     *
     * <p>Used by the async path to fail fast on the request thread: a send
     * that could never succeed must surface as a real 4xx to the caller, not
     * be queued and silently die in a worker where the user only saw a
     * success toast.</p>
     */
    public String resolveRecipient(Long invoiceId, Long businessId) {
        Invoice invoice = invoiceRepository.findByIdAndBusinessIdWithItems(invoiceId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", invoiceId));
        Customer customer = customerRepository.findByIdAndBusinessId(invoice.getCustomerId(), businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", invoice.getCustomerId()));
        if (customer.getEmail() == null || customer.getEmail().isBlank()) {
            throw new BadRequestException("This customer has no email address. Add one to the customer and try again.");
        }
        return customer.getEmail();
    }

    /**
     * @return the recipient address the invoice was sent to
     */
    public String sendInvoiceEmail(Long invoiceId, Long businessId, String frontendOrigin) {
        return sendInvoiceEmail(invoiceId, businessId, frontendOrigin, null, null);
    }

    /**
     * @param pdfAttachment optional client-rendered PDF of the invoice (seal/stamp
     *                      already applied by the browser). When present it is
     *                      attached so the emailed document is byte-identical to
     *                      what the user downloads.
     * @return the recipient address the invoice was sent to
     */
    public String sendInvoiceEmail(Long invoiceId, Long businessId, String frontendOrigin,
                                   byte[] pdfAttachment, String pdfFilename) {
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
        // At most one part: the invoice PDF. The seller logo is never
        // embedded — the email header is text-only, so nothing in the message
        // can arrive broken or be stripped by a mail client.

        // V52: the attachment must be the EXACT template the user picked in the
        // UI, pixel for pixel. Headless Chromium prints the public share page
        // (the customer-facing React document built from the same payload), so
        // API/PAT callers — who have no browser of their own — get the real
        // template, not a re-interpretation of it. The generic PDFBox renderer
        // is strictly a last resort when no engine exists on the host.
        byte[] effectivePdf = pdfAttachment;
        String rendererKind = "client";
        if (effectivePdf == null || effectivePdf.length == 0) {
            byte[] exact = headlessInvoicePdfService.render(share.getToken(), invoice.getInvoiceNumber());
            if (exact != null) {
                effectivePdf = exact;
                rendererKind = "template-exact";
            } else {
                try {
                    effectivePdf = publicInvoicePdfService.render(
                            new ResolvedPublicInvoice(invoice, business, customer));
                    rendererKind = "generic";
                } catch (Exception e) {
                    log.warn("Server-side PDF render failed for invoice {}; sending email without attachment",
                            invoice.getInvoiceNumber(), e);
                }
            }
        }

        java.util.List<com.insideinvoice.email.EmailAttachment> parts = new java.util.ArrayList<>();
        com.insideinvoice.email.EmailAttachment pdf = toPdfAttachment(effectivePdf, pdfFilename, invoice.getInvoiceNumber());
        if (pdf != null) parts.add(pdf);
        emailService.sendHtmlEmail(fromName, customer.getEmail().trim(),
                content.subject(), content.html(), content.text(),
                notBlank(business.getEmail()) ? business.getEmail().trim() : null, parts);

        log.info("Invoice email sent | invoice: {} | to: {} | parts: {} | renderer: {}",
                invoice.getInvoiceNumber(), customer.getEmail(), pdf != null ? "pdf" : "-", rendererKind);
        return customer.getEmail().trim();
    }

    /**
     * The attachment is generated in the browser and posted by an authenticated
     * user, so it is validated before it is trusted: it must actually be a PDF
     * (magic bytes) and stay under the size cap. A bad upload is dropped rather
     * than failing the send — the email (with its share link) must still go out.
     */
    private EmailAttachment toPdfAttachment(byte[] pdf, String filename, String invoiceNumber) {
        if (pdf == null || pdf.length == 0) return null;
        if (pdf.length > MAX_PDF_BYTES) {
            log.warn("Invoice PDF attachment too large ({} bytes), sending email without it", pdf.length);
            return null;
        }
        if (pdf.length < 5 || pdf[0] != '%' || pdf[1] != 'P' || pdf[2] != 'D' || pdf[3] != 'F') {
            log.warn("Invoice PDF attachment failed the PDF magic-byte check, sending email without it");
            return null;
        }
        return EmailAttachment.pdf(sanitizeFilename(filename, invoiceNumber), pdf);
    }

    /**
     * Strips anything that could break out of the MIME filename header
     * (path separators, control characters) and guarantees a .pdf suffix.
     */
    static String sanitizeFilename(String filename, String invoiceNumber) {
        String fallback = "Invoice_" + (invoiceNumber == null ? "document" : invoiceNumber) + ".pdf";
        if (filename == null || filename.isBlank()) return fallback;
        String cleaned = filename.replaceAll("[\\\\/\\r\\n\\t]", "_")
                .replaceAll("[^A-Za-z0-9._ -]", "")
                .trim();
        if (cleaned.isBlank() || cleaned.equals(".") || cleaned.equals("..")) return fallback;
        return cleaned.toLowerCase().endsWith(".pdf") ? cleaned : cleaned + ".pdf";
    }

    /**
     * Fail-fast origin check for the async path: validates the caller's
     * frontend origin on the REQUEST thread before anything is queued, so a
     * bad origin is a real 400 response instead of a queued task that dies
     * silently in a worker (the user would only see a success toast).
     *
     * @return the normalized base URL (callers may ignore the value — the
     *         send path recomputes it inside the worker)
     */
    public String resolveFrontendOrigin(String frontendOrigin) {
        return resolveFrontendBase(frontendOrigin);
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
