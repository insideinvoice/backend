package com.insideinvoice.deliverychallan.repository;

import com.insideinvoice.deliverychallan.entity.DeliveryChallan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DeliveryChallanRepository extends JpaRepository<DeliveryChallan, Long> {

    Page<DeliveryChallan> findByBusinessId(Long businessId, Pageable pageable);

    /**
     * Cross-tenant admin list: challan number + customer name in one joined
     * query, ordered by created_at DESC for the page index. The Pageable must
     * carry no Sort — ORDER BY lives in the SQL (native property translation).
     */
    @Query(value = "SELECT d.* FROM delivery_challans d "
            + "LEFT JOIN customers c ON c.id = d.customer_id "
            + "WHERE (:q = '' "
            + "OR LOWER(d.challan_number) LIKE LOWER('%' || :q || '%') "
            + "OR LOWER(c.name) LIKE LOWER('%' || :q || '%')) "
            + "ORDER BY d.created_at DESC",
            countQuery = "SELECT COUNT(*) FROM delivery_challans d "
                    + "LEFT JOIN customers c ON c.id = d.customer_id "
                    + "WHERE (:q = '' "
                    + "OR LOWER(d.challan_number) LIKE LOWER('%' || :q || '%') "
                    + "OR LOWER(c.name) LIKE LOWER('%' || :q || '%'))",
            nativeQuery = true)
    Page<DeliveryChallan> searchAll(@Param("q") String q, Pageable pageable);

    Optional<DeliveryChallan> findByIdAndBusinessId(Long id, Long businessId);

    boolean existsByBusinessIdAndChallanNumber(Long businessId, String challanNumber);

    void deleteByBusinessId(Long businessId);
}
