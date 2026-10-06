package com.insideinvoice.invoice;

import com.insideinvoice.invoice.service.InvoiceNumberGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InvoiceConventionTest {

    @Test
    void companyInitialsPrefix() {
        assertEquals("INV-RSHWE-1", InvoiceNumberGenerator.formatInvoiceNumber("INV-RSHWE", 1));
        assertEquals("INV-RSHWE-2", InvoiceNumberGenerator.formatInvoiceNumber("INV-RSHWE", 2));
        assertEquals("INV-RSHWE-45", InvoiceNumberGenerator.formatInvoiceNumber("INV-RSHWE", 45));
    }

    @Test
    void shortPrefix() {
        assertEquals("INV-1", InvoiceNumberGenerator.formatInvoiceNumber("INV", 1));
        assertEquals("INV-9", InvoiceNumberGenerator.formatInvoiceNumber("INV", 9));
        assertEquals("INV-46", InvoiceNumberGenerator.formatInvoiceNumber("INV", 46));
    }

    @Test
    void literalYearPrefixStaysAndSequenceAppends() {
        assertEquals("INV-2026-1", InvoiceNumberGenerator.formatInvoiceNumber("INV-2026", 1));
        assertEquals("INV-2026-2", InvoiceNumberGenerator.formatInvoiceNumber("INV-2026", 2));
        assertEquals("INV-2026-46", InvoiceNumberGenerator.formatInvoiceNumber("INV-2026", 46));
    }

    @Test
    void trailingZeroPaddedDigitsBecomePaddingWidth() {
        assertEquals("INV-00001", InvoiceNumberGenerator.formatInvoiceNumber("INV-00001", 1));
        assertEquals("INV-00002", InvoiceNumberGenerator.formatInvoiceNumber("INV-00001", 2));
        assertEquals("INV-00100", InvoiceNumberGenerator.formatInvoiceNumber("INV-00001", 100));
        assertEquals("00001", InvoiceNumberGenerator.formatInvoiceNumber("00001", 1));
        assertEquals("INV-2026-001", InvoiceNumberGenerator.formatInvoiceNumber("INV-2026-002", 1));
        assertEquals("INV-2026-010", InvoiceNumberGenerator.formatInvoiceNumber("INV-2026-002", 10));
        assertEquals("INV-01", InvoiceNumberGenerator.formatInvoiceNumber("INV-02", 1));
        assertEquals("INV-02", InvoiceNumberGenerator.formatInvoiceNumber("INV-02", 2));
        assertEquals("INV-RSHWE-00001", InvoiceNumberGenerator.formatInvoiceNumber("INV-RSHWE-00000", 1));
    }

    @Test
    void blankConventionMeansBareIncrementingSequence() {
        assertEquals("1", InvoiceNumberGenerator.formatInvoiceNumber(null, 1));
        assertEquals("2", InvoiceNumberGenerator.formatInvoiceNumber("", 2));
        assertEquals("3", InvoiceNumberGenerator.formatInvoiceNumber("   ", 3));
        assertEquals("10", InvoiceNumberGenerator.formatInvoiceNumber(null, 10));
    }

    @Test
    void trailingSeparatorIsStripped() {
        assertEquals("INV-1", InvoiceNumberGenerator.formatInvoiceNumber("INV-", 1));
        assertEquals("INV-RSHWE-1", InvoiceNumberGenerator.formatInvoiceNumber("INV-RSHWE-", 1));
    }

    @Test
    void sequenceGrowsPastPaddingNaturally() {
        assertEquals("INV-1000", InvoiceNumberGenerator.formatInvoiceNumber("INV-001", 1000));
        assertEquals("RS-44", InvoiceNumberGenerator.formatInvoiceNumber("RS", 44));
    }
}
