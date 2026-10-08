package com.insideinvoice.email;

import com.insideinvoice.exception.EmailDeliveryException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Delivery-failure contract: an explicit user action (invoice email) must
 * surface unconfigured mail as 503 instead of silently skipping like the
 * fire-and-forget OTP/contact helpers do.
 */
class EmailServiceSendTest {

    @Test
    @DisplayName("Blank API key -> EmailDeliveryException with 503")
    void blankKeyThrowsServiceUnavailable() {
        EmailService service = new EmailService();
        ReflectionTestUtils.setField(service, "resendApiKey", "");

        assertThatThrownBy(() -> service.sendHtmlEmail(
                "Acme via Inside Invoice", "cust@example.com", "Invoice 1", "<p>hi</p>", "hi", null))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageContaining("not configured")
                .extracting(e -> ((EmailDeliveryException) e).getStatus())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("Null API key -> EmailDeliveryException with 503")
    void nullKeyThrowsServiceUnavailable() {
        EmailService service = new EmailService();
        ReflectionTestUtils.setField(service, "resendApiKey", null);

        assertThatThrownBy(() -> service.sendHtmlEmail(
                "Acme via Inside Invoice", "cust@example.com", "Invoice 1", "<p>hi</p>", "hi", null))
                .isInstanceOf(EmailDeliveryException.class);
    }

    @Test
    @DisplayName("From display name is sanitized before hitting the header")
    void fromNameIsSanitized() {
        assertThat(EmailService.sanitizeFromName("Acme\r\nBcc: evil@example.com"))
                .doesNotContain("\r").doesNotContain("\n");
        assertThat(EmailService.sanitizeFromName(null)).isEqualTo("Inside Invoice");
        assertThat(EmailService.sanitizeFromName("  ")).isEqualTo("Inside Invoice");
    }

    @Test
    @DisplayName("API key: raw, base64-of-re_, and junk inputs all resolve predictably")
    void apiKeyResolution() {
        String raw = "re_test_key_123";
        String b64 = java.util.Base64.getEncoder().encodeToString(raw.getBytes());

        assertThat(EmailService.resolveApiKey(raw)).isEqualTo(raw);
        assertThat(EmailService.resolveApiKey(b64)).isEqualTo(raw);
        assertThat(EmailService.resolveApiKey("  " + raw + "  ")).isEqualTo(raw);
        // base64 that decodes to something Resend-shaped is NOT accepted
        assertThat(EmailService.resolveApiKey(java.util.Base64.getEncoder().encodeToString("hello".getBytes())))
                .isEqualTo(java.util.Base64.getEncoder().encodeToString("hello".getBytes()));
        // invalid base64 characters fall back to the raw value
        assertThat(EmailService.resolveApiKey("not_base64!!")).isEqualTo("not_base64!!");
        assertThat(EmailService.resolveApiKey(null)).isEmpty();
        assertThat(EmailService.resolveApiKey("")).isEmpty();
    }
}
