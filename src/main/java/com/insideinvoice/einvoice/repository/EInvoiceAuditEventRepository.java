package com.insideinvoice.einvoice.repository;

import com.insideinvoice.einvoice.entity.EInvoiceAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EInvoiceAuditEventRepository extends JpaRepository<EInvoiceAuditEvent, Long> {

    List<EInvoiceAuditEvent> findTop50ByBusinessIdOrderByCreatedAtDescIdDesc(Long businessId);
}
