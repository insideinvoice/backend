package com.insideinvoice.customer.repository;

import com.insideinvoice.customer.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Page<Customer> findByBusinessId(Long businessId, Pageable pageable);

    Optional<Customer> findByIdAndBusinessId(Long id, Long businessId);

    boolean existsByEmailAndBusinessId(String email, Long businessId);

    boolean existsByIdAndBusinessId(Long id, Long businessId);

    void deleteByBusinessId(Long businessId);
}
