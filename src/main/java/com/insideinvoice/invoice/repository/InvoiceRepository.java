package com.insideinvoice.invoice.repository;

import com.insideinvoice.invoice.entity.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Page<Invoice> findByBusinessId(Long businessId, Pageable pageable);

    @Query("SELECT DISTINCT i FROM Invoice i LEFT JOIN FETCH i.items WHERE i.businessId = :businessId")
    List<Invoice> findAllByBusinessId(@Param("businessId") Long businessId);

    Optional<Invoice> findByIdAndBusinessId(Long id, Long businessId);

    @Query("SELECT MAX(i.invoiceNumber) FROM Invoice i WHERE i.businessId = :businessId AND i.invoiceNumber LIKE :prefix%")
    Optional<String> findLastInvoiceNumberByBusinessIdAndPrefix(@Param("businessId") Long businessId, @Param("prefix") String prefix);

    boolean existsByInvoiceNumberAndBusinessId(String invoiceNumber, Long businessId);

    void deleteByBusinessId(Long businessId);
}
