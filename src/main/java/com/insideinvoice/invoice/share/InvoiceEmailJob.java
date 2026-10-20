package com.insideinvoice.invoice.share;

import com.insideinvoice.email.EmailAttachment;
import java.util.List;

/**
 * Plain-data snapshot of one queued send. Built SYNCHRONOUSLY by
 * {@link InvoiceEmailService} (HTML rendered, PDF bytes already read off the
 * request) and handed to the async pool — no MultipartFile, no lazy entity
 * crosses the thread boundary.
 */
public record InvoiceEmailJob(
        Long invoiceId,
        String recipient,
        String fromName,
        String subject,
        String html,
        String text,
        String replyTo,
        List<EmailAttachment> attachments) {
}
