package com.insideinvoice.product.repository;

import com.insideinvoice.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Page<Product> findByBusinessId(Long businessId, Pageable pageable);

    Optional<Product> findByIdAndBusinessId(Long id, Long businessId);

    boolean existsByIdAndBusinessId(Long id, Long businessId);

    void deleteByBusinessId(Long businessId);
}
