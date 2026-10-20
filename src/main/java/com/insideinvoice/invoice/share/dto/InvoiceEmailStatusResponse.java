package com.insideinvoice.invoice.share.dto;

import com.insideinvoice.invoice.entity.InvoiceEmailStatus;
import java.time.LocalDateTime;

/**
 * GET /api/invoices/{id}/email-status response body. {@code error} is null
 * unless FAILED; {@code emailedAt} is null unless SENT.
 */
public record InvoiceEmailStatusResponse(InvoiceEmailStatus status, String error, LocalDateTime emailedAt) {
}
