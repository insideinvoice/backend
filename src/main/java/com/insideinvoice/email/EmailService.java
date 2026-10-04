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

    private static final String LOGO_DATA_URI = "data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNjQiIGhlaWdodD0iNjQiIHZpZXdCb3g9IjAgMCAyNTYgMjU2IiBmaWxsPSJub25lIiB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciPgogIDxyZWN0IHdpZHRoPSIyNTYiIGhlaWdodD0iMjU2IiByeD0iNTYiIGZpbGw9InVybCgjZ3JhZDEpIi8+CiAgPGNpcmNsZSBjeD0iMjIwIiBjeT0iMzYiIHI9IjQ4IiBmaWxsPSJ3aGl0ZSIgb3BhY2l0eT0iMC4wNSIvPgogIDxjaXJjbGUgY3g9IjM2IiBjeT0iMjIwIiByPSI2NCIgZmlsbD0id2hpdGUiIG9wYWNpdHk9IjAuMDUiLz4KICA8Y2lyY2xlIGN4PSI5NiIgY3k9IjgwIiByPSIyMCIgZmlsbD0id2hpdGUiLz4KICA8cmVjdCB4PSI3NiIgeT0iMTEyIiB3aWR0aD0iNDAiIGhlaWdodD0iOTYiIHJ4PSIyMCIgZmlsbD0id2hpdGUiLz4KICA8Y2lyY2xlIGN4PSIxNzYiIGN5PSIxMDQiIHI9IjE0LjQiIGZpbGw9IndoaXRlIiBvcGFjaXR5PSIwLjk1Ii8+CiAgPHJlY3QgeD0iMTYxLjYiIHk9IjEzNiIgd2lkdGg9IjI4LjgiIGhlaWdodD0iNzIiIHJ4PSIxNC40IiBmaWxsPSJ3aGl0ZSIgb3BhY2l0eT0iMC45NSIvPgogIDxkZWZzPgogICAgPGxpbmVhckdyYWRpZW50IGlkPSJncmFkMSIgeDE9IjAlIiB5MT0iMCUiIHgyPSIxMDAlIiB5Mj0iMTAwJSI+CiAgICAgIDxzdG9wIG9mZnNldD0iMCUiIHN0eWxlPSJzdG9wLWNvbG9yOiMzMzQxNTU7c3RvcC1vcGFjaXR5OjEiIC8+CiAgICAgIDxzdG9wIG9mZnNldD0iNTAlIiBzdHlsZT0ic3RvcC1jb2xvcjojNDc1NTY5O3N0b3Atb3BhY2l0eToxIiAvPgogICAgICA8c3RvcCBvZmZzZXQ9IjEwMCUiIHN0eWxlPSJzdG9wLWNvbG9yOiMxZTI5M2I7c3RvcC1vcGFjaXR5OjEiIC8+CiAgICA8L2xpbmVhckdyYWRpZW50PgogIDwvZGVmcz4KPC9zdmc+";

    @Value("${app.mail.resend-api-key:}")
    private String resendApiKey;

    @Value("${app.mail.from:noreply@insideinvoice.com}")
    private String mailFrom;

    @Value("${app.mail.contact-email:insideinvoice87@gmail.com}")
    private String contactEmail;

    public void sendOtpEmail(String toEmail, String otp) {
        if (resendApiKey == null || resendApiKey.isBlank()) {
            log.warn("Resend API key not configured, skipping email send to: {}", toEmail);
            return;
        }

        try {
            String subject = "Your Inside Invoice Verification Code";
            String htmlContent = buildOtpHtml(otp);
            String textContent = buildOtpText(otp);

            sendViaResend(toEmail, subject, htmlContent, textContent, null);
            log.info("OTP email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send OTP email to: {}", toEmail, e);
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

    public void sendRawEmail(String toEmail, String subject, String textContent) {
        if (resendApiKey == null || resendApiKey.isBlank()) {
            log.warn("Resend API key not configured, skipping raw email send");
            return;
        }
        try {
            sendViaResend(toEmail, subject, "<pre style=\"white-space:pre-wrap;font-family:monospace;\">"
                    + escapeHtml(textContent) + "</pre>", textContent, null);
            log.info("Raw email forwarded to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send raw email to: {}", toEmail, e);
        }
    }

    private void sendViaResend(String toEmail, String subject, String htmlContent, String textContent, String replyTo) throws Exception {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("from", "Inside Invoice <" + mailFrom + ">");
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

    // ==================== OTP EMAIL ====================

    private String buildOtpHtml(String otp) {
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
                                <tr><td style="padding:40px;">
                                    <h1 style="margin:0 0 28px;color:#0f172a;font-size:26px;font-weight:bold;text-align:center;">Here is your verification code:</h1>
                                    <table role="presentation" cellpadding="0" cellspacing="0" style="margin:0 auto 28px;border:2px solid #e2e8f0;border-radius:12px;background-color:#f8fafc;">
                                        <tr><td style="padding:24px 56px;">
                                            <p style="margin:0;font-size:44px;font-weight:bold;color:#0f172a;font-family:monospace;letter-spacing:12px;text-align:center;">%s</p>
                                        </td></tr>
                                    </table>
                                    <p style="margin:0 0 10px;color:#334155;font-size:15px;text-align:center;">Please make sure you never share this code with anyone.</p>
                                    <p style="margin:0 0 32px;color:#64748b;font-size:14px;text-align:center;">Note: The code will expire in 10 minutes.</p>
                                    <table role="presentation" cellpadding="0" cellspacing="0" style="margin:0 auto;border-top:1px solid #e2e8f0;padding-top:24px;">
                                        <tr><td style="text-align:center;">
                                            <p style="margin:0 0 6px;color:#334155;font-size:14px;">Didn't try to log in? Someone may know your password</p>
                                            <p style="margin:0;color:#334155;font-size:14px;">- change it in your account and contact support.</p>
                                        </td></tr>
                                    </table>
                                </td></tr>
                                <tr><td style="padding:24px 40px;background-color:#f8fafc;border-top:1px solid #e2e8f0;border-radius:0 0 12px 12px;">
                                    <p style="margin:0 0 8px;color:#0f172a;font-size:16px;font-weight:bold;text-align:center;">Inside Invoice</p>
                                    <p style="margin:0 0 12px;text-align:center;">
                                        <a href="https://x.com/InsideInvoice" style="color:#64748b;text-decoration:none;font-size:14px;">X / Twitter</a>
                                    </p>
                                    <p style="margin:0;color:#94a3b8;font-size:13px;text-align:center;">&copy; 2026 Inside Invoice. All rights reserved.</p>
                                </td></tr>
                            </table>
                        </td></tr>
                    </table>
                </body>
                </html>
                """.formatted(otp != null ? otp : "N/A");
    }

    private String buildOtpText(String otp) {
        return "Inside Invoice\n" +
               "=============\n\n" +
               "Here is your verification code:\n\n" +
               "  " + (otp != null ? otp : "N/A") + "\n\n" +
               "Please make sure you never share this code with anyone.\n" +
               "Note: The code will expire in 10 minutes.\n\n" +
               "Didn't try to log in? Someone may know your password - change it in your account and contact support.\n\n" +
               "Inside Invoice\n" +
               "X / Twitter: https://x.com/InsideInvoice\n" +
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
