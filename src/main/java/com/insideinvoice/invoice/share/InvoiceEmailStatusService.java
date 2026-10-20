package com.insideinvoice.invoice.share;

import com.insideinvoice.exception.ResourceNotFoundException;
import com.insideinvoice.invoice.entity.Invoice;
import com.insideinvoice.invoice.entity.InvoiceEmailStatus;
import com.insideinvoice.invoice.repository.InvoiceRepository;
import com.insideinvoice.invoice.share.dto.InvoiceEmailStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * All writes to the invoices email-status columns. Each method is its own
 * short transaction so a status flip commits immediately — the async
 * dispatcher and the GET endpoint never share one, and a failed status write
 * can never roll back the share-link mint (which committed earlier in
 * {@link InvoiceShareService}'s transaction).
 */
@Service
@RequiredArgsConstructor
public class InvoiceEmailStatusService {

    /** Short, user-displayable failure reason; longer provider messages are cut. */
    private static final int MAX_ERROR_CHARS = 200;

    private final InvoiceRepository invoiceRepository;

    /** Called SYNCHRONOUSLY before the async dispatch so the queued state is visible immediately. */
    @Transactional
    public void markPending(Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", invoiceId));
        invoice.setEmailStatus(InvoiceEmailStatus.PENDING);
        invoice.setEmailError(null);
        invoice.setEmailedAt(null);
        invoiceRepository.save(invoice);
    }

    /** Async: re-fetches the invoice row (fresh transaction) and records success. */
    @Transactional
    public void markSent(Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", invoiceId));
        invoice.setEmailStatus(InvoiceEmailStatus.SENT);
        invoice.setEmailError(null);
        invoice.setEmailedAt(LocalDateTime.now());
        invoiceRepository.save(invoice);
    }

    /** Async: re-fetches the invoice row and records a short failure reason. */
    @Transactional
    public void markFailed(Long invoiceId, String error) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", invoiceId));
        invoice.setEmailStatus(InvoiceEmailStatus.FAILED);
        invoice.setEmailError(truncate(error));
        invoice.setEmailedAt(null);
        invoiceRepository.save(invoice);
    }

    /**
     * Cross-tenant safe read: ownership comes from the authenticated business id.
     * An invoice never queued (NULL status) reports PENDING — the API contract
     * exposes only PENDING/SENT/FAILED.
     */
    @Transactional(readOnly = true)
    public InvoiceEmailStatusResponse getStatus(Long invoiceId, Long businessId) {
        Invoice invoice = invoiceRepository.findByIdAndBusinessId(invoiceId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", invoiceId));
        InvoiceEmailStatus status = invoice.getEmailStatus() == null
                ? InvoiceEmailStatus.PENDING : invoice.getEmailStatus();
        return new InvoiceEmailStatusResponse(status, invoice.getEmailError(), invoice.getEmailedAt());
    }

    private static String truncate(String error) {
        if (error == null || error.isBlank()) {
            return "Unknown error";
        }
        String trimmed = error.trim();
        return trimmed.length() <= MAX_ERROR_CHARS ? trimmed : trimmed.substring(0, MAX_ERROR_CHARS);
    }
}
