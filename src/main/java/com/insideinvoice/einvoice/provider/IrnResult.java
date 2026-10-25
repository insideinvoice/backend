package com.insideinvoice.einvoice.provider;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Outcome of a Generate-IRN call. {@code success} is true only when the
 * provider actually registered the document; mock results carry
 * {@code mock=true} and mock-stamped identifiers so they can never be
 * mistaken for government data. The raw provider response travels in
 * {@code raw} (persisted for audit, never shown with secrets).
 */
public record IrnResult(
        boolean success,
        boolean mock,
        String irn,
        String ackNo,
        LocalDateTime ackDate,
        String signedQr,
        String errorCode,
        String errorMessage,
        Map<String, Object> raw) {

    public static IrnResult failure(String code, String message) {
        return new IrnResult(false, false, null, null, null, null, code, message, null);
    }
}
