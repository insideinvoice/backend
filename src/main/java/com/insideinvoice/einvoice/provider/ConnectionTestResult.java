package com.insideinvoice.einvoice.provider;

/**
 * Outcome of a provider connectivity test. Statuses are persisted on the
 * config and shown in Settings; a CONNECTED result is a transport/auth check
 * ONLY and never implies that any invoice was registered.
 */
public record ConnectionTestResult(String status, String message, boolean mock) {

    public static final String CONNECTED = "CONNECTED";
    public static final String AUTH_FAILED = "AUTH_FAILED";
    public static final String PROVIDER_UNAVAILABLE = "PROVIDER_UNAVAILABLE";
    public static final String NOT_TESTED = "NOT_TESTED";
}
