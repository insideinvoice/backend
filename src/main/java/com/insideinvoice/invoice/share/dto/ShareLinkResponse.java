package com.insideinvoice.invoice.share.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Owner-facing view of an invoice's public share link.
 * The raw token is only ever returned to the authenticated owner of the invoice.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShareLinkResponse {

    private String token;
    private boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime revokedAt;
}
