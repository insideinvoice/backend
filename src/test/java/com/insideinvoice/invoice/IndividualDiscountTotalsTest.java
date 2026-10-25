package com.insideinvoice.invoice;

import com.insideinvoice.invoice.entity.DiscountType;
import com.insideinvoice.invoice.entity.Invoice;
import com.insideinvoice.invoice.entity.InvoiceItem;
import com.insideinvoice.invoice.mapper.InvoiceMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * V45 INDIVIDUAL (per-line) discount totals — the backend half of a contract
 * whose frontend half lives in inside-invoice/scripts/individualDiscount.test.mjs.
 * The two suites deliberately assert the SAME numbers on the SAME inputs, so
 * the stored invoice and every screen can never drift apart.
 *
 * Rule (CGST Act s.15(3)(b)): each line's GST applies to that line's own NET
 * value — no invoice-level pro-rating like the FULL path.
 */
class IndividualDiscountTotalsTest {

    private final InvoiceMapper mapper = new InvoiceMapper();

    private static Invoice itemInvoice(DiscountType type, Object... lines) {
        Invoice invoice = new Invoice();
        invoice.setDiscountType(type);
        invoice.setDiscountPercent(BigDecimal.ZERO);
        java.util.List<InvoiceItem> items = new ArrayList<>();
        for (int i = 0; i < lines.length; i += 3) {
            InvoiceItem item = new InvoiceItem();
            item.setTaxableValue(new BigDecimal(String.valueOf(lines[i])));
            item.setGstPercentage(new BigDecimal(String.valueOf(lines[i + 1])));
            item.setDiscountPercent(lines[i + 2] == null ? null : new BigDecimal(String.valueOf(lines[i + 2])));
            items.add(item);
        }
        invoice.setItems(items);
        return invoice;
    }

    @Test
    void perLineDiscountsWithPerLineGst() {
        // 1000 @18% with 10% off + 500 @18% with 5% off
        Invoice invoice = itemInvoice(DiscountType.INDIVIDUAL,
                1000, 18, 10,
                500, 18, 5);
        mapper.calculateInvoiceTotals(invoice);

        assertEquals(new BigDecimal("1500.00"), invoice.getSubtotal());
        // 100 + 25
        assertEquals(new BigDecimal("125.00"), mapperDiscountAmount(invoice));
        assertEquals(new BigDecimal("1375.00"), taxableOf(invoice));
        // 900×18% + 475×18% = 162.00 + 85.50
        assertEquals(new BigDecimal("247.50"), invoice.getTaxAmount());
        assertEquals(new BigDecimal("1622.50"), invoice.getGrandTotal());
    }

    @Test
    void discountOnOneLineOnly() {
        Invoice invoice = itemInvoice(DiscountType.INDIVIDUAL,
                100, 18, null,
                100, 18, null,
                100, 18, null,
                100, 18, 25);
        mapper.calculateInvoiceTotals(invoice);

        assertEquals(new BigDecimal("400.00"), invoice.getSubtotal());
        assertEquals(new BigDecimal("25.00"), mapperDiscountAmount(invoice));
        assertEquals(new BigDecimal("375.00"), taxableOf(invoice));
        // 3 × 18.00 + 75×18% = 54 + 13.50
        assertEquals(new BigDecimal("67.50"), invoice.getTaxAmount());
        assertEquals(new BigDecimal("442.50"), invoice.getGrandTotal());
    }

    @Test
    void perLinePercentIsClampedToHundred() {
        Invoice invoice = itemInvoice(DiscountType.INDIVIDUAL, 200, 18, 150);
        mapper.calculateInvoiceTotals(invoice);

        assertEquals(new BigDecimal("200.00"), mapperDiscountAmount(invoice));
        assertEquals(new BigDecimal("0.00"), taxableOf(invoice));
        assertEquals(new BigDecimal("0.00"), invoice.getTaxAmount());
        assertEquals(new BigDecimal("0.00"), invoice.getGrandTotal());
    }

    @Test
    void roundingMatchesFrontendToThePaisa() {
        // 33.33 × 33.33% → 11.1089 → HALF_UP 11.11; net 22.22 × 18% → 3.9996 → 4.00
        Invoice invoice = itemInvoice(DiscountType.INDIVIDUAL, 33.33, 18, 33.33);
        mapper.calculateInvoiceTotals(invoice);

        assertEquals(new BigDecimal("11.11"), mapperDiscountAmount(invoice));
        assertEquals(new BigDecimal("22.22"), taxableOf(invoice));
        assertEquals(new BigDecimal("4.00"), invoice.getTaxAmount());
    }

    @Test
    void cessRidesOnTheDiscountedBase() {
        Invoice invoice = itemInvoice(DiscountType.INDIVIDUAL, 1000, 18, 10);
        invoice.setCessPercentage(new BigDecimal("5"));
        mapper.calculateInvoiceTotals(invoice);

        assertEquals(new BigDecimal("45.00"), invoice.getCessAmount());
        // 900 + 162 + 45
        assertEquals(new BigDecimal("1107.00"), invoice.getGrandTotal());
    }

    @Test
    void legacyNullTypeKeepsTheOriginalProRataFlow() {
        Invoice invoice = itemInvoice(null, 1000, 18, 10);
        invoice.setDiscountPercent(new BigDecimal("10"));
        mapper.calculateInvoiceTotals(invoice);

        assertEquals(new BigDecimal("100.00"), mapperDiscountAmount(invoice));
        assertEquals(new BigDecimal("900.00"), taxableOf(invoice));
        // pro-rated: 1000 × 0.9 × 18%
        assertEquals(new BigDecimal("162.00"), invoice.getTaxAmount());
        assertEquals(new BigDecimal("1062.00"), invoice.getGrandTotal());
    }

    @Test
    void normalizeForcesInvoiceLevelPercentToZeroOnIndividual() {
        assertEquals(BigDecimal.ZERO,
                InvoiceMapper.normalizeInvoiceDiscount(DiscountType.INDIVIDUAL, new BigDecimal("15")));
        assertEquals(new BigDecimal("15"),
                InvoiceMapper.normalizeInvoiceDiscount(DiscountType.FULL, new BigDecimal("15")));
        assertEquals(BigDecimal.ZERO, InvoiceMapper.normalizeInvoiceDiscount(null, null));
    }

    private BigDecimal mapperDiscountAmount(Invoice invoice) {
        return InvoiceMapper.discountAmount(invoice);
    }

    private static BigDecimal taxableOf(Invoice invoice) {
        return invoice.getSubtotal().subtract(InvoiceMapper.discountAmount(invoice));
    }
}
