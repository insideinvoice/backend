package com.insideinvoice.labels.repository;

import com.insideinvoice.labels.entity.ShippingLabel;
import com.insideinvoice.labels.enums.LabelStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ShippingLabelRepository extends JpaRepository<ShippingLabel, Long> {

    Optional<ShippingLabel> findByIdAndBusinessIdAndDeletedAtIsNull(Long id, Long businessId);

    long countByBusinessIdAndDeletedAtIsNull(Long businessId);

    boolean existsByBusinessIdAndLabelNumber(Long businessId, String labelNumber);

    Page<ShippingLabel> findByBusinessIdAndDeletedAtIsNull(Long businessId, Pageable pageable);

    @Query("""
            SELECT s FROM ShippingLabel s
            WHERE s.businessId = :businessId AND s.deletedAt IS NULL
              AND (:status IS NULL OR s.status = :status)
              AND (:carrier = '' OR LOWER(s.carrier) = LOWER(:carrier))
              AND (:invoiceId IS NULL OR s.invoiceId = :invoiceId)
              AND (:q = '' OR LOWER(s.labelNumber) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(s.trackingNumber) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<ShippingLabel> search(@Param("businessId") Long businessId,
                               @Param("status") LabelStatus status,
                               @Param("carrier") String carrier,
                               @Param("invoiceId") Long invoiceId,
                               @Param("q") String q,
                               Pageable pageable);
}
