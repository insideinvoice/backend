package com.insideinvoice.deliverychallan.repository;

import com.insideinvoice.deliverychallan.entity.DeliveryChallan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeliveryChallanRepository extends JpaRepository<DeliveryChallan, Long> {

    Page<DeliveryChallan> findByBusinessId(Long businessId, Pageable pageable);

    Optional<DeliveryChallan> findByIdAndBusinessId(Long id, Long businessId);

    boolean existsByBusinessIdAndChallanNumber(Long businessId, String challanNumber);

    void deleteByBusinessId(Long businessId);
}
