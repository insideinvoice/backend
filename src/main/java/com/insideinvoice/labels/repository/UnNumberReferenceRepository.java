package com.insideinvoice.labels.repository;

import com.insideinvoice.labels.entity.UnNumberReference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UnNumberReferenceRepository extends JpaRepository<UnNumberReference, String> {

    @Query("""
            SELECT u FROM UnNumberReference u
            WHERE LOWER(u.unNumber) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(u.properShippingName) LIKE LOWER(CONCAT('%', :q, '%'))
            ORDER BY u.unNumber
            """)
    List<UnNumberReference> search(@Param("q") String q);
}
