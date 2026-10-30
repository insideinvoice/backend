package com.insideinvoice.invoice.share;

import com.insideinvoice.config.AsyncConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.TimeUnit;

/**
 * Asynchronous invoice-email delivery.
 *
 * <p><b>Why this is a separate bean.</b> {@code @Async} is applied by a Spring
 * proxy. A service calling its OWN {@code @Async} method bypasses that proxy
 * (plain {@code this} call) and runs synchronously on the request thread —
 * silently, with no error. The async entry point must therefore live on a
 * different bean than the caller, and one async method must never call another
 * through {@code this} for the same reason.</p>
 *
 * <p><b>Why the API returns in milliseconds.</b> Delivery holds a thread for
 * 1-8s: headless Chromium renders the exact template the business selected,
 * then a mail-provider round-trip follows. Running that on the Tomcat request
 * thread couples every customer's latency to their mail provider's health, and
 * a burst can exhaust the request pool — one slow provider becomes a whole-app
 * outage. Offloading means the HTTP response is decoupled from delivery time,
 * which is how payment and commerce platforms (Stripe, Shopify) treat any
 * outbound notification.</p>
 *
 * <p><b>Transaction.</b> The worker thread inherits no transaction and no
 * thread-bound EntityManager, so every JPA read inside
 * {@link InvoiceEmailService} needs one. {@code REQUIRES_NEW} (not the default
 * propagation) is used deliberately: these sends are frequently dispatched from
 * an {@code afterCommit} hook, where the originating transaction's resources are
 * still thread-bound but already completed. Default propagation would join
 * those dead resources and fail with "Query requires transaction be in progress".</p>
 *
 * <p><b>Failure is contained.</b> Every exception is caught and logged here.
 * An exception escaping {@code @Async} is only visible in the async uncaught
 * handler — it can never reach the (already-completed) caller — so catching
 * here is what keeps failures greppable in application logs with full context.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InvoiceEmailDispatcher {

    private final InvoiceEmailService invoiceEmailService;
    private final PlatformTransactionManager transactionManager;

    /**
     * Queues delivery and returns immediately. Safe to call from a request
     * thread, an {@code afterCommit} hook, or another async worker.
     *
     * <p>There is deliberately ONE public entry point. Callers that only differ
     * in having the customer email already known simply pass it through — the
     * duplicate-signature overload this class previously had called
     * {@code sendAsync} via {@code this}, which silently defeated the proxy.</p>
     *
     * @param invoiceId      invoice to send (must already be committed)
     * @param businessId     owning business, for tenant-scoped lookups
     * @param frontendOrigin base URL used for the share link, or null for the
     *                       configured default
     * @param pdfAttachment  optional client-rendered PDF; null triggers a
     *                       server-side render of the exact template
     * @param pdfFilename    filename for the attachment, or null
     */
    @Async(AsyncConfig.INVOICE_EMAIL_EXECUTOR)
    public void sendAsync(Long invoiceId, Long businessId, String frontendOrigin,
                          byte[] pdfAttachment, String pdfFilename) {
        long start = System.nanoTime();
        // Only for the log line: the send path re-reads everything it needs
        // inside its own transaction.
        String label = "#" + invoiceId;
        try {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            tx.executeWithoutResult(status ->
                    invoiceEmailService.sendInvoiceEmail(
                            invoiceId, businessId, frontendOrigin, pdfAttachment, pdfFilename));
            log.info("Invoice email delivered | invoice: {} | {} ms | thread: {}",
                    label, elapsedMs(start), Thread.currentThread().getName());
        } catch (Exception e) {
            // Contained by design: the invoice is already saved, so a mail or
            // render failure must never surface as a request error. Logged at
            // WARN with the invoice id so it is greppable and alertable.
            log.warn("Invoice email FAILED | invoice: {} | {} ms | {}: {}",
                    label, elapsedMs(start), e.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    private static long elapsedMs(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}
