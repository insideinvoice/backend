package com.insideinvoice.invoice.repository;

import com.insideinvoice.invoice.entity.Invoice;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Page<Invoice> findByBusinessId(Long businessId, Pageable pageable);

    /**
     * Platform daily activity: invoices generated per day and how many distinct
     * users generated at least one (COUNT DISTINCT skips NULL createdBy rows,
     * e.g. imports), for the admin dashboard's last-30-days charts.
     */
    @Query(value = "SELECT CAST(created_at AS DATE) AS day, "
            + "COUNT(*) AS invoice_count, "
            + "COUNT(DISTINCT created_by) AS active_users "
            + "FROM invoices WHERE created_at >= :cutoff "
            + "GROUP BY 1 ORDER BY 1", nativeQuery = true)
    List<Object[]> dailyActivitySince(@Param("cutoff") OffsetDateTime cutoff);

    /**
     * Window totals: row 0 = [COUNT(DISTINCT created_by), COUNT(*)]. Distinct users
     * in the window can't be derived from the daily rows (sum-of-daily ≠ window-distinct).
     */
    @Query(value = "SELECT COUNT(DISTINCT created_by), COUNT(*) FROM invoices WHERE created_at >= :cutoff",
            nativeQuery = true)
    List<Object[]> periodTotalsSince(@Param("cutoff") OffsetDateTime cutoff);

    /**
     * Last-activity per user scoped to ONE page of users. The old platform-wide
     * GROUP BY (every {@code GET /admin/users}) scanned the whole invoices table
     * to decorate ≤25 rows; this bounded variant rides
     * {@code idx_invoices_created_by_created_at} instead.
     */
    @Query("SELECT i.createdBy, MAX(i.createdAt) FROM Invoice i "
            + "WHERE i.createdBy IS NOT NULL AND i.createdBy IN :userIds GROUP BY i.createdBy")
    List<Object[]> lastActivityForUsers(@Param("userIds") java.util.Collection<Long> userIds);

    /** Monthly invoice counts for ONE business since cutoff (row 0 = month timestamp). Admin "my invoices" chart. */
    @Query(value = "SELECT DATE_TRUNC('month', created_at) AS m, COUNT(*) FROM invoices "
            + "WHERE business_id = :businessId AND created_at >= :cutoff GROUP BY 1 ORDER BY 1", nativeQuery = true)
    List<Object[]> monthlyCountByBusiness(@Param("businessId") Long businessId, @Param("cutoff") OffsetDateTime cutoff);

    /**
     * Cross-tenant admin invoice list in ONE query: joins customer/business/owner
     * names for both search and display, ordered by created_at DESC for the page
     * index. The Pageable must carry no Sort — ORDER BY lives in the SQL so native
     * property translation is never needed. {@code q} matches invoice number,
     * customer name and business name (trigram-indexed columns, V35).
     */
    @Query(value = "SELECT i.* FROM invoices i "
            + "LEFT JOIN customers c ON c.id = i.customer_id "
            + "LEFT JOIN businesses b ON b.id = i.business_id "
            + "WHERE (:q = '' "
            + "OR LOWER(i.invoice_number) LIKE LOWER('%' || :q || '%') "
            + "OR LOWER(c.name) LIKE LOWER('%' || :q || '%') "
            + "OR LOWER(b.business_name) LIKE LOWER('%' || :q || '%')) "
            + "ORDER BY i.created_at DESC",
            countQuery = "SELECT COUNT(*) FROM invoices i "
                    + "LEFT JOIN customers c ON c.id = i.customer_id "
                    + "LEFT JOIN businesses b ON b.id = i.business_id "
                    + "WHERE (:q = '' "
                    + "OR LOWER(i.invoice_number) LIKE LOWER('%' || :q || '%') "
                    + "OR LOWER(c.name) LIKE LOWER('%' || :q || '%') "
                    + "OR LOWER(b.business_name) LIKE LOWER('%' || :q || '%'))",
            nativeQuery = true)
    Page<Invoice> searchAll(@Param("q") String q, Pageable pageable);

    /** Admin workspace list: one business's invoices with optional invoice-number search. */
    @Query("SELECT i FROM Invoice i WHERE i.businessId = :businessId "
            + "AND (:q = '' OR LOWER(i.invoiceNumber) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<Invoice> searchByBusinessId(@Param("businessId") Long businessId, @Param("q") String q, Pageable pageable);

    Optional<Invoice> findByIdAndBusinessId(Long id, Long businessId);

    /** Join-fetches items so the collection is initialized without a caller-side transaction. */
    @Query("SELECT i FROM Invoice i LEFT JOIN FETCH i.items WHERE i.id = :id AND i.businessId = :businessId")
    Optional<Invoice> findByIdAndBusinessIdWithItems(@Param("id") Long id, @Param("businessId") Long businessId);

    /** Join-fetches items so the collection is initialized without a caller-side transaction. */
    @Query("SELECT i FROM Invoice i LEFT JOIN FETCH i.items WHERE i.id = :id")
    Optional<Invoice> findByIdWithItems(@Param("id") Long id);

    /** Public lookup: only active shares resolve. NULL-safe (share_enabled is NOT NULL). */
    @Query("SELECT i FROM Invoice i WHERE i.shareToken = :token AND i.shareEnabled = true")
    Optional<Invoice> findActiveByShareToken(@Param("token") String token);

    /**
     * Locks the invoice row for share-link creation so two concurrent create-or-retrieve
     * calls serialize on the same row instead of writing two different active tokens.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Invoice i WHERE i.id = :id AND i.businessId = :businessId")
    Optional<Invoice> findByIdAndBusinessIdForUpdate(@Param("id") Long id, @Param("businessId") Long businessId);

    @Query("SELECT MAX(i.invoiceNumber) FROM Invoice i WHERE i.businessId = :businessId AND i.invoiceNumber LIKE :prefix%")
    Optional<String> findLastInvoiceNumberByBusinessIdAndPrefix(@Param("businessId") Long businessId, @Param("prefix") String prefix);

    boolean existsByInvoiceNumberAndBusinessId(String invoiceNumber, Long businessId);

    boolean existsByCustomerId(Long customerId);

    /** All invoices of a customer — loaded for the customer-delete cascade. */
    List<Invoice> findByCustomerId(Long customerId);

    boolean existsByShareToken(String shareToken);

    void deleteByBusinessId(Long businessId);
}
