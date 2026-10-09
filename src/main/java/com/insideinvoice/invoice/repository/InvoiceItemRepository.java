package com.insideinvoice.invoice.repository;

import com.insideinvoice.invoice.entity.InvoiceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface InvoiceItemRepository extends JpaRepository<InvoiceItem, Long> {

    List<InvoiceItem> findByInvoiceId(Long invoiceId);

    /** Batch-loads items for many invoices in one query (avoids N+1 on the list screen). */
    @Query("SELECT it FROM InvoiceItem it WHERE it.invoice.id IN :invoiceIds ORDER BY it.invoice.id, it.sno")
    List<InvoiceItem> findByInvoiceIdIn(@Param("invoiceIds") Collection<Long> invoiceIds);

    void deleteByInvoiceId(Long invoiceId);
}
