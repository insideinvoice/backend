package com.insideinvoice.invoice.share;

import com.insideinvoice.exception.BadRequestException;
import com.insideinvoice.exception.ResourceNotFoundException;
import com.insideinvoice.invoice.entity.Invoice;
import com.insideinvoice.invoice.repository.InvoiceRepository;
import com.insideinvoice.invoice.share.dto.ShareLinkResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class InvoiceShareServiceImpl implements InvoiceShareService {

    /**
     * 256 bits of entropy from a single shared SecureRandom (thread-safe, seeded from
     * the platform CSPRNG), encoded base64url without padding -> 43-char URL-safe token.
     * Invoice ids/numbers/timestamps are never used as token material.
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;
    private static final int MAX_MINT_ATTEMPTS = 3;

    private final InvoiceRepository invoiceRepository;

    @Override
    @Transactional
    public ShareLinkResponse createOrRetrieve(Long invoiceId, Long businessId) {
        Invoice invoice = lockOwnedInvoice(invoiceId, businessId);

        if (invoice.getShareToken() != null && Boolean.TRUE.equals(invoice.getShareEnabled())) {
            // Stable link: ordinary views/refreshes must not mint new tokens.
            return toResponse(invoice);
        }

        mintToken(invoice);
        return toResponse(invoice);
    }

    @Override
    @Transactional
    public ShareLinkResponse revoke(Long invoiceId, Long businessId) {
        Invoice invoice = lockOwnedInvoice(invoiceId, businessId);
        invoice.setShareEnabled(false);
        invoice.setShareToken(null);
        invoice.setShareRevokedAt(LocalDateTime.now());
        invoiceRepository.save(invoice);
        return toResponse(invoice);
    }

    @Override
    @Transactional
    public ShareLinkResponse regenerate(Long invoiceId, Long businessId) {
        Invoice invoice = lockOwnedInvoice(invoiceId, businessId);
        mintToken(invoice);
        return toResponse(invoice);
    }

    private Invoice lockOwnedInvoice(Long invoiceId, Long businessId) {
        // Ownership comes from the authenticated JWT business id, never from the request.
        return invoiceRepository.findByIdAndBusinessIdForUpdate(invoiceId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", invoiceId));
    }

    /**
     * Writes a fresh token in one flush. A unique-index collision (astronomically unlikely
     * at 256 bits) fails the whole transaction safely: no partial state, generic 400, and a
     * pre-check avoids reusing a token that already exists.
     */
    private void mintToken(Invoice invoice) {
        for (int attempt = 1; attempt <= MAX_MINT_ATTEMPTS; attempt++) {
            String candidate = newToken();
            if (invoiceRepository.existsByShareToken(candidate)) {
                continue;
            }
            invoice.setShareToken(candidate);
            invoice.setShareEnabled(true);
            invoice.setShareCreatedAt(LocalDateTime.now());
            invoice.setShareRevokedAt(null);
            try {
                invoiceRepository.saveAndFlush(invoice);
                return;
            } catch (DataIntegrityViolationException collision) {
                // Roll back entirely and ask the client to retry; never echo the token.
                if (attempt == MAX_MINT_ATTEMPTS) {
                    throw new BadRequestException("Could not create a share link right now. Please try again.");
                }
            }
        }
        throw new BadRequestException("Could not create a share link right now. Please try again.");
    }

    private static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static ShareLinkResponse toResponse(Invoice invoice) {
        return ShareLinkResponse.builder()
                .token(invoice.getShareToken())
                .enabled(Boolean.TRUE.equals(invoice.getShareEnabled()) && invoice.getShareToken() != null)
                .createdAt(invoice.getShareCreatedAt())
                .revokedAt(invoice.getShareRevokedAt())
                .build();
    }
}
