package com.insideinvoice.deliverychallan.repository;

import com.insideinvoice.deliverychallan.entity.DeliveryChallanItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface DeliveryChallanItemRepository extends JpaRepository<DeliveryChallanItem, Long> {

    /** Batch-loads items for many challans in one query (avoids N+1 on the list screen). */
    @Query("SELECT it FROM DeliveryChallanItem it WHERE it.challan.id IN :challanIds ORDER BY it.challan.id, it.sno")
    List<DeliveryChallanItem> findByChallanIdIn(@Param("challanIds") Collection<Long> challanIds);
}
