package com.insideinvoice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Background work execution for fire-and-forget operations (invoice email
 * delivery, e-invoice registration, PDF rendering).
 *
 * <p><b>Why a dedicated pool and not the default.</b> Spring Boot's auto-configured
 * {@code applicationTaskExecutor} is effectively unbounded (Integer.MAX_VALUE
 * queue, elastic max pool). Under a burst that is indistinguishable from
 * unlimited: every queued task holds its payload in memory until it runs, so a
 * slow downstream (a mail provider degrading, headless Chromium wedging) turns
 * into heap exhaustion and then a full-app outage. Every task here is I/O-bound
 * — it waits on a network call or a browser subprocess — so a small bounded
 * pool absorbs real concurrency while capping worst-case memory.</p>
 *
 * <p><b>Sizing.</b> 4 core / 8 max / 100 queued. The dominant cost is the wait,
 * not the CPU: invoice email holds a worker for ~1-8s (headless render + SMTP
 * API round-trip) while consuming almost no CPU. 100 queued bounds a bad
 * afternoon at ~100 × (invoiceId + a browser-rendered PDF ≈ a few MB worst
 * case) — a known, survivable ceiling instead of a heap spike.</p>
 *
 * <p><b>Rejection.</b> When the pool and queue are both full the task is
 * REFUSED, loudly, and the refusal is swallowed. These operations are
 * best-effort by contract: an email that cannot even be queued is an
 * operational event to alert on, never a reason to fail an HTTP request that
 * has already succeeded (the invoice is legally saved). Caller-runs is
 * deliberately avoided — it would re-introduce the original failure mode,
 * blocking a Tomcat request thread on an 8-second browser render.</p>
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    /** Bean name referenced by {@code @Async("invoiceEmailExecutor")}. */
    public static final String INVOICE_EMAIL_EXECUTOR = "invoiceEmailExecutor";

    @Bean(name = INVOICE_EMAIL_EXECUTOR)
    public ThreadPoolTaskExecutor invoiceEmailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        // Named threads: in a thread dump or APM flame graph a stuck delivery
        // must be identifiable as ours, not mistaken for a request thread.
        executor.setThreadNamePrefix("invoice-email-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // Give in-flight sends a bounded window to finish across a deploy.
        executor.setAwaitTerminationSeconds(15);
        executor.setRejectedExecutionHandler(loudButHarmlessRefusal());
        executor.initialize();
        return executor;
    }

    /**
     * Full pool + full queue: log it as an operational event and drop the task.
     * Never throws back into the caller — see the class note on why.
     */
    private static RejectedExecutionHandler loudButHarmlessRefusal() {
        return (Runnable task, ThreadPoolExecutor pool) -> log.warn(
                "Background email pool saturated (active={}, queued={}) — delivery dropped, "
                        + "invoice itself is unaffected. Raise AsyncConfig capacity if this recurs.",
                pool.getActiveCount(), pool.getQueue().size());
    }
}
