package com.insideinvoice.invoice.share;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitUntilState;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Prints the PUBLIC SHARE PAGE to a pixel-exact PDF with headless Chromium —
 * the same React document the customer opens ({@code /i/<token>}), i.e. the
 * exact template the user selected in the UI, with the paper size, theme,
 * table styling and the per-invoice seal baked into the share payload.
 *
 * <p>This is the engine behind the "emailed PDF must be the selected template"
 * requirement for API/PAT callers ({@link InvoiceEmailService}): those callers
 * have no browser, so the server does the browsing. The engine chain is
 * user-installed Google Chrome, then Microsoft Edge, then Playwright's bundled
 * Chromium — the first available one wins, nothing is downloaded at runtime.
 * The generic PDFBox renderer ({@link PublicInvoicePdfService}) remains only
 * as the fallback for when no engine exists on the host.</p>
 */
@Service
@Slf4j
public class HeadlessInvoicePdfService {

    /** SPA start → document ready budget; the share page fetches its payload itself. */
    private static final long GOTO_TIMEOUT_MS = 45_000;
    private static final long READY_TIMEOUT_MS = 30_000;

    @Value("${app.frontend-base-url:https://insideinvoice.in}")
    private String frontendBaseUrl;

    private final ReentrantLock lifecycle = new ReentrantLock();
    private Playwright playwright;
    private Browser browser;
    private volatile boolean unusable;

    /**
     * @return the PDF bytes of the exact share-page template, or {@code null}
     * when no engine is available / the render failed (caller falls back).
     */
    public byte[] render(String shareToken, String invoiceNumber) {
        if (unusable) return null;
        lifecycle.lock();
        try {
            ensureStarted();
            if (browser == null) return null;
            return doRender(shareToken, invoiceNumber);
        } catch (Exception e) {
            log.warn("Headless template render failed for invoice {} — falling back to the generic PDF renderer",
                    invoiceNumber, e);
            return null;
        } finally {
            lifecycle.unlock();
        }
    }

    /** One Playwright driver per JVM; channels avoid any browser download. */
    private void ensureStarted() {
        if (browser != null) return;
        long started = System.currentTimeMillis();
        playwright = Playwright.create();
        // chrome/msedge = the user's installed browsers (Playwright only picks
        // their binary; headless is Playwright's default, so nothing flashes on screen).
        for (String channel : new String[]{"chrome", "msedge"}) {
            try {
                browser = playwright.chromium().launch(
                        new BrowserType.LaunchOptions().setChannel(channel));
                log.info("Headless invoice PDF engine: system {} ({} ms)", channel, System.currentTimeMillis() - started);
                return;
            } catch (PlaywrightException e) {
                log.debug("Headless channel {} unavailable: {}", channel, e.getMessage());
            }
        }
        try {
            browser = playwright.chromium().launch();
            log.info("Headless invoice PDF engine: bundled Chromium ({} ms)", System.currentTimeMillis() - started);
            return;
        } catch (PlaywrightException e) {
            unusable = true;
            log.info("No headless browser engine found (Chrome/Edge/Chromium) — emailed PDFs use the generic renderer");
        }
    }

    private byte[] doRender(String shareToken, String invoiceNumber) throws Exception {
        try (BrowserContext context = browser.newContext(
                new Browser.NewContextOptions().setViewportSize(1400, 2000))) {
            Page page = context.newPage();
            page.goto(frontendBaseUrl + "/i/" + shareToken + "?print=1",
                    new Page.GotoOptions()
                            .setTimeout(GOTO_TIMEOUT_MS)
                            .setWaitUntil(WaitUntilState.COMMIT));
            // The share page itself signals when the document is laid out and
            // @page has been pinned — no magic sleeps, no image races.
            page.waitForSelector("body[data-ii-print-ready='true']",
                    new Page.WaitForSelectorOptions().setTimeout(READY_TIMEOUT_MS));
            // Chrome marks progress in the title: "ii-print|<paperSize>|<template>"
            String label = page.title();
            byte[] pdf = page.pdf(new Page.PdfOptions()
                    .setPrintBackground(true)
                    .setPreferCSSPageSize(true));
            log.info("Headless template PDF rendered | invoice: {} | sheet: {} | bytes: {}",
                    invoiceNumber, label, pdf.length);
            dumpDebugPdf(pdf, invoiceNumber);
            return pdf;
        }
    }

    /**
     * Local-only visual sink (II_PDF_DEBUG_DIR env): writes the rendered sheet
     * next to the logs so the output can be eyeballed against the template.
     */
    private void dumpDebugPdf(byte[] pdf, String invoiceNumber) {
        String dir = System.getenv("II_PDF_DEBUG_DIR");
        if (dir == null || dir.isBlank()) return;
        try {
            Path target = Path.of(dir, "invoice-"
                    + (invoiceNumber == null ? "unknown" : invoiceNumber)
                    + "-" + Instant.now().toEpochMilli() + ".pdf");
            Files.createDirectories(target.getParent());
            Files.write(target, pdf);
            log.info("Headless PDF debug copy: {}", target);
        } catch (Exception e) {
            log.debug("Debug PDF dump failed: {}", e.getMessage());
        }
    }

    @PreDestroy
    void shutdown() {
        lifecycle.lock();
        try {
            if (browser != null) browser.close();
            if (playwright != null) playwright.close();
        } catch (Exception ignored) {
            // teardown best-effort
        } finally {
            browser = null;
            playwright = null;
            lifecycle.unlock();
        }
    }
}
