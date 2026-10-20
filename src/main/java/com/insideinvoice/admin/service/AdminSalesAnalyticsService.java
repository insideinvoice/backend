package com.insideinvoice.admin.service;

import com.insideinvoice.admin.dto.SalesAnalyticsResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sales aggregates computed in SQL (GROUP BY + FILTER) instead of in the
 * browser from a full invoice download. Four bounded queries replace shipping
 * every row of the invoices table to React on every dashboard load; the
 * {@code businessId} parameter scopes the same statements to one tenant for
 * the admin user workspace. Bucketing matches the previous client-side
 * behaviour exactly: monthly/daily buckets key off <b>invoice_date</b>, and
 * DRAFT/CANCELLED rows count toward {@code totalSales}/{@code invoiceCount}
 * but not toward the billable figures.
 */
@Service
@RequiredArgsConstructor
public class AdminSalesAnalyticsService {

    private static final String NOT_BILLABLE = "status NOT IN ('DRAFT','CANCELLED')";

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public SalesAnalyticsResponse getSalesAnalytics(Long businessId) {
        LocalDate today = LocalDate.now();
        LocalDate cutoff30 = today.minusDays(29);

        Object[] totals = totals(businessId, cutoff30);
        long invoiceCount = num(totals[0]).longValue();
        BigDecimal totalSales = dec(totals[1]);
        long billableInvoiceCount = num(totals[2]).longValue();
        BigDecimal netSales = dec(totals[3]);
        BigDecimal last30BillableSales = dec(totals[4]);

        return SalesAnalyticsResponse.builder()
                .invoiceCount(invoiceCount)
                .totalSales(totalSales)
                .billableInvoiceCount(billableInvoiceCount)
                .netSales(netSales)
                .last30BillableSales(last30BillableSales)
                .salesByMonth(monthly(businessId, today))
                .salesByPayment(byPayment(businessId))
                .dailySales30(daily(businessId, cutoff30, today))
                .build();
    }

    /** Row 0 = [count, totalSales, billableCount, billableSales, last30BillableSales]. */
    private Object[] totals(Long businessId, LocalDate cutoff30) {
        String sql = "SELECT COUNT(*), COALESCE(SUM(grand_total), 0), "
                + "COUNT(*) FILTER (WHERE " + NOT_BILLABLE + "), "
                + "COALESCE(SUM(grand_total) FILTER (WHERE " + NOT_BILLABLE + "), 0), "
                + "COALESCE(SUM(grand_total) FILTER (WHERE " + NOT_BILLABLE + " AND invoice_date >= :cutoff30), 0) "
                + "FROM invoices" + scope(businessId);
        Query query = entityManager.createNativeQuery(sql);
        bindScope(query, businessId);
        query.setParameter("cutoff30", cutoff30);
        return (Object[]) query.getSingleResult();
    }

    /** Months ascending (from first data month), current month always present, zero-filled. */
    private List<SalesAnalyticsResponse.MonthBucket> monthly(Long businessId, LocalDate today) {
        String sql = "SELECT TO_CHAR(date_trunc('month', invoice_date), 'YYYY-MM') AS period, "
                + "COALESCE(SUM(grand_total), 0), COUNT(*), "
                + "COALESCE(SUM(grand_total) FILTER (WHERE " + NOT_BILLABLE + "), 0), "
                + "COUNT(*) FILTER (WHERE " + NOT_BILLABLE + ") "
                + "FROM invoices" + scope(businessId) + " GROUP BY 1 ORDER BY 1";
        Query query = entityManager.createNativeQuery(sql);
        bindScope(query, businessId);

        Map<String, SalesAnalyticsResponse.MonthBucket> byMonth = new LinkedHashMap<>();
        for (Object rowObj : query.getResultList()) {
            Object[] row = (Object[]) rowObj;
            if (row[0] == null) continue;
            byMonth.put((String) row[0], SalesAnalyticsResponse.MonthBucket.builder()
                    .period((String) row[0])
                    .sales(dec(row[1]))
                    .count(num(row[2]).longValue())
                    .billableSales(dec(row[3]))
                    .billableCount(num(row[4]).longValue())
                    .build());
        }

        // Zero-fill the tail through the current month (and any future-dated
        // data months) so "This Month" KPIs never fall back to last month's
        // number — mirrors the old client-side behaviour.
        YearMonth current = YearMonth.from(today);
        YearMonth first = byMonth.isEmpty()
                ? current
                : YearMonth.parse(byMonth.keySet().iterator().next());
        YearMonth last = current;
        if (!byMonth.isEmpty()) {
            YearMonth lastData = YearMonth.parse(byMonth.keySet().stream().reduce((a, b) -> b).orElse(current.toString()));
            if (lastData.isAfter(last)) last = lastData;
        }
        List<SalesAnalyticsResponse.MonthBucket> filled = new ArrayList<>();
        for (YearMonth m = first; !m.isAfter(last); m = m.plusMonths(1)) {
            String key = m.toString();
            filled.add(byMonth.getOrDefault(key, SalesAnalyticsResponse.MonthBucket.builder()
                    .period(key)
                    .sales(BigDecimal.ZERO)
                    .count(0L)
                    .billableSales(BigDecimal.ZERO)
                    .billableCount(0L)
                    .build()));
        }
        return filled;
    }

