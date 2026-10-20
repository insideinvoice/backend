package com.insideinvoice.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * SQL-side sales aggregates for the dashboards (platform-wide when no
 * businessId is passed, single business otherwise). Replaces the old approach
 * of shipping EVERY invoice row to the browser so React could sum them —
 * with real data volumes that made the dashboard wait on tens of MB of JSON
 * before the first KPI painted. Bucket keys stay raw (ISO) so each screen
 * formats labels with its own locale rules.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesAnalyticsResponse {

    /** Invoices of every status (DRAFT/CANCELLED included) — "All invoices" KPI. */
    private long invoiceCount;
    /** Σ grand_total over every status — Dashboard "Total Sales". */
    private BigDecimal totalSales;

    /** Invoices excluding DRAFT/CANCELLED — net / billable counts. */
    private long billableInvoiceCount;
    /** Σ grand_total excluding DRAFT/CANCELLED — Admin console "platformSales" + workspace "All-time billed". */
    private BigDecimal netSales;
    /** Σ billable grand_total with invoice_date in the last 30 days (today-29..today). */
    private BigDecimal last30BillableSales;

    /** Months ascending from first data month (current month always present, zero-filled). */
    private List<MonthBucket> salesByMonth;
    /** Payment-mode split, largest first; empty/unknown mode normalised to "OTHER". */
    private List<PaymentBucket> salesByPayment;
    /** Last 30 days (today-29..today), zero-filled — drives daily trend + last-7-days charts. */
    private List<DayBucket> dailySales30;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MonthBucket {
        /** "YYYY-MM" */
        private String period;
        private BigDecimal sales;
        private long count;
        private BigDecimal billableSales;
        private long billableCount;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PaymentBucket {
        private String mode;
        private BigDecimal sales;
        private BigDecimal billableSales;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DayBucket {
        /** "YYYY-MM-DD" */
        private String date;
        private BigDecimal sales;
        private long count;
        private BigDecimal billableSales;
    }
}
