package com.insideinvoice.invoice.repository;

import com.insideinvoice.invoice.entity.Invoice;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Page<Invoice> findByBusinessId(Long businessId, Pageable pageable);

    @Query("SELECT DISTINCT i FROM Invoice i LEFT JOIN FETCH i.items WHERE i.businessId = :businessId")
    List<Invoice> findAllByBusinessId(@Param("businessId") Long businessId);

    Optional<Invoice> findByIdAndBusinessId(Long id, Long businessId);

    /** Join-fetches items so the collection is initialized without a caller-side transaction. */
    @Query("SELECT i FROM Invoice i LEFT JOIN FETCH i.items WHERE i.id = :id AND i.businessId = :businessId")
    Optional<Invoice> findByIdAndBusinessIdWithItems(@Param("id") Long id, @Param("businessId") Long businessId);

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

    boolean existsByShareToken(String shareToken);

    void deleteByBusinessId(Long businessId);
}