    private List<SalesAnalyticsResponse.PaymentBucket> byPayment(Long businessId) {
        String sql = "SELECT COALESCE(NULLIF(TRIM(payment_mode), ''), 'OTHER') AS mode, "
                + "COALESCE(SUM(grand_total), 0), "
                + "COALESCE(SUM(grand_total) FILTER (WHERE " + NOT_BILLABLE + "), 0) "
                + "FROM invoices" + scope(businessId) + " GROUP BY 1 ORDER BY 2 DESC";
        Query query = entityManager.createNativeQuery(sql);
        bindScope(query, businessId);

        List<SalesAnalyticsResponse.PaymentBucket> result = new ArrayList<>();
        for (Object rowObj : query.getResultList()) {
            Object[] row = (Object[]) rowObj;
            result.add(SalesAnalyticsResponse.PaymentBucket.builder()
                    .mode((String) row[0])
                    .sales(dec(row[1]))
                    .billableSales(dec(row[2]))
                    .build());
        }
        return result;
    }

    /** Last 30 calendar days, zero-filled day by day (today-29 .. today). */
    private List<SalesAnalyticsResponse.DayBucket> daily(Long businessId, LocalDate cutoff30, LocalDate today) {
        String sql = "SELECT invoice_date, COALESCE(SUM(grand_total), 0), COUNT(*), "
                + "COALESCE(SUM(grand_total) FILTER (WHERE " + NOT_BILLABLE + "), 0) "
                + "FROM invoices WHERE invoice_date >= :cutoff30"
                + (businessId != null ? " AND business_id = :businessId" : "")
                + " GROUP BY 1 ORDER BY 1";
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("cutoff30", cutoff30);
        bindScope(query, businessId);

        Map<LocalDate, SalesAnalyticsResponse.DayBucket> byDay = new LinkedHashMap<>();
        for (Object rowObj : query.getResultList()) {
            Object[] row = (Object[]) rowObj;
            if (row[0] == null) continue;
            byDay.put(toLocalDate(row[0]), SalesAnalyticsResponse.DayBucket.builder()
                    .date(toLocalDate(row[0]).toString())
                    .sales(dec(row[1]))
                    .count(num(row[2]).longValue())
                    .billableSales(dec(row[3]))
                    .build());
        }

        List<SalesAnalyticsResponse.DayBucket> filled = new ArrayList<>();
        for (LocalDate d = cutoff30; !d.isAfter(today); d = d.plusDays(1)) {
            filled.add(byDay.getOrDefault(d, SalesAnalyticsResponse.DayBucket.builder()
                    .date(d.toString())
                    .sales(BigDecimal.ZERO)
                    .count(0L)
                    .billableSales(BigDecimal.ZERO)
                    .build()));
        }
        return filled;
    }

    private static String scope(Long businessId) {
        return businessId == null ? "" : " WHERE business_id = :businessId";
    }

    private static void bindScope(Query query, Long businessId) {
        if (businessId != null) {
            query.setParameter("businessId", businessId);
        }
    }

    private static Number num(Object value) {
        return value instanceof Number number ? number : java.math.BigDecimal.ZERO;
    }

    private static BigDecimal dec(Object value) {
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return new BigDecimal(number.toString());
        return BigDecimal.ZERO;
    }

    /** JDBC/hibernate have returned LocalDate, java.sql.Date, Timestamp or Instant for DATE — tolerate each. */
    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate date) return date;
        if (value instanceof java.sql.Date date) return date.toLocalDate();
        if (value instanceof java.sql.Timestamp ts) return ts.toLocalDateTime().toLocalDate();
        if (value instanceof java.time.Instant instant) return instant.atZone(java.time.ZoneOffset.UTC).toLocalDate();
        throw new IllegalStateException("Unexpected DATE value from query: " + value);
    }
}
