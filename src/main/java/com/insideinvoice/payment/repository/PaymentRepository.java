package com.insideinvoice.payment.repository;

import com.insideinvoice.payment.entity.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Page<Payment> findByBusinessId(Long businessId, Pageable pageable);

    List<Payment> findByBusinessIdOrderByCreatedAtDesc(Long businessId);

    List<Payment> findByInvoiceId(Long invoiceId);
}
