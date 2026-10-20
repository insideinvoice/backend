package com.insideinvoice.invoice.share;

import com.insideinvoice.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Runs the provider call for a queued invoice email OFF the request thread.
 *
 * <p>This is a SEPARATE bean on purpose: {@code @Async} is a proxy feature and
 * a self-invocation ({@code this.sendAsync(...)}) would silently run
 * synchronously on the request thread. The job it receives is a plain-data
 * snapshot — the share link was already committed and the HTML already built
 * by {@link InvoiceEmailService} before the response was written, so nothing
 * here depends on the caller's transaction or a live MultipartFile. Status
 * flips happen in {@link InvoiceEmailStatusService}'s own transactions after
 * the send settles.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceEmailDispatcher {

    private final EmailService emailService;
    private final InvoiceEmailStatusService emailStatusService;

    @Async("invoiceEmailExecutor")
    public void sendAsync(InvoiceEmailJob job) {
        try {
            emailService.sendHtmlEmail(job.fromName(), job.recipient(), job.subject(),
                    job.html(), job.text(), job.replyTo(), job.attachments());
            emailStatusService.markSent(job.invoiceId());
            log.info("Invoice email sent (async) | invoiceId: {} | to: {} | parts: {}",
                    job.invoiceId(), job.recipient(), job.attachments() == null ? 0 : job.attachments().size());
        } catch (Throwable t) {
            // NOTHING may leave this method with the invoice stuck in PENDING:
            // record FAILED with a short reason. If even that write fails
            // (e.g. DB outage) the error is logged loudly for operators.
            log.error("Invoice email failed (async) | invoiceId: {} | to: {}",
                    job.invoiceId(), job.recipient(), t);
            try {
                emailStatusService.markFailed(job.invoiceId(), shortReason(t));
            } catch (Throwable statusWriteFailure) {
                log.error("Could not record FAILED status for invoice {} — it may remain PENDING",
                        job.invoiceId(), statusWriteFailure);
            }
        }
    }

    /** Short, user-displayable reason; falls back to the exception type when the message is empty. */
    private static String shortReason(Throwable t) {
        String message = t.getMessage();
        return (message == null || message.isBlank()) ? t.getClass().getSimpleName() : message;
    }
}
