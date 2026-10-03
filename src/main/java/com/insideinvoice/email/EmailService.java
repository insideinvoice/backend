package com.insideinvoice.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class EmailService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.mail.resend-api-key:}")
    private String resendApiKey;

    @Value("${app.mail.from:noreply@insideinvoice.com}")
    private String mailFrom;

    @Value("${app.mail.contact-email:insideinvoice87@gmail.com}")
    private String contactEmail;

    public void sendPasswordResetEmail(String toEmail, String resetToken, String temporaryPassword) {
        if (resendApiKey == null || resendApiKey.isBlank()) {
            log.warn("Resend API key not configured, skipping email send to: {}", toEmail);
            return;
        }

        try {
            String subject = "Your Inside Invoice Temporary Password";
            String htmlContent = buildPasswordResetHtml(temporaryPassword, resetToken);
            String textContent = buildPasswordResetText(temporaryPassword, resetToken);

            sendViaResend(toEmail, subject, htmlContent, textContent, null);
            log.info("Password reset email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send password reset email to: {}", toEmail, e);
        }
    }

    public void sendContactEmail(String name, String email, String phone, String message) {
        if (resendApiKey == null || resendApiKey.isBlank()) {
            log.warn("Resend API key not configured, skipping contact email");
            return;
        }

        try {
            String subject = "Inside Invoice Enquiry: " + name;
            String htmlContent = buildContactHtml(name, email, phone, message);
            String textContent = buildContactText(name, email, phone, message);

            sendViaResend(contactEmail, subject, htmlContent, textContent, email);
            log.info("Contact enquiry email sent to: {} | Subject: {}", contactEmail, subject);
        } catch (Exception e) {
            log.error("Failed to send contact email", e);
        }
    }

    private void sendViaResend(String toEmail, String subject, String htmlContent, String textContent, String replyTo) throws Exception {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("from", mailFrom);
        body.put("to", new String[]{toEmail});
        body.put("subject", subject);
        body.put("html", htmlContent);
        body.put("text", textContent);
        if (replyTo != null && !replyTo.isBlank()) {
            body.put("reply_to", replyTo);
        }
        body.put("headers", Map.of(
                "X-Entity-Ref-ID", UUID.randomUUID().toString(),
                "X-Priority", "3",
                "X-Mailer", "Inside Invoice"
        ));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(resendApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.exchange(
                "https://api.resend.com/emails",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class
        );

        if (response.getStatusCode().is2xxSuccessful()) {
            log.info("Email sent via Resend | from: {} | to: {} | subject: {}", mailFrom, toEmail, subject);
        } else {
            log.warn("Resend API returned status {}: {}", response.getStatusCode(), response.getBody());
        }
    }

    // ==================== PASSWORD RESET EMAIL ====================

    private String buildPasswordResetHtml(String temporaryPassword, String resetToken) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                </head>
                <body style="margin:0;padding:0;background-color:#f4f5f7;font-family:Arial,Helvetica,sans-serif;">
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background-color:#f4f5f7;padding:32px 16px;">
                        <tr><td align="center">
                            <table role="presentation" width="600" cellpadding="0" cellspacing="0" style="background-color:#ffffff;border-radius:12px;border:1px solid #e2e8f0;">
                                <tr><td style="padding:32px 40px;">
                                    <h2 style="margin:0 0 4px;color:#0f172a;font-size:22px;">Inside Invoice</h2>
                                    <p style="margin:0 0 20px;color:#64748b;font-size:13px;">Password Reset Request</p>
                                    <hr style="border:none;border-top:1px solid #e2e8f0;margin:0 0 20px;">
                                    <p style="margin:0 0 20px;color:#334155;font-size:15px;line-height:1.6;">
                                        We received a request to reset your password. Use the temporary password below to sign in:
                                    </p>
                                    <table role="presentation" cellpadding="0" cellspacing="0" style="background-color:#f1f5f9;border:1px solid #e2e8f0;border-radius:8px;padding:16px 20px;margin:0 0 24px;">
                                        <tr><td>
                                            <p style="margin:0;font-size:12px;color:#94a3b8;text-transform:uppercase;letter-spacing:1px;">Temporary Password</p>
                                            <p style="margin:8px 0 0;font-size:24px;font-weight:bold;color:#0f172a;font-family:monospace;letter-spacing:2px;">%s</p>
                                        </td></tr>
                                    </table>
                                    <p style="margin:0 0 24px;color:#64748b;font-size:13px;line-height:1.6;">
                                        For your security, you will be asked to change this password after signing in. If you did not request this reset, you can safely ignore this email.
                                    </p>
                                    <hr style="border:none;border-top:1px solid #e2e8f0;margin:24px 0 16px;">
                                    <p style="margin:0 0 4px;color:#94a3b8;font-size:12px;">This is an automated notification from Inside Invoice.</p>
                                    <p style="margin:0;color:#94a3b8;font-size:12px;">&copy; 2026 Inside Invoice. All rights reserved.</p>
                                </td></tr>
                            </table>
                        </td></tr>
                    </table>
                </body>
                </html>
                """.formatted(temporaryPassword != null ? temporaryPassword : "N/A");
    }

    private String buildPasswordResetText(String temporaryPassword, String resetToken) {
        return "Inside Invoice - Password Reset Request\n" +
               "================================\n\n" +
               "We received a request to reset your password.\n" +
               "Use the temporary password below to sign in:\n\n" +
               "  Temporary Password: " + (temporaryPassword != null ? temporaryPassword : "N/A") + "\n\n" +
               "For your security, you will be asked to change this password after signing in.\n" +
               "If you did not request this reset, you can safely ignore this email.\n\n" +
               "This is an automated notification from Inside Invoice.\n" +
               "(c) 2026 Inside Invoice. All rights reserved.\n";
    }

    // ==================== CONTACT ENQUIRY EMAIL ====================

    private String buildContactHtml(String name, String email, String phone, String message) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                </head>
                <body style="margin:0;padding:0;background-color:#f4f5f7;font-family:Arial,Helvetica,sans-serif;">
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background-color:#f4f5f7;padding:32px 16px;">
                        <tr><td align="center">
                            <table role="presentation" width="600" cellpadding="0" cellspacing="0" style="background-color:#ffffff;border-radius:12px;border:1px solid #e2e8f0;">
                                <tr><td style="padding:32px 40px;">
                                    <h2 style="margin:0 0 4px;color:#0f172a;font-size:22px;">Inside Invoice</h2>
                                    <p style="margin:0 0 20px;color:#64748b;font-size:13px;">New Enquiry Received</p>
                                    <hr style="border:none;border-top:1px solid #e2e8f0;margin:0 0 20px;">
                                    <p style="margin:0 0 16px;color:#0f172a;font-size:16px;font-weight:bold;">You have a new enquiry from %s</p>
                                    <hr style="border:none;border-top:1px solid #e2e8f0;margin:0 0 16px;">
                                    <p style="margin:0 0 8px;color:#334155;font-size:14px;"><strong>Name:</strong> %s</p>
                                    <p style="margin:0 0 8px;color:#334155;font-size:14px;"><strong>Email:</strong> %s</p>
                                    <p style="margin:0 0 8px;color:#334155;font-size:14px;"><strong>Phone:</strong> %s</p>
                                    <hr style="border:none;border-top:1px solid #e2e8f0;margin:16px 0;">
                                    <p style="margin:0 0 8px;color:#0f172a;font-size:13px;font-weight:bold;">Message:</p>
                                    <p style="margin:0 0 16px;color:#334155;font-size:14px;line-height:1.6;white-space:pre-wrap;">%s</p>
                                    <p style="margin:0 0 8px;color:#64748b;font-size:12px;">
                                        <a href="mailto:%s" style="color:#6366f1;text-decoration:none;">Reply to this email</a>
                                    </p>
                                    <hr style="border:none;border-top:1px solid #e2e8f0;margin:20px 0 16px;">
                                    <p style="margin:0 0 4px;color:#94a3b8;font-size:12px;">This is an automated notification from Inside Invoice.</p>
                                    <p style="margin:0;color:#94a3b8;font-size:12px;">&copy; 2026 Inside Invoice. All rights reserved.</p>
                                </td></tr>
                            </table>
                        </td></tr>
                    </table>
                </body>
                </html>
                """.formatted(
                escapeHtml(name),
                escapeHtml(name),
                escapeHtml(email),
                escapeHtml(phone),
                escapeHtml(message),
                escapeHtml(email)
        );
    }

    private String buildContactText(String name, String email, String phone, String message) {
        return "Inside Invoice - New Enquiry\n" +
               "============================\n\n" +
               "You have a new enquiry from " + name + "\n\n" +
               "Name:  " + name + "\n" +
               "Email: " + email + "\n" +
               "Phone: " + phone + "\n\n" +
               "Message:\n" + message + "\n\n" +
               "Reply to this email to respond to " + name + "\n\n" +
               "This is an automated notification from Inside Invoice.\n" +
               "(c) 2026 Inside Invoice. All rights reserved.\n";
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
