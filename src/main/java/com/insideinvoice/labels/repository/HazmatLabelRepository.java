package com.insideinvoice.labels.repository;

import com.insideinvoice.labels.entity.HazmatLabel;
import com.insideinvoice.labels.enums.LabelStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HazmatLabelRepository extends JpaRepository<HazmatLabel, Long> {

    Optional<HazmatLabel> findByIdAndBusinessIdAndDeletedAtIsNull(Long id, Long businessId);

    long countByBusinessIdAndDeletedAtIsNull(Long businessId);

    boolean existsByBusinessIdAndLabelNumber(Long businessId, String labelNumber);

    /** All rows incl. soft-deleted — used by admin purge and label-file cleanup. */
    List<Long> findIdByBusinessId(Long businessId);

    void deleteByBusinessId(Long businessId);

    /** Soft-deleted rows keep their FK, so the pre-check must not filter deletedAt. */
    boolean existsByInvoiceId(Long invoiceId);

    Page<HazmatLabel> findByBusinessIdAndDeletedAtIsNull(Long businessId, Pageable pageable);

    @Query("""
            SELECT h FROM HazmatLabel h
            WHERE h.businessId = :businessId AND h.deletedAt IS NULL
              AND (:status IS NULL OR h.status = :status)
              AND (:q = '' OR LOWER(h.labelNumber) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(h.unNumber) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(h.properShippingName) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<HazmatLabel> search(@Param("businessId") Long businessId,
                             @Param("status") LabelStatus status,
                             @Param("q") String q,
                             Pageable pageable);
}
