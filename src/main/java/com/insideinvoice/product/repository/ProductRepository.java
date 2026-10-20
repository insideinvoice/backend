package com.insideinvoice.product.repository;

import com.insideinvoice.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Page<Product> findByBusinessId(Long businessId, Pageable pageable);

    /** Admin list search on name / HSN (trigram-indexed, V35). {@code q = ''} matches all. */
    @Query("SELECT p FROM Product p WHERE (:q = '' "
            + "OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR LOWER(p.hsn) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<Product> searchAll(@Param("q") String q, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.businessId = :businessId AND (:q = '' "
            + "OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR LOWER(p.hsn) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<Product> searchByBusinessId(@Param("businessId") Long businessId, @Param("q") String q, Pageable pageable);

    Optional<Product> findByIdAndBusinessId(Long id, Long businessId);

    boolean existsByIdAndBusinessId(Long id, Long businessId);

    void deleteByBusinessId(Long businessId);

    List<Product> findByBusinessIdAndHsn(Long businessId, String hsn);

    List<Product> findByBusinessId(Long businessId);
}
