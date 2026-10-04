package com.insideinvoice.email;

import com.insideinvoice.auth.dto.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/email")
@RequiredArgsConstructor
@Slf4j
public class EmailInboundController {

    private final EmailService emailService;

    @Value("${app.mail.from}")
    private String mailFrom;

    @Value("${app.mail.contact-email}")
    private String contactEmail;

    @PostMapping("/inbound")
    public ResponseEntity<?> handleInbound(@RequestBody Map<String, Object> payload) {
        try {
            log.info("Inbound email webhook received: {}", payload);

            String from = extractField(payload, "from");
            String subject = extractField(payload, "subject");
            String text = extractField(payload, "text");
            String html = extractField(payload, "html");
            String to = extractField(payload, "to");

            if (text == null || text.isBlank()) {
                text = html != null ? html.replaceAll("<[^>]*>", "") : "(no content)";
            }

            String forwardedSubject = "[Inbound] " + (subject != null && !subject.isBlank() ? subject : "No subject")
                    + " → " + (to != null ? to : "unknown");

            emailService.sendRawEmail(
                    contactEmail,
                    forwardedSubject,
                    "From: " + (from != null ? from : "unknown")
                            + "\nTo: " + (to != null ? to : "unknown")
                            + "\n\n" + text
            );

            return ResponseEntity.ok(ApiResponse.success("Forwarded", null));
        } catch (Exception e) {
            log.error("Failed to forward inbound email", e);
            return ResponseEntity.ok(ApiResponse.success("Error", null));
        }
    }

    private String extractField(Map<String, Object> payload, String key) {
        Object val = payload.get(key);
        if (val == null) return null;
        if (val instanceof Map) {
            Object sub = ((Map<?, ?>) val).get("email");
            return sub != null ? sub.toString() : val.toString();
        }
        return val.toString();
    }
}
