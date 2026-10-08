package com.insideinvoice.invoice.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Payload for POST /api/invoices/{id}/email. Optional: the body may be
 * omitted entirely and the server falls back to its configured
 * {@code app.frontend-base-url} when building the share link.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SendInvoiceEmailRequest {

    /**
     * Frontend origin (e.g. https://insideinvoice.in) used to build the public
     * share link inside the email. Must be an http(s) host without a path.
     */
    private String frontendOrigin;
}
