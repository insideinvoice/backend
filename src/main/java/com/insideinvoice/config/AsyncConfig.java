package com.insideinvoice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Async infrastructure. Invoice emails are the only async workload so far:
 * the send is a bounded HTTP call to the provider that must not hold the
 * request thread (or its DB connection) for up to 30s.
 *
 * <p>A dedicated pool (NOT {@code @Async}'s default executor, which would
 * share sizing with any future async work) keeps a burst of sends from
 * starving other tasks. {@link ThreadPoolExecutor.CallerRunsPolicy} back-
 * pressures: if all 4 threads and 100 queued jobs are busy the HTTP request
 * thread runs the send itself rather than dropping the email.</p>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /** Bean name referenced by {@code @Async("invoiceEmailExecutor")} in the dispatcher. */
    public static final String INVOICE_EMAIL_EXECUTOR = "invoiceEmailExecutor";

    @Bean(name = INVOICE_EMAIL_EXECUTOR)
    public ThreadPoolTaskExecutor invoiceEmailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("invoice-email-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }
}
