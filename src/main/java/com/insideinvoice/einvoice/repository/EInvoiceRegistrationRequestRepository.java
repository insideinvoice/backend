package com.insideinvoice.einvoice.repository;

import com.insideinvoice.einvoice.entity.EInvoiceRegistrationRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EInvoiceRegistrationRequestRepository extends JpaRepository<EInvoiceRegistrationRequest, Long> {

    Optional<EInvoiceRegistrationRequest> findByInvoiceId(Long invoiceId);
}
