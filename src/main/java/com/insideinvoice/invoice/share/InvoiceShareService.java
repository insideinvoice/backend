package com.insideinvoice.invoice.share;

import com.insideinvoice.invoice.share.dto.ShareLinkResponse;

public interface InvoiceShareService {

    /** Create the link if absent, or return the existing active link. Revoked links rotate to a new token. */
    ShareLinkResponse createOrRetrieve(Long invoiceId, Long businessId);

    /** Disable sharing. The token stops resolving on both public endpoints immediately. */
    ShareLinkResponse revoke(Long invoiceId, Long businessId);

    /** Issue a replacement token and invalidate the previous one. */
    ShareLinkResponse regenerate(Long invoiceId, Long businessId);
}
