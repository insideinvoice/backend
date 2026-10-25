package com.insideinvoice.einvoice.repository;

import com.insideinvoice.einvoice.entity.EInvoiceRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EInvoiceRegistrationRepository extends JpaRepository<EInvoiceRegistration, Long> {

    Optional<EInvoiceRegistration> findFirstByInvoiceIdOrderByCreatedAtDescIdDesc(Long invoiceId);

    List<EInvoiceRegistration> findTop20ByBusinessIdOrderByCreatedAtDescIdDesc(Long businessId);

    boolean existsByInvoiceIdAndStatus(Long invoiceId, com.insideinvoice.einvoice.entity.EInvoiceStatus status);
}
