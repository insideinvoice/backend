package com.insideinvoice.exception;

import org.springframework.http.HttpStatus;

/**
 * Outbound email could not be handed to the provider — either the mail
 * integration is not configured (503) or the provider rejected/failed the
 * call (502). Messages are safe to show to the caller and never contain
 * credentials or provider response bodies.
 */
public class EmailDeliveryException extends RuntimeException {

    private final HttpStatus status;

    public EmailDeliveryException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
