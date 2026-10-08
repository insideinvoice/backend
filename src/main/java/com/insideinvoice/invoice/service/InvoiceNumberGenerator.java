package com.insideinvoice.invoice.service;

import com.insideinvoice.business.entity.Business;
import com.insideinvoice.business.repository.BusinessRepository;
import com.insideinvoice.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class InvoiceNumberGenerator {

    private static final Logger log = LoggerFactory.getLogger(InvoiceNumberGenerator.class);

    private static final Pattern TRAILING_SEPARATORS = Pattern.compile("[-_/.]+$");
    private static final Pattern TRAILING_DIGITS = Pattern.compile("^(.*?)(\\d+)$");

    private final BusinessRepository businessRepository;

    @Transactional
    public String generateNextInvoiceNumber(Long businessId) {
        Business business = businessRepository.findByIdWithLock(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business", "id", businessId));

        Long sequence = business.getNextInvoiceSequence();
        String invoiceNumber = formatInvoiceNumber(business.getInvoicePrefix(), sequence);
        assertWithinColumnLimit(invoiceNumber);

        business.setNextInvoiceSequence(sequence + 1);
        businessRepository.save(business);

        log.debug("Generated invoice number: {} for businessId: {}", invoiceNumber, businessId);
        return invoiceNumber;
    }

    /**
     * invoices.invoice_number is VARCHAR(50) (V1:81) while businesses.invoice_prefix is
     * VARCHAR(50) (V17): a long convention (e.g. "INV-RSHWE-2026-00001") overflowed the
     * column and turned every future invoice creation for that business into a
     * DataIntegrityViolation → 500. Fail fast with an actionable 400 instead.
     */
    private static void assertWithinColumnLimit(String invoiceNumber) {
        if (invoiceNumber.length() > 50) {
            throw new com.insideinvoice.exception.BadRequestException(
                    "Invoice number '" + invoiceNumber + "' exceeds 50 characters. "
                            + "Please shorten the invoice prefix/convention in business settings.");
        }
    }

    @Transactional
    public void reserveNextSequence(Long businessId) {
        Business business = businessRepository.findByIdWithLock(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business", "id", businessId));

        business.setNextInvoiceSequence(business.getNextInvoiceSequence() + 1);
        businessRepository.save(business);

        log.debug("Reserved next invoice sequence for businessId: {}", businessId);
    }

    /**
     * Builds an invoice number from the business's saved invoice convention.
     *
     * Examples: "INV-RSHWE" -&gt; INV-RSHWE-1, INV-RSHWE-2...
     *           "INV"        -&gt; INV-1, INV-2...
     *           "INV-2026"   -&gt; INV-2026-1, INV-2026-2...
     *           "INV-00001"  -&gt; INV-00001, INV-00002... (trailing zero-padded digits = padding width)
     *           blank/null   -&gt; 1, 2, 3...
     */
    public static String formatInvoiceNumber(String convention, long sequence) {
        Convention parsed = parseConvention(convention);
        String number = parsed.padding() > 0
                ? String.format("%0" + parsed.padding() + "d", sequence)
                : Long.toString(sequence);
        return parsed.prefix().isEmpty() ? number : parsed.prefix() + "-" + number;
    }

    public static Convention parseConvention(String convention) {
        String s = convention == null ? "" : convention.trim();
        s = TRAILING_SEPARATORS.matcher(s).replaceAll("");
        Matcher m = TRAILING_DIGITS.matcher(s);
        if (m.matches()) {
            String digits = m.group(2);
            if (digits.length() >= 2 && digits.charAt(0) == '0') {
                String prefix = TRAILING_SEPARATORS.matcher(m.group(1)).replaceAll("");
                return new Convention(prefix, digits.length());
            }
        }
        return new Convention(s, 0);
    }

    public record Convention(String prefix, int padding) {
    }
}
