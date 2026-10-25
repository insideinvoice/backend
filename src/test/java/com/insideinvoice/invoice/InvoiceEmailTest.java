package com.insideinvoice.invoice;

import com.insideinvoice.BaseIntegrationTest;
import com.insideinvoice.auth.entity.Role;
import com.insideinvoice.auth.entity.User;
import com.insideinvoice.customer.entity.Customer;
import com.insideinvoice.email.EmailService;
import com.insideinvoice.invoice.entity.Invoice;
import com.insideinvoice.invoice.share.InvoiceShareService;
import com.insideinvoice.invoice.share.dto.ShareLinkResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract tests for POST /api/invoices/{id}/email.
 *
 * <p>EmailService is mocked: nothing leaves the machine, and the exact
 * recipient/subject/HTML the platform would send is asserted instead. The
 * share-link lifecycle runs against the real database.</p>
 */
class InvoiceEmailTest extends BaseIntegrationTest {

    private static final String ORIGIN = "http://localhost:5173";

    @MockBean
    private EmailService emailService;

    private User owner;
    private Invoice invoice;
    private Customer customer;

    @BeforeEach
    void seedOwnerInvoice() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        owner = makeUser("mailowner-" + run, Role.USER);
        invoice = seedInvoice(owner);
        customer = customerRepository.findById(invoice.getCustomerId()).orElseThrow();
    }

    @Test
    @DisplayName("Owner sends: 200, correct recipient, branded subject, link + GST breakup in HTML")
    void ownerSendsInvoiceEmail() throws Exception {
        mockMvc.perform(multipart("/api/invoices/" + invoice.getId() + "/email")
                        .header("Authorization", bearerFor(owner))
                        .param("frontendOrigin", ORIGIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Invoice email sent to " + customer.getEmail()));

        ArgumentCaptor<String> to = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> fromName = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(1)).sendHtmlEmail(
                fromName.capture(), to.capture(), subject.capture(), html.capture(), text.capture(), any(), anyList());

        // From brands the user's business; delivery is via Inside Invoice.
        assertThat(fromName.getValue()).contains(owner.getName() + " Co").contains("via Inside Invoice");
        // Sent to the customer on file — never to the owner or a hardcoded address.
        assertThat(to.getValue()).isEqualTo(customer.getEmail());
        assertThat(subject.getValue()).contains(invoice.getInvoiceNumber()).contains("Tax Invoice");

        Invoice reloaded = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertThat(reloaded.getShareEnabled()).isTrue();
        assertThat(reloaded.getShareToken()).isNotBlank();
        String expectedUrl = ORIGIN + "/i/" + reloaded.getShareToken();

        assertThat(html.getValue())
                .contains(expectedUrl)
                .contains(invoice.getInvoiceNumber())
                .contains(owner.getName() + " Co")           // business heading
                .contains("Inside Invoice")                  // platform attribution
                .contains("Place of supply")                 // GST context in the meta grid
                .contains("Test Item")
                .contains("HSN 998877")
                .contains("236.00")                          // grand total with breakup
                .contains("Rupees Two Hundred Thirty Six Only");
        assertThat(text.getValue()).contains(expectedUrl).contains(invoice.getInvoiceNumber());
    }

    @Test
    @DisplayName("No body: falls back to the configured frontend base URL")
    void noBodyUsesConfiguredOrigin() throws Exception {
        mockMvc.perform(multipart("/api/invoices/" + invoice.getId() + "/email")
                        .header("Authorization", bearerFor(owner)))
                .andExpect(status().isOk());

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(1)).sendHtmlEmail(any(), any(), any(), html.capture(), any(), any(), anyList());
        assertThat(html.getValue()).contains("https://insideinvoice.in/i/");
    }

    @Test
    @DisplayName("Revoked link is recreated before the email goes out — never a dead link")
    void revokedLinkIsRecreated() throws Exception {
        ShareLinkResponse revoked = invoiceShareService.revoke(invoice.getId(), owner.getBusinessId());
        assertThat(revoked.getToken()).isNull();

        mockMvc.perform(multipart("/api/invoices/" + invoice.getId() + "/email")
                        .header("Authorization", bearerFor(owner))
                        .param("frontendOrigin", ORIGIN))
                .andExpect(status().isOk());

        Invoice reloaded = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertThat(reloaded.getShareEnabled()).isTrue();
        assertThat(reloaded.getShareToken()).isNotBlank();

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(1)).sendHtmlEmail(any(), any(), any(), html.capture(), any(), any(), anyList());
        assertThat(html.getValue()).contains("/i/" + reloaded.getShareToken());
    }

    @Test
    @DisplayName("Customer without an email is 400 and nothing is sent")
    void customerWithoutEmailIs400() throws Exception {
        customer.setEmail(null);
        customerRepository.save(customer);

        mockMvc.perform(multipart("/api/invoices/" + invoice.getId() + "/email")
                        .header("Authorization", bearerFor(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("no email address")));

        verify(emailService, never()).sendHtmlEmail(any(), any(), any(), any(), any(), any(), anyList());
    }

    @Test
    @DisplayName("Cross-tenant send is a uniform 404 and nothing is sent")
    void crossTenantSendIs404() throws Exception {
        String run = UUID.randomUUID().toString().substring(0, 8);
        User attacker = makeUser("mailattacker-" + run, Role.USER);

        mockMvc.perform(multipart("/api/invoices/" + invoice.getId() + "/email")
                        .header("Authorization", bearerFor(attacker)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));

        verify(emailService, never()).sendHtmlEmail(any(), any(), any(), any(), any(), any(), anyList());
    }

    @Test
    @DisplayName("Anonymous send is 401")
    void anonymousSendIs401() throws Exception {
        mockMvc.perform(multipart("/api/invoices/" + invoice.getId() + "/email"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Invalid frontend origin is 400, no link is created, nothing is sent")
    void invalidOriginIs400() throws Exception {
        mockMvc.perform(multipart("/api/invoices/" + invoice.getId() + "/email")
                        .header("Authorization", bearerFor(owner))
                        .param("frontendOrigin", "javascript:alert(1)"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Invalid frontend origin")));

        Invoice reloaded = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertThat(reloaded.getShareEnabled()).isFalse();
        verify(emailService, never()).sendHtmlEmail(any(), any(), any(), any(), any(), any(), anyList());
    }

    @Test
    @DisplayName("Uploaded invoice PDF is attached so the emailed doc matches the download (seal included)")
    void uploadedPdfIsAttached() throws Exception {
        // The browser renders the PDF with the seal/stamp applied and uploads it.
        byte[] pdf = "%PDF-1.7 fake invoice bytes".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);

        mockMvc.perform(multipart("/api/invoices/" + invoice.getId() + "/email")
                        .file(new org.springframework.mock.web.MockMultipartFile(
                                "pdf", "Tax_Invoice_" + invoice.getInvoiceNumber() + ".pdf",
                                "application/pdf", pdf))
                        .header("Authorization", bearerFor(owner))
                        .param("frontendOrigin", ORIGIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.List<com.insideinvoice.email.EmailAttachment>> parts =
                ArgumentCaptor.forClass((Class) java.util.List.class);
        verify(emailService, times(1)).sendHtmlEmail(
                any(), any(), any(), any(), any(), any(), parts.capture());

        // Exactly one part: the PDF (no logo seeded on this business).
        assertThat(parts.getValue()).hasSize(1);
        com.insideinvoice.email.EmailAttachment attachment = parts.getValue().get(0);
        assertThat(attachment.filename())
                .isEqualTo("Tax_Invoice_" + invoice.getInvoiceNumber() + ".pdf");
        assertThat(attachment.mimeType()).isEqualTo("application/pdf");
        assertThat(attachment.content()).isEqualTo(pdf);
    }

    @Test
    @DisplayName("The seller's uploaded logo is never embedded — the email header stays text-only")
    void uploadedLogoIsNeverEmbedded() throws Exception {
        // A real (tiny) PNG seeded on the business; the mailer is mocked, so
        // only the parts list and the HTML body are asserted.
        byte[] png = java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
        com.insideinvoice.business.entity.Business business =
                businessRepository.findById(owner.getBusinessId()).orElseThrow();
        business.setLogo(java.util.Base64.getEncoder().encodeToString(png));
        businessRepository.save(business);

        mockMvc.perform(multipart("/api/invoices/" + invoice.getId() + "/email")
                        .header("Authorization", bearerFor(owner))
                        .param("frontendOrigin", ORIGIN))
                .andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.List<com.insideinvoice.email.EmailAttachment>> parts =
                ArgumentCaptor.forClass((Class) java.util.List.class);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(1)).sendHtmlEmail(
                any(), any(), any(), html.capture(), any(), any(), parts.capture());

        // No logo part goes out (nothing would reference it) — the email is
        // text-only in its header, so no inline cid: image is attached.
        assertThat(parts.getValue()).isEmpty();

        // The body carries no image at all.
        assertThat(html.getValue()).doesNotContain("<img");
        assertThat(html.getValue()).doesNotContain("cid:");
    }

    @Test
    @DisplayName("A non-PDF upload is dropped — the email still sends, without the bad attachment")
    void nonPdfUploadIsDropped() throws Exception {
        byte[] notPdf = "not a pdf at all".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);

        mockMvc.perform(multipart("/api/invoices/" + invoice.getId() + "/email")
                        .file(new org.springframework.mock.web.MockMultipartFile(
                                "pdf", "evil.pdf", "application/pdf", notPdf))
                        .header("Authorization", bearerFor(owner))
                        .param("frontendOrigin", ORIGIN))
                .andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.List<com.insideinvoice.email.EmailAttachment>> parts =
                ArgumentCaptor.forClass((Class) java.util.List.class);
        // The email still sends; the bad upload never reaches the mailer (and no
        // logo is seeded here, so the parts list stays empty).
        verify(emailService, times(1)).sendHtmlEmail(
                any(), any(), any(), any(), any(), any(), parts.capture());
        assertThat(parts.getValue()).isEmpty();
    }
}
