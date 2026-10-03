package com.insideinvoice.contact.controller;

import com.insideinvoice.auth.dto.response.ApiResponse;
import com.insideinvoice.contact.dto.ContactRequest;
import com.insideinvoice.contact.entity.Contact;
import com.insideinvoice.contact.repository.ContactRepository;
import com.insideinvoice.email.EmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/contact")
@RequiredArgsConstructor
public class ContactController {

    private static final Logger log = LoggerFactory.getLogger(ContactController.class);

    private final ContactRepository contactRepository;
    private final EmailService emailService;

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> submitContact(@Valid @RequestBody ContactRequest request) {
        Contact contact = Contact.builder()
                .name(request.getName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .message(request.getMessage())
                .createdAt(LocalDateTime.now())
                .build();
        contactRepository.save(contact);

        log.info("Contact form submission from: {} ({})", request.getName(), request.getEmail());

        try {
            emailService.sendContactEmail(request.getName(), request.getEmail(), request.getPhone(), request.getMessage());
        } catch (Exception e) {
            log.error("Failed to send contact notification email", e);
        }

        return ResponseEntity.ok(ApiResponse.success("Thank you for contacting us. We'll get back to you shortly."));
    }
}
