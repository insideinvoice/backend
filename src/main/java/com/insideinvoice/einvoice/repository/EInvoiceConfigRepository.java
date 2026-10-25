package com.insideinvoice.einvoice.repository;

import com.insideinvoice.einvoice.entity.EInvoiceConfig;
import com.insideinvoice.einvoice.entity.EInvoiceEnvironment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EInvoiceConfigRepository extends JpaRepository<EInvoiceConfig, Long> {

    List<EInvoiceConfig> findByBusinessIdOrderByUpdatedAtDesc(Long businessId);

    Optional<EInvoiceConfig> findByBusinessIdAndGstinAndEnvironment(Long businessId, String gstin, EInvoiceEnvironment environment);

    Optional<EInvoiceConfig> findFirstByBusinessIdOrderByUpdatedAtDesc(Long businessId);
}
