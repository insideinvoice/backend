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

    @Value("${app.frontend-base-url:https://insideinvoice.in}")
    private String defaultFrontendBaseUrl;

    @Value("${app.mail.logo-url:}")
    private String sellerLogoUrl;

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
                InvoiceEmailTemplate.build(invoice, customer, business, shareUrl, logoRefFor(business));

        String fromName = safeName(business.getBusinessName()) + " via Inside Invoice";
        // Two parts when both exist: the client PDF + the seller logo as an
        // INLINE content-id part so the email header shows the ACTUAL uploaded
        // logo (data: URIs are stripped by Gmail, static config URLs go stale).
        com.insideinvoice.email.EmailAttachment logoAttachment = logoAttachment(business.getLogo());
        java.util.List<com.insideinvoice.email.EmailAttachment> parts = new java.util.ArrayList<>();
        com.insideinvoice.email.EmailAttachment pdf = toPdfAttachment(pdfAttachment, pdfFilename, invoice.getInvoiceNumber());
        if (pdf != null) parts.add(pdf);
        if (logoAttachment != null) parts.add(logoAttachment);
        emailService.sendHtmlEmail(fromName, customer.getEmail().trim(),
                content.subject(), content.html(), content.text(),
                notBlank(business.getEmail()) ? business.getEmail().trim() : null, parts);

        log.info("Invoice email sent | invoice: {} | to: {} | parts: {}",
                invoice.getInvoiceNumber(), customer.getEmail(),
                (pdf != null ? "pdf" : "-") + (logoAttachment != null ? "+logo" : ""));
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

    // ---------------------------------------------------------------- logo
    // The uploaded logo is Base64 (optionally wrapped in a data URI). It is
    // decoded once per send; a decodable PNG/JPEG under the size cap becomes
    // an INLINE content-id attachment ("seller-logo") that the HTML references
    // via cid:. Anything else falls back to the configured static logo URL.

    private static final String LOGO_CID = "seller-logo";
    private static final int MAX_LOGO_BYTES = 512 * 1024; // inline part cap

    /** The HTML head renders the uploaded logo inline when decodable, else the
     *  configured public URL, else nothing (template hides the image). */
    private String logoRefFor(Business business) {
        return logoAttachment(business.getLogo()) != null
                ? "cid:" + LOGO_CID
                : (notBlank(sellerLogoUrl) ? sellerLogoUrl : null);
    }

    private EmailAttachment logoAttachment(String stored) {
        if (stored == null || stored.isBlank()) return null;
        String b64 = stored.trim();
        int comma = b64.indexOf(","); // tolerate a data-URI wrapper
        if (b64.startsWith("data:image/") && comma > 0) b64 = b64.substring(comma + 1).trim();
        byte[] bytes;
        try {
            bytes = java.util.Base64.getDecoder().decode(b64);
        } catch (IllegalArgumentException e) {
            log.warn("Seller logo is not valid base64; email header falls back to text-only");
            return null;
        }
        if (bytes.length == 0 || bytes.length > MAX_LOGO_BYTES) {
            if (bytes.length > MAX_LOGO_BYTES) log.warn("Seller logo too large ({} bytes) for inline embedding", bytes.length);
            return null;
        }
        // Sniff the raster type — PNG or JPEG only; anything else (SVG etc.)
        // is not broadly renderable in mail clients.
        String mime;
        if (bytes.length >= 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P') {
            mime = "image/png";
        } else if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && bytes[1] == (byte) 0xD8 && bytes[2] == (byte) 0xFF) {
            mime = "image/jpeg";
        } else {
            log.info("Seller logo is not PNG/JPEG; email header falls back without it");
            return null;
        }
        return EmailAttachment.inline("seller-logo" + (mime.endsWith("png") ? ".png" : ".jpg"),
                bytes, mime, LOGO_CID);
    }
}
