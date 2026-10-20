package com.insideinvoice.invoice.share.dto;

import com.insideinvoice.invoice.entity.InvoiceEmailStatus;

/**
 * POST /api/invoices/{id}/email response body: the send is QUEUED, not done.
 * The frontend shows "queued" and polls {@code GET /{id}/email-status}.
 */
public record InvoiceEmailQueueResponse(InvoiceEmailStatus status, String recipient) {

    public static InvoiceEmailQueueResponse pending(String recipient) {
        return new InvoiceEmailQueueResponse(InvoiceEmailStatus.PENDING, recipient);
    }
}
